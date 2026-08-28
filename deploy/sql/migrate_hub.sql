-- 已有库升级：个人 AI 服务中枢增量表。全新安装走 schema.sql 即可。
USE lumen_cs;

CREATE TABLE IF NOT EXISTS cs_feedback (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id  VARCHAR(36)  NOT NULL,
    message_id  BIGINT       NOT NULL,
    score       VARCHAR(16)  NOT NULL,
    cited       TINYINT(1)   NOT NULL DEFAULT 0,
    comment     VARCHAR(512) DEFAULT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_feedback_msg (message_id),
    INDEX idx_feedback_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_inbox (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_type  VARCHAR(64)  NOT NULL,
    event_id    VARCHAR(128) NOT NULL,
    title       VARCHAR(255) NOT NULL,
    body        TEXT,
    read_flag   TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_inbox_event (event_id),
    INDEX idx_inbox_read (read_flag, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_notify_channel (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(64)  NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    config_json JSON         DEFAULT NULL,
    enabled     TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_notify_log (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    channel_id  BIGINT       DEFAULT NULL,
    event_type  VARCHAR(64)  NOT NULL,
    event_id    VARCHAR(128) NOT NULL,
    success     TINYINT(1)   NOT NULL DEFAULT 1,
    detail      VARCHAR(512) DEFAULT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notify_log_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
