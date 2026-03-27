package com.it.ai.aiagent.controller;

import com.it.ai.aiagent.assistant.XiaocAgent;
import com.it.ai.aiagent.bean.ChatForm;
import com.it.ai.aiagent.store.MongoChatMemoryStore;
import dev.langchain4j.data.message.ChatMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@Tag(name = "我的智能体")
@RestController
@RequestMapping("/agent")
public class XiaocController {
    @Autowired
    private XiaocAgent xiaocAgent;

    @Autowired
    private MongoChatMemoryStore mongoChatMemoryStore;

    @Operation(summary = "对话")
    @PostMapping("/chat")
    public Flux<String> chat(@RequestBody ChatForm chatForm) {
        return xiaocAgent.chat(chatForm.getMemoryId(), chatForm.getMessage());
    }

    @Operation(summary = "查看历史聊天")
    @GetMapping("/history/{memoryId}")
    public List<java.util.Map<String, Object>> history(@PathVariable("memoryId") Long memoryId) {
        List<ChatMessage> messages = mongoChatMemoryStore.getMessages(memoryId);
        return messages.stream().map(m -> {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("type", m.type().name());
            
            String text = "";
            if (m instanceof dev.langchain4j.data.message.UserMessage) {
                text = ((dev.langchain4j.data.message.UserMessage) m).singleText();
            } else if (m instanceof dev.langchain4j.data.message.AiMessage) {
                text = ((dev.langchain4j.data.message.AiMessage) m).text();
            } else if (m instanceof dev.langchain4j.data.message.SystemMessage) {
                text = ((dev.langchain4j.data.message.SystemMessage) m).text();
            } else if (m instanceof dev.langchain4j.data.message.ToolExecutionResultMessage) {
                text = ((dev.langchain4j.data.message.ToolExecutionResultMessage) m).text();
            }
            map.put("text", text);
            return map;
        }).collect(java.util.stream.Collectors.toList());
    }

    @Operation(summary = "获取所有历史会话列表")
    @GetMapping("/history/sessions")
    public List<java.util.Map<String, Object>> getSessions() {
        List<com.it.ai.aiagent.bean.ChatMessages> allMessages = mongoChatMemoryStore.getAllSessions();
        return allMessages.stream().map(cm -> {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("memoryId", cm.getMemoryId());
            
            String title = "新对话 " + cm.getMemoryId();
            try {
                List<ChatMessage> parsedMsgs = dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson(cm.getContent());
                for (ChatMessage msg : parsedMsgs) {
                    if (msg instanceof dev.langchain4j.data.message.UserMessage) {
                        title = ((dev.langchain4j.data.message.UserMessage) msg).singleText();
                        if (title.length() > 20) title = title.substring(0, 20) + "...";
                        break;
                    }
                }
            } catch (Exception e) {
            }
            map.put("title", title);
            return map;
        }).collect(java.util.stream.Collectors.toList());
    }
}
