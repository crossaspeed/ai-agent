package com.it.ai.aiagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.it.ai.aiagent.bean.StudyReminderTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReminderNotificationService {

    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${feishu.app-id:}")
    private String feishuAppId;

    @Value("${feishu.app-secret:}")
    private String feishuAppSecret;

    private volatile String feishuToken;
    private volatile Instant feishuTokenExpireAt = Instant.EPOCH;

    public String sendReminder(StudyReminderTask task, boolean testMode) {
        List<String> channels = Arrays.stream(task.getChannelsJson().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(String::toLowerCase)
                .toList();

        if (channels.isEmpty()) {
            throw new IllegalArgumentException("提醒渠道不能为空");
        }

        String message = buildMessage(task, testMode);
        StringBuilder sentChannels = new StringBuilder();
        StringBuilder errors = new StringBuilder();

        for (String channel : channels) {
            try {
                if ("feishu".equals(channel)) {
                    sendByFeishu(task.getFeishuOpenId(), "open_id", message);
                    sentChannels.append("feishu,");
                } else {
                    errors.append(channel).append(": 当前渠道未启用; ");
                }
            } catch (Exception e) {
                errors.append(channel).append(": ").append(rootMessage(e)).append("; ");
            }
        }

        if (sentChannels.isEmpty()) {
            throw new IllegalStateException("所有提醒渠道发送失败: " + errors);
        }

        return "发送成功渠道: " + sentChannels.substring(0, sentChannels.length() - 1);
    }

    public void sendFeishuText(String openId, String message) {
        sendByFeishu(openId, "open_id", message);
    }

    public void sendFeishuChatText(String chatId, String message) {
        sendByFeishu(chatId, "chat_id", message);
    }

    private String buildMessage(StudyReminderTask task, boolean testMode) {
        StringBuilder msg = new StringBuilder();
        if (testMode) {
            msg.append("[测试提醒] ");
        }
        msg.append("学习计划提醒\n")
                .append("时间: ").append(formatTaskTime(task)).append("\n")
                .append("主题: ").append(task.getRagTopic()).append("\n");
        if (StringUtils.hasText(task.getStudyContent())) {
            msg.append("目标: ").append(task.getStudyContent()).append("\n");
        }

        return msg.toString();
    }

    private String formatTaskTime(StudyReminderTask task) {
        LocalDateTime dateTime = task.getTriggerTime();
        if (dateTime == null) {
            return "未知";
        }
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " (" + task.getTimezone() + ")";
    }

    private void sendByFeishu(String receiveId, String receiveIdType, String message) {
        if (!StringUtils.hasText(receiveId)) {
            throw new IllegalArgumentException("飞书 receive_id 未填写");
        }
        if (!StringUtils.hasText(feishuAppId) || !StringUtils.hasText(feishuAppSecret)) {
            throw new IllegalArgumentException("飞书 appId/appSecret 未配置");
        }

        String token = getFeishuTenantToken();
        Map<String, Object> payload = new HashMap<>();
        payload.put("receive_id", receiveId);
        payload.put("msg_type", "text");
        payload.put("content", wrapFeishuText(message));

        Map<?, ?> response = webClient.post()
                .uri("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=" + receiveIdType)
                .header("Authorization", "Bearer " + token)
                .bodyValue(payload)
                // 执行请求并准备等待响应
                .retrieve()
                // 把响应体按JSON格式解析成MAP的格式
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(10)); //等待响应，等待时间最多是10s，否则抛出异常

        if (response == null || !Integer.valueOf(0).equals(response.get("code"))) {
            throw new IllegalStateException("飞书发送失败: " + response);
        }
    }

    private synchronized String getFeishuTenantToken() {
        if (StringUtils.hasText(feishuToken) && Instant.now().isBefore(feishuTokenExpireAt.minusSeconds(60))) {
            return feishuToken;
        }

        Map<String, String> payload = new HashMap<>();
        payload.put("app_id", feishuAppId);
        payload.put("app_secret", feishuAppSecret);

        Map<?, ?> response = webClient.post()
                .uri("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(10));

        if (response == null || !Integer.valueOf(0).equals(response.get("code"))) {
            throw new IllegalStateException("获取飞书 tenant_access_token 失败: " + response);
        }

        Object tokenObj = response.get("tenant_access_token");
        Object expireObj = response.get("expire");
        if (!(tokenObj instanceof String)) {
            throw new IllegalStateException("飞书 tenant_access_token 响应格式异常");
        }

        long expireSeconds = 7200;
        if (expireObj instanceof Number number) {
            expireSeconds = number.longValue();
        }

        feishuToken = (String) tokenObj;
        feishuTokenExpireAt = Instant.now().plusSeconds(expireSeconds);
        return feishuToken;
    }

    private String wrapFeishuText(String text) {
        Map<String, String> content = new HashMap<>();
        content.put("text", text);
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("构建飞书消息体失败", e);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? throwable.toString() : current.getMessage();
    }
}
