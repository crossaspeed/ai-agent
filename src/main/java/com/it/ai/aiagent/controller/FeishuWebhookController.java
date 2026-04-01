package com.it.ai.aiagent.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.it.ai.aiagent.service.FeishuMessageRouterService;
import com.it.ai.aiagent.service.ReminderNotificationService;
import com.it.ai.aiagent.store.FeishuEventLogStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "飞书回调")
@RestController
@RequestMapping("/feishu")
@org.springframework.web.bind.annotation.CrossOrigin(origins = "*")
public class FeishuWebhookController {

    @Autowired
    private FeishuEventLogStore feishuEventLogStore;

    @Autowired
    private FeishuMessageRouterService feishuMessageRouterService;

    @Autowired
    private ReminderNotificationService reminderNotificationService;

    @Value("${feishu.callback-verification-token:}")
    private String callbackVerificationToken;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Operation(summary = "飞书事件回调")
    @PostMapping("/event")
    public Map<String, Object> callback(@RequestBody Map<String, Object> payload) {
        // 1) URL 验证阶段：飞书会先发 challenge，原样返回即可通过校验。
        if (isUrlVerification(payload)) {
            return Map.of("challenge", payload.get("challenge"));
        }

        Map<String, Object> header = asMap(payload.get("header"));
        String eventId = asString(header.get("event_id"));
        String eventType = asString(header.get("event_type"));
        String token = extractToken(payload, header);

        // 2) 可选 token 校验，防止伪造请求。
        if (!verifyToken(token)) {
            return Map.of("code", 401, "msg", "invalid token");
        }

        // 当前控制器仅处理消息接收事件，其余事件直接忽略。
        if (!"im.message.receive_v1".equals(eventType)) {
            return Map.of("code", 0, "msg", "ignored");
        }

        Map<String, Object> event = asMap(payload.get("event"));
        Map<String, Object> sender = asMap(event.get("sender"));
        Map<String, Object> senderId = asMap(sender.get("sender_id"));
        Map<String, Object> message = asMap(event.get("message"));

        String openId = asString(senderId.get("open_id"));
        String messageId = asString(message.get("message_id"));
        String messageType = asString(message.get("message_type"));
        String chatType = asString(message.get("chat_type"));
        String content = asString(message.get("content"));

        if (!StringUtils.hasText(eventId)) {
            return Map.of("code", 0, "msg", "missing event id");
        }

        // 3) 事件幂等：同 event_id 只消费一次。
        String rawBody = toJson(payload);
        boolean inserted = feishuEventLogStore.tryInsertEvent(eventId, messageId, openId, eventType, rawBody);
        if (!inserted) {
            return Map.of("code", 0, "msg", "duplicate");
        }

        // 此 webhook 兼容路径仅处理私聊文本；群聊推荐走长连接实现。
        if (!"text".equals(messageType) || !"p2p".equals(chatType)) {
            feishuEventLogStore.markStatus(eventId, 2, "仅处理私聊文本消息");
            return Map.of("code", 0, "msg", "ignored");
        }

        try {
            // 4) 提取文本 -> 统一路由（学习计划CRUD / 知识问答）-> 回执消息。
            String userText = extractTextFromContent(content);
            FeishuMessageRouterService.RouteProcessResult result = feishuMessageRouterService.process(openId, userText);
            if (!result.handled()) {
                feishuEventLogStore.markStatus(eventId, 2, result.message());
                return Map.of("code", 0, "msg", "ignored");
            }

            if (result.success()) {
                reminderNotificationService.sendFeishuText(openId, result.message());
                feishuEventLogStore.markStatus(eventId, 1, null);
                return Map.of("code", 0, "msg", "ok");
            }

            reminderNotificationService.sendFeishuText(openId, "消息处理失败：" + result.message());
            feishuEventLogStore.markStatus(eventId, 3, result.message());
            return Map.of("code", 0, "msg", "failed");
        } catch (Exception e) {
            String error = e.getMessage() == null ? "处理失败" : e.getMessage();
            feishuEventLogStore.markStatus(eventId, 3, truncate(error, 900));
            try {
                if (StringUtils.hasText(openId)) {
                    reminderNotificationService.sendFeishuText(openId, "学习计划处理失败，请按格式重试：\n创建学习计划：明晚20:00 学 RAG 检索");
                }
            } catch (Exception ignored) {
            }
            return Map.of("code", 0, "msg", "error");
        }
    }

    private boolean isUrlVerification(Map<String, Object> payload) {
        return "url_verification".equals(asString(payload.get("type"))) && payload.get("challenge") != null;
    }

    private String extractToken(Map<String, Object> payload, Map<String, Object> header) {
        String token = asString(payload.get("token"));
        if (StringUtils.hasText(token)) {
            return token;
        }
        return asString(header.get("token"));
    }

    private boolean verifyToken(String token) {
        if (!StringUtils.hasText(callbackVerificationToken)) {
            return true;
        }
        return callbackVerificationToken.equals(token);
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            return (Map<String, Object>) mapValue;
        }
        return new HashMap<>();
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
