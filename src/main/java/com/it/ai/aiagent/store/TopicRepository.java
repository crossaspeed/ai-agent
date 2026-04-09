package com.it.ai.aiagent.store;

import com.it.ai.aiagent.bean.Topic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TopicRepository extends MongoRepository<Topic, String> {
    Topic findByName(String name);

    @Query(value = "{}", fields = "{ '_id': 1, 'name': 1, 'docCount': 1 }")
    Page<TopicSummaryProjection> findTopicSummaries(Pageable pageable);

    interface TopicSummaryProjection {
        String getId();

        String getName();

        int getDocCount();
    }
}
