# LifeDesk

> 个人工作+生活一体化仪表盘 — 前端 Vue 3 + 后端 Spring Boot + MySQL 全栈实现

按 `docs/功能设计文档/` 下 8 份 PRD 级文档（00 总览 + 02-08 共 7 个页面）落地，覆盖首页 Dashboard、项目管理、Skills 工坊、日程会议、任务工单、系统监控、个人健康 7 大模块。

## 技术栈

**后端** — Spring Boot 2.7.18 + MyBatis-Plus 3.5.5 + MySQL 8（启动自动执行 schema.sql / data.sql）

**前端** — Vue 3.4 + Vite 5 + TypeScript 5 + Element Plus 2.5 + ECharts 5 + Pinia 2 + Vue Router 4

**设计风格** — 深色主题，紫色主色 `#7C3AED` / `#A78BFA`，青绿正向、橙黄进行中、红色告警。

## 目录结构

```
lifedesk/
├── backend/                                # Spring Boot 工程
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/lifedesk/
│       │   ├── LifeDeskApplication.java    # 启动入口
│       │   ├── common/                     # R / BizException / GlobalExceptionHandler / BaseEntity
│       │   ├── config/                     # MybatisPlusConfig / CorsConfig
│       │   ├── dashboard/                  # 首页（聚合 7 模块）
│       │   ├── project/                    # 项目管理
│       │   ├── skill/                      # Skills 工坊
│       │   ├── schedule/                   # 日程会议
│       │   ├── task/                       # 任务工单
│       │   ├── monitor/                    # 系统监控
│       │   └── health/                     # 个人健康
│       └── resources/
│           ├── application.yml             # 含目标 MySQL 连接串
│           ├── schema.sql                  # 18 张表，启动自动执行
│           └── data.sql                    # 初始示例数据
└── frontend/                               # Vue 3 工程
    ├── package.json / vite.config.ts / tsconfig.json
    └── src/
        ├── main.ts / App.vue / env.d.ts
        ├── styles/                          # variables.scss + index.scss（深色主题令牌）
        ├── router/                          # 7 大模块路由
        ├── utils/http.ts                    # axios + R 包装
        ├── api/                             # 7 个模块 API 封装
        ├── components/                      # StatCard / RingChart / ProgressBar / StatusBadge
        ├── layout/MainLayout.vue           # 侧边栏+顶栏+今日健康迷你卡
        └── views/
            ├── dashboard/  project/  skill/
            ├── schedule/   task/     monitor/  health/
```

## 快速启动

### 1. 数据库

后端 `application.yml` 已配置目标 MySQL：

```
url:      jdbc:mysql://10.139.92.107:3307/workbench?...
username: devuser
password: Cds@1234
```

`workbench` 库需预先存在（Schema 自动建，库需自备）。启动时由 `schema.sql` 创建全部 18 张表、`data.sql` 注入示例数据（与文档示例值一致：林深用户、payment-service 项目、5 张技能卡、6 条任务等）。

### 2. 后端

```bash
cd backend
mvn spring-boot:run
# 或：mvn -DskipTests package && java -jar target/lifedesk-backend.jar
```

启动后监听 `:8080`，接口前缀 `/api`，例如：

- `GET /api/dashboard/overview` 首页整体聚合
- `GET /api/project/stat` 项目管理统计卡
- `GET /api/task/board` 任务看板三列
- `POST /api/health/record` 统一健康录入（首页快速打卡同源）

### 3. 前端

```bash
cd frontend
npm install
npm run dev      # http://localhost:8081
# 生产构建
npm run build    # 输出 dist/
```

Vite 已配置 `/api` 代理到 `http://localhost:8080`，前端启动后直接访问 `http://localhost:8081` 即可看到完整深色主题仪表盘。

## 模块与文档映射

| 模块 | 设计文档 | 关键页面 | 后端接口前缀 |
| --- | --- | --- | --- |
| 首页 | `02_首页.md` | 问候 + 六宫格 + 个人健康六组件 | `/api/dashboard` |
| 项目管理 | `03_项目管理.md` | 统计卡 / 活跃项目 / 里程碑 / 提交动态 | `/api/project` |
| Skills 工坊 | `04_Skills工坊.md` | 5 张技能成长卡 | `/api/skill` |
| 日程会议 | `05_日程会议.md` | 今日时间轴 + 本周日历 | `/api/schedule` |
| 任务工单 | `06_任务工单.md` | 待办/进行中/已完成三列看板 | `/api/task` |
| 系统监控 | `07_系统监控.md` | 服务状态 + 24h 折线 | `/api/monitor` |
| 个人健康 | `08_个人健康.md` | 6 大健康组件（含心率/睡眠/水分） | `/api/health` |

## 字段映射

所有实体字段均严格按各文档「关键数据字段说明」表落地，例如：

- `module_stat.count / note` → `DashboardService.moduleStat()`
- `task.priority / due_label / assign_state` → `task` 表 `priority / due_label / assign_state`
- `calorie.intake_avg / burn_avg / net_today` → `calorie_record` 表 + Service 聚合

## License

MIT — 仅作为 LifeDesk 个人项目交付。
