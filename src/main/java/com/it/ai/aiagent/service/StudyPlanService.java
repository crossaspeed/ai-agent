package com.it.ai.aiagent.service;

import com.it.ai.aiagent.bean.StudyPlanCreateRequest;
import com.it.ai.aiagent.bean.StudyPlanDayRequest;
import com.it.ai.aiagent.bean.StudyReminderTask;
import com.it.ai.aiagent.bean.StudyReminderTaskView;
import com.it.ai.aiagent.store.StudyReminderTaskStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StudyPlanService {

    @Autowired
    private StudyReminderTaskStore studyReminderTaskStore;

    @Autowired
    private ReminderNotificationService reminderNotificationService;

    /**
     * 将前端页面填写的数据进行数据库的持久化的操作
     * @param request
     * @return
     */
    public Map<String, Object> createWeeklyPlan(StudyPlanCreateRequest request) {
        validateCreateRequest(request);
        // 设置默认值
        String timezone = StringUtils.hasText(request.getTimezone()) ? request.getTimezone() : "Asia/Shanghai";
        String planName = StringUtils.hasText(request.getPlanName()) ? request.getPlanName() : "接下来一周学习计划";
        String channels = request.getChannels().stream()
                .map(String::trim)
                .map(v -> v.toLowerCase(Locale.ROOT))
                .filter("feishu"::equals)
                .collect(Collectors.joining(","));

        if (!StringUtils.hasText(channels)) {
            channels = "feishu";
        }
        // 某些数据为空的话，进行默认操作的处理
        List<StudyReminderTask> tasks = new ArrayList<>();
        String batchId = StringUtils.hasText(request.getPlanBatchId())
            ? request.getPlanBatchId()
            : UUID.randomUUID().toString();
        String sourceOpenId = StringUtils.hasText(request.getSourceOpenId())
            ? request.getSourceOpenId()
            : request.getFeishuOpenId();
        String sourceChannel = StringUtils.hasText(request.getSourceChannel())
            ? request.getSourceChannel()
            : "feishu";
        for (StudyPlanDayRequest day : request.getDays()) {
            if (!StringUtils.hasText(day.getRagTopic()) || !StringUtils.hasText(day.getReminderTime()) || !StringUtils.hasText(day.getDate())) {
                continue;
            }

            LocalDate date = parseDate(day.getDate());
            LocalTime time = parseTime(day.getReminderTime());
            LocalDateTime trigger = LocalDateTime.of(date, time);

            // Allow short client-server delays for near-term reminders (e.g. set to 1-2 minutes later).
            // 如果设定的时间在当前时间的下一分钟，那么进行跳过的操作
            if (trigger.isBefore(LocalDateTime.now().minusMinutes(1))) {
                continue;
            }
            // 数据库表对象的设置
            StudyReminderTask task = new StudyReminderTask();
            task.setPlanName(planName);
            task.setPlanBatchId(batchId);
            task.setStudyDate(date);
            task.setReminderTime(time);
            task.setTriggerTime(trigger);
            task.setRagTopic(day.getRagTopic().trim());
            task.setStudyContent(day.getStudyContent());
            task.setChannelsJson(channels);
            task.setFeishuOpenId(request.getFeishuOpenId());
            task.setSourceOpenId(sourceOpenId);
            task.setSourceChannel(sourceChannel);
            task.setSourceMsgId(request.getSourceMsgId());
            task.setTimezone(timezone);
            task.setDeletedFlag(0);
            task.setStatus(1);
            task.setSentStatus(0);
            tasks.add(task);
        }

        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("没有可保存的提醒任务，请检查日期和时间");
        }

        int created = studyReminderTaskStore.saveBatch(tasks);
        return Map.of(
                "created", created,
                "message", "周计划已保存"
        );
    }
    // 查询接下来几天的计划 （默认为14天）
    public List<StudyReminderTaskView> getUpcomingTasks(int days) {
        int safeDays = Math.max(1, Math.min(days, 30));
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(safeDays - 1L);
        return studyReminderTaskStore.findTasksInRange(from, to).stream()
                .map(this::toView)
                .toList();
    }

    public List<StudyReminderTaskView> getUpcomingTasksByOpenId(String openId, int days) {
        if (!StringUtils.hasText(openId)) {
            return List.of();
        }
        int safeDays = Math.max(1, Math.min(days, 30));
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(safeDays - 1L);
        return studyReminderTaskStore.findTasksInRangeByOpenId(openId, from, to).stream()
                .map(this::toView)
                .toList();
    }

    public List<StudyReminderTaskView> getTasksByOpenIdInRange(String openId, LocalDate from, LocalDate to) {
        if (!StringUtils.hasText(openId) || from == null || to == null) {
            return List.of();
        }
        return studyReminderTaskStore.findTasksInRangeByOpenId(openId, from, to).stream()
                .map(this::toView)
                .toList();
    }

    public int deleteTasksByOpenId(String openId, LocalDate from, LocalDate to, LocalTime timeFilter) {
        if (!StringUtils.hasText(openId) || from == null || to == null) {
            return 0;
        }
        return studyReminderTaskStore.softDeleteByOpenIdAndRange(openId, from, to, timeFilter);
    }

    public int updateTaskByOpenId(String openId,
                                  LocalDate targetDate,
                                  LocalTime oldTime,
                                  LocalTime newTime,
                                  String newTopic,
                                  String newStudyContent) {
        if (!StringUtils.hasText(openId) || targetDate == null) {
            return 0;
        }
        return studyReminderTaskStore.updateByOpenIdAndDate(openId, targetDate, oldTime, newTime, newTopic, newStudyContent);
    }

    //更新语句：启用或者停止某个任务
    public Map<String, Object> updateTaskStatus(Long id, boolean enabled) {
        int rows = studyReminderTaskStore.updateTaskStatus(id, enabled);
        if (rows == 0) {
            throw new IllegalArgumentException("任务不存在");
        }
        return Map.of(
                "updated", rows,
                "enabled", enabled
        );
    }

    public Map<String, Object> sendTestReminder(Long id) {
        // Optional的使用：理解成一个盒子 盒子里面有值，那么久查询到了任务 盒子里面没有值 那么久没有查询到任务
        // 可能查询不到值 现代业务的常见实践
        Optional<StudyReminderTask> taskOptional = studyReminderTaskStore.findById(id);
        if (taskOptional.isEmpty()) {
            throw new IllegalArgumentException("任务不存在");
        }

        String result = reminderNotificationService.sendReminder(taskOptional.get(), true);
        return Map.of(
                "taskId", id,
                "result", result
        );
    }

    public void executeDueTasks() {
        List<StudyReminderTask> dueTasks = studyReminderTaskStore.findDueTasks(LocalDateTime.now(), 100);
        for (StudyReminderTask task : dueTasks) {
            try {
                reminderNotificationService.sendReminder(task, false);
                studyReminderTaskStore.markSent(task.getId(), LocalDateTime.now());
            } catch (Exception e) {
                String message = e.getMessage() == null ? "发送失败" : e.getMessage();
                studyReminderTaskStore.markFailed(task.getId(), truncate(message, 900));
            }
        }
    }

    private StudyReminderTaskView toView(StudyReminderTask task) {
        StudyReminderTaskView view = new StudyReminderTaskView();
        view.setId(task.getId());
        view.setPlanName(task.getPlanName());
        view.setStudyDate(task.getStudyDate() == null ? null : task.getStudyDate().toString());
        view.setReminderTime(task.getReminderTime() == null ? null : task.getReminderTime().toString());
        view.setRagTopic(task.getRagTopic());
        view.setStudyContent(task.getStudyContent());
        view.setTimezone(task.getTimezone());
        view.setEnabled(task.getStatus() != null && task.getStatus() == 1);
        view.setSentStatus(task.getSentStatus());
        view.setErrorMessage(task.getErrorMessage());
        view.setSentAt(task.getSentAt() == null ? null : task.getSentAt().toString());
        view.setHasFeishuConfig(StringUtils.hasText(task.getFeishuOpenId()));
        view.setChannels(task.getChannelsJson());
        return view;
    }

    private void validateCreateRequest(StudyPlanCreateRequest request) {
        if (request == null || request.getDays() == null || request.getDays().isEmpty()) {
            throw new IllegalArgumentException("请至少配置一天学习计划");
        }
        if (request.getChannels() == null || request.getChannels().isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个提醒渠道");
        }

        List<String> channels = request.getChannels().stream()
                .map(String::trim)
                .map(v -> v.toLowerCase(Locale.ROOT))
                .toList();

        if (channels.stream().anyMatch(v -> !"feishu".equals(v))) {
            throw new IllegalArgumentException("当前仅支持飞书提醒");
        }

        if (channels.contains("feishu") && !StringUtils.hasText(request.getFeishuOpenId())) {
            throw new IllegalArgumentException("已选择飞书提醒，请填写飞书 open_id");
        }
    }

    private LocalDate parseDate(String rawDate) {
        try {
            return LocalDate.parse(rawDate, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("日期格式必须为 yyyy-MM-dd: " + rawDate);
        }
    }

    private LocalTime parseTime(String rawTime) {
        try {
            return LocalTime.parse(rawTime, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("时间格式必须为 HH:mm: " + rawTime);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
