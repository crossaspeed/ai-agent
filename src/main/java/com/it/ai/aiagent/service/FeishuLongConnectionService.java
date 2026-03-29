package com.it.ai.aiagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.it.ai.aiagent.store.FeishuEventLogStore;
import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.ws.Client;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class FeishuLongConnectionService {

    private static final Logger log = LoggerFactory.getLogger(FeishuLongConnectionService.class);

    @Value("${feishu.app-id:}")
    private String feishuAppId;

    @Value("${feishu.app-secret:}")
    private String feishuAppSecret;

    @Value("${feishu.long-connection.enabled:true}")
    private boolean longConnectionEnabled;

    @Value("${feishu.long-connection.worker-threads:2}")
    private int workerThreads;

    @Value("${feishu.bot-open-id:}")
    private String feishuBotOpenId;

    @Autowired
    private FeishuPlanIntentService feishuPlanIntentService;

    @Autowired
    private ReminderNotificationService reminderNotificationService;

    @Autowired
    private FeishuEventLogStore feishuEventLogStore;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ExecutorService executorService;
    private Thread longConnectionThread;

    @PostConstruct
    public void start() {
        if (!longConnectionEnabled) {
            log.info("飞书长连接模式已禁用 (feishu.long-connection.enabled=false)");
            return;
        }
        if (!StringUtils.hasText(feishuAppId) || !StringUtils.hasText(feishuAppSecret)) {
            log.warn("飞书长连接未启动：appId/appSecret 未配置");
            return;
        }

        // 长连接回调要求 3 秒内完成，主回调线程只做轻量工作，重逻辑丢给线程池。
        executorService = Executors.newFixedThreadPool(Math.max(1, workerThreads));

        // 使用 SDK 事件分发器注册「接收消息 v2.0」处理器。
        EventDispatcher dispatcher = EventDispatcher.newBuilder("", "")
                .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                    @Override
                    public void handle(P2MessageReceiveV1 event) {
                        // SDK 对象先转成 JSON，再走统一解析路径，便于后续日志与调试。
                        String raw = Jsons.DEFAULT.toJson(event.getEvent());
                        executorService.submit(() -> processMessageEvent(raw));
                    }
                })
                .build();

        // 建立飞书长连接客户端并在后台线程启动，避免阻塞 Spring 主线程。
        Client client = new Client.Builder(feishuAppId, feishuAppSecret)
                .eventHandler(dispatcher)
                .build();

        longConnectionThread = new Thread(client::start, "feishu-long-connection");
        longConnectionThread.setDaemon(true);
        longConnectionThread.start();
        log.info("飞书长连接客户端已启动");
    }

    @PreDestroy
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    private void processMessageEvent(String raw) {
        // 1) 解析事件结构。
        Map<String, Object> root = readMap(raw);
        Map<String, Object> header = asMap(root.get("header"));
        Map<String, Object> event = asMap(root.get("event"));
        if (event.isEmpty() && root.containsKey("message")) {
            event = root;
        }

        Map<String, Object> message = asMap(event.get("message"));
        Map<String, Object> sender = asMap(event.get("sender"));
        Map<String, Object> senderId = asMap(sender.get("sender_id"));

        String eventId = asString(header.get("event_id"));
        String eventType = asString(header.get("event_type"));
        String messageId = asString(message.get("message_id"));
        String chatId = asString(message.get("chat_id"));
        String openId = asString(senderId.get("open_id"));
        String messageType = asString(message.get("message_type"));
        String chatType = asString(message.get("chat_type"));
        String content = asString(message.get("content"));

        if (!StringUtils.hasText(eventId)) {
            eventId = StringUtils.hasText(messageId) ? messageId : UUID.randomUUID().toString();
        }
        if (!StringUtils.hasText(eventType)) {
            eventType = "im.message.receive_v1";
        }

        // 2) 幂等去重：同一个 event_id 只处理一次。
        boolean inserted = feishuEventLogStore.tryInsertEvent(eventId, messageId, openId, eventType, raw);
        if (!inserted) {
            return;
        }

        // 3) 仅处理文本消息，图片/文件/卡片等先忽略。
        if (!"text".equals(messageType)) {
            feishuEventLogStore.markStatus(eventId, 2, "仅处理文本消息");
            return;
        }

        // 4) 私聊直接处理；群聊必须 @ 到机器人才处理，避免刷屏。
        if (!isP2P(chatType) && !isGroupMentioned(message, content)) {
            feishuEventLogStore.markStatus(eventId, 2, "群聊中未@机器人，忽略");
            return;
        }

        try {
            // 5) 清洗文本（移除 @ 提及占位符），再进入意图识别与计划创建。
            String userText = normalizeUserText(content, message);
            FeishuPlanIntentService.PlanProcessResult result = feishuPlanIntentService.processPlanIntent(openId, userText);
            if (!result.handled()) {
                feishuEventLogStore.markStatus(eventId, 2, result.message());
                return;
            }

            // 6) 把处理结果回复给用户：私聊回个人，群聊回群。
            if (result.success()) {
                sendReply(chatType, openId, chatId, result.message());
                feishuEventLogStore.markStatus(eventId, 1, null);
            } else {
                sendReply(chatType, openId, chatId, "学习计划处理失败：" + result.message());
                feishuEventLogStore.markStatus(eventId, 3, result.message());
            }
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "处理失败" : e.getMessage();
            feishuEventLogStore.markStatus(eventId, 3, truncate(msg, 900));
            log.error("飞书长连接事件处理失败: eventId={}, error={}", eventId, msg, e);
        }
    }

    private String extractTextFromContent(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        try {
            Map<String, Object> map = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
            return asString(map.get("text"));
        } catch (Exception ignored) {
            return content;
        }
    }

    private String normalizeUserText(String content, Map<String, Object> message) {
        String text = extractTextFromContent(content);
        List<Object> mentions = asList(message.get("mentions"));
        // 飞书群聊里 @ 会在文本里放占位 key，这里移除占位避免影响 LLM 抽取。
        for (Object mentionObj : mentions) {
            Map<String, Object> mention = asMap(mentionObj);
            String key = asString(mention.get("key"));
            if (StringUtils.hasText(key)) {
                text = text.replace(key, " ");
            }
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private boolean isP2P(String chatType) {
        return "p2p".equalsIgnoreCase(chatType);
    }

    private boolean isGroupMentioned(Map<String, Object> message, String content) {
        List<Object> mentions = asList(message.get("mentions"));
        if (!mentions.isEmpty()) {
            // 配了 bot-open-id 时做精确判断：必须 @ 到当前机器人。
            if (StringUtils.hasText(feishuBotOpenId)) {
                for (Object mentionObj : mentions) {
                    Map<String, Object> mention = asMap(mentionObj);
                    Map<String, Object> idMap = asMap(mention.get("id"));
                    String openId = asString(idMap.get("open_id"));
                    if (feishuBotOpenId.equals(openId)) {
                        return true;
                    }
                }
                return false;
            }
            // 未配置 bot-open-id 时，mentions 非空即认为命中。
            return true;
        }
        // 兜底：某些场景 mentions 可能为空，保守用文本中是否包含 @ 判断。
        String text = extractTextFromContent(content);
        return text.contains("@");
    }

    private void sendReply(String chatType, String openId, String chatId, String message) {
        if (isP2P(chatType)) {
            reminderNotificationService.sendFeishuText(openId, message);
            return;
        }

        // 群聊优先回群；若拿不到 chatId，再退化为回发给消息发送者。
        if (StringUtils.hasText(chatId)) {
            reminderNotificationService.sendFeishuChatText(chatId, message);
        } else {
            reminderNotificationService.sendFeishuText(openId, message);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            return (Map<String, Object>) mapValue;
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object value) {
        if (value instanceof List<?> listValue) {
            return (List<Object>) listValue;
        }
        return List.of();
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
