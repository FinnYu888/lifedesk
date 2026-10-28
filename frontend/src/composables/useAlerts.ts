import { ref } from "vue";
import { fetchAlerts } from "@/api/extMonitor";

/**
 * 当前活跃告警（单例共享）。
 *
 * 由 {@link startAlertPolling} 在应用启动后（MainLayout onMounted）启动一次轮询，
 * 所有页面（右上角闹铃、系统监控页关键事件条）共用同一份响应式状态，避免重复请求。
 */
export interface AlertItem {
  source: string;
  level: string; // critical / warning
  key: string;
  message: string;
  firstTime: string;
  lastTime: string;
  count: number;
}

const SOURCE_LABEL: Record<string, string> = {
  rocketmq: "RocketMQ",
  redis: "Redis",
  mysql: "MySQL",
  oss: "OSS"
};

const alerts = ref<AlertItem[]>([]);
const alertCount = ref(0);

let timer: number | null = null;
let started = false;

async function load() {
  try {
    const r: any = await fetchAlerts();
    const d = r?.data || {};
    alerts.value = d.list || [];
    alertCount.value = d.count ?? alerts.value.length;
  } catch {
    /* 拦截器已提示，保持上一次状态 */
  }
}

/** 启动全局告警轮询（幂等，仅首次生效） */
export function startAlertPolling(pollMs = 20000) {
  if (started) return;
  started = true;
  load();
  timer = window.setInterval(load, pollMs);
}

/** 获取共享的告警响应式状态 */
export function useAlerts() {
  return {
    alerts,
    alertCount,
    sourceLabel: (s: string) => SOURCE_LABEL[s] || s,
    levelLabel: (lv: string) => (lv === "critical" ? "严重" : lv === "warning" ? "警告" : lv)
  };
}
