package com.lifedesk;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * LifeDesk 后端启动类
 *
 * <p>个人工作+生活一体化仪表盘后端服务，提供 7 大模块的 REST API：
 * 首页 / 项目管理 / 任务工单 / 日程会议 / 系统监控 / Skills 工坊 / 个人健康。</p>
 *
 * @author LifeDesk
 */
@SpringBootApplication
@MapperScan("com.lifedesk.**.mapper")
public class LifeDeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifeDeskApplication.class, args);
        System.out.println();
        System.out.println("====================================================");
        System.out.println("  LifeDesk Backend 已启动");
        System.out.println("  接口: http://localhost:8080/api/dashboard/overview");
        System.out.println("====================================================");
    }
}