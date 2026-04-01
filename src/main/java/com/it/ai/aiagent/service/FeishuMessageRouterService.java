package com.it.ai.aiagent.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
public class FeishuMessageRouterService {

    @Autowired
    private FeishuPlanIntentService feishuPlanIntentService;

    @Autowired
    private FeishuQaService feishuQaService;

    public RouteProcessResult process(String openId, String userText) {
        String normalized = normalizeText(userText);
        // 根据用户传入消息，选择不同的路由
        if (!StringUtils.hasText(normalized)) {
            return RouteProcessResult.ignored("未识别到有效文本内容");
        }
        // 是否是帮助命令
        if (isHelpIntent(normalized)) {
            return RouteProcessResult.success(buildHelpMessage(), "help");
        }
        // 是否是问答命令
        if (isQaPrefixed(normalized)) {
            //  提取问答的关键信息
            String qaText = stripQaPrefix(normalized);
            FeishuQaService.QaProcessResult qaResult = feishuQaService.processQa(openId, qaText);
            return RouteProcessResult.of(qaResult.handled(), qaResult.success(), qaResult.message(), "qa");
        }
        // 执行学习计划的CRUD
        FeishuPlanIntentService.PlanProcessResult planResult = feishuPlanIntentService.processPlanIntent(openId, normalized);
        if (planResult.handled()) {
            return RouteProcessResult.of(true, planResult.success(), planResult.message(), "plan");
        }
        // 兜底机制，如果前面三个都没有命中，那么可能是无效的回答
        FeishuQaService.QaProcessResult qaFallback = feishuQaService.processQa(openId, normalized);
        return RouteProcessResult.of(qaFallback.handled(), qaFallback.success(), qaFallback.message(), "qa");
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private boolean isHelpIntent(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return "help".equals(normalized)
                || "菜单".equals(text)
                || "帮助".equals(text)
                || "指令".equals(text)
                || "功能".equals(text);
    }

    private boolean isQaPrefixed(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.startsWith("问答:")
                || normalized.startsWith("问答：")
                || normalized.startsWith("qa:")
                || normalized.startsWith("qa：")
                || normalized.startsWith("知识问答:")
                || normalized.startsWith("知识问答：");
    }

    private String stripQaPrefix(String text) {
        int index = text.indexOf(':');
        if (index < 0) {
            index = text.indexOf('：');
        }
        if (index < 0 || index == text.length() - 1) {
            return "";
        }
        return text.substring(index + 1).trim();
    }

    private String buildHelpMessage() {
        return "我支持两类能力：\n"
                + "1) 学习计划管理（创建/查询/修改/删除）\n"
                + "   示例：创建学习计划：明晚20:00 学 RAG 检索\n"
                + "2) 知识考察（基于你的RAG知识库）\n"
                + "   示例：问答：考我计算机网络的知识\n"
                + "说明：未命中计划意图时会自动进入知识问答。";
    }

    public record RouteProcessResult(boolean handled, boolean success, String message, String route) {
        public static RouteProcessResult ignored(String message) {
            return new RouteProcessResult(false, false, message, "ignored");
        }

        public static RouteProcessResult success(String message, String route) {
            return new RouteProcessResult(true, true, message, route);
        }

        public static RouteProcessResult of(boolean handled, boolean success, String message, String route) {
            return new RouteProcessResult(handled, success, message, route);
        }
    }
}