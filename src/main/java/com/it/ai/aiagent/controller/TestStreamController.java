package com.it.ai.aiagent.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;

@RestController
public class TestStreamController {

    @GetMapping(value = "/agent/test-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> testStream() {
        return Flux.interval(Duration.ofSeconds(1))
                .map(i -> "Test chunk " + i)
                .take(5);
    }
}
