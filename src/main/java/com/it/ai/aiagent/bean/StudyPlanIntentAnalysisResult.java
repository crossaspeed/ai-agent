package com.it.ai.aiagent.bean;

import lombok.Data;

import java.util.Map;

@Data
public class StudyPlanIntentAnalysisResult {
    private String intent; // 意图
    private Double confidence; // 置信度（ai对这次意图判断的“把握程度”）
    private Boolean needsClarification; // 是否需要澄清（“先不要去创建计划，先去问用户一个问题确认一下”）
    private String clarificationQuestion; // 澄清问题的预设话术
    private Map<String, String> entities; // 从原始文中提取出来的信息
}
