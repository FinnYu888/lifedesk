package com.lifedesk.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.lifedesk.auth.UserContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * MyBatis-Plus 配置：分页 + 多租户（按用户隔离数据）。
 *
 * <p>除「系统监控」相关的系统级表外，所有业务表都有 user_id 列（由 DatabaseMigration 补齐），
 * 插件自动在 SELECT/UPDATE/DELETE 追加 user_id = 当前登录用户、INSERT 自动填充，
 * 业务代码无需任何改动。</p>
 *
 * @author LifeDesk
 */
@Configuration
public class MybatisPlusConfig {

    /** 忽略租户的表：用户表自身、系统监控相关、全局字典 */
    private static final Set<String> IGNORE_TABLES = new HashSet<String>(Arrays.asList(
            "sys_user",
            // 系统监控：服务器状态/告警/性能指标属系统级数据，不做用户归属
            "server_status", "alert", "perf_metric",
            // 全局字典：运动/食物字典所有用户共享
            "exercise_dict", "food_dict"));

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 多租户插件必须放在分页插件之前
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                // 未登录上下文（异步线程、启动任务）回退默认用户 1（俞鹏飞）
                return new LongValue(UserContext.getOrDefault());
            }

            @Override
            public String getTenantIdColumn() {
                return "user_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return IGNORE_TABLES.contains(tableName.toLowerCase());
            }
        }));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
