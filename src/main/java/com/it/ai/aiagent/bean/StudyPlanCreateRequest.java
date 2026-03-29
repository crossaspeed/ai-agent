package com.it.ai.aiagent.bean;

import lombok.Data;

import java.util.List;

@Data
public class StudyPlanCreateRequest {
    private String planName;
    private List<StudyPlanDayRequest> days;
    private List<String> channels;
    private String feishuOpenId;
    private String timezone;
    private String planBatchId;
    private String sourceOpenId;
    private String sourceChannel;
    private String sourceMsgId;
}
