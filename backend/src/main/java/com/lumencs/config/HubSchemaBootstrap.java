package com.lumencs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 已有数据卷不会重跑 schema.sql。启动时补齐中枢相关表。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HubSchemaBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HubSchemaBootstrap.class);
    private final JdbcTemplate jdbc;

    public HubSchemaBootstrap(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbc.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS cs_notify_channel (
                    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
                    name        VARCHAR(64)  NOT NULL,
                    type        VARCHAR(32)  NOT NULL,
                    config_json JSON         DEFAULT NULL,
                    enabled     TINYINT(1)   NOT NULL DEFAULT 1,
                    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS cs_notify_log (
                    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
                    channel_id  BIGINT       DEFAULT NULL,
                    event_type  VARCHAR(64)  NOT NULL,
                    event_id    VARCHAR(128) NOT NULL,
                    success     TINYINT(1)   NOT NULL DEFAULT 1,
                    detail      VARCHAR(512) DEFAULT NULL,
                    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_notify_log_event (event_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        log.info("hub tables ready");
    }
}
