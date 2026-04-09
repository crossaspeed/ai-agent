package com.it.ai.aiagent.bean;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document("topics")
public class Topic {
    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    private int docCount;

    private Instant createdAt;

    @Indexed(direction = IndexDirection.DESCENDING)
    private Instant updatedAt;
}
