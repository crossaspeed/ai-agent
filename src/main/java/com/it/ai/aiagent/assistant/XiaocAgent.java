package com.it.ai.aiagent.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel",
        streamingChatModel = "openAiStreamingChatModel",
        chatMemoryProvider = "memoryProvider",
        contentRetriever = "pineconeContentRetriever"
)
public interface XiaocAgent {
    @SystemMessage(fromResource = "prompt-template.txt")
    TokenStream chat(@MemoryId Long memoryId, @UserMessage String userMessage);
}
