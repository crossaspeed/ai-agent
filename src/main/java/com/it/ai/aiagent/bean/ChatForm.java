package com.it.ai.aiagent.bean;

import lombok.Data;

@Data
public class ChatForm {
    private Long memoryId;//对话id
    private String message;//用户问题
    private String type;//可选路由类型
}