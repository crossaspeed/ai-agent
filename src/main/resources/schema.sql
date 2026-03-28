CREATE TABLE IF NOT EXISTS study_reminder_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    plan_name VARCHAR(128) NOT NULL,
    study_date DATE NOT NULL,
    reminder_time TIME NOT NULL,
    trigger_time DATETIME NOT NULL,
    rag_topic VARCHAR(255) NOT NULL,
    study_content VARCHAR(1000) NULL,
    channels_json VARCHAR(255) NOT NULL,
    feishu_open_id VARCHAR(128) NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    status TINYINT NOT NULL DEFAULT 1,
    sent_status TINYINT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    sent_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_trigger_status (trigger_time, status, sent_status),
    INDEX idx_study_date (study_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE study_reminder_task CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
