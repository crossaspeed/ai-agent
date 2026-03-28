package com.it.ai.aiagent.store;

import com.it.ai.aiagent.bean.StudyReminderTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class StudyReminderTaskStore {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<StudyReminderTask> taskRowMapper = (rs, rowNum) -> {
        StudyReminderTask task = new StudyReminderTask();
        task.setId(rs.getLong("id"));
        task.setPlanName(rs.getString("plan_name"));
        task.setStudyDate(rs.getDate("study_date").toLocalDate());
        Time reminderTime = rs.getTime("reminder_time");
        task.setReminderTime(reminderTime == null ? null : reminderTime.toLocalTime());
        Timestamp triggerTime = rs.getTimestamp("trigger_time");
        task.setTriggerTime(triggerTime == null ? null : triggerTime.toLocalDateTime());
        task.setRagTopic(rs.getString("rag_topic"));
        task.setStudyContent(rs.getString("study_content"));
        task.setChannelsJson(rs.getString("channels_json"));
        task.setFeishuOpenId(rs.getString("feishu_open_id"));
        task.setTimezone(rs.getString("timezone"));
        task.setStatus(rs.getInt("status"));
        task.setSentStatus(rs.getInt("sent_status"));
        task.setErrorMessage(rs.getString("error_message"));
        Timestamp sentAt = rs.getTimestamp("sent_at");
        task.setSentAt(sentAt == null ? null : sentAt.toLocalDateTime());
        Timestamp createdAt = rs.getTimestamp("created_at");
        task.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        task.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
        return task;
    };

    public int saveBatch(List<StudyReminderTask> tasks) {
        String sql = "INSERT INTO study_reminder_task (plan_name, study_date, reminder_time, trigger_time, rag_topic, study_content, channels_json, feishu_open_id, timezone, status, sent_status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        int[] rows = jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                StudyReminderTask task = tasks.get(i);
                ps.setString(1, task.getPlanName());
                ps.setObject(2, task.getStudyDate());
                ps.setObject(3, task.getReminderTime());
                ps.setObject(4, task.getTriggerTime());
                ps.setString(5, task.getRagTopic());
                ps.setString(6, task.getStudyContent());
                ps.setString(7, task.getChannelsJson());
                ps.setString(8, task.getFeishuOpenId());
                ps.setString(9, task.getTimezone());
                ps.setInt(10, task.getStatus() == null ? 1 : task.getStatus());
                ps.setInt(11, task.getSentStatus() == null ? 0 : task.getSentStatus());
            }

            @Override
            public int getBatchSize() {
                return tasks.size();
            }
        });
        return rows.length;
    }

    public List<StudyReminderTask> findTasksInRange(LocalDate from, LocalDate to) {
        String sql = "SELECT * FROM study_reminder_task WHERE study_date BETWEEN ? AND ? ORDER BY trigger_time ASC";
        return jdbcTemplate.query(sql, taskRowMapper, from, to);
    }

    public List<StudyReminderTask> findDueTasks(LocalDateTime now, int limit) {
        String sql = "SELECT * FROM study_reminder_task WHERE status = 1 AND sent_status = 0 AND trigger_time <= ? ORDER BY trigger_time ASC LIMIT ?";
        return jdbcTemplate.query(sql, taskRowMapper, now, limit);
    }

    public int updateTaskStatus(Long id, boolean enabled) {
        String sql = "UPDATE study_reminder_task SET status = ? WHERE id = ?";
        return jdbcTemplate.update(sql, enabled ? 1 : 0, id);
    }

    public int markSent(Long id, LocalDateTime sentAt) {
        String sql = "UPDATE study_reminder_task SET sent_status = 1, sent_at = ?, error_message = NULL WHERE id = ?";
        return jdbcTemplate.update(sql, sentAt, id);
    }

    public int markFailed(Long id, String errorMessage) {
        String sql = "UPDATE study_reminder_task SET sent_status = 2, error_message = ? WHERE id = ?";
        return jdbcTemplate.update(sql, errorMessage, id);
    }

    public Optional<StudyReminderTask> findById(Long id) {
        String sql = "SELECT * FROM study_reminder_task WHERE id = ?";
        List<StudyReminderTask> tasks = jdbcTemplate.query(sql, taskRowMapper, id);
        if (tasks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(tasks.get(0));
    }
}
