package com.it.ai.aiagent.bean;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class StudyReminderTask {
    private Long id;
    private String planName;
    // 学习日期
    private LocalDate studyDate;
    // 提醒时间
    private LocalTime reminderTime;
    // 实际触发的日期
    private LocalDateTime triggerTime;
    // 学习主题
    private String ragTopic;
    // 学习内容
    private String studyContent;
    // 提醒渠道列表（为多渠道预留扩展）
    private String channelsJson;
    // 飞书接收人的OpenId
    private String feishuOpenId;
    private String timezone;
    // 同一批周计划的批次号，用于关联同一批计划生成的多个任务
    private String planBatchId;
    // 发起人的OpenId
    private String sourceOpenId;
    // 来源渠道
    private String sourceChannel;
    // 来源信息ID
    private String sourceMsgId;
    // 逻辑删除标记
    private Integer deletedFlag;
    // 任务开关状态：1 启用 0 停用
    private Integer status;
    // 发送状态 0 待发送 1 已发送 2 发送失败
    private Integer sentStatus;
    private String errorMessage;
    // 实际发送时间
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
