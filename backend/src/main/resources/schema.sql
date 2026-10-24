-- LifeDesk 数据库表结构（启动时自动执行）
-- 数据库: workbench (已在 jdbc url 中指定)
-- 开发期使用 DROP+CREATE，data.sql 配合 INSERT IGNORE 保证幂等

DROP TABLE IF EXISTS commit_activity;
DROP TABLE IF EXISTS milestone;
DROP TABLE IF EXISTS project;
DROP TABLE IF EXISTS skill;
DROP TABLE IF EXISTS meeting;
DROP TABLE IF EXISTS task;
DROP TABLE IF EXISTS alert;
DROP TABLE IF EXISTS perf_metric;
DROP TABLE IF EXISTS server_status;
DROP TABLE IF EXISTS workout_record;
DROP TABLE IF EXISTS diet_check;
DROP TABLE IF EXISTS calorie_record;
DROP TABLE IF EXISTS weight_record;
DROP TABLE IF EXISTS exercise_plan;
DROP TABLE IF EXISTS step_record;
DROP TABLE IF EXISTS vital_sign;
DROP TABLE IF EXISTS sys_user;

-- ========== 用户 ==========
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    nickname VARCHAR(64) NOT NULL COMMENT '昵称，如 林深',
    avatar VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

-- ========== 项目管理 ==========
CREATE TABLE project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL COMMENT '项目名',
    tech VARCHAR(64) DEFAULT NULL COMMENT '技术栈标签，如 Go/React',
    description VARCHAR(255) DEFAULT NULL COMMENT '项目描述',
    progress INT DEFAULT 0 COMMENT '完成率 0~100',
    last_active VARCHAR(32) DEFAULT NULL COMMENT '最近活跃时间文案，如 2h前',
    status VARCHAR(16) DEFAULT 'normal' COMMENT 'normal / pending',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_deleted(status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目';

CREATE TABLE milestone (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT DEFAULT NULL COMMENT '所属项目',
    name VARCHAR(128) NOT NULL COMMENT '里程碑名，如 v1.2 发布',
    progress INT DEFAULT 0,
    note VARCHAR(255) DEFAULT NULL COMMENT '预期注记',
    due_date DATE DEFAULT NULL,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='里程碑';

CREATE TABLE commit_activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    author VARCHAR(64) NOT NULL COMMENT '提交人',
    message VARCHAR(255) NOT NULL COMMENT 'commit 信息',
    type VARCHAR(16) DEFAULT 'feat' COMMENT 'feat/fix/chore/refactor/docs',
    time_ago VARCHAR(32) DEFAULT NULL COMMENT '时间文案，如 2h前',
    committed_at DATETIME DEFAULT NULL,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提交动态';

-- ========== Skills 工坊 ==========
CREATE TABLE skill (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL COMMENT '技能名',
    icon VARCHAR(32) DEFAULT 'star' COMMENT '图标枚举: diamond/doubleRing/triangle/square/star',
    level INT DEFAULT 1 COMMENT '等级 1~5',
    stage VARCHAR(32) DEFAULT '入门' COMMENT '成长阶段文案',
    progress INT DEFAULT 0 COMMENT '当前等级内进度 0~100',
    description VARCHAR(255) DEFAULT NULL,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技能';

-- ========== 日程会议 ==========
CREATE TABLE meeting (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(128) NOT NULL COMMENT '会议名',
    location VARCHAR(64) DEFAULT NULL COMMENT '地点',
    start_time DATETIME NOT NULL,
    end_time DATETIME DEFAULT NULL,
    duration_min INT DEFAULT NULL COMMENT '时长（分钟）',
    attendees INT DEFAULT 0 COMMENT '参会人数',
    status VARCHAR(16) DEFAULT 'pending' COMMENT 'pending / current / done',
    is_today TINYINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_start_time(start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议/日程';

-- ========== 任务工单 ==========
CREATE TABLE task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(16) DEFAULT 'todo' COMMENT 'todo / doing / done',
    priority VARCHAR(16) DEFAULT 'normal' COMMENT 'normal / urgent',
    due_label VARCHAR(32) DEFAULT NULL COMMENT '今日截止/本周内/昨天...',
    assign_state VARCHAR(16) DEFAULT 'assigned' COMMENT 'assigned / unassigned',
    sort_no INT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_deleted(status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务工单';

-- ========== 系统监控 ==========
CREATE TABLE server_status (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL COMMENT '服务名',
    latency INT DEFAULT 0 COMMENT '延迟(ms)',
    status VARCHAR(32) DEFAULT 'running' COMMENT 'running/checking/alert/timeout',
    latency_unit VARCHAR(8) DEFAULT 'ms',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务状态';

CREATE TABLE alert (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL COMMENT '告警标题',
    source VARCHAR(128) DEFAULT NULL COMMENT '告警来源',
    time_ago VARCHAR(32) DEFAULT NULL,
    status VARCHAR(16) DEFAULT 'active' COMMENT 'active/resolved',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警';

CREATE TABLE perf_metric (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    point_time VARCHAR(8) NOT NULL COMMENT '时间点，如 00:00',
    cpu DOUBLE DEFAULT 0 COMMENT 'CPU 使用率(%)',
    memory DOUBLE DEFAULT 0 COMMENT '内存使用率(%)',
    latency DOUBLE DEFAULT 0 COMMENT '延迟(ms)',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='24h 性能指标';

-- ========== 个人健康 ==========
CREATE TABLE workout_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    part VARCHAR(32) NOT NULL COMMENT '部位：胸/背/腿',
    plan_count INT DEFAULT 0,
    done_count INT DEFAULT 0,
    status VARCHAR(16) DEFAULT 'done' COMMENT 'done/pending',
    record_date DATE DEFAULT NULL,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='健身动作记录';

CREATE TABLE diet_check (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    check_date DATE NOT NULL,
    breakfast TINYINT DEFAULT 0,
    lunch TINYINT DEFAULT 0,
    dinner TINYINT DEFAULT 0,
    snack TINYINT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date(check_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='饮食打卡';

CREATE TABLE calorie_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_date DATE NOT NULL,
    intake INT DEFAULT 0 COMMENT '摄入 kcal',
    burn INT DEFAULT 0 COMMENT '消耗 kcal',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date(record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡路里每日记录';

CREATE TABLE weight_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_date DATE NOT NULL,
    weight DOUBLE DEFAULT 0 COMMENT '体重 kg',
    fat_rate DOUBLE DEFAULT 0 COMMENT '体脂率 %',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_date(record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='体重体脂记录';

CREATE TABLE exercise_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category VARCHAR(32) NOT NULL COMMENT '力量训练/有氧/柔韧/拉伸放松',
    rate INT DEFAULT 0 COMMENT '达成率 0~100',
    reached TINYINT DEFAULT 0 COMMENT '是否达标 0/1',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运动计划达成率';

CREATE TABLE step_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_date DATE NOT NULL,
    steps INT DEFAULT 0,
    goal INT DEFAULT 10000,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date(record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日步数';

CREATE TABLE vital_sign (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_date DATE NOT NULL,
    heart_rate INT DEFAULT 0 COMMENT '心率 bpm',
    sleep_hours DOUBLE DEFAULT 0 COMMENT '睡眠时长 h',
    water_ml INT DEFAULT 0 COMMENT '水分摄入 ml',
    water_goal_ml INT DEFAULT 2500,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date(record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生理体征：心率/睡眠/水分';