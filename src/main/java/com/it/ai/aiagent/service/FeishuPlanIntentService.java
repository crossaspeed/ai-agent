package com.it.ai.aiagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.it.ai.aiagent.assistant.StudyPlanExtractAgent;
import com.it.ai.aiagent.bean.StudyPlanCreateRequest;
import com.it.ai.aiagent.bean.StudyPlanDayRequest;
import com.it.ai.aiagent.bean.StudyPlanExtractResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FeishuPlanIntentService {

    @Autowired
    private StudyPlanExtractAgent studyPlanExtractAgent;

    @Autowired
    private StudyPlanService studyPlanService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlanProcessResult processPlanIntent(String openId, String userText) {
        // 先做意图筛选，避免把普通聊天都送去计划创建。
        if (!isPlanIntent(userText)) {
            return PlanProcessResult.ignored("未命中学习计划意图");
        }

        // 1) 优先尝试 LLM 抽取结构化计划。
        StudyPlanExtractResult extracted = tryExtractByAi(userText);
        // 2) 标准化 + 兜底规则解析（支持“主题依次是 ...”）。
        List<StudyPlanDayRequest> normalizedDays = normalizeDays(extracted == null ? null : extracted.getDays(), userText);
        // 3) 防止提取到过去时间，统一顺延到未来可执行时间。
        normalizedDays = ensureFutureSchedule(normalizedDays);
        if (normalizedDays.isEmpty()) {
            return PlanProcessResult.failed("没有抽取到可用的学习计划日期与主题");
        }

        // 4) 复用现有学习计划服务落库，渠道固定飞书。
        StudyPlanCreateRequest request = new StudyPlanCreateRequest();
        request.setPlanName(extracted != null && StringUtils.hasText(extracted.getPlanName()) ? extracted.getPlanName() : "飞书学习计划");
        request.setTimezone(extracted != null && StringUtils.hasText(extracted.getTimezone()) ? extracted.getTimezone() : "Asia/Shanghai");
        request.setFeishuOpenId(openId);
        request.setChannels(List.of("feishu"));
        request.setDays(normalizedDays);

        Map<String, Object> created = studyPlanService.createWeeklyPlan(request);
        Object count = created.getOrDefault("created", 0);

        String firstDate = normalizedDays.get(0).getDate();
        String firstTime = normalizedDays.get(0).getReminderTime();
        String reply = buildSuccessReply(count, firstDate, firstTime, normalizedDays);
        return PlanProcessResult.success(reply);
    }

    private String buildSuccessReply(Object createdCount,
                                     String firstDate,
                                     String firstTime,
                                     List<StudyPlanDayRequest> days) {
        // 创建成功后返回可核对的明细，降低“创建了什么”不透明感。
        StringBuilder builder = new StringBuilder();
        builder.append("学习计划已创建成功，共 ")
                .append(createdCount)
                .append(" 条。首条提醒时间：")
                .append(firstDate)
                .append(" ")
                .append(firstTime)
                .append("\n\n")
                .append("以下是本次计划明细：\n");

        int maxCount = Math.min(7, days.size());
        for (int i = 0; i < maxCount; i++) {
            StudyPlanDayRequest day = days.get(i);
            builder.append(i + 1)
                    .append(". ")
                    .append(day.getDate())
                    .append(" ")
                    .append(day.getReminderTime())
                    .append(" - ")
                    .append(day.getRagTopic());
            if (StringUtils.hasText(day.getStudyContent())) {
                builder.append("（").append(day.getStudyContent()).append("）");
            }
            builder.append("\n");
        }

        String text = builder.toString().trim();
        // 飞书文本消息有长度上限，超长时截断并提示。
        if (text.length() > 1800) {
            text = text.substring(0, 1800) + "\n...(内容过长，已截断)";
        }
        return text;
    }

    private boolean isPlanIntent(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("学习计划")
                || normalized.contains("接下来一周")
                || normalized.contains("提醒")
                || normalized.contains("每日")
                || normalized.contains("rag");
    }

    private StudyPlanExtractResult tryExtractByAi(String userText) {
        try {
            String raw = studyPlanExtractAgent.extract(userText);
            return objectMapper.readValue(raw, StudyPlanExtractResult.class);
        } catch (Exception e) {
            // LLM 抽取失败不抛错，后续走规则兜底。
            return null;
        }
    }

    private List<StudyPlanDayRequest> normalizeDays(List<StudyPlanDayRequest> inputDays, String rawText) {
        List<StudyPlanDayRequest> result = new ArrayList<>();
        LocalDate fallbackDate = LocalDate.now();
        if (inputDays != null) {
            // 优先使用模型返回的结构化 days。
            for (int i = 0; i < inputDays.size() && i < 7; i++) {
                StudyPlanDayRequest day = inputDays.get(i);
                if (day == null || !StringUtils.hasText(day.getRagTopic())) {
                    continue;
                }

                String date = normalizeDate(day.getDate(), fallbackDate.plusDays(i));
                String time = normalizeTime(day.getReminderTime());

                StudyPlanDayRequest normalized = new StudyPlanDayRequest();
                normalized.setDate(date);
                normalized.setReminderTime(time);
                normalized.setRagTopic(day.getRagTopic().trim());
                normalized.setStudyContent(day.getStudyContent());
                result.add(normalized);
            }
        }

        List<String> topicList = parseTopicsFromText(rawText);
        // 如果模型没抽全，且文案有“主题依次是 ...”，按主题列表重建 7 天计划。
        if (!topicList.isEmpty() && (result.isEmpty() || result.size() == 1)) {
            String defaultTime = parseDefaultTimeFromText(rawText);
            List<StudyPlanDayRequest> rebuilt = new ArrayList<>();
            for (int i = 0; i < topicList.size() && i < 7; i++) {
                StudyPlanDayRequest day = new StudyPlanDayRequest();
                day.setDate(fallbackDate.plusDays(i).toString());
                day.setReminderTime(defaultTime);
                day.setRagTopic(topicList.get(i));
                day.setStudyContent(null);
                rebuilt.add(day);
            }
            result = rebuilt;
        }

        return result;
    }

    private List<StudyPlanDayRequest> ensureFutureSchedule(List<StudyPlanDayRequest> days) {
        if (days == null || days.isEmpty()) {
            return List.of();
        }

        List<StudyPlanDayRequest> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now().minusMinutes(1);
        LocalDateTime lastTrigger = null;

        for (StudyPlanDayRequest day : days) {
            if (day == null || !StringUtils.hasText(day.getRagTopic())) {
                continue;
            }

            LocalDate date = parseDateOrFallback(day.getDate(), LocalDate.now());
            LocalTime time = parseTimeOrFallback(day.getReminderTime(), LocalTime.of(20, 0));
            LocalDateTime trigger = LocalDateTime.of(date, time);

            // 单条计划若落在过去，按天往后顺延到未来。
            while (trigger.isBefore(now)) {
                trigger = trigger.plusDays(1);
            }

            // 保证最终计划时间严格递增，避免同一天重复或逆序。
            if (lastTrigger != null && !trigger.isAfter(lastTrigger)) {
                trigger = LocalDateTime.of(lastTrigger.toLocalDate().plusDays(1), time);
                while (trigger.isBefore(now)) {
                    trigger = trigger.plusDays(1);
                }
            }

            StudyPlanDayRequest normalized = new StudyPlanDayRequest();
            normalized.setDate(trigger.toLocalDate().toString());
            normalized.setReminderTime(trigger.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            normalized.setRagTopic(day.getRagTopic().trim());
            normalized.setStudyContent(day.getStudyContent());
            result.add(normalized);
            lastTrigger = trigger;

            if (result.size() >= 7) {
                break;
            }
        }

        return result;
    }

    private List<String> parseTopicsFromText(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return List.of();
        }

        // 兼容常见口语："主题依次是 A、B、C" / "主题是 A,B,C"。
        String normalized = rawText.replace("。", "").replace("；", "，");
        String marker = "主题依次是";
        int start = normalized.indexOf(marker);
        if (start < 0) {
            marker = "主题是";
            start = normalized.indexOf(marker);
        }
        if (start < 0) {
            return List.of();
        }

        String part = normalized.substring(start + marker.length()).trim();
        if (!StringUtils.hasText(part)) {
            return List.of();
        }

        String[] segments = part.split("[、,，;；]");
        List<String> topics = new ArrayList<>();
        for (String segment : segments) {
            String topic = segment.trim();
            if (StringUtils.hasText(topic)) {
                topics.add(topic);
            }
            if (topics.size() >= 7) {
                break;
            }
        }
        return topics;
    }

    private String parseDefaultTimeFromText(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return "20:00";
        }
        // 解析“每天20:00 / 每日 20：00”这种写法。
        Pattern pattern = Pattern.compile("(?:每天|每日)\\s*(\\d{1,2}[:：]\\d{2})");
        Matcher matcher = pattern.matcher(rawText);
        if (matcher.find()) {
            String raw = matcher.group(1).replace('：', ':');
            return normalizeTime(raw);
        }
        return "20:00";
    }

    private LocalDate parseDateOrFallback(String value, LocalDate fallback) {
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private LocalTime parseTimeOrFallback(String value, LocalTime fallback) {
        try {
            String normalized = StringUtils.hasText(value) ? value.replace('：', ':') : "";
            return LocalTime.parse(normalized, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String normalizeDate(String value, LocalDate fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback.toString();
        }
        try {
            LocalDate date = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            return date.toString();
        } catch (DateTimeParseException ignored) {
            return fallback.toString();
        }
    }

    private String normalizeTime(String value) {
        if (!StringUtils.hasText(value)) {
            return "20:00";
        }
        try {
            String normalized = value.replace('：', ':');
            LocalTime time = LocalTime.parse(normalized, DateTimeFormatter.ofPattern("HH:mm"));
            return time.format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException ignored) {
            return "20:00";
        }
    }

    public record PlanProcessResult(boolean handled, boolean success, String message) {
        public static PlanProcessResult ignored(String message) {
            return new PlanProcessResult(false, false, message);
        }

        public static PlanProcessResult success(String message) {
            return new PlanProcessResult(true, true, message);
        }

        public static PlanProcessResult failed(String message) {
            return new PlanProcessResult(true, false, message);
        }
    }
}
