-- LifeDesk 初始示例数据（与设计文档示例一致）
-- 使用 INSERT IGNORE 避免重启重复插入

-- 用户
INSERT IGNORE INTO sys_user(id, nickname, avatar) VALUES
(1, '林深', NULL);

-- 项目（7 模块首页：项目管理 12；放 6 条活跃项目示例）
INSERT IGNORE INTO project(id, name, tech, description, progress, last_active, status) VALUES
(1, 'payment-service', 'Go', '支付核心服务', 88, '2h 前', 'normal'),
(2, 'admin-portal', 'React', '管理后台', 83, '5h 前', 'normal'),
(3, 'data-pipeline', 'Python', '数据管道', 68, '待完善', 'pending'),
(4, 'order-center', 'Java', '订单中心', 92, '1h 前', 'normal'),
(5, 'bff-gateway', 'Go', 'BFF 网关', 75, '今日', 'normal'),
(6, 'lifedesk-web', 'Vue', 'LifeDesk 仪表盘本身', 35, '昨日', 'pending'),
(7, 'ml-monitor', 'Python', '模型指标监控', 60, '3 天前', 'normal');

-- 里程碑
INSERT IGNORE INTO milestone(id, project_id, name, progress, note, due_date) VALUES
(1, NULL, 'v1.2 发布', 80, '预计 11 月初发布', '2026-11-05'),
(2, NULL, 'v1.1 重构', 100, '已发布', '2026-10-15'),
(3, NULL, 'v1.0 发版', 65, '进行中', '2026-10-30');

-- 提交动态
INSERT IGNORE INTO commit_activity(id, author, message, type, time_ago, committed_at) VALUES
(1, 'liuXin24', 'feat(auth): 完成授权接口', 'feat', '2h 前', '2026-10-24 22:00:00'),
(2, 'devFsgn', 'fix(api): 修复分页边界', 'fix', '5h 前', '2026-10-24 19:00:00'),
(3, 'li78672', 'chore(deps): 升级依赖版本', 'chore', '昨天', '2026-10-23 14:00:00'),
(4, 'm8n42e', 'refactor(core): 重构核心模块', 'refactor', '2 天前', '2026-10-22 10:00:00'),
(5, 'q7sn5v', 'docs(readme): 补充提交文档', 'docs', '3 天前', '2026-10-21 09:00:00');

-- 技能
INSERT IGNORE INTO skill(id, name, icon, level, stage, progress, description) VALUES
(1, '系统设计', 'diamond', 3, '进阶中', 80, '微服务与系统拆分'),
(2, '分布式', 'doubleRing', 5, '已掌握', 90, '高并发与一致性'),
(3, '性能优化', 'triangle', 4, '熟练中', 80, '性能调优与 profiling'),
(4, '容器编排', 'square', 2, '入门', 40, 'K8s 与容器编排'),
(5, 'AI 工程', 'star', 1, '快速成长', 10, 'LLM 应用与智能体');

-- 会议（人为示例数据，今天 10-24 有 3 场）
INSERT IGNORE INTO meeting(id, title, location, start_time, end_time, duration_min, attendees, status, is_today) VALUES
(1, '高层技术评审', '会议室 A', '2026-10-24 14:00:00', '2026-10-24 15:30:00', 90, 3, 'current', 1),
(2, '面试技术评审', '会议室 A', '2026-10-24 16:00:00', '2026-10-24 16:30:00', 30, 4, 'pending', 1),
(3, '1:1 with Mentor', '线上', '2026-10-24 16:30:00', '2026-10-24 17:00:00', 30, 2, 'pending', 1),
(4, '并行处理线上会', '线上', '2026-10-24 20:00:00', '2026-10-24 20:30:00', 30, 5, 'pending', 1),
(5, '季度目标会议', '会议室 B', '2026-10-25 09:00:00', '2026-10-25 10:00:00', 60, 8, 'pending', 0);

-- 任务工单
INSERT IGNORE INTO task(id, title, status, priority, due_label, assign_state, sort_no) VALUES
(1, '修复线上支付失败', 'todo', 'urgent', '今日截止', 'assigned', 1),
(2, '补充接口文档', 'todo', 'normal', '本周内', 'unassigned', 2),
(3, '重构审核中间件', 'doing', 'normal', '明日截止', 'assigned', 1),
(4, '登入页的警告', 'doing', 'normal', '本周内', 'assigned', 2),
(5, '升级依赖版本', 'done', 'normal', '昨天', 'assigned', 1),
(6, '修复分页越界', 'done', 'normal', '2 天前', 'assigned', 2);

-- 系统监控-服务状态
INSERT IGNORE INTO server_status(id, name, latency, status, latency_unit) VALUES
(1, '队列消费延迟阈值', 0, 'alert', ''),
(2, 'API 网关', 14, 'running', 'ms'),
(3, '主数据库', 3, 'running', 'ms'),
(4, 'Redis 缓存', 3, 'running', 'ms'),
(5, '消息队列', 0, 'checking', '');

-- 告警
INSERT IGNORE INTO alert(id, title, source, time_ago, status) VALUES
(1, '队列消费延迟阈值', 'payment-queue', '5min 前', 'active');

-- 24h 性能指标（每 2 小时一个采样点，共 13 个）
INSERT IGNORE INTO perf_metric(id, point_time, cpu, memory, latency) VALUES
(1, '00:00', 22, 30, 12),
(2, '02:00', 18, 28, 10),
(3, '04:00', 15, 25, 9),
(4, '06:00', 19, 27, 11),
(5, '08:00', 35, 33, 15),
(6, '10:00', 52, 38, 17),
(7, '12:00', 65, 41, 19),
(8, '14:00', 72, 44, 22),
(9, '16:00', 68, 42, 19),
(10, '18:00', 58, 40, 18),
(11, '20:00', 48, 38, 16),
(12, '22:00', 38, 35, 14),
(13, '24:00', 25, 31, 12);

-- 健身记录
INSERT IGNORE INTO workout_record(id, part, plan_count, done_count, status, record_date) VALUES
(1, '胸', 4, 4, 'done', '2026-10-19'),
(2, '背', 4, 4, 'done', '2026-10-21'),
(3, '腿', 5, 3, 'pending', '2026-10-23');

-- 饮食打卡（本周一到本周日，已打卡 5 天）
INSERT IGNORE INTO diet_check(id, check_date, breakfast, lunch, dinner, snack) VALUES
(1, '2026-10-19', 1, 1, 1, 0),
(2, '2026-10-20', 1, 1, 1, 1),
(3, '2026-10-21', 1, 1, 0, 0),
(4, '2026-10-22', 1, 1, 1, 0),
(5, '2026-10-23', 1, 1, 1, 1),
(6, '2026-10-24', 0, 0, 0, 0);

-- 卡路里本周 7 天
INSERT IGNORE INTO calorie_record(id, record_date, intake, burn) VALUES
(1, '2026-10-19', 1980, 2150),
(2, '2026-10-20', 1950, 2250),
(3, '2026-10-21', 2010, 2180),
(4, '2026-10-22', 1890, 2520),
(5, '2026-10-23', 1875, 2610),
(6, '2026-10-24', 1810, 2130);

-- 体重体脂近 7 天
INSERT IGNORE INTO weight_record(id, record_date, weight, fat_rate) VALUES
(1, '2026-10-19', 65.8, 18.6),
(2, '2026-10-20', 65.7, 18.5),
(3, '2026-10-21', 65.6, 18.4),
(4, '2026-10-22', 65.5, 18.3),
(5, '2026-10-23', 65.3, 18.2),
(6, '2026-10-24', 65.2, 18.0);

-- 运动计划达成率
INSERT IGNORE INTO exercise_plan(id, category, rate, reached) VALUES
(1, '力量训练', 84, 1),
(2, '有氧', 65, 0),
(3, '柔韧', 40, 0),
(4, '拉伸放松', 90, 1);

-- 步数近 7 天
INSERT IGNORE INTO step_record(id, record_date, steps, goal) VALUES
(1, '2026-10-19', 7820, 10000),
(2, '2026-10-20', 8190, 10000),
(3, '2026-10-21', 9100, 10000),
(4, '2026-10-22', 7340, 10000),
(5, '2026-10-23', 6890, 10000),
(6, '2026-10-24', 8425, 10000);

-- 生理体征今天
INSERT IGNORE INTO vital_sign(id, record_date, heart_rate, sleep_hours, water_ml, water_goal_ml) VALUES
(1, '2026-10-24', 88, 7.5, 1600, 2500);