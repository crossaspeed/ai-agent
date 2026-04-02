package com.it.ai.aiagent.service;

import com.it.ai.aiagent.bean.StudyReminderTask;
import com.it.ai.aiagent.store.StudyReminderTaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring Boot 应用启动时，将数据库中尚未完成的提醒任务（Pending Tasks）全量或增量地同步到 Redis 队列中
 */
@Component
public class ReminderRedisBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReminderRedisBootstrapService.class);

    @Autowired
    private StudyReminderTaskStore studyReminderTaskStore;

    @Autowired
    private ReminderRedisQueueService reminderRedisQueueService;

    @Value("${reminder.redis.bootstrap-on-startup:true}")
    private boolean bootstrapOnStartup;

    @Value("${reminder.redis.bootstrap-batch-size:1000}")
    private int bootstrapBatchSize;

    @Override
    public void run(ApplicationArguments args) {
        if (!bootstrapOnStartup || !reminderRedisQueueService.isEnabled()) {
            return;
        }

        int safeBatchSize = Math.max(100, bootstrapBatchSize);
        long lastId = 0L;
        int total = 0;

        while (true) {
            List<StudyReminderTask> tasks = studyReminderTaskStore.findPendingTasksByCursor(lastId, safeBatchSize);
            if (tasks.isEmpty()) {
                break;
            }

            reminderRedisQueueService.upsertTasks(tasks);
            total += tasks.size();
            lastId = tasks.get(tasks.size() - 1).getId();

            if (tasks.size() < safeBatchSize) {
                break;
            }
        }

        log.info("提醒任务Redis预热完成: total={}", total);
    }
}
