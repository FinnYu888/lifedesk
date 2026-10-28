package com.lifedesk.extmonitor.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部监控告警中心（内存态）。
 *
 * <p>各监控源定时评估后通过 {@link #fire} 触发告警、条件恢复后调用 {@link #resolve} 消除，
 * 同一 key 的重复触发只更新 lastTime / count / message，不产生重复告警条目。
 * 「当前告警」语义：条件恢复即移除条目，前端右上角闹铃徽标展示活跃告警数量。</p>
 *
 * @author LifeDesk
 */
@Slf4j
@Service
public class AlertCenter {

    /** key -> 告警条目（保持插入顺序，输出时按首次触发时间倒序） */
    private final Map<String, Alert> alerts = new LinkedHashMap<>();

    /**
     * 触发（或更新）一条告警。
     *
     * @param source  来源标识：rocketmq / redis / mysql / oss
     * @param level   级别：critical / warning
     * @param key     同类告警去重键（如 "rocketmq:backlog"）
     * @param message 告警描述（每次评估刷新，展示最新状态）
     */
    public void fire(String source, String level, String key, String message) {
        synchronized (alerts) {
            Alert exist = alerts.get(key);
            if (exist == null) {
                Alert a = new Alert();
                a.source = source;
                a.level = level;
                a.key = key;
                a.message = message;
                long now = System.currentTimeMillis();
                a.firstTime = now;
                a.lastTime = now;
                a.count = 1;
                alerts.put(key, a);
                log.warn("ALERT [{}] {} {}: {}", level, source, key, message);
            } else {
                exist.lastTime = System.currentTimeMillis();
                exist.count++;
                exist.message = message;
                if (!exist.level.equals(level)) {
                    exist.level = level;
                }
            }
        }
    }

    /** 条件恢复：移除对应告警（无该条目时静默） */
    public void resolve(String key) {
        synchronized (alerts) {
            if (alerts.remove(key) != null) {
                log.info("ALERT resolved: {}", key);
            }
        }
    }

    /** 条件恢复：移除所有以 prefix 开头的告警（如动态 key 的消费组延迟告警） */
    public void resolvePrefix(String prefix) {
        synchronized (alerts) {
            alerts.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }

    /** 记录本轮已触发的动态 key 集合，移除不在集合内的前缀告警（防泄漏） */
    public void retainPrefix(String prefix, java.util.Set<String> keepKeys) {
        synchronized (alerts) {
            alerts.keySet().removeIf(k -> k.startsWith(prefix) && !keepKeys.contains(k));
        }
    }

    /** 当前活跃告警（按首次触发时间倒序） */
    public List<Alert> active() {
        synchronized (alerts) {
            List<Alert> list = new ArrayList<>(alerts.values());
            list.sort(Comparator.comparingLong((Alert a) -> a.firstTime).reversed());
            return list;
        }
    }

    /** 当前活跃告警数量（右上角闹铃徽标） */
    public int activeCount() {
        synchronized (alerts) {
            return alerts.size();
        }
    }

    /** 告警条目 */
    public static class Alert {
        public String source;
        public String level;
        public String key;
        public String message;
        public long firstTime;
        public long lastTime;
        public long count;

        public String getSource() { return source; }
        public String getLevel() { return level; }
        public String getKey() { return key; }
        public String getMessage() { return message; }
        public String getFirstTime() { return fmt(firstTime); }
        public String getLastTime() { return fmt(lastTime); }
        public long getCount() { return count; }

        private static String fmt(long ts) {
            return new SimpleDateFormat("MM-dd HH:mm:ss").format(new Date(ts));
        }
    }
}
