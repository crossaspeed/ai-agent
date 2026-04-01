package com.it.ai.aiagent.service;

public interface FeishuMessageRouteStrategy {

    int getOrder();

    default String getRouteType() {
        return "";
    }

    FeishuMessageRouterService.RouteProcessResult process(RouteContext context);

    record RouteContext(String openId, String normalizedText, String routeType) {
        public RouteContext(String openId, String normalizedText) {
            this(openId, normalizedText, "");
        }
    }
}
