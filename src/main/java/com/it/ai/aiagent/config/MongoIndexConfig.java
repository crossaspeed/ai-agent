package com.it.ai.aiagent.config;

import com.it.ai.aiagent.bean.Topic;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

@Configuration
public class MongoIndexConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexConfig.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @PostConstruct
    public void ensureTopicIndexes() {
        IndexOperations indexOperations = mongoTemplate.indexOps(Topic.class);
        ensureIndex(indexOperations, new Index().on("name", Sort.Direction.ASC).named("name_1").unique());
        ensureIndex(indexOperations, new Index().on("updatedAt", Sort.Direction.DESC).named("updatedAt_-1"));
    }

    private void ensureIndex(IndexOperations indexOperations, Index index) {
        try {
            String ensured = indexOperations.ensureIndex(index);
            log.info("Mongo index ensured on topics: {}", ensured);
        } catch (Exception ex) {
            log.warn("Failed to ensure Mongo index on topics: {}", ex.getMessage());
        }
    }
}