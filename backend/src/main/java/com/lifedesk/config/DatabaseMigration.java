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

    @Override
    public void run(ApplicationArguments args) {
        migrateProjectColumns();
        upgradeColumnTypes();
        fixProjectNullTimestamps();
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
