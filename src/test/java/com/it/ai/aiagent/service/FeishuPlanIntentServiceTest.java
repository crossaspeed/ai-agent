package com.it.ai.aiagent.service;

import com.it.ai.aiagent.assistant.StudyPlanExtractAgent;
import com.it.ai.aiagent.bean.StudyReminderTaskView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeishuPlanIntentServiceTest {

    @InjectMocks
    private FeishuPlanIntentService feishuPlanIntentService;

    @Mock
    private StudyPlanExtractAgent studyPlanExtractAgent;

    @Mock
    private StudyPlanService studyPlanService;

    @Test
    void queryUpcomingPlanShouldNotCreatePlan() {
        when(studyPlanService.getTasksByOpenIdInRange(anyString(), any(), any())).thenReturn(List.of());

        FeishuPlanIntentService.PlanProcessResult result =
                feishuPlanIntentService.processPlanIntent("ou_test_user", "我接下来几天的学习计划是什么");

        assertTrue(result.handled());
        assertTrue(result.success());
        assertTrue(result.message().contains("没有学习计划"));
        verify(studyPlanService, never()).createWeeklyPlan(any());
        verifyNoInteractions(studyPlanExtractAgent);
    }

    @Test
    void queryCreatedPlanShouldNotCreatePlanAgain() {
        StudyReminderTaskView task = new StudyReminderTaskView();
        task.setStudyDate("2026-03-30");
        task.setReminderTime("20:00");
        task.setRagTopic("RAG 检索");

        when(studyPlanService.getTasksByOpenIdInRange(anyString(), any(), any())).thenReturn(List.of(task));

        FeishuPlanIntentService.PlanProcessResult result =
                feishuPlanIntentService.processPlanIntent("ou_test_user", "我制定了什么学习计划");

        assertTrue(result.handled());
        assertTrue(result.success());
        assertTrue(result.message().contains("已为你查询到"));
        verify(studyPlanService, never()).createWeeklyPlan(any());
        verifyNoInteractions(studyPlanExtractAgent);
    }

        @Test
        void createPlanWithTodayTimeShouldPreferCreateIntent() {
                when(studyPlanExtractAgent.extract(anyString())).thenReturn("""
                                {
                                    "planName": "飞书学习计划",
                                    "timezone": "Asia/Shanghai",
                                    "days": [
                                        {
                                            "date": "2026-04-01",
                                            "reminderTime": "15:00",
                                            "ragTopic": "计算机网络",
                                            "studyContent": "了解TCP三次握手"
                                        }
                                    ]
                                }
                                """);
                when(studyPlanService.createWeeklyPlan(any())).thenReturn(Map.of("created", 1));

                FeishuPlanIntentService.PlanProcessResult result = feishuPlanIntentService.processPlanIntent(
                                "ou_test_user",
                                "创建学习计划：今天15：00学学计算机网络的知识，了解TCP的三次握手"
                );

                assertTrue(result.handled());
                assertTrue(result.success());
                assertTrue(result.message().contains("学习计划已创建成功"));
                verify(studyPlanService).createWeeklyPlan(any());
                verify(studyPlanService, never()).getTasksByOpenIdInRange(anyString(), any(), any());
        }

        @Test
        void queryTodayPlanShouldNotBeMistakenAsCreateIntent() {
                when(studyPlanService.getTasksByOpenIdInRange(anyString(), any(), any())).thenReturn(List.of());

                FeishuPlanIntentService.PlanProcessResult result =
                                feishuPlanIntentService.processPlanIntent("ou_test_user", "我今天有什么学习计划");

                assertTrue(result.handled());
                assertTrue(result.success());
                assertTrue(result.message().contains("今天没有学习计划"));
                verify(studyPlanService, never()).createWeeklyPlan(any());
                verifyNoInteractions(studyPlanExtractAgent);
        }
}
