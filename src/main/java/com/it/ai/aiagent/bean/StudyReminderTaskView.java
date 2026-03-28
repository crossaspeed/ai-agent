package com.it.ai.aiagent.bean;

import lombok.Data;

@Data
public class StudyReminderTaskView {
    private Long id;
    private String planName;
    private String studyDate;
    private String reminderTime;
    private String ragTopic;
    private String studyContent;
    private String timezone;
    private Boolean enabled;
    private Integer sentStatus;
    private String errorMessage;
    private String sentAt;
    private Boolean hasFeishuConfig;
    private String channels;
}
