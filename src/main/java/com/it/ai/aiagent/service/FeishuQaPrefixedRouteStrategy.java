package com.it.ai.aiagent.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class FeishuQaPrefixedRouteStrategy implements FeishuMessageRouteStrategy {

    @Autowired
    private FeishuQaService feishuQaService;

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public FeishuMessageRouterService.RouteProcessResult process(RouteContext context) {
        String text = context.normalizedText();
        if (!isQaPrefixed(text)) {
            return FeishuMessageRouterService.RouteProcessResult.ignored("未命中问答前缀");
        }

        String qaText = stripQaPrefix(text);
        FeishuQaService.QaProcessResult qaResult = feishuQaService.processQa(context.openId(), qaText);
        return FeishuMessageRouterService.RouteProcessResult.of(
                qaResult.handled(),
                qaResult.success(),
                qaResult.message(),
                "qa"
        );
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
}
