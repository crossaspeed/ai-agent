package com.it.ai.aiagent.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StudyReminderScheduler {

    @Autowired
    private StudyPlanService studyPlanService;

    @Scheduled(fixedDelayString = "${scheduler.reminder.fixed-delay-ms:60000}")
    public void runReminderSchedule() {
        studyPlanService.executeDueTasks();
    }
}
