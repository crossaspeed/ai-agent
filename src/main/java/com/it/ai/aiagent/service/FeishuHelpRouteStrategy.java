package com.it.ai.aiagent.service;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class FeishuHelpRouteStrategy implements FeishuMessageRouteStrategy {

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public FeishuMessageRouterService.RouteProcessResult process(RouteContext context) {
        String text = context.normalizedText();
        if (!isHelpIntent(text)) {
            return FeishuMessageRouterService.RouteProcessResult.ignored("未命中帮助命令");
        }
        return FeishuMessageRouterService.RouteProcessResult.success(buildHelpMessage(), "help");
    }

    private boolean isHelpIntent(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return "help".equals(normalized)
                || "菜单".equals(text)
                || "帮助".equals(text)
                || "指令".equals(text)
                || "功能".equals(text);
    }

    private String buildHelpMessage() {
        return "我支持两类能力：\n"
                + "1) 学习计划管理（创建/查询/修改/删除）\n"
                + "   示例：创建学习计划：明晚20:00 学 RAG 检索\n"
                + "2) 知识考察（基于你的RAG知识库）\n"
                + "   示例：问答：考我计算机网络的知识\n"
                + "说明：未命中计划意图时会自动进入知识问答。";
    }
}
