package com.it.ai.aiagent.controller;

import com.it.ai.aiagent.assistant.XiaocAgent;
import com.it.ai.aiagent.bean.ChatForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Tag(name = "我的智能体")
@RestController
@RequestMapping("/agent")
public class XiaocController {
    @Autowired
    private XiaocAgent xiaocAgent;

    @Operation(summary = "对话")
    @PostMapping("/chat")
    public Flux<String> chat(@RequestBody ChatForm chatForm) {
        return xiaocAgent.chat(chatForm.getMemoryId(), chatForm.getMessage());
    }
}