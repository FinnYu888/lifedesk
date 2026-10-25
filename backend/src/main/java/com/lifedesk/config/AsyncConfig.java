package com.lifedesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置。
 *
 * <p>提供 git 同步专用线程池 {@code gitSyncExecutor}，让 ProjectService.create 与 syncProject
 * 触发的 JGit clone 操作在独立线程异步执行，HTTP 请求线程立即返回，避免前端 axios 超时。</p>
 *
 * <p>线程池策略：核心 2，最大 4，队列 16，拒绝策略 CallerRuns（队列满时回退到主线程执行，
 * 至少保证任务不丢）；线程命名前缀 git-sync- 便于排查。</p>
 *
 * @author LifeDesk
 */
@Configuration
public class AsyncConfig {

    @Bean("gitSyncExecutor")
    public ThreadPoolTaskExecutor gitSyncExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(16);
        exec.setKeepAliveSeconds(60);
        exec.setThreadNamePrefix("git-sync-");
        // 队列满时由调用线程自己跑，避免任务丢失
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(10);
        exec.initialize();
        return exec;
    }
}
