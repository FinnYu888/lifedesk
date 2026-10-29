package com.lifedesk.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据库增量迁移 Runner。
 *
 * <p>由于 schema.sql 用 CREATE TABLE IF NOT EXISTS（保留用户数据），已存在的表不会被重建，
 * 新增字段必须用代码检查 INFORMATION_SCHEMA 后再 ALTER TABLE ADD COLUMN。
 * MySQL 8 不支持 ALTER TABLE ADD COLUMN IF NOT EXISTS（仅 MariaDB 支持），所以走 JDBC 路径。</p>
 *
 * <p>迁移项：
 * <ul>
 *   <li>project 表加 sync_status / sync_error / git_platform 字段（如缺失）</li>
 *   <li>把 project 表中 createTime/updateTime 为 null 的行补当前时间（修复历史脏数据）</li>
 * </ul>
 * </p>
 *
 * @author LifeDesk
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class DatabaseMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    /** project 表增量字段清单：列名 → 列定义 */
    private static final Map<String, String> PROJECT_NEW_COLUMNS = new LinkedHashMap<String, String>() {{
        put("git_platform", "VARCHAR(16) DEFAULT 'generic' COMMENT 'github/gitlab/generic'");
        put("sync_status", "VARCHAR(16) DEFAULT NULL COMMENT '最近一次同步状态：success/failed/syncing'");
        put("sync_error", "VARCHAR(500) DEFAULT NULL COMMENT '最近一次同步失败错误信息'");
    }};

    /** favorite 表增量字段清单：列名 → 列定义（用于收藏卡片展示环境/CDS版本/网页截图） */
    private static final Map<String, String> FAVORITE_NEW_COLUMNS = new LinkedHashMap<String, String>() {{
        put("env", "VARCHAR(16) DEFAULT NULL COMMENT '环境：test/integration/prod'");
        put("cds_version", "VARCHAR(8) DEFAULT NULL COMMENT 'CDS 版本：3.0/5.0'");
        put("screenshot", "VARCHAR(500) DEFAULT NULL COMMENT '网页截图 URL（可选）'");
    }};

    /** 其他表字段长度升级清单：表 → (列 → 列定义)。用于把已存在的窄字段升级为更宽字段 */
    private static final Map<String, Map<String, String>> COLUMN_TYPE_UPGRADES = new LinkedHashMap<String, Map<String, String>>() {{
        put("commit_activity", new LinkedHashMap<String, String>() {{
            put("message", "VARCHAR(500) NOT NULL COMMENT 'commit 信息（取首行，最多 500 字符）'");
        }});
        put("pull_request", new LinkedHashMap<String, String>() {{
            put("title", "VARCHAR(500) DEFAULT NULL COMMENT 'PR 标题'");
            put("source_branch", "VARCHAR(128) DEFAULT NULL COMMENT '源分支'");
            put("target_branch", "VARCHAR(128) DEFAULT NULL COMMENT '目标分支'");
            put("message", "VARCHAR(2000) DEFAULT NULL COMMENT 'PR description / commit message'");
        }});
        put("release_record", new LinkedHashMap<String, String>() {{
            put("name", "VARCHAR(500) DEFAULT NULL COMMENT '发布名/标签附注'");
            put("message", "VARCHAR(2000) DEFAULT NULL COMMENT 'release description / 标签附注'");
        }});
    }};

    /** 需要按用户归属（user_id 隔离）的业务表：除系统监控/字典外的全部表 */
    private static final String[] TENANT_TABLES = {
            "project", "milestone", "commit_activity", "pull_request", "release_record",
            "skill", "meeting", "task", "requirement", "favorite",
            "english_task", "custom_menu", "daily_goal", "english_punch", "native_app",
            "music_track", "workout_record", "diet_record", "diet_check", "calorie_record",
            "weight_record", "exercise_plan", "step_record", "vital_sign"
    };

    @Override
    public void run(ApplicationArguments args) {
        migrateProjectColumns();
        migrateFavoriteColumns();
        upgradeColumnTypes();
        fixProjectNullTimestamps();
        migrateTenantColumns();
        migrateSysUser();
        migrateMusicLyrics();
    }

    /** music_track 补 lyrics 歌词列（LRC 同步歌词或纯文本），如缺失 */
    private void migrateMusicLyrics() {
        try {
            if (!columnExistsInDb("music_track", "lyrics")) {
                String sql = "ALTER TABLE music_track ADD COLUMN lyrics MEDIUMTEXT DEFAULT NULL COMMENT '歌词（LRC 或纯文本）'";
                log.info("[DBMigration] music_track 补 lyrics 列");
                jdbcTemplate.execute(sql);
            }
        } catch (Exception ex) {
            log.warn("[DBMigration] music_track lyrics 迁移失败（忽略）：{}", ex.getMessage());
        }
    }

    /** 给所有业务表补 user_id 列（多租户数据归属），历史数据默认归属用户 1 */
    private void migrateTenantColumns() {
        try {
            for (String table : TENANT_TABLES) {
                if (!columnExistsInDb(table, "user_id")) {
                    String sql = "ALTER TABLE " + table + " ADD COLUMN user_id BIGINT NOT NULL DEFAULT 1 COMMENT '归属用户 id'";
                    log.info("[DBMigration] 补数据归属字段：{}", sql);
                    jdbcTemplate.execute(sql);
                }
            }
        } catch (Exception ex) {
            log.warn("[DBMigration] user_id 迁移失败（忽略，可能表还未创建）：{}", ex.getMessage());
        }
    }

    /**
     * sys_user 补 username/password 列并预置账号：
     * 用户名 俞鹏飞 / 密码 finn（id=1，全部历史数据归属该账号）。
     * 用 INFORMATION_SCHEMA 精确判断列是否存在（JDBC meta.getColumns 在共享实例上可能误报）。
     */
    private void migrateSysUser() {
        try {
            if (!columnExistsInDb("sys_user", "username")) {
                jdbcTemplate.execute("ALTER TABLE sys_user ADD COLUMN username VARCHAR(64) DEFAULT NULL COMMENT '登录用户名'");
                log.info("[DBMigration] sys_user 补 username 列");
            }
            if (!columnExistsInDb("sys_user", "password")) {
                jdbcTemplate.execute("ALTER TABLE sys_user ADD COLUMN password VARCHAR(128) DEFAULT NULL COMMENT '登录密码'");
                log.info("[DBMigration] sys_user 补 password 列");
            }
            // 预置账号：已存在俞鹏飞则跳过；否则复用 id=1（保持历史数据归属），不存在再插入
            Integer preset = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_user WHERE username = '俞鹏飞'", Integer.class);
            if (preset == null || preset == 0) {
                Integer first = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM sys_user WHERE id = 1", Integer.class);
                if (first != null && first > 0) {
                    jdbcTemplate.update(
                            "UPDATE sys_user SET username = '俞鹏飞', password = 'finn', nickname = '俞鹏飞' WHERE id = 1");
                    log.info("[DBMigration] 预置账号已写入 id=1（俞鹏飞）");
                } else {
                    jdbcTemplate.update(
                            "INSERT INTO sys_user(id, nickname, username, password) VALUES (1, '俞鹏飞', '俞鹏飞', 'finn')");
                    log.info("[DBMigration] 预置账号已插入 id=1（俞鹏飞）");
                }
            }
        } catch (Exception ex) {
            log.warn("[DBMigration] sys_user 迁移失败（忽略）：{}", ex.getMessage());
        }
    }

    /** 用 INFORMATION_SCHEMA 精确检查当前库某表某列是否存在 */
    private boolean columnExistsInDb(String table, String column) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        return cnt != null && cnt > 0;
    }

    /** 给 project 表补缺失字段 */
    private void migrateProjectColumns() {
        try (Connection conn = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            // 检查每个新字段是否存在，不存在则 ALTER ADD
            for (Map.Entry<String, String> e : PROJECT_NEW_COLUMNS.entrySet()) {
                String column = e.getKey();
                if (!columnExists(meta, "project", column)) {
                    String sql = "ALTER TABLE project ADD COLUMN " + column + " " + e.getValue();
                    log.info("[DBMigration] 补字段：{}", sql);
                    jdbcTemplate.execute(sql);
                }
            }
        } catch (Exception ex) {
            log.warn("[DBMigration] project 表迁移失败（忽略，可能表还未创建）：{}", ex.getMessage());
        }
    }

    /** 给 favorite 表补缺失字段（env / cds_version / screenshot） */
    private void migrateFavoriteColumns() {
        try (Connection conn = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            for (Map.Entry<String, String> e : FAVORITE_NEW_COLUMNS.entrySet()) {
                String column = e.getKey();
                if (!columnExists(meta, "favorite", column)) {
                    String sql = "ALTER TABLE favorite ADD COLUMN " + column + " " + e.getValue();
                    log.info("[DBMigration] 补字段：{}", sql);
                    jdbcTemplate.execute(sql);
                }
            }
        } catch (Exception ex) {
            log.warn("[DBMigration] favorite 表迁移失败（忽略，可能表还未创建）：{}", ex.getMessage());
        }
    }

    /** 修复 project 表 createTime/updateTime 为 null 的历史脏数据 */
    private void fixProjectNullTimestamps() {
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE project SET " +
                            "create_time = COALESCE(create_time, NOW()), " +
                            "update_time = COALESCE(update_time, NOW()) " +
                            "WHERE create_time IS NULL OR update_time IS NULL");
            if (updated > 0) {
                log.info("[DBMigration] 修复 project 表 {} 行 createTime/updateTime 为 null 的脏数据", updated);
            }
        } catch (Exception ex) {
            log.warn("[DBMigration] 修复 createTime null 失败（忽略）：{}", ex.getMessage());
        }
    }

    /** 把已存在表的窄字段升级为更宽字段，避免 GitLab/GitHub 长文本导致 MysqlDataTruncation */
    private void upgradeColumnTypes() {
        try (Connection conn = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            for (Map.Entry<String, Map<String, String>> tableEntry : COLUMN_TYPE_UPGRADES.entrySet()) {
                String table = tableEntry.getKey();
                for (Map.Entry<String, String> col : tableEntry.getValue().entrySet()) {
                    String column = col.getKey();
                    String typeDef = col.getValue();
                    String desiredType = extractTypeOnly(typeDef);
                    String currentType = currentColumnType(meta, table, column);
                    // 仅当当前类型与期望类型不一致时才 MODIFY（避免无谓 DDL）
                    if (currentType != null && !desiredType.equalsIgnoreCase(currentType)) {
                        String sql = "ALTER TABLE " + table + " MODIFY COLUMN " + column + " " + typeDef;
                        log.info("[DBMigration] 升级字段类型：{}", sql);
                        try {
                            jdbcTemplate.execute(sql);
                        } catch (Exception ex) {
                            log.warn("[DBMigration] 升级 {} {} 失败（忽略）：{}", table, column, ex.getMessage());
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[DBMigration] 字段类型升级整体失败（忽略）：{}", ex.getMessage());
        }
    }

    /** 从 "VARCHAR(500) NOT NULL COMMENT '...'" 中提取 "VARCHAR(500)" 转大写 */
    private String extractTypeOnly(String typeDef) {
        int paren = typeDef.indexOf(')');
        if (paren < 0) return typeDef.split("\\s")[0].toUpperCase();
        return typeDef.substring(0, paren + 1).toUpperCase();
    }

    /** 查询某列当前的 TYPE_NAME（如 VARCHAR）+ COLUMN_SIZE，组合成 VARCHAR(500) 形式 */
    private String currentColumnType(DatabaseMetaData meta, String table, String column) {
        try (ResultSet rs = meta.getColumns(null, null, table, column)) {
            if (rs.next()) {
                String typeName = rs.getString("TYPE_NAME");
                int size = rs.getInt("COLUMN_SIZE");
                return typeName.toUpperCase() + "(" + size + ")";
            }
        } catch (Exception ex) {
            log.warn("[DBMigration] 查 {} {} 当前类型失败：{}", table, column, ex.getMessage());
        }
        return null;
    }

    /** 检查列是否存在 */
    private boolean columnExists(DatabaseMetaData meta, String table, String column) throws Exception {
        try (ResultSet rs = meta.getColumns(null, null, table, column)) {
            return rs.next();
        }
    }
}
