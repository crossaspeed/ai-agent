package com.it.ai.aiagent.assistant;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel"
)
public interface StudyPlanIntentAgent {

    @SystemMessage("你是飞书学习计划路由意图分析器。请根据用户输入识别学习计划操作意图，严格输出 JSON，禁止输出 markdown、注释或额外解释。"
            + "可选 intent：CREATE_PLAN、QUERY_PLAN、UPDATE_PLAN、DELETE_PLAN、OTHER。"
            + "输出格式：{\"intent\":\"CREATE_PLAN\",\"confidence\":0.0,\"needsClarification\":false,\"clarificationQuestion\":\"\",\"entities\":{\"date\":\"yyyy-MM-dd\",\"time\":\"HH:mm\",\"topic\":\"...\"}}。"
            + "规则：1）confidence 范围 [0,1]。2）若语义歧义或信息不足，needsClarification=true 并给出简短追问。"
            + "3）若用户并非学习计划管理诉求，intent=OTHER。4）entities 可省略不存在字段。")
    String analyze(@UserMessage String text);
}
