package com.it.ai.aiagent.assistant;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel"
)
public interface SummaryPromptAgent {

    @SystemMessage(fromResource = "summary-prompt-template.txt")
    String summarize(@UserMessage String conversationHistory);
}
