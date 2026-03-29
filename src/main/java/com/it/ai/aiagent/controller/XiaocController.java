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

    /**
     * 方法的作用流程：用户发送一句话->后端调用大模型->大模型每吐出一个片段就推送给前端展示
     * @param chatForm
     * @param response
     * @return
     */
    @Operation(summary = "对话")
    //produces 声明响应的内容为text/event-stream
    // 1. 告诉spring这是流式响应
    // 2. 告诉前端按照SSE协议持续接收内容
    @PostMapping(value = "/chat", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter chat(@RequestBody ChatForm chatForm, jakarta.servlet.http.HttpServletResponse response) {
        if (response != null) {
            //每次进行请求的时候都要进行验证操作
            response.setHeader("Cache-Control", "no-cache");
            //Nginx作为中间层的时候，默认会为后端缓存，设置这个之后，Nginx会禁用缓存，收到数据立马转发给客户端
            response.setHeader("X-Accel-Buffering", "no");
            //这次响应结束之后，不关闭TCP连接，后续的连接都用这条连接
            response.setHeader("Connection", "keep-alive");
        }
        //SseEmitter SpringMVC提供的SSE推送对象
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(120000L);
        try {
            //发送了一个注释事件，用作把连接件一起来
            //send方法，推送一条事件
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().comment("stream-start"));
        } catch (Exception ignored) {
        }
        //LangChain4j 的流式输出对象
        dev.langchain4j.service.TokenStream tokenStream = xiaocAgent.chat(chatForm.getMemoryId(), chatForm.getMessage());
        //每来一个token就执行这个onPartialResponse
        tokenStream.onPartialResponse(token -> {
            try {
                //推送一条事件（token）
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().data(token));
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        })
                //模型完整输出
        .onCompleteResponse(res -> emitter.complete())
                //生成中出现的错误
        .onError(emitter::completeWithError)
                //调用.start()才会开始流式调用
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
                    //type的作用：判断是ai的消息还是用户的消息
                    map.put("type", m.type().name());
                    
                    String text = "";
                    // 判断这个消息属于那个子类
                    //将消息转换成前端容易渲染的格式
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
                    //提取用户的第一句话当作标题
                    if (msg instanceof dev.langchain4j.data.message.UserMessage) {
                        String text = ((dev.langchain4j.data.message.UserMessage) msg).singleText();
                        // 过滤掉 RAG 内容作为侧边栏标题
                        if (text != null && text.contains("\n\nAnswer using the following information:\n")) {
                            text = text.split("\n\nAnswer using the following information:\n")[0];
                        }
                        title = text;
                        //如果用户的第一句话过长，那么就进行阶段的操作
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
