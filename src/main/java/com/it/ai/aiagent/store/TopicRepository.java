package com.it.ai.aiagent.store;

import com.it.ai.aiagent.bean.Topic;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TopicRepository extends MongoRepository<Topic, String> {
    Topic findByName(String name);
}
