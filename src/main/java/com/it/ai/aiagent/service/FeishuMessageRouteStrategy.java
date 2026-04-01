package com.it.ai.aiagent.service;

public interface FeishuMessageRouteStrategy {

    int getOrder();

    FeishuMessageRouterService.RouteProcessResult process(RouteContext context);

    record RouteContext(String openId, String normalizedText) {
    }
}
