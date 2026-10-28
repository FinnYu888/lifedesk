<template>
  <div class="monitor">
    <div class="stat-row">
      <StatCard label="在线服务" :value="stat.online_services ?? 0" note="运行良好" noteType="positive" />
      <StatCard label="告警" :value="stat.alerts ?? 0" :note="stat.alerts_note || ''" noteType="danger" />
      <StatCard label="平均延迟" :value="(stat.avg_latency ?? 0) + 'ms'" :note="(stat.avg_latency_delta || 0) + 'ms'" noteType="positive" />
    </div>

    <div class="dual-row">
      <!-- 服务器状态列表（内置项 + RocketMQ/Redis 实时状态同步） -->
      <div class="ld-card">
        <div class="card-title">服务器状态</div>
        <div v-for="(s, i) in serverList" :key="s.name + i" class="server-row" :class="{ alert: s.status === 'alert' || s.status === 'checking' }">
          <span class="server-dot" :class="serverDotCls(s.status)" />
          <div class="server-info">
            <div class="server-name">{{ s.name }}</div>
            <div class="server-sub" :class="serverTextCls(s.status)">{{ s.sub || statusLabel(s.status) }}</div>
          </div>
          <div class="server-latency">{{ s.metric || '—' }}</div>
        </div>
      </div>

      <!-- 性能 24h -->
      <div class="ld-card">
        <div class="card-title">性能指标·24h</div>
        <div class="perf-current">
          <span>CPU <b>68%</b></span>
          <span>内存 <b>42%</b></span>
          <span>延迟 <b>19ms</b></span>
        </div>
        <VChart :option="perfOption" autoresize style="height: 220px" />
      </div>
    </div>

    <!-- 外部监控源区域：RocketMQ / Redis / MySQL / OSS（后续新增数据源面板在此依次排列） -->
    <div class="ext-monitor-section">
      <RocketMqPanel @status="onExtStatus('rocketmq', $event)" />
      <RedisPanel @status="onExtStatus('redis', $event)" />
      <MysqlPanel @status="onExtStatus('mysql', $event)" />
      <OssPanel @status="onExtStatus('oss', $event)" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from "vue";
import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { LineChart } from "echarts/charts";
import { GridComponent, TooltipComponent, LegendComponent } from "echarts/components";
import StatCard from "@/components/StatCard.vue";
import RocketMqPanel from "./components/RocketMqPanel.vue";
import RedisPanel from "./components/RedisPanel.vue";
import MysqlPanel from "./components/MysqlPanel.vue";
import OssPanel from "./components/OssPanel.vue";
import { fetchMonitorOverview } from "@/api/monitor";

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent]);

defineOptions({ name: "MonitorView" });

const stat = ref<any>({});
const servers = ref<any[]>([]);
const alerts = ref<any[]>([]);
const perf = ref<any[]>([]);

// ===== 外部监控源实时状态（面板加载后上报，同步进服务器状态列表） =====
const extStatus = reactive<Record<string, { name: string; running: boolean; metric: string; sub: string }>>({});

function onExtStatus(key: string, payload: { running: boolean; metric: string; sub: string }) {
  const names: Record<string, string> = {
    rocketmq: "消息队列 RocketMQ",
    redis: "Redis 缓存",
    mysql: "MySQL 数据库",
    oss: "文件存储 OSS"
  };
  extStatus[key] = { name: names[key] || key, running: payload.running, metric: payload.metric, sub: payload.sub };
}

/** 服务器状态列表：内置项（剔除 mock 的监控类行）+ 外部监控源实时行 */
const serverList = computed<any[]>(() => {
  const mockExclude = ["消息队列", "Redis 缓存", "数据库", "文件存储"];
  const builtin = servers.value
    .filter((s: any) => !mockExclude.some(k => (s.name || "").includes(k)))
    .map((s: any) => ({
      name: s.name,
      status: s.status,
      metric: s.latency ? s.latency + (s.latencyUnit || "ms") : ""
    }));
  const live = ["rocketmq", "redis", "mysql", "oss"]
    .filter(k => extStatus[k])
    .map(k => ({
      name: extStatus[k].name,
      status: extStatus[k].running ? "running" : "alert",
      sub: extStatus[k].sub,
      metric: extStatus[k].metric
    }));
  return [...builtin, ...live];
});

const perfOption = computed(() => {
  const x = perf.value.map(p => p.pointTime);
  return {
    tooltip: { trigger: "axis" },
    legend: {
      data: ["CPU", "内存", "延迟"],
      textStyle: { color: "#a8a0c8" }
    },
    grid: { left: 36, right: 16, top: 30, bottom: 26 },
    xAxis: {
      type: "category",
      data: x,
      axisLabel: { color: "#a8a0c8", fontSize: 10 }
    },
    yAxis: [
      {
        type: "value",
        name: "%",
        axisLabel: { color: "#a8a0c8", fontSize: 10 }
      }
    ],
    series: [
      {
        name: "CPU",
        type: "line",
        smooth: true,
        data: perf.value.map(p => p.cpu),
        lineStyle: { color: "#2dd4bf", width: 2 },
        itemStyle: { color: "#2dd4bf" }
      },
      {
        name: "内存",
        type: "line",
        smooth: true,
        data: perf.value.map(p => p.memory),
        lineStyle: { color: "#a78bfa", width: 2 },
        itemStyle: { color: "#a78bfa" }
      },
      {
        name: "延迟",
        type: "line",
        smooth: true,
        data: perf.value.map(p => p.latency),
        lineStyle: { color: "#fbbf24", width: 2 },
        itemStyle: { color: "#fbbf24" }
      }
    ]
  };
});

function serverDotCls(status: string) {
  if (status === "alert") return "dot-danger";
  if (status === "checking") return "dot-danger";
  return "dot-positive";
}
function serverTextCls(status: string) {
  if (status === "alert" || status === "checking") return "text-danger";
  return "text-positive";
}
function statusLabel(status: string) {
  switch (status) {
    case "running":
      return "运行中";
    case "checking":
      return "检查中";
    case "alert":
      return "告警";
    case "timeout":
      return "超时";
    default:
      return status;
  }
}

async function load() {
  try {
    const r = await fetchMonitorOverview();
    stat.value = r.data.stat;
    servers.value = r.data.servers;
    alerts.value = r.data.alerts;
    perf.value = r.data.perf;
  } catch {}
}

onMounted(load);
</script>

<style scoped lang="scss">
.stat-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}
.dual-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 18px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 14px;
}
.server-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 12px;
  border-radius: $radius-sm;
  border: 1px solid $border-subtle;
  margin-bottom: 8px;
  &.alert {
    border-color: rgba(248, 113, 113, 0.4);
    background: rgba(248, 113, 113, 0.04);
  }
}
.server-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.dot-positive { background: $color-positive; }
.dot-danger { background: $color-danger; }
.server-info { flex: 1; }
.server-name {
  font-size: 13px;
  color: $text-primary;
}
.server-sub {
  font-size: 11px;
  margin-top: 2px;
}
.server-latency {
  font-size: 13px;
  color: $text-secondary;
  font-weight: 500;
}
.perf-current {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: $text-secondary;
  margin-bottom: 10px;
  b {
    color: $text-primary;
    font-size: 15px;
  }
}
</style>