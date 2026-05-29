package com.fsd.bootstrap.integration;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * H2 schema for bootstrap integration tests (aligned with V7/V8/V9).
 */
final class IntegrationTestSchema {

    private IntegrationTestSchema() {
    }

    static void recreateSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_admin_session");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_admin_user");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_charging_session");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_charging_pile");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_parking_slot");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_road_segment");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_road_node");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_dispatch_exception_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_dispatch_task_operate_log");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_dispatch_event_outbox");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_dispatch_task");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_order");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_vehicle_credential");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_vehicle_command");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_vehicle");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_station");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_park");

        jdbcTemplate.execute("""
                CREATE TABLE t_park (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    park_code VARCHAR(64) NOT NULL,
                    park_name VARCHAR(128) NOT NULL,
                    map_width INT,
                    map_height INT,
                    min_zoom INT,
                    max_zoom INT,
                    vehicle_speed_px_per_second DECIMAL(10,2),
                    status VARCHAR(32) NOT NULL,
                    default_flag TINYINT NOT NULL DEFAULT 0,
                    remark VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    version INT DEFAULT 0,
                    deleted TINYINT DEFAULT 0
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_station (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    park_id BIGINT NOT NULL,
                    station_code VARCHAR(64) NOT NULL,
                    station_name VARCHAR(128) NOT NULL,
                    station_type VARCHAR(32) NOT NULL,
                    coord_x DECIMAL(12,4) NOT NULL,
                    coord_y DECIMAL(12,4) NOT NULL,
                    area VARCHAR(32),
                    status VARCHAR(32) NOT NULL,
                    sort_order INT DEFAULT 0,
                    remark VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    version INT DEFAULT 0,
                    deleted TINYINT DEFAULT 0
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_order (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    order_no VARCHAR(64) NOT NULL,
                    external_order_no VARCHAR(64),
                    source_type VARCHAR(32) NOT NULL,
                    biz_type VARCHAR(32) NOT NULL,
                    park_id BIGINT,
                    pickup_point_id BIGINT NOT NULL,
                    dropoff_point_id BIGINT NOT NULL,
                    priority VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    dispatch_task_id BIGINT,
                    remark VARCHAR(255),
                    created_by VARCHAR(64),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    version INT DEFAULT 0,
                    deleted TINYINT DEFAULT 0
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_dispatch_task (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    task_no VARCHAR(64) NOT NULL,
                    order_id BIGINT NOT NULL,
                    vehicle_id BIGINT,
                    dispatch_type VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    fail_reason_code VARCHAR(64),
                    fail_reason_msg VARCHAR(255),
                    assign_time TIMESTAMP,
                    start_time TIMESTAMP,
                    finish_time TIMESTAMP,
                    manual_flag TINYINT DEFAULT 0,
                    retry_count INT DEFAULT 0,
                    remark VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    version INT DEFAULT 0,
                    deleted TINYINT DEFAULT 0
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_vehicle (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    vehicle_code VARCHAR(64) NOT NULL,
                    vehicle_name VARCHAR(128) NOT NULL,
                    vehicle_type VARCHAR(32),
                    link_mode VARCHAR(16) NOT NULL DEFAULT 'SIM',
                    online_status VARCHAR(32) NOT NULL,
                    dispatch_status VARCHAR(32) NOT NULL,
                    current_task_id BIGINT,
                    current_order_id BIGINT,
                    current_latitude DECIMAL(10,6),
                    current_longitude DECIMAL(10,6),
                    battery_level INT,
                    last_report_time TIMESTAMP,
                    remark VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    version INT DEFAULT 0,
                    deleted TINYINT DEFAULT 0
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_vehicle_command (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    vehicle_id BIGINT NOT NULL,
                    task_id BIGINT NOT NULL,
                    order_id BIGINT NOT NULL,
                    command_type VARCHAR(32) NOT NULL,
                    command_status VARCHAR(32) NOT NULL,
                    payload_json CLOB NOT NULL,
                    fail_reason VARCHAR(255),
                    issued_at TIMESTAMP NOT NULL,
                    delivered_at TIMESTAMP,
                    acked_at TIMESTAMP,
                    failed_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_vehicle_credential (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    vehicle_id BIGINT NOT NULL,
                    api_key VARCHAR(128) NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_dispatch_task_operate_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    task_id BIGINT NOT NULL,
                    operate_type VARCHAR(32) NOT NULL,
                    before_status VARCHAR(32),
                    after_status VARCHAR(32),
                    operator_type VARCHAR(32) NOT NULL,
                    operator_id VARCHAR(64),
                    operator_name VARCHAR(64),
                    operate_remark VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_dispatch_exception_record (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    task_id BIGINT,
                    order_id BIGINT,
                    vehicle_id BIGINT,
                    exception_type VARCHAR(32) NOT NULL,
                    exception_status VARCHAR(32) NOT NULL,
                    exception_msg VARCHAR(255),
                    severity VARCHAR(16) NOT NULL DEFAULT 'WARN',
                    occur_time TIMESTAMP NOT NULL,
                    resolved_time TIMESTAMP,
                    resolver_id VARCHAR(64),
                    resolve_remark VARCHAR(255),
                    resolve_action VARCHAR(32),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_dispatch_event_outbox (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    event_id VARCHAR(64) NOT NULL,
                    event_type VARCHAR(64) NOT NULL,
                    business_key VARCHAR(64) NOT NULL,
                    payload CLOB NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    retry_count INT DEFAULT 0,
                    last_error VARCHAR(255),
                    next_retry_time TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_parking_slot (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    park_id BIGINT NOT NULL,
                    slot_code VARCHAR(64) NOT NULL,
                    slot_name VARCHAR(128) NOT NULL,
                    slot_type VARCHAR(32) NOT NULL,
                    coord_x DECIMAL(12,4) NOT NULL,
                    coord_y DECIMAL(12,4) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    occupied_vehicle_id BIGINT,
                    sort_order INT DEFAULT 0,
                    remark VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    version INT DEFAULT 0,
                    deleted TINYINT DEFAULT 0
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_charging_pile (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    park_id BIGINT NOT NULL,
                    pile_code VARCHAR(64) NOT NULL,
                    pile_name VARCHAR(128) NOT NULL,
                    parking_slot_id BIGINT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    occupied_vehicle_id BIGINT,
                    max_power_kw DECIMAL(8,2),
                    sort_order INT DEFAULT 0,
                    remark VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    version INT DEFAULT 0,
                    deleted TINYINT DEFAULT 0
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_charging_session (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    park_id BIGINT NOT NULL,
                    vehicle_id BIGINT NOT NULL,
                    parking_slot_id BIGINT NOT NULL,
                    charging_pile_id BIGINT,
                    session_status VARCHAR(32) NOT NULL,
                    start_soc INT,
                    end_soc INT,
                    start_time TIMESTAMP,
                    end_time TIMESTAMP,
                    remark VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    version INT DEFAULT 0,
                    deleted TINYINT DEFAULT 0
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_road_node (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    park_id BIGINT NOT NULL,
                    node_code VARCHAR(64) NOT NULL,
                    coord_x DECIMAL(12,4) NOT NULL,
                    coord_y DECIMAL(12,4) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    remark VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    version INT DEFAULT 0,
                    deleted TINYINT DEFAULT 0
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_road_segment (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    park_id BIGINT NOT NULL,
                    from_node_code VARCHAR(64) NOT NULL,
                    to_node_code VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    remark VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    version INT DEFAULT 0,
                    deleted TINYINT DEFAULT 0
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_admin_user (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(64) NOT NULL,
                    password_hash VARCHAR(128) NOT NULL,
                    display_name VARCHAR(64) NOT NULL,
                    role VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    last_login_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    version INT DEFAULT 0,
                    deleted TINYINT DEFAULT 0
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE t_admin_session (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    token VARCHAR(64) NOT NULL,
                    user_id BIGINT NOT NULL,
                    expires_at TIMESTAMP NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    static void seedParkData(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("""
                INSERT INTO t_park (
                    id, park_code, park_name, map_width, map_height, min_zoom, max_zoom,
                    vehicle_speed_px_per_second, status, default_flag, version, deleted
                ) VALUES (1, 'DEFAULT', 'Default Park', 1200, 800, -1, 3, 8, 'ACTIVE', 1, 0, 0)
                """);
        jdbcTemplate.update("""
                INSERT INTO t_station (
                    id, park_id, station_code, station_name, station_type, coord_x, coord_y, area, status, sort_order, version, deleted
                ) VALUES
                (101, 1, 'A1', 'A1 Pickup', 'PICKUP', 220, 170, 'A', 'ACTIVE', 1, 0, 0),
                (201, 1, 'B1', 'B1 Dropoff', 'DROPOFF', 220, 620, 'B', 'ACTIVE', 11, 0, 0)
                """);
    }
}
