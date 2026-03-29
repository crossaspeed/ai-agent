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
import java.time.LocalTime;
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
        task.setPlanBatchId(rs.getString("plan_batch_id"));
        task.setSourceOpenId(rs.getString("source_open_id"));
        task.setSourceChannel(rs.getString("source_channel"));
        task.setSourceMsgId(rs.getString("source_msg_id"));
        task.setTimezone(rs.getString("timezone"));
        task.setDeletedFlag(rs.getInt("deleted_flag"));
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
        String sql = "INSERT INTO study_reminder_task (plan_name, plan_batch_id, study_date, reminder_time, trigger_time, rag_topic, study_content, channels_json, feishu_open_id, source_open_id, source_channel, source_msg_id, timezone, deleted_flag, status, sent_status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        int[] rows = jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                StudyReminderTask task = tasks.get(i);
                ps.setString(1, task.getPlanName());
                ps.setString(2, task.getPlanBatchId());
                ps.setObject(3, task.getStudyDate());
                ps.setObject(4, task.getReminderTime());
                ps.setObject(5, task.getTriggerTime());
                ps.setString(6, task.getRagTopic());
                ps.setString(7, task.getStudyContent());
                ps.setString(8, task.getChannelsJson());
                ps.setString(9, task.getFeishuOpenId());
                ps.setString(10, task.getSourceOpenId());
                ps.setString(11, task.getSourceChannel());
                ps.setString(12, task.getSourceMsgId());
                ps.setString(13, task.getTimezone());
                ps.setInt(14, task.getDeletedFlag() == null ? 0 : task.getDeletedFlag());
                ps.setInt(15, task.getStatus() == null ? 1 : task.getStatus());
                ps.setInt(16, task.getSentStatus() == null ? 0 : task.getSentStatus());
            }

            @Override
            public int getBatchSize() {
                return tasks.size();
            }
        });
        return rows.length;
    }

    public List<StudyReminderTask> findTasksInRange(LocalDate from, LocalDate to) {
        String sql = "SELECT * FROM study_reminder_task WHERE deleted_flag = 0 AND study_date BETWEEN ? AND ? ORDER BY trigger_time ASC";
        return jdbcTemplate.query(sql, taskRowMapper, from, to);
    }

    public List<StudyReminderTask> findTasksInRangeByOpenId(String openId, LocalDate from, LocalDate to) {
        String sql = "SELECT * FROM study_reminder_task WHERE deleted_flag = 0 AND source_open_id = ? AND study_date BETWEEN ? AND ? ORDER BY trigger_time ASC";
        return jdbcTemplate.query(sql, taskRowMapper, openId, from, to);
    }

    public List<StudyReminderTask> findDueTasks(LocalDateTime now, int limit) {
        String sql = "SELECT * FROM study_reminder_task WHERE deleted_flag = 0 AND status = 1 AND sent_status = 0 AND trigger_time <= ? ORDER BY trigger_time ASC LIMIT ?";
        return jdbcTemplate.query(sql, taskRowMapper, now, limit);
    }

    public int updateTaskStatus(Long id, boolean enabled) {
        String sql = "UPDATE study_reminder_task SET status = ? WHERE deleted_flag = 0 AND id = ?";
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
        String sql = "SELECT * FROM study_reminder_task WHERE deleted_flag = 0 AND id = ?";
        List<StudyReminderTask> tasks = jdbcTemplate.query(sql, taskRowMapper, id);
        if (tasks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(tasks.get(0));
    }

    public int softDeleteByOpenIdAndRange(String openId, LocalDate from, LocalDate to, LocalTime timeFilter) {
        if (timeFilter == null) {
            String sql = "UPDATE study_reminder_task SET deleted_flag = 1, status = 0 WHERE deleted_flag = 0 AND source_open_id = ? AND study_date BETWEEN ? AND ?";
            return jdbcTemplate.update(sql, openId, from, to);
        }
        String sql = "UPDATE study_reminder_task SET deleted_flag = 1, status = 0 WHERE deleted_flag = 0 AND source_open_id = ? AND study_date BETWEEN ? AND ? AND reminder_time = ?";
        return jdbcTemplate.update(sql, openId, from, to, timeFilter);
    }

    public int updateByOpenIdAndDate(String openId,
                                     LocalDate targetDate,
                                     LocalTime oldTime,
                                     LocalTime newTime,
                                     String newTopic,
                                     String newStudyContent) {
        StringBuilder sql = new StringBuilder("UPDATE study_reminder_task SET ");
        java.util.List<Object> params = new java.util.ArrayList<>();

        if (newTime != null) {
            sql.append("reminder_time = ?, trigger_time = TIMESTAMP(study_date, ?), sent_status = 0, ");
            params.add(newTime);
            params.add(newTime);
        }
        if (newTopic != null) {
            sql.append("rag_topic = ?, ");
            params.add(newTopic);
        }
        if (newStudyContent != null) {
            sql.append("study_content = ?, ");
            params.add(newStudyContent);
        }

        if (params.isEmpty()) {
            return 0;
        }

        sql.append("updated_at = CURRENT_TIMESTAMP WHERE deleted_flag = 0 AND source_open_id = ? AND study_date = ?");
        params.add(openId);
        params.add(targetDate);

        if (oldTime != null) {
            sql.append(" AND reminder_time = ?");
            params.add(oldTime);
        }

        return jdbcTemplate.update(sql.toString(), params.toArray());
    }
}
