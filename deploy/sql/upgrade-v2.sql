-- LumenCS 增量升级 SQL（已有库手工执行；全新库直接跑 schema.sql 即可）
-- V2：审计字段 + 上一轮新增表

-- 1) 审计字段（SuperEntity：create_user / update_user）
ALTER TABLE cs_ticket   ADD COLUMN create_user VARCHAR(64) NOT NULL DEFAULT 'system' AFTER priority, ADD COLUMN update_user VARCHAR(64) NOT NULL DEFAULT 'system' AFTER create_user;
ALTER TABLE cs_document ADD COLUMN create_user VARCHAR(64) NOT NULL DEFAULT 'system' AFTER chunk_count, ADD COLUMN update_user VARCHAR(64) NOT NULL DEFAULT 'system' AFTER create_user;
ALTER TABLE cs_session  ADD COLUMN create_user VARCHAR(64) NOT NULL DEFAULT 'system' AFTER user_label, ADD COLUMN update_user VARCHAR(64) NOT NULL DEFAULT 'system' AFTER create_user;

-- 2) 上一轮新增表（若 schema.sql 未含）
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
