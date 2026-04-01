package com.it.ai.aiagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.it.ai.aiagent.assistant.StudyPlanExtractAgent;
import com.it.ai.aiagent.assistant.StudyPlanIntentAgent;
import com.it.ai.aiagent.bean.StudyPlanCreateRequest;
import com.it.ai.aiagent.bean.StudyPlanDayRequest;
import com.it.ai.aiagent.bean.StudyPlanExtractResult;
import com.it.ai.aiagent.bean.StudyPlanIntentAnalysisResult;
import com.it.ai.aiagent.bean.StudyReminderTaskView;
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

    //如果模型对某个意图的置信度分数 大于或等于 0.75，系统认为“非常确定用户的意图”
    private static final double AGENT_ROUTE_CONFIDENCE_THRESHOLD = 0.75D;
    //如果置信度分数介于 0.50 和 0.75 之间，系统认为“大概知道用户想做什么，但不是特别有把握”
    private static final double AGENT_CLARIFICATION_CONFIDENCE_THRESHOLD = 0.50D;

    @Autowired
    private StudyPlanExtractAgent studyPlanExtractAgent;

    @Autowired(required = false)
    private StudyPlanIntentAgent studyPlanIntentAgent;

    @Autowired
    private StudyPlanService studyPlanService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlanProcessResult processPlanIntent(String openId, String userText) {
        // 通过关键词的方式判断用户的意图
        PlanAction action = resolvePlanAction(userText);
        if (action == PlanAction.UNKNOWN) {
            // 通过ai的方式判断用户的意图
            AgentDecision decision = resolvePlanActionByAgent(userText);
            // 如果需要进一步的确认意图，向用户进行提问
            if (decision != null && decision.needsClarification()) {
                return PlanProcessResult.success(decision.clarificationQuestion());
            }
            if (decision != null) {
                action = decision.action();
            }
        }
        // agent判断不出意图并且不需要进行进一步的提问
        if (action == PlanAction.UNKNOWN) {
            // 判断用户是不是说了一个模糊的调整意图
            if (isAmbiguousAdjustmentIntent(userText)) {
                return PlanProcessResult.success(buildClarificationQuestion(userText, null));
            }
            return PlanProcessResult.ignored("未识别到创建/查询/修改/删除学习计划意图");
        }

        return handlePlanAction(action, openId, userText);
    }

    private PlanProcessResult handlePlanAction(PlanAction action, String openId, String userText) {
        if (action == PlanAction.QUERY) {
            return handleQuery(openId, userText);
        }

        if (action == PlanAction.DELETE) {
            return handleDelete(openId, userText);
        }

        if (action == PlanAction.UPDATE) {
            return handleUpdate(openId, userText);
        }

        return action == PlanAction.CREATE
                ? handleCreate(openId, userText)
                : PlanProcessResult.ignored("未识别到学习计划操作");
    }

    private PlanProcessResult handleCreate(String openId, String userText) {

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
        request.setSourceOpenId(openId);
        request.setSourceChannel("feishu");
        request.setChannels(List.of("feishu"));
        request.setDays(normalizedDays);

        Map<String, Object> created = studyPlanService.createWeeklyPlan(request);
        Object count = created.getOrDefault("created", 0);

        String firstDate = normalizedDays.get(0).getDate();
        String firstTime = normalizedDays.get(0).getReminderTime();
        String reply = buildSuccessReply(count, firstDate, firstTime, normalizedDays);
        return PlanProcessResult.success(reply);
    }

    private PlanAction resolvePlanAction(String text) {
        if (!StringUtils.hasText(text)) {
            return PlanAction.UNKNOWN;
        }

        // 判断是否有学习计划的标记（确定当前的意图是跟学习计划有关）
        boolean hasPlanSignal = isPlanIntent(text);

        if (isDeleteIntent(text) && hasPlanSignal) {
            return PlanAction.DELETE;
        }

        if (isUpdateIntent(text) && hasPlanSignal) {
            return PlanAction.UPDATE;
        }

        // 显式“创建”语义优先，避免“今天15:00创建计划”被错误归类成查询。
        // 通过正则表达式判断用户输入的文本中，是否包含时间词汇跟学习相关的词汇（用户是不是想要安排一个关于“学习”的计划或者日程）
        if (hasExplicitCreateVerb(text) && (hasPlanSignal || hasScheduleLearningHint(text))) {
            return PlanAction.CREATE;
        }

        if (isQueryIntent(text)) {
            return PlanAction.QUERY;
        }

        if (isCreateIntent(text)) {
            return PlanAction.CREATE;
        }

        return PlanAction.UNKNOWN;
    }

    private AgentDecision resolvePlanActionByAgent(String userText) {
        // 判断意图是否需要通过agent进行判断
        if (!shouldTryAgentFallback(userText)) {
            return null;
        }
        // 通过agent分析用户的意图
        StudyPlanIntentAnalysisResult analysis = tryAnalyzeIntentByAi(userText);
        if (analysis == null) {
            return null;
        }
        // 根据agent返回的意图进行映射
        PlanAction action = mapIntentToAction(analysis.getIntent());
        if (action == PlanAction.UNKNOWN) {
            return null;
        }
        // 判断agent返回的意图的可信度
        double confidence = normalizeConfidence(analysis.getConfidence());
        // 判断是否需要进行进一步的提问
        boolean needsClarification = Boolean.TRUE.equals(analysis.getNeedsClarification());
        // 如果可信度较高并且不需要进行进一步的提问，直接进行路由，执行相应的操作
        if (confidence >= AGENT_ROUTE_CONFIDENCE_THRESHOLD && !needsClarification) {
            return AgentDecision.route(action);
        }
        // 如果可信度不是很高 或者 agent判断需要进一步的提问
        if (confidence >= AGENT_CLARIFICATION_CONFIDENCE_THRESHOLD || needsClarification) {
            return AgentDecision.clarify(buildClarificationQuestion(userText, analysis));
        }

        return null;
    }

    /**
     * 这是一个意图筛选器，
     * @param text 用户输入的文本
     * @return
     */
    private boolean shouldTryAgentFallback(String text) {
        // 如果是空文本，不需要交给agent进行处理，直接返回空
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return isPlanIntent(text) // 判断是否跟学习计划有关
                || hasScheduleLearningHint(text) // 判断文本是否同时包含时间跟学习相关的提示词
                || hasExplicitCreateVerb(text) // 判断用户的额关键词是否包含创建一类的动词
                || text.contains("查询")
                || text.contains("修改")
                || text.contains("删除");
    }

    private PlanAction mapIntentToAction(String intent) {
        if (!StringUtils.hasText(intent)) {
            return PlanAction.UNKNOWN;
        }
        String normalized = intent.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CREATE", "CREATE_PLAN" -> PlanAction.CREATE;
            case "QUERY", "QUERY_PLAN" -> PlanAction.QUERY;
            case "UPDATE", "UPDATE_PLAN" -> PlanAction.UPDATE;
            case "DELETE", "DELETE_PLAN" -> PlanAction.DELETE;
            default -> PlanAction.UNKNOWN;
        };
    }

    private double normalizeConfidence(Double confidence) {
        if (confidence == null) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, confidence));
    }

    /**
     * 构建返回信息的内容
     * @param userText
     * @param analysis
     * @return
     */
    private String buildClarificationQuestion(String userText, StudyPlanIntentAnalysisResult analysis) {
        if (analysis != null && StringUtils.hasText(analysis.getClarificationQuestion())) {
            return analysis.getClarificationQuestion().trim();
        }

        String normalized = StringUtils.hasText(userText) ? userText.trim() : "你的消息";
        if (normalized.length() > 40) {
            normalized = normalized.substring(0, 40) + "...";
        }
        return "我理解你可能在处理学习计划（" + normalized + "）。请确认你是要：新增（创建）计划、查询计划、修改计划，还是删除计划？";
    }

    private PlanProcessResult handleQuery(String openId, String userText) {
        if (!StringUtils.hasText(openId)) {
            return PlanProcessResult.failed("无法识别当前用户身份，暂时不能查询计划");
        }

        QueryCommand command = parseQueryCommand(userText);
        List<StudyReminderTaskView> tasks = studyPlanService.getTasksByOpenIdInRange(openId, command.from(), command.to());
        if (tasks.isEmpty()) {
            return PlanProcessResult.success("你在" + command.label() + "没有学习计划。");
        }

        StringBuilder builder = new StringBuilder();
        builder.append("已为你查询到").append(command.label()).append("的学习计划，共 ").append(tasks.size()).append(" 条：\n");
        int maxCount = Math.min(10, tasks.size());
        for (int i = 0; i < maxCount; i++) {
            StudyReminderTaskView task = tasks.get(i);
            builder.append(i + 1)
                    .append(". ")
                    .append(task.getStudyDate())
                    .append(" ")
                    .append(task.getReminderTime())
                    .append(" - ")
                    .append(task.getRagTopic())
                    .append("\n");
        }
        if (tasks.size() > maxCount) {
            builder.append("...其余 ").append(tasks.size() - maxCount).append(" 条未展示");
        }
        return PlanProcessResult.success(builder.toString().trim());
    }

    private PlanProcessResult handleDelete(String openId, String userText) {
        if (!StringUtils.hasText(openId)) {
            return PlanProcessResult.failed("无法识别当前用户身份，暂时不能删除计划");
        }

        DeleteCommand command = parseDeleteCommand(userText);
        if (command == null) {
            return PlanProcessResult.failed("删除计划请说明日期，或明确说“删除全部计划”");
        }

        int rows = studyPlanService.deleteTasksByOpenId(openId, command.from(), command.to(), command.timeFilter());
        if (rows <= 0) {
            return PlanProcessResult.failed("未找到可删除的学习计划，请确认日期/时间是否正确");
        }

        String scope = command.from().equals(command.to())
                ? command.from().toString()
                : command.from() + " 到 " + command.to();
        String timeScope = command.timeFilter() == null ? "" : " " + command.timeFilter();
        return PlanProcessResult.success("已删除 " + rows + " 条学习计划（" + scope + timeScope + "）。");
    }

    private PlanProcessResult handleUpdate(String openId, String userText) {
        if (!StringUtils.hasText(openId)) {
            return PlanProcessResult.failed("无法识别当前用户身份，暂时不能修改计划");
        }

        UpdateCommand command = parseUpdateCommand(userText);
        if (command == null || command.targetDate() == null) {
            if (isAmbiguousAdjustmentIntent(userText)) {
                return PlanProcessResult.success(buildClarificationQuestion(userText, null));
            }
            return PlanProcessResult.failed("修改计划请至少说明日期，例如：把 2025-01-01 的提醒改到 20:30");
        }

        int rows = studyPlanService.updateTaskByOpenId(
                openId,
                command.targetDate(),
                command.oldTime(),
                command.newTime(),
                command.newTopic(),
                command.newStudyContent()
        );
        if (rows <= 0) {
            return PlanProcessResult.failed("未匹配到可修改的任务，请确认日期/时间是否准确");
        }

        StringBuilder reply = new StringBuilder();
        reply.append("已更新 ").append(rows).append(" 条学习计划（").append(command.targetDate());
        if (command.oldTime() != null) {
            reply.append(" ").append(command.oldTime());
        }
        reply.append("）。");
        if (command.newTime() != null) {
            reply.append(" 新提醒时间：").append(command.newTime()).append("。");
        }
        if (StringUtils.hasText(command.newTopic())) {
            reply.append(" 新主题：").append(command.newTopic()).append("。");
        }
        return PlanProcessResult.success(reply.toString());
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
        boolean hasStrongDomainWord = normalized.contains("学习计划")
            || normalized.contains("学习提醒")
            || normalized.contains("学习任务")
            || normalized.contains("复习计划")
            || normalized.contains("rag");
        boolean hasPlanAndStudyWord = normalized.contains("计划")
            && (normalized.contains("学习") || normalized.contains("复习") || normalized.contains("提醒"));
        return hasStrongDomainWord || hasPlanAndStudyWord;
    }

    private boolean isDeleteIntent(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.contains("删除") || text.contains("取消") || text.contains("清空");
    }

    private boolean isUpdateIntent(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.contains("修改") || text.contains("改成") || text.contains("改为") || text.contains("调整") || text.contains("推迟") || text.contains("提前");
    }

    /**
     * 判断用户是否表达了一个模糊的调整意图
     * @param text
     * @return
     */
    private boolean isAmbiguousAdjustmentIntent(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        boolean hasAdjustmentWord = text.contains("调整") || text.contains("改一改") || text.contains("改改");
        boolean hasExplicitUpdateTarget = text.contains("改到")
                || text.contains("改成")
                || text.contains("改为")
                || text.contains("调整到")
                || text.contains("推迟")
                || text.contains("提前")
                || Pattern.compile("(?:主题|学习主题|内容|备注|说明)\\s*(?:改到|改成|改为)").matcher(text).find();
        return hasAdjustmentWord && !hasExplicitUpdateTarget && isPlanIntent(text);
    }

    private boolean isCreateIntent(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }

        if (hasExplicitCreateVerb(text) && (isPlanIntent(text) || hasScheduleLearningHint(text))) {
            return true;
        }

        // 没有显式创建动词时，至少要有“时间安排”信号，避免把纯问句当创建。
        boolean hasScheduleHint = Pattern.compile("(\\d{4}-\\d{1,2}-\\d{1,2})|(\\d{1,2}[:：]\\d{2})|(每天|每日|明天|后天|今晚|明晚|下周)")
                .matcher(text)
                .find();
        boolean hasPlanDomainWord = isPlanIntent(text);
        return hasScheduleHint && hasPlanDomainWord;
    }

    private boolean isQueryIntent(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }

        boolean hasPlanNoun = text.contains("学习计划") || text.contains("计划") || text.contains("提醒");

        boolean hasExplicitQueryVerb = text.contains("查询")
                || text.contains("查看")
                || text.contains("有哪些")
                || text.contains("我的计划")
                || text.contains("接下来几天")
                || text.contains("制定了什么")
                || text.contains("什么学习计划")
                || text.contains("计划是什么")
                || text.contains("都有什么计划");

        if (hasExplicitQueryVerb && hasPlanNoun) {
            return true;
        }

        boolean hasDateScopeHint = text.contains("今天")
                || text.contains("明天")
                || text.contains("后天")
                || text.contains("接下来几天")
                || text.contains("一周")
                || text.contains("7天");
        boolean hasQuestionTone = text.contains("什么")
                || text.contains("哪些")
                || text.contains("多少")
                || text.contains("吗")
                || text.contains("?")
                || text.contains("？");

        if (hasPlanNoun && hasQuestionTone) {
            return true;
        }

        return hasPlanNoun && hasDateScopeHint && !hasExplicitCreateVerb(text)
                && !isDeleteIntent(text)
                && !isUpdateIntent(text);
    }

    private boolean hasScheduleLearningHint(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }

        boolean hasTimeHint = Pattern.compile("(\\d{4}-\\d{1,2}-\\d{1,2})|(\\d{1,2}[:：]\\d{2})|(今天|明天|后天|今晚|明晚|下周|每天|每日)")
                .matcher(text)
                .find();
        boolean hasLearningWord = Pattern.compile("(学习|学学|复习|刷题|预习|了解|掌握)")
                .matcher(text)
                .find();
        return hasTimeHint && hasLearningWord;
    }

    private boolean hasExplicitCreateVerb(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }

        boolean hasCreateVerb = text.contains("创建")
                || text.contains("制定")
                || text.contains("新增")
                || text.contains("生成")
                || text.contains("做个")
                || text.contains("做一份")
                || text.contains("帮我定")
                || text.contains("帮我安排")
                || text.contains("帮我创建");

        if (!hasCreateVerb) {
            return false;
        }

        // 避免把“我制定了什么计划”这类回顾式问句识别成创建。
        boolean asksWhatWasCreated = Pattern.compile("(?:创建|制定|安排|新增|生成).{0,4}(?:了)?什么")
                .matcher(text)
                .find();
        return !asksWhatWasCreated;
    }

    private QueryCommand parseQueryCommand(String text) {
        LocalDate today = LocalDate.now();
        if (!StringUtils.hasText(text)) {
            return new QueryCommand(today, today.plusDays(13), "接下来14天");
        }

        LocalDate date = parseFirstDate(text);
        if (date != null) {
            return new QueryCommand(date, date, date.toString());
        }

        if (text.contains("今天")) {
            return new QueryCommand(today, today, "今天");
        }

        if (text.contains("明天")) {
            LocalDate tomorrow = today.plusDays(1);
            return new QueryCommand(tomorrow, tomorrow, "明天");
        }

        if (text.contains("后天")) {
            LocalDate afterTomorrow = today.plusDays(2);
            return new QueryCommand(afterTomorrow, afterTomorrow, "后天");
        }

        if (text.contains("一周") || text.contains("7天")) {
            return new QueryCommand(today, today.plusDays(6), "接下来7天");
        }

        Matcher matcher = Pattern.compile("(\\d{1,2})\\s*天").matcher(text);
        if (matcher.find()) {
            try {
                int days = Integer.parseInt(matcher.group(1));
                int safeDays = Math.max(1, Math.min(days, 30));
                return new QueryCommand(today, today.plusDays(safeDays - 1L), "接下来" + safeDays + "天");
            } catch (NumberFormatException ignored) {
                return new QueryCommand(today, today.plusDays(13), "接下来14天");
            }
        }

        return new QueryCommand(today, today.plusDays(13), "接下来14天");
    }

    private DeleteCommand parseDeleteCommand(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        LocalTime timeFilter = parseFirstTime(text);
        if (text.contains("全部") || text.contains("所有")) {
            return new DeleteCommand(LocalDate.now(), LocalDate.now().plusDays(365), timeFilter);
        }

        LocalDate date = parseFirstDate(text);
        if (date != null) {
            return new DeleteCommand(date, date, timeFilter);
        }

        if (text.contains("今天")) {
            LocalDate today = LocalDate.now();
            return new DeleteCommand(today, today, timeFilter);
        }
        if (text.contains("明天")) {
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            return new DeleteCommand(tomorrow, tomorrow, timeFilter);
        }

        return null;
    }

    private UpdateCommand parseUpdateCommand(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        LocalDate date = parseFirstDate(text);
        if (date == null) {
            if (text.contains("今天")) {
                date = LocalDate.now();
            } else if (text.contains("明天")) {
                date = LocalDate.now().plusDays(1);
            }
        }

        LocalTime oldTime = null;
        LocalTime newTime = null;

        Matcher fromTo = Pattern.compile("从\\s*(\\d{1,2}[:：]\\d{2})\\s*(?:改到|改成|改为|调整到)\\s*(\\d{1,2}[:：]\\d{2})").matcher(text);
        if (fromTo.find()) {
            oldTime = parseTimeOrFallback(fromTo.group(1), null);
            newTime = parseTimeOrFallback(fromTo.group(2), null);
        } else {
            Matcher singleNew = Pattern.compile("(?:改到|改成|改为|调整到)\\s*(\\d{1,2}[:：]\\d{2})").matcher(text);
            if (singleNew.find()) {
                newTime = parseTimeOrFallback(singleNew.group(1), null);
            }
        }

        String newTopic = parseFieldByPattern(text, "(?:主题|学习主题|内容)\\s*(?:改到|改成|改为)\\s*([^，,。；;]+)");
        String newStudyContent = parseFieldByPattern(text, "(?:备注|说明|学习内容)\\s*(?:改到|改成|改为)\\s*([^，,。；;]+)");

        if (date == null) {
            return null;
        }
        if (oldTime == null && newTime == null && !StringUtils.hasText(newTopic) && !StringUtils.hasText(newStudyContent)) {
            return null;
        }
        return new UpdateCommand(date, oldTime, newTime, newTopic, newStudyContent);
    }

    private LocalDate parseFirstDate(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = Pattern.compile("(\\d{4}-\\d{1,2}-\\d{1,2})").matcher(text);
        if (matcher.find()) {
            try {
                return LocalDate.parse(matcher.group(1), DateTimeFormatter.ofPattern("yyyy-M-d"));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private LocalTime parseFirstTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = Pattern.compile("(\\d{1,2}[:：]\\d{2})").matcher(text);
        if (matcher.find()) {
            return parseTimeOrFallback(matcher.group(1), null);
        }
        return null;
    }

    private String parseFieldByPattern(String text, String regex) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = Pattern.compile(regex).matcher(text);
        if (matcher.find()) {
            String value = matcher.group(1);
            return StringUtils.hasText(value) ? value.trim() : null;
        }
        return null;
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

    private StudyPlanIntentAnalysisResult tryAnalyzeIntentByAi(String userText) {
        if (studyPlanIntentAgent == null || !StringUtils.hasText(userText)) {
            return null;
        }
        try {
            // 借助提示词，分析用户的意图
            String raw = studyPlanIntentAgent.analyze(userText);
            return objectMapper.readValue(raw, StudyPlanIntentAnalysisResult.class);
        } catch (Exception e) {
            // 意图分析失败不抛错，继续走规则兜底。
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

    private record DeleteCommand(LocalDate from, LocalDate to, LocalTime timeFilter) {
    }

    private record UpdateCommand(LocalDate targetDate,
                                 LocalTime oldTime,
                                 LocalTime newTime,
                                 String newTopic,
                                 String newStudyContent) {
    }

    private record QueryCommand(LocalDate from, LocalDate to, String label) {
    }

    private record AgentDecision(PlanAction action, boolean needsClarification, String clarificationQuestion) {

        private static AgentDecision route(PlanAction action) {
            return new AgentDecision(action, false, "");
        }

        private static AgentDecision clarify(String clarificationQuestion) {
            return new AgentDecision(PlanAction.UNKNOWN, true, clarificationQuestion);
        }
    }

    private enum PlanAction {
        CREATE,
        QUERY,
        UPDATE,
        DELETE,
        UNKNOWN
    }
}
