package com.it.ai.aiagent.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FeishuQaFallbackRouteStrategy implements FeishuMessageRouteStrategy {

    @Autowired
    private FeishuQaService feishuQaService;

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public FeishuMessageRouterService.RouteProcessResult process(RouteContext context) {
        FeishuQaService.QaProcessResult qaResult = feishuQaService.processQa(context.openId(), context.normalizedText());
        return FeishuMessageRouterService.RouteProcessResult.of(
                qaResult.handled(),
                qaResult.success(),
                qaResult.message(),
                "qa"
        );
    }
}