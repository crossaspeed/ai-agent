package com.it.ai.aiagent.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FeishuPlanRouteStrategy implements FeishuMessageRouteStrategy {

    @Autowired
    private FeishuPlanIntentService feishuPlanIntentService;

    @Override
    public int getOrder() {
        return 30;
    }

    @Override
    public String getRouteType() {
        return "plan";
    }

    @Override
    public FeishuMessageRouterService.RouteProcessResult process(RouteContext context) {
        FeishuPlanIntentService.PlanProcessResult planResult =
                feishuPlanIntentService.processPlanIntent(context.openId(), context.normalizedText());
        return FeishuMessageRouterService.RouteProcessResult.of(
                planResult.handled(),
                planResult.success(),
                planResult.message(),
                "plan"
        );
    }
}
