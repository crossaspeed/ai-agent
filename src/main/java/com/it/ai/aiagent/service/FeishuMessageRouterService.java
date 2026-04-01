package com.it.ai.aiagent.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;

@Service
public class FeishuMessageRouterService {

    @Autowired
    private List<FeishuMessageRouteStrategy> routeStrategies = List.of();

    public RouteProcessResult process(String openId, String userText) {
        String normalized = normalizeText(userText);
        if (!StringUtils.hasText(normalized)) {
            return RouteProcessResult.ignored("未识别到有效文本内容");
        }

        FeishuMessageRouteStrategy.RouteContext context =
                new FeishuMessageRouteStrategy.RouteContext(openId, normalized);
        List<FeishuMessageRouteStrategy> sortedStrategies = routeStrategies.stream()
                .sorted(Comparator.comparingInt(FeishuMessageRouteStrategy::getOrder))
                .toList();

        for (FeishuMessageRouteStrategy strategy : sortedStrategies) {
            RouteProcessResult result = strategy.process(context);
            if (result != null && result.handled()) {
                return result;
            }
        }
        return RouteProcessResult.ignored("未命中可用路由策略");
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
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