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

import java.util.List;

@Tag(name = "我的智能体")
@RestController
@RequestMapping("/agent")
@org.springframework.web.bind.annotation.CrossOrigin(origins = "*")
public class XiaocController {
    @Autowired
    private XiaocAgent xiaocAgent;

    @Autowired
    private MongoChatMemoryStore mongoChatMemoryStore;

    @Operation(summary = "对话")
    @PostMapping(value = "/chat", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter chat(@RequestBody ChatForm chatForm, jakarta.servlet.http.HttpServletResponse response) {
        if (response != null) {
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("X-Accel-Buffering", "no");
            response.setHeader("Connection", "keep-alive");
        }
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(120000L);
        try {
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().comment("stream-start"));
        } catch (Exception ignored) {
        }
        
        dev.langchain4j.service.TokenStream tokenStream = xiaocAgent.chat(chatForm.getMemoryId(), chatForm.getMessage());
        tokenStream.onPartialResponse(token -> {
            try {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().data(token));
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        })
        .onCompleteResponse(res -> emitter.complete())
        .onError(emitter::completeWithError)
        .start();
        
        return emitter;
    }

    @Operation(summary = "查看历史聊天")
    @GetMapping("/history/{memoryId}")
    public List<java.util.Map<String, Object>> history(@PathVariable("memoryId") Long memoryId) {
        List<ChatMessage> messages = mongoChatMemoryStore.getMessages(memoryId);
        return messages.stream()
                // 1. 过滤掉不应该向前端暴露的系统消息（SystemMessage）
                .filter(m -> !(m instanceof dev.langchain4j.data.message.SystemMessage))
                .map(m -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("type", m.type().name());
                    
                    String text = "";
                    if (m instanceof dev.langchain4j.data.message.UserMessage) {
                        text = ((dev.langchain4j.data.message.UserMessage) m).singleText();
                        // 2. 移除 LangChain4j 自动注入的 RAG 上下文提示
                        // LangChain4j 的默认拼接格式是: "{用户原始问题}\n\nAnswer using the following information:\n{文档内容}"
                        if (text != null && text.contains("\n\nAnswer using the following information:\n")) {
                            text = text.split("\n\nAnswer using the following information:\n")[0];
                        }
                    } else if (m instanceof dev.langchain4j.data.message.AiMessage) {
                        text = ((dev.langchain4j.data.message.AiMessage) m).text();
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
                        String text = ((dev.langchain4j.data.message.UserMessage) msg).singleText();
                        // 过滤掉 RAG 内容作为侧边栏标题
                        if (text != null && text.contains("\n\nAnswer using the following information:\n")) {
                            text = text.split("\n\nAnswer using the following information:\n")[0];
                        }
                        title = text;
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
