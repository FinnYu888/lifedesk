package com.lifedesk;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * LifeDesk 后端启动类
 *
 * <p>个人工作+生活一体化仪表盘后端服务，提供 7 大模块的 REST API：
 * 首页 / 项目管理 / 任务工单 / 日程会议 / 系统监控 / Skills 工坊 / 个人健康。</p>
 *
 * <p>端口策略（修复"大量接口 500 错误"的关键根因）：</p>
 * <ul>
 *   <li>沙盒环境的 {@code SERVER_PORT=58657} 环境变量会覆盖 {@code application.yml}
 *       中的 {@code server.port=8080}（环境变量优先级高于 yml），导致后端监听在 58657，
 *       而前端 vite proxy 转发到 8080 失败 → 前端"大量接口 500"。</li>
 *   <li>{@link SpringApplication#run} 的命令行 args 优先级最高（高于环境变量），
 *       因此在 main 方法里若用户未显式指定端口，则注入 {@code --server.port=8080}。</li>
 *   <li>用户仍可命令行覆盖：{@code java -jar lifedesk-backend.jar --server.port=9090}。</li>
 * </ul>
 *
 * @author LifeDesk
 */
@SpringBootApplication
@MapperScan("com.lifedesk.**.mapper")
@EnableAsync
@EnableScheduling
public class LifeDeskApplication {

    public static void main(String[] args) {
        // 检测用户是否已通过命令行参数 / -D 系统属性指定端口
        boolean userSpecifiedOnCmd = false;
        for (String a : args) {
            if (a.startsWith("--server.port") || a.startsWith("--port")) {
                userSpecifiedOnCmd = true;
                break;
            }
        }
        boolean userSpecifiedBySysProp = System.getProperty("server.port") != null;

        List<String> finalArgs = new ArrayList<>(Arrays.asList(args));
        if (!userSpecifiedOnCmd && !userSpecifiedBySysProp) {
            // 注入命令行参数 --server.port=8080，能覆盖 SERVER_PORT 环境变量
            finalArgs.add("--server.port=8080");
        }
        SpringApplication.run(LifeDeskApplication.class, finalArgs.toArray(new String[0]));

        System.out.println();
        System.out.println("====================================================");
        System.out.println("  LifeDesk Backend 已启动");
        System.out.println("  接口: http://localhost:8080/api/dashboard/overview");
        System.out.println("  前端: http://localhost:8081");
        System.out.println("====================================================");
    }
}
