package com.it.ai.aiagent.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FeishuMessageRouterService {

    @Autowired
    private List<FeishuMessageRouteStrategy> routeStrategies = List.of();

    private volatile List<FeishuMessageRouteStrategy> sortedStrategies = List.of();

    private volatile Map<String, FeishuMessageRouteStrategy> routeStrategyMap = Map.of();

    public RouteProcessResult process(String openId, String userText) {
        return process(openId, userText, null);
    }

    public RouteProcessResult process(String openId, String userText, String routeType) {
        String normalized = normalizeText(userText);
        String normalizedType = normalizeType(routeType);

        if (!StringUtils.hasText(normalizedType)) {
            normalizedType = extractTypeFromSlashCommand(normalized);
            if (StringUtils.hasText(normalizedType)) {
                normalized = stripSlashCommandPrefix(normalized);
            }
        }

        if (!StringUtils.hasText(normalized) && !StringUtils.hasText(normalizedType)) {
            return RouteProcessResult.ignored("未识别到有效文本内容");
        }
        //填充好sortedStrategies和routeStrategyMap里面的数据
        ensureStrategyIndexesReady();

        //填充对象的文本，ID，type属性
        FeishuMessageRouteStrategy.RouteContext context =
                new FeishuMessageRouteStrategy.RouteContext(openId, normalized, normalizedType);
        // 如果有type类型，先从map里面获取策略，时间复杂度为O（1）
        FeishuMessageRouteStrategy typedStrategy = null;
        if (StringUtils.hasText(normalizedType)) {
            typedStrategy = routeStrategyMap.get(normalizedType);
            if (typedStrategy != null) {
                RouteProcessResult typedResult = typedStrategy.process(context);
                if (typedResult != null && typedResult.handled()) {
                    return typedResult;
                }
            }
        }

        // 兜底处理，如果没有填写type属性，选择默认的for循环遍历的方式寻找策略
        for (FeishuMessageRouteStrategy strategy : sortedStrategies) {
            if (typedStrategy != null && strategy == typedStrategy) {
                continue;
            }
            RouteProcessResult result = strategy.process(context);
            if (result != null && result.handled()) {
                return result;
            }
        }
        return RouteProcessResult.ignored("未命中可用路由策略");
    }

    private void ensureStrategyIndexesReady() {
        if (!sortedStrategies.isEmpty() || routeStrategies.isEmpty()) {
            return;
        }

        synchronized (this) {
            if (!sortedStrategies.isEmpty() || routeStrategies.isEmpty()) {
                return;
            }

            List<FeishuMessageRouteStrategy> sorted = routeStrategies.stream()
                    .sorted(Comparator.comparingInt(FeishuMessageRouteStrategy::getOrder))
                    .toList();

            Map<String, FeishuMessageRouteStrategy> typedStrategies = new LinkedHashMap<>();
            for (FeishuMessageRouteStrategy strategy : sorted) {
                String type = normalizeType(strategy.getRouteType());
                if (!StringUtils.hasText(type)) {
                    continue;
                }
                FeishuMessageRouteStrategy existed = typedStrategies.putIfAbsent(type, strategy);
                if (existed != null) {
                    throw new IllegalStateException("检测到重复路由类型: " + type);
                }
            }

            sortedStrategies = sorted;
            routeStrategyMap = Map.copyOf(typedStrategies);
        }
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private String normalizeType(String routeType) {
        if (!StringUtils.hasText(routeType)) {
            return "";
        }
        String normalized = routeType.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    private String extractTypeFromSlashCommand(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("/")) {
            return "";
        }
        int splitIndex = trimmed.indexOf(' ');
        String command = splitIndex >= 0 ? trimmed.substring(0, splitIndex) : trimmed;
        return normalizeType(command);
    }

    private String stripSlashCommandPrefix(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        int splitIndex = trimmed.indexOf(' ');
        if (splitIndex < 0 || splitIndex == trimmed.length() - 1) {
            return "";
        }
        return trimmed.substring(splitIndex + 1).trim();
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