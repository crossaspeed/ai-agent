package com.it.ai.aiagent;

import com.it.ai.aiagent.bean.Topic;
import com.it.ai.aiagent.store.TopicRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class MongoInit {
    @Autowired
    TopicRepository repo;
    
    @Test
    public void init() {
        if (repo.findByName("计算机网络") == null) {
            Topic t = new Topic();
            t.setName("计算机网络");
            t.setDocCount(1);
            repo.save(t);
        }
    }
}
