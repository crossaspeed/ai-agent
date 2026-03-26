package com.it.ai.aiagent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Health", description = "Basic health check endpoints")
@RestController
@RequestMapping("/api")
public class PingController {

    @Operation(summary = "Ping", description = "Returns a simple pong message.")
    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
