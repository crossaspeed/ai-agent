package com.it.ai.aiagent.bean;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class StudyReminderTask {
    private Long id;
    private String planName;
    private LocalDate studyDate;
    private LocalTime reminderTime;
    private LocalDateTime triggerTime;
    private String ragTopic;
    private String studyContent;
    private String channelsJson;
    private String feishuOpenId;
    private String timezone;
    private String planBatchId;
    private String sourceOpenId;
    private String sourceChannel;
    private String sourceMsgId;
    private Integer deletedFlag;
    private Integer status;
    private Integer sentStatus;
    private String errorMessage;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
