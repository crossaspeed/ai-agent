package com.it.ai.aiagent.bean;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document("topics")
public class Topic {
    @Id
    private String id;
    private String name;
    private int docCount;
}
