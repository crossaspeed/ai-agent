package com.it.ai.aiagent.service;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FeishuMessageRouterServiceTest {

    @Mock
    private FeishuQaService feishuQaService;

    @Mock
    private FeishuPlanIntentService feishuPlanIntentService;

    private FeishuMessageRouterService routerService;

    @BeforeEach
    void setUp() {
        routerService = new FeishuMessageRouterService();

        FeishuHelpRouteStrategy helpStrategy = new FeishuHelpRouteStrategy();

        FeishuQaPrefixedRouteStrategy qaPrefixedRouteStrategy = new FeishuQaPrefixedRouteStrategy();
        ReflectionTestUtils.setField(qaPrefixedRouteStrategy, "feishuQaService", feishuQaService);

        FeishuPlanRouteStrategy planRouteStrategy = new FeishuPlanRouteStrategy();
        ReflectionTestUtils.setField(planRouteStrategy, "feishuPlanIntentService", feishuPlanIntentService);

        FeishuQaFallbackRouteStrategy qaFallbackRouteStrategy = new FeishuQaFallbackRouteStrategy();
        ReflectionTestUtils.setField(qaFallbackRouteStrategy, "feishuQaService", feishuQaService);

        ReflectionTestUtils.setField(
                routerService,
                "routeStrategies",
                List.of(helpStrategy, qaPrefixedRouteStrategy, planRouteStrategy, qaFallbackRouteStrategy)
        );
    }

    @Test
    void blankTextShouldBeIgnored() {
        FeishuMessageRouterService.RouteProcessResult result = routerService.process("ou_test_user", "   ");

        assertFalse(result.handled());
        assertEquals("ignored", result.route());
        verify(feishuQaService, never()).processQa(anyString(), anyString());
        verify(feishuPlanIntentService, never()).processPlanIntent(anyString(), anyString());
    }

    @Test
    void helpIntentShouldReturnHelpRoute() {
        FeishuMessageRouterService.RouteProcessResult result = routerService.process("ou_test_user", "帮助");

        assertTrue(result.handled());
        assertTrue(result.success());
        assertEquals("help", result.route());
        assertTrue(result.message().contains("学习计划管理"));
        verify(feishuQaService, never()).processQa(anyString(), anyString());
        verify(feishuPlanIntentService, never()).processPlanIntent(anyString(), anyString());
    }

    @Test
    void qaPrefixedIntentShouldInvokeQaBeforePlan() {
        when(feishuQaService.processQa("ou_test_user", "考我计算机网络"))
                .thenReturn(FeishuQaService.QaProcessResult.success("这是回答"));

        FeishuMessageRouterService.RouteProcessResult result =
                routerService.process("ou_test_user", "问答: 考我计算机网络");

        assertTrue(result.handled());
        assertEquals("qa", result.route());
        assertEquals("这是回答", result.message());
        verify(feishuQaService).processQa("ou_test_user", "考我计算机网络");
        verify(feishuPlanIntentService, never()).processPlanIntent(anyString(), anyString());
    }

    @Test
    void planHandledShouldNotFallbackToQa() {
        when(feishuPlanIntentService.processPlanIntent("ou_test_user", "创建学习计划 明晚20:00 学RAG"))
                .thenReturn(FeishuPlanIntentService.PlanProcessResult.success("计划创建成功"));

        FeishuMessageRouterService.RouteProcessResult result =
                routerService.process("ou_test_user", "创建学习计划 明晚20:00 学RAG");

        assertTrue(result.handled());
        assertEquals("plan", result.route());
        assertEquals("计划创建成功", result.message());
        verify(feishuPlanIntentService).processPlanIntent("ou_test_user", "创建学习计划 明晚20:00 学RAG");
        verify(feishuQaService, never()).processQa(anyString(), anyString());
    }

    @Test
    void planIgnoredShouldFallbackToQa() {
        when(feishuPlanIntentService.processPlanIntent("ou_test_user", "TCP三次握手是啥"))
                .thenReturn(FeishuPlanIntentService.PlanProcessResult.ignored("未命中学习计划意图"));
        when(feishuQaService.processQa("ou_test_user", "TCP三次握手是啥"))
                .thenReturn(FeishuQaService.QaProcessResult.success("QA兜底回答"));

        FeishuMessageRouterService.RouteProcessResult result =
                routerService.process("ou_test_user", "TCP三次握手是啥");

        assertTrue(result.handled());
        assertEquals("qa", result.route());
        assertEquals("QA兜底回答", result.message());
        verify(feishuPlanIntentService).processPlanIntent("ou_test_user", "TCP三次握手是啥");
        verify(feishuQaService).processQa("ou_test_user", "TCP三次握手是啥");
    }

    @Test
    void typedRouteShouldHitQaDirectly() {
        when(feishuQaService.processQa("ou_test_user", "TCP三次握手是啥"))
                .thenReturn(FeishuQaService.QaProcessResult.success("QA类型直达回答"));

        FeishuMessageRouterService.RouteProcessResult result =
                routerService.process("ou_test_user", "TCP三次握手是啥", "qa");

        assertTrue(result.handled());
        assertTrue(result.success());
        assertEquals("qa", result.route());
        assertEquals("QA类型直达回答", result.message());
        verify(feishuQaService).processQa("ou_test_user", "TCP三次握手是啥");
        verify(feishuPlanIntentService, never()).processPlanIntent(anyString(), anyString());
    }

    @Test
    void illegalTypeShouldFallbackToOrderedStrategies() {
        when(feishuPlanIntentService.processPlanIntent("ou_test_user", "创建学习计划 明晚20:00 学RAG"))
                .thenReturn(FeishuPlanIntentService.PlanProcessResult.success("计划创建成功"));

        FeishuMessageRouterService.RouteProcessResult result =
                routerService.process("ou_test_user", "创建学习计划 明晚20:00 学RAG", "unknown");

        assertTrue(result.handled());
        assertEquals("plan", result.route());
        assertEquals("计划创建成功", result.message());
        verify(feishuPlanIntentService).processPlanIntent("ou_test_user", "创建学习计划 明晚20:00 学RAG");
        verify(feishuQaService, never()).processQa(anyString(), anyString());
    }

    @Test
    void nullTypeShouldRemainBackwardCompatible() {
        when(feishuQaService.processQa("ou_test_user", "考我操作系统"))
                .thenReturn(FeishuQaService.QaProcessResult.success("兼容模式回答"));

        FeishuMessageRouterService.RouteProcessResult result =
                routerService.process("ou_test_user", "问答: 考我操作系统", null);

        assertTrue(result.handled());
        assertTrue(result.success());
        assertEquals("qa", result.route());
        assertEquals("兼容模式回答", result.message());
        verify(feishuQaService).processQa("ou_test_user", "考我操作系统");
        verify(feishuPlanIntentService, never()).processPlanIntent(anyString(), anyString());
    }
}
