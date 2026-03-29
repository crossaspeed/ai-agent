package com.it.ai.aiagent.store;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FeishuEventLogStore {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public boolean tryInsertEvent(String eventId, String messageId, String openId, String eventType, String rawBody) {
        String sql = "INSERT IGNORE INTO feishu_event_log (event_id, message_id, open_id, event_type, raw_body, process_status) VALUES (?, ?, ?, ?, ?, 0)";
        int rows = jdbcTemplate.update(sql, eventId, messageId, openId, eventType, rawBody);
        return rows > 0;
    }

    public void markStatus(String eventId, int processStatus, String errorMessage) {
        String sql = "UPDATE feishu_event_log SET process_status = ?, error_message = ? WHERE event_id = ?";
        jdbcTemplate.update(sql, processStatus, errorMessage, eventId);
    }
}
