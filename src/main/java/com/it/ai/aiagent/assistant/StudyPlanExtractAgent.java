package com.it.ai.aiagent.assistant;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel"
)
public interface StudyPlanExtractAgent {

    @SystemMessage("你是学习计划抽取器。请从用户自然语言中抽取接下来一周（最多7天）的学习计划，严格输出 JSON，禁止输出 markdown 或解释。"
            + "JSON 格式：{\"planName\":\"...\",\"timezone\":\"Asia/Shanghai\",\"days\":[{\"date\":\"yyyy-MM-dd\",\"reminderTime\":\"HH:mm\",\"ragTopic\":\"...\",\"studyContent\":\"...\"}]}。"
            + "规则：1）如果用户未提供日期，按从今天开始连续填充。2）如果用户未提供时间，默认 20:00。"
            + "3）date 必须是 yyyy-MM-dd，reminderTime 必须是 HH:mm。4）days 至少1条，最多7条。"
            + "5）planName 默认 \"飞书学习计划\"，timezone 默认 Asia/Shanghai。")
    String extract(@UserMessage String text);
}
