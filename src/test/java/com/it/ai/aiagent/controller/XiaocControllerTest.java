package com.it.ai.aiagent.controller;

import com.it.ai.aiagent.assistant.XiaocAgent;
import com.it.ai.aiagent.store.MongoChatMemoryStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(XiaocController.class)
class XiaocControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private XiaocAgent xiaocAgent;

    @MockBean
    private MongoChatMemoryStore mongoChatMemoryStore;

    @Test
    void historyShouldReturnChatHistoryByMemoryId() throws Exception {
        when(mongoChatMemoryStore.getMessages(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/agent/history/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(mongoChatMemoryStore).getMessages(1L);
    }
}
