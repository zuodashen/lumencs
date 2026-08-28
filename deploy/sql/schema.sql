-- 手工执行。应用不集成 Flyway。
CREATE DATABASE IF NOT EXISTS lumen_cs DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE lumen_cs;

CREATE TABLE IF NOT EXISTS cs_admin (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_session (
    id          VARCHAR(36)  PRIMARY KEY,
    user_label  VARCHAR(64)  NOT NULL DEFAULT '访客',
    create_user VARCHAR(64)  NOT NULL DEFAULT 'system',
    update_user VARCHAR(64)  NOT NULL DEFAULT 'system',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_message (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id     VARCHAR(36)  NOT NULL,
    role           VARCHAR(16)  NOT NULL,
    content        MEDIUMTEXT   NOT NULL,
    intent         VARCHAR(64)  DEFAULT NULL,
    citations_json JSON         DEFAULT NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_msg_session (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_document (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    title       VARCHAR(255) NOT NULL,
    source      VARCHAR(255) NOT NULL DEFAULT '',
    content     MEDIUMTEXT   NOT NULL,
    status      VARCHAR(32)  NOT NULL DEFAULT 'READY',
    chunk_count INT          NOT NULL DEFAULT 0,
    create_user VARCHAR(64)  NOT NULL DEFAULT 'system',
    update_user VARCHAR(64)  NOT NULL DEFAULT 'system',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_chunk (
    id          VARCHAR(36)  PRIMARY KEY,
    document_id BIGINT       NOT NULL,
    content     TEXT         NOT NULL,
    source      VARCHAR(255) NOT NULL DEFAULT '',
    sort_order  INT          NOT NULL DEFAULT 0,
    INDEX idx_chunk_doc (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_ticket (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_no   VARCHAR(32)  NOT NULL UNIQUE,
    session_id  VARCHAR(36)  DEFAULT NULL,
    user_label  VARCHAR(64)  NOT NULL DEFAULT '访客',
    title       VARCHAR(255) NOT NULL,
    description TEXT         NOT NULL,
    status      VARCHAR(32)  NOT NULL DEFAULT 'CREATED',
    priority    VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    create_user VARCHAR(64)  NOT NULL DEFAULT 'system',
    update_user VARCHAR(64)  NOT NULL DEFAULT 'system',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ticket_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_span (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id   VARCHAR(36)  NOT NULL,
    message_id   BIGINT       DEFAULT NULL,
    agent        VARCHAR(64)  NOT NULL,
    method       VARCHAR(64)  NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    duration_ms  BIGINT       NOT NULL DEFAULT 0,
    detail_json  JSON         DEFAULT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_span_session (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_tool_log (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id     VARCHAR(36)  DEFAULT NULL,
    tool           VARCHAR(64)  NOT NULL,
    arguments_json JSON         DEFAULT NULL,
    result_json    JSON         DEFAULT NULL,
    success        TINYINT(1)   NOT NULL DEFAULT 1,
    duration_ms    BIGINT       NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tool_log_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_review (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id       VARCHAR(36)  NOT NULL,
    message_id       BIGINT       DEFAULT NULL,
    original_content TEXT         NOT NULL,
    intent           VARCHAR(64)  DEFAULT NULL,
    violations_json  JSON         DEFAULT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    review_note      VARCHAR(512) DEFAULT NULL,
    reviewed_by      VARCHAR(64)  DEFAULT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at      DATETIME     DEFAULT NULL,
    INDEX idx_review_status (status),
    INDEX idx_review_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
