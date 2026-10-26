-- LifeDesk 数据库表结构（启动时自动执行）
-- 数据库: workbench (已在 jdbc url 中指定)
-- 保留用户数据：使用 CREATE TABLE IF NOT EXISTS，已存在的表不会被重建
-- data.sql 配合 INSERT IGNORE：已存在的预置行不会被覆盖，用户的编辑/删除/新增状态全部保留
-- SET NAMES utf8mb4 确保连接层字符集与表字符集一致，避免字段注释/中文数据乱码
SET NAMES utf8mb4;

-- 注意：不要在这里加 DROP TABLE，否则会清空用户编辑过的数据
-- 如需彻底重建（开发期重置），手动执行：DROP DATABASE workbench; CREATE DATABASE workbench;

-- ========== 用户 ==========
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    nickname VARCHAR(64) NOT NULL COMMENT '昵称，如 林深',
    avatar VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

-- ========== 项目管理 ==========
CREATE TABLE IF NOT EXISTS project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL COMMENT '项目名',
    tech VARCHAR(64) DEFAULT NULL COMMENT '技术栈标签，如 Go/React',
    description VARCHAR(255) DEFAULT NULL COMMENT '项目描述',
    progress INT DEFAULT 0 COMMENT '完成率 0~100',
    last_active VARCHAR(32) DEFAULT NULL COMMENT '最近活跃时间文案，如 2h前',
    status VARCHAR(16) DEFAULT 'normal' COMMENT 'normal / pending',
    git_url VARCHAR(500) DEFAULT NULL COMMENT 'Git 仓库地址，如 https://github.com/org/repo.git',
    git_username VARCHAR(128) DEFAULT NULL COMMENT 'Git 鉴权用户名',
    git_password VARCHAR(255) DEFAULT NULL COMMENT 'Git 鉴权密码或 Access Token',
    git_branch VARCHAR(64) DEFAULT 'main' COMMENT '使用分支',
    git_platform VARCHAR(16) DEFAULT 'generic' COMMENT '仓库类型：github / gitlab / generic（决定走哪个 REST API）',
    last_sync_at DATETIME DEFAULT NULL COMMENT '最近一次同步时间',
    commits_count INT DEFAULT 0 COMMENT '最近一次同步拉取的提交数',
    prs_count INT DEFAULT 0 COMMENT 'PR 总数（已合并/开放）',
    releases_count INT DEFAULT 0 COMMENT '发布(标签)总数',
    sync_status VARCHAR(16) DEFAULT NULL COMMENT '最近一次同步状态：success/failed/syncing',
    sync_error VARCHAR(500) DEFAULT NULL COMMENT '最近一次同步失败的错误信息（syncStatus=failed 时填充）',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_deleted(status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目';

CREATE TABLE IF NOT EXISTS milestone (
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

CREATE TABLE IF NOT EXISTS commit_activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT DEFAULT NULL COMMENT '关联 project.id，便于按项目过滤',
    author VARCHAR(64) NOT NULL COMMENT '提交人',
    message VARCHAR(500) NOT NULL COMMENT 'commit 信息（取首行，最多 500 字符）',
    type VARCHAR(16) DEFAULT 'feat' COMMENT 'feat/fix/chore/refactor/docs/merge',
    time_ago VARCHAR(32) DEFAULT NULL COMMENT '时间文案，如 2h前',
    committed_at DATETIME DEFAULT NULL,
    sha VARCHAR(64) DEFAULT NULL COMMENT 'commit sha，幂等去重',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project(project_id),
    INDEX idx_committed(committed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提交动态';

-- ========== Git 合并请求 / 发布 ==========
CREATE TABLE IF NOT EXISTS pull_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT DEFAULT NULL COMMENT '关联 project.id',
    number VARCHAR(32) DEFAULT NULL COMMENT 'PR 编号，如 #123',
    title VARCHAR(500) DEFAULT NULL COMMENT 'PR 标题（来自 merge commit message）',
    author VARCHAR(64) DEFAULT NULL COMMENT '合并人',
    state VARCHAR(16) DEFAULT 'merged' COMMENT 'merged/open/closed（来自 merge commit 推断）',
    source_branch VARCHAR(128) DEFAULT NULL COMMENT '源分支',
    target_branch VARCHAR(128) DEFAULT NULL COMMENT '目标分支',
    merged_at DATETIME DEFAULT NULL COMMENT '合并时间',
    message VARCHAR(2000) DEFAULT NULL COMMENT '原始 commit message / PR description',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project(project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Git 合并请求';

CREATE TABLE IF NOT EXISTS release_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT DEFAULT NULL COMMENT '关联 project.id',
    tag VARCHAR(128) NOT NULL COMMENT '标签名，如 v1.2.0',
    name VARCHAR(500) DEFAULT NULL COMMENT '发布名/标签附注',
    author VARCHAR(64) DEFAULT NULL COMMENT '打 tag 人',
    published_at DATETIME DEFAULT NULL COMMENT '发布时间',
    message VARCHAR(2000) DEFAULT NULL COMMENT '标签附注 / release description',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project(project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Git 发布(标签)记录';

-- ========== Skills 工坊 ==========
CREATE TABLE IF NOT EXISTS skill (
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
CREATE TABLE IF NOT EXISTS meeting (
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
CREATE TABLE IF NOT EXISTS task (
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
CREATE TABLE IF NOT EXISTS server_status (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL COMMENT '服务名',
    latency INT DEFAULT 0 COMMENT '延迟(ms)',
    status VARCHAR(32) DEFAULT 'running' COMMENT 'running/checking/alert/timeout',
    latency_unit VARCHAR(8) DEFAULT 'ms',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务状态';

CREATE TABLE IF NOT EXISTS alert (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL COMMENT '告警标题',
    source VARCHAR(128) DEFAULT NULL COMMENT '告警来源',
    time_ago VARCHAR(32) DEFAULT NULL,
    status VARCHAR(16) DEFAULT 'active' COMMENT 'active/resolved',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警';

CREATE TABLE IF NOT EXISTS perf_metric (
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
CREATE TABLE IF NOT EXISTS exercise_dict (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL COMMENT '动作名，如 卧推/深蹲/硬拉',
    part VARCHAR(32) NOT NULL COMMENT '部位：胸/背/腿/核心/肩臂/有氧',
    met DOUBLE DEFAULT 5.0 COMMENT 'MET 代谢当量，用于时长模式计算',
    kcal_per_set DOUBLE DEFAULT 8.0 COMMENT '每组默认消耗 kcal，用于组数模式',
    icon VARCHAR(32) DEFAULT 'star' COMMENT '图标枚举',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_part(part)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运动动作字典';

CREATE TABLE IF NOT EXISTS food_dict (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL COMMENT '食物名，如 米饭/鸡胸肉/苹果',
    category VARCHAR(32) NOT NULL COMMENT '主食/肉蛋/蔬菜/水果/饮品/零食',
    kcal_per100g DOUBLE NOT NULL COMMENT '每 100g 卡路里',
    default_amount INT DEFAULT 100 COMMENT '默认份量 g/ml',
    default_unit VARCHAR(8) DEFAULT 'g' COMMENT '默认单位 g/ml/份',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category(category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食物字典';

CREATE TABLE IF NOT EXISTS workout_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exercise_id BIGINT DEFAULT NULL COMMENT '关联 exercise_dict.id',
    exercise_name VARCHAR(64) NOT NULL COMMENT '动作名冗余，便于列表展示',
    part VARCHAR(32) NOT NULL COMMENT '部位冗余：胸/背/腿/核心/肩臂/有氧',
    sets INT DEFAULT 0 COMMENT '组数',
    reps INT DEFAULT 0 COMMENT '每组次数',
    weight_kg DOUBLE DEFAULT 0 COMMENT '负重 kg（有氧/核心可 0）',
    duration_min INT DEFAULT 0 COMMENT '时长 分钟（有氧/拉伸优先）',
    calories_burned DOUBLE DEFAULT 0 COMMENT '消耗 kcal，后端自动计算',
    plan_count INT DEFAULT 0 COMMENT '原计划组数，保留兼容',
    done_count INT DEFAULT 0 COMMENT '实际完成组数',
    status VARCHAR(16) DEFAULT 'done' COMMENT 'done/pending',
    record_date DATE DEFAULT NULL,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_date(record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='健身动作记录';

CREATE TABLE IF NOT EXISTS diet_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    food_id BIGINT DEFAULT NULL COMMENT '关联 food_dict.id',
    food_name VARCHAR(64) NOT NULL COMMENT '食物名冗余',
    meal VARCHAR(16) NOT NULL COMMENT '餐次：breakfast/lunch/dinner/snack',
    amount_g DOUBLE NOT NULL COMMENT '摄入量 g/ml',
    calories_intake DOUBLE DEFAULT 0 COMMENT '摄入 kcal，后端自动计算',
    record_date DATE DEFAULT NULL,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_date_meal(record_date, meal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='饮食摄入记录';

CREATE TABLE IF NOT EXISTS diet_check (
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

CREATE TABLE IF NOT EXISTS calorie_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_date DATE NOT NULL,
    intake INT DEFAULT 0 COMMENT '摄入 kcal',
    burn INT DEFAULT 0 COMMENT '消耗 kcal',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date(record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡路里每日记录';

CREATE TABLE IF NOT EXISTS weight_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_date DATE NOT NULL,
    weight DOUBLE DEFAULT 0 COMMENT '体重 kg',
    fat_rate DOUBLE DEFAULT 0 COMMENT '体脂率 %',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_date(record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='体重体脂记录';

CREATE TABLE IF NOT EXISTS exercise_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category VARCHAR(32) NOT NULL COMMENT '力量训练/有氧/柔韧/拉伸放松',
    rate INT DEFAULT 0 COMMENT '达成率 0~100',
    reached TINYINT DEFAULT 0 COMMENT '是否达标 0/1',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运动计划达成率';

CREATE TABLE IF NOT EXISTS step_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_date DATE NOT NULL,
    steps INT DEFAULT 0,
    goal INT DEFAULT 10000,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date(record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日步数';

CREATE TABLE IF NOT EXISTS vital_sign (
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

-- ========== 我的需求（第三方需求平台拉取） ==========
-- 字段对齐第三方接口 https://wetrans.cimc.cn/gateway/apiIssue/developTask/query 的返回
-- sprint 形如 CDS20261025，其中 20261025 即为里程碑时间节点（版本）
CREATE TABLE IF NOT EXISTS requirement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sprint VARCHAR(32) NOT NULL COMMENT 'Sprint 版本号，如 CDS20261025（后 8 位为里程碑日期）',
    person_day DECIMAL(5,1) DEFAULT 0 COMMENT '人/天估时',
    task_status VARCHAR(16) DEFAULT 'todo' COMMENT '任务状态：todo/doing/done/cancel',
    topic VARCHAR(255) DEFAULT NULL COMMENT '关联话题',
    task_detail VARCHAR(1000) NOT NULL COMMENT '任务详情',
    product VARCHAR(64) DEFAULT NULL COMMENT '产品',
    tester VARCHAR(64) DEFAULT NULL COMMENT '测试人',
    source_id VARCHAR(64) DEFAULT NULL COMMENT '第三方平台需求 ID，便于幂等去重',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sprint_deleted(sprint, deleted),
    INDEX idx_status_deleted(task_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='我的需求';

-- ========== 收藏与笔记 ==========
CREATE TABLE IF NOT EXISTS favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL COMMENT '名称：收藏网站名 / 笔记标题',
    value VARCHAR(2000) NOT NULL COMMENT '值：URL（type=link）/ 笔记正文（type=note）',
    type VARCHAR(8) DEFAULT 'link' COMMENT 'link=收藏 / note=笔记',
    description VARCHAR(255) DEFAULT NULL COMMENT '备注/分类（可选）',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_type_deleted(type, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏与笔记';

-- ========== 英语提升 ==========
CREATE TABLE IF NOT EXISTS english_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL COMMENT '任务名称，如 每日单词 50',
    category VARCHAR(32) DEFAULT 'other' COMMENT '类别：vocabulary/grammar/listening/speaking/reading/writing/other',
    daily_goal VARCHAR(255) DEFAULT NULL COMMENT '每日目标（描述性，如 50 个新词）',
    target_days INT DEFAULT 30 COMMENT '目标天数，如 30/100 天计划',
    total_completed INT DEFAULT 0 COMMENT '累计完成次数',
    streak_days INT DEFAULT 0 COMMENT '连续打卡天数（断签清零）',
    last_done_date DATE DEFAULT NULL COMMENT '最近一次打卡日期',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category_deleted(category, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='英语提升任务';

-- ========== 自定义菜单（侧栏挂载外部网站，iframe 嵌入当前系统展示） ==========
CREATE TABLE IF NOT EXISTS custom_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL COMMENT '菜单名（显示在侧栏）',
    url VARCHAR(2000) NOT NULL COMMENT '网站地址，点击后通过 iframe 嵌入当前系统展示',
    sort_no INT DEFAULT 0 COMMENT '排序号，越小越靠前',
    icon VARCHAR(64) DEFAULT NULL COMMENT '图标枚举（前端 Element Plus 图标名，可选，缺省用 Link）',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sort_deleted(sort_no, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自定义菜单';

-- ========== 增量字段迁移说明 ==========
-- CREATE TABLE IF NOT EXISTS 不会更新已存在的表，新增字段由 DatabaseMigration Runner
-- 在启动后用 JDBC 检查 INFORMATION_SCHEMA 并 ALTER TABLE ADD COLUMN（精确无副作用）。
-- 这里不写 ALTER TABLE ADD COLUMN IF NOT EXISTS，因为 MySQL 8 不支持该语法（仅 MariaDB 支持）。