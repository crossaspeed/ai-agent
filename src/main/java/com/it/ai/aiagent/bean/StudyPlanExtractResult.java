package com.it.ai.aiagent.bean;

import lombok.Data;

import java.util.List;

@Data
public class StudyPlanExtractResult {
    private String planName;
    private String timezone;
    private List<StudyPlanDayRequest> days;
}
