package com.it.ai.aiagent.service;

import com.fasterxml.jackson.databind.ObjectMapper; 
import com.it.ai.aiagent.bean.StudyReminderTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReminderRedisQueueService {

    // spring提供的redis操作模板
    private final StringRedisTemplate stringRedisTemplate;
    // 将Java对象转换成JSON对象
    private final ObjectMapper objectMapper;
    // 服务的开关，设置为false的时候禁用此服务
    private final boolean enabled;
    // 每次拉取任务的最大数量
    private final int pollLimit;
    // 处理超时时间
    private final long processingTimeoutMs;
    // 任务ID -> 提醒时间戳（ZSET）
    private final String indexKey;
    // 任务ID -> 任务详情JSON（HASH）
    private final String payloadKey;
    // 任务ID -> 开始处理的时间戳（ZSET）
    private final String processingKey;
    //DefaultRedisScript 预加载 Lua 脚本
    private final DefaultRedisScript<List> claimDueTasksScript;
    private final DefaultRedisScript<Long> upsertTaskScript;
    private final DefaultRedisScript<Long> removeTaskScript;
    private final DefaultRedisScript<Long> recoverProcessingTasksScript;

    public ReminderRedisQueueService(StringRedisTemplate stringRedisTemplate,
                                     ObjectMapper objectMapper,
                                     @Value("${reminder.redis.enabled:true}") boolean enabled,
                                     @Value("${reminder.redis.poll-limit:100}") int pollLimit,
                                     @Value("${reminder.redis.processing-timeout-ms:120000}") long processingTimeoutMs,
                                     @Value("${reminder.redis.key-prefix:reminder}") String keyPrefix) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.pollLimit = Math.max(1, pollLimit);
        this.processingTimeoutMs = Math.max(1000L, processingTimeoutMs);

        String normalizedPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix.trim() : "reminder";
        this.indexKey = normalizedPrefix + ":index";
        this.payloadKey = normalizedPrefix + ":payload";
        this.processingKey = normalizedPrefix + ":processing";

        this.claimDueTasksScript = loadScript("lua/reminder/claim_due_tasks.lua", List.class);
        this.upsertTaskScript = loadScript("lua/reminder/upsert_task.lua", Long.class);
        this.removeTaskScript = loadScript("lua/reminder/remove_task.lua", Long.class);
        this.recoverProcessingTasksScript = loadScript("lua/reminder/recover_processing_tasks.lua", Long.class);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 将任务详情存入到缓存中
     * @param task
     */
    public void upsertTask(StudyReminderTask task) {
        if (!enabled || task == null || task.getId() == null) {
            return;
        }

        if (!canSchedule(task)) {
            removeTask(task.getId());
            return;
        }
        // 将Java对象转换为JSON字符串
        String payload = toPayload(task);
        // 获取触发时间
        long triggerEpochMs = toEpochMillis(task.getTriggerTime(), task.getTimezone());

        stringRedisTemplate.execute(
                upsertTaskScript,
                List.of(indexKey, payloadKey, processingKey),
                String.valueOf(task.getId()),
                String.valueOf(triggerEpochMs),
                payload
        );
    }

    public void upsertTasks(List<StudyReminderTask> tasks) {
        if (!enabled || tasks == null || tasks.isEmpty()) {
            return;
        }
        for (StudyReminderTask task : tasks) {
            upsertTask(task);
        }
    }

    /**
     * 从redis中查找到期任务，当前时间-pollLimit之间的任务
     * @return
     */
    public List<StudyReminderTask> claimDueTasks() {
        if (!enabled) {
            return List.of();
        }

        long now = System.currentTimeMillis();
        long processingDeadline = now + processingTimeoutMs;

        List<?> payloads = stringRedisTemplate.execute(
                claimDueTasksScript,
                List.of(indexKey, payloadKey, processingKey),
                String.valueOf(now),
                String.valueOf(pollLimit),
                String.valueOf(processingDeadline)
        );

        if (payloads == null || payloads.isEmpty()) {
            return List.of();
        }

        List<StudyReminderTask> tasks = new ArrayList<>(payloads.size());
        for (Object payloadObj : payloads) {
            if (payloadObj == null) {
                continue;
            }
            try {
                StudyReminderTask task = objectMapper.readValue(String.valueOf(payloadObj), StudyReminderTask.class);
                tasks.add(task);
            } catch (Exception ignored) {
                // Ignore malformed payloads to keep dispatch loop healthy.
            }
        }
        return tasks;
    }

    /**
     * 超时重试机制，将某种原因没有处理到的在正在执行的队列中的任务重新放回到待处理队列中
     * @return
     */
    public int recoverExpiredProcessingTasks() {
        if (!enabled) {
            return 0;
        }

        long now = System.currentTimeMillis();
        Long recovered = stringRedisTemplate.execute(
                recoverProcessingTasksScript,
                List.of(processingKey, indexKey, payloadKey),
                String.valueOf(now),
                String.valueOf(pollLimit),
                String.valueOf(now)
        );

        return recovered == null ? 0 : recovered.intValue();
    }

    /**
     * 删除缓存里面的数据
     * @param taskId
     */
    public void removeTask(Long taskId) {
        if (!enabled || taskId == null) {
            return;
        }

        stringRedisTemplate.execute(
                removeTaskScript,
                List.of(indexKey, payloadKey, processingKey),
                String.valueOf(taskId)
        );
    }

    private boolean canSchedule(StudyReminderTask task) {
        if (task.getTriggerTime() == null) {
            return false;
        }
        if (task.getDeletedFlag() != null && task.getDeletedFlag() == 1) {
            return false;
        }
        if (task.getStatus() != null && task.getStatus() != 1) {
            return false;
        }
        if (task.getSentStatus() != null && task.getSentStatus() != 0) {
            return false;
        }
        return true;
    }

    private String toPayload(StudyReminderTask task) {
        try {
            return objectMapper.writeValueAsString(task);
        } catch (Exception e) {
            throw new IllegalStateException("序列化提醒任务失败: taskId=" + task.getId(), e);
        }
    }

    private long toEpochMillis(LocalDateTime triggerTime, String timezone) {
        String zoneText = StringUtils.hasText(timezone) ? timezone : "Asia/Shanghai";
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(zoneText);
        } catch (Exception e) {
            zoneId = ZoneId.of("Asia/Shanghai");
        }
        return triggerTime.atZone(zoneId).toInstant().toEpochMilli();
    }

    private <T> DefaultRedisScript<T> loadScript(String classpath, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(classpath));
        script.setResultType(resultType);
        return script;
    }
}
