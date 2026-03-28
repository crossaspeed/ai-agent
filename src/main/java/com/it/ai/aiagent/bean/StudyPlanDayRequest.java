package com.it.ai.aiagent.bean;

import lombok.Data;

@Data
public class StudyPlanDayRequest {
    private String date;
    private String reminderTime;
    private String ragTopic;
    private String studyContent;
}
