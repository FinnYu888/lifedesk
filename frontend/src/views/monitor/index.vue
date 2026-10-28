<template>
  <div class="monitor">
    <!-- 全局关键事件 / 告警条：聚合各中间件当前活跃告警，一目了然 -->
    <div class="alert-strip" :class="alertCount > 0 ? 'has-alert' : 'ok'">
      <el-icon class="as-icon" :size="18"><Bell /></el-icon>
      <template v-if="alertCount > 0">
        <span class="as-title">当前告警 {{ alertCount }} 条</span>
        <span
          v-for="a in alerts.slice(0, 5)"
          :key="a.key"
          class="as-chip"
          :class="'lv-' + a.level"
          :title="a.message"
        >
          <b>{{ sourceLabel(a.source) }}</b> · {{ a.message }}
        </span>
        <span v-if="alerts.length > 5" class="as-more">…等 {{ alerts.length }} 条</span>
      </template>
      <template v-else>
        各中间件运行正常，暂无活跃告警 🎉
      </template>
    </div>

    <div class="dual-row">
      <!-- 服务器状态列表（由 4 个外部监控面板实时上报） -->
      <div class="ld-card">
        <div class="card-title">服务器状态</div>
        <div
          v-for="(s, i) in serverList"
          :key="s.name + i"
          class="server-row"
          :class="{ alert: s.status === 'alert' }"
        >
          <span class="server-dot" :class="serverDotCls(s.status)" />
          <div class="server-info">
            <div class="server-name">{{ s.name }}</div>
            <div class="server-sub" :class="serverTextCls(s.status)">
              {{ s.sub || statusLabel(s.status) }}
            </div>
          </div>
          <div class="server-latency">{{ s.metric || "—" }}</div>
        </div>
        <div v-if="!serverList.length" class="server-empty">等待监控面板上报状态…</div>
      </div>

      <!-- Redis 性能走势（24h，QPS / 内存 / 命中率 / 连接数） -->
      <div class="ld-card">
        <div class="card-title">
          Redis 性能走势
          <span class="card-sub">近 24h 采样（30s/点）</span>
        </div>
        <div v-if="trend.length" class="trend-legend">
          <span><i class="dot c-ops" /> QPS</span>
          <span><i class="dot c-mem" /> 内存 MB</span>
          <span><i class="dot c-hit" /> 命中率 %</span>
          <span><i class="dot c-cli" /> 连接数</span>
        </div>
        <VChart v-if="trend.length" :option="perfOption" autoresize style="height: 240px" />
        <div v-else class="trend-loading">正在采集 Redis 趋势数据（约 30s 后首点出现）…</div>
      </div>
    </div>

    <!-- 外部监控源区域：RocketMQ / Redis / MySQL / OSS -->
    <div class="ext-monitor-section">
      <RocketMqPanel @status="onExtStatus('rocketmq', $event)" />
      <RedisPanel @status="onExtStatus('redis', $event)" />
      <MysqlPanel @status="onExtStatus('mysql', $event)" />
      <OssPanel @status="onExtStatus('oss', $event)" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, onUnmounted } from "vue";
import { Bell } from "@element-plus/icons-vue";
import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { LineChart } from "echarts/charts";
import { GridComponent, TooltipComponent, LegendComponent } from "echarts/components";
import RocketMqPanel from "./components/RocketMqPanel.vue";
import RedisPanel from "./components/RedisPanel.vue";
import MysqlPanel from "./components/MysqlPanel.vue";
import OssPanel from "./components/OssPanel.vue";
import { fetchRedisTrend } from "@/api/extMonitor";
import { useAlerts } from "@/composables/useAlerts";

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent]);

defineOptions({ name: "MonitorView" });

// 全局告警（与右上角闹铃共享同一份状态）
const { alerts, alertCount, sourceLabel, levelLabel } = useAlerts();
void levelLabel;

// ===================== 服务器状态列表（由面板实时上报） =====================
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

const serverList = computed<any[]>(() =>
  ["rocketmq", "redis", "mysql", "oss"]
    .filter((k) => extStatus[k])
    .map((k) => ({
      name: extStatus[k].name,
      status: extStatus[k].running ? "running" : "alert",
      sub: extStatus[k].sub,
      metric: extStatus[k].metric
    }))
);

function serverDotCls(status: string) {
  return status === "alert" ? "dot-danger" : "dot-positive";
}
function serverTextCls(status: string) {
  return status === "alert" ? "text-danger" : "text-positive";
}
function statusLabel(status: string) {
  return status === "running" ? "运行中" : status === "alert" ? "告警" : status;
}

// ===================== Redis 趋势图 =====================
const trend = ref<any[]>([]);
let trendTimer: number | null = null;

async function loadTrend() {
  try {
    const r: any = await fetchRedisTrend();
    trend.value = r?.data?.points || [];
  } catch {
    /* 忽略 */
  }
}

const perfOption = computed(() => {
  const x = trend.value.map((p) => p.time);
  return {
    tooltip: { trigger: "axis" },
    legend: { show: false },
    grid: { left: 44, right: 44, top: 16, bottom: 28 },
    xAxis: {
      type: "category",
      data: x,
      axisLabel: { color: "#a8a0c8", fontSize: 10, hideOverlap: true }
    },
    yAxis: [
      {
        type: "value",
        name: "QPS / 连接",
        nameTextStyle: { color: "#a8a0c8", fontSize: 10 },
        axisLabel: { color: "#a8a0c8", fontSize: 10 }
      },
      {
        type: "value",
        name: "MB / %",
        nameTextStyle: { color: "#a8a0c8", fontSize: 10 },
        axisLabel: { color: "#a8a0c8", fontSize: 10 }
      }
    ],
    series: [
      {
        name: "QPS",
        type: "line",
        smooth: true,
        showSymbol: false,
        yAxisIndex: 0,
        data: trend.value.map((p) => p.ops),
        lineStyle: { color: "#2dd4bf", width: 2 },
        itemStyle: { color: "#2dd4bf" }
      },
      {
        name: "连接数",
        type: "line",
        smooth: true,
        showSymbol: false,
        yAxisIndex: 0,
        data: trend.value.map((p) => p.clients),
        lineStyle: { color: "#a78bfa", width: 2 },
        itemStyle: { color: "#a78bfa" }
      },
      {
        name: "内存MB",
        type: "line",
        smooth: true,
        showSymbol: false,
        yAxisIndex: 1,
        data: trend.value.map((p) => p.usedMb),
        lineStyle: { color: "#fbbf24", width: 2 },
        itemStyle: { color: "#fbbf24" }
      },
      {
        name: "命中率%",
        type: "line",
        smooth: true,
        showSymbol: false,
        yAxisIndex: 1,
        data: trend.value.map((p) => p.hitRate),
        lineStyle: { color: "#f472b6", width: 2, type: "dashed" },
        itemStyle: { color: "#f472b6" }
      }
    ]
  };
});

onMounted(() => {
  loadTrend();
  trendTimer = window.setInterval(loadTrend, 30000);
});
onUnmounted(() => {
  if (trendTimer) clearInterval(trendTimer);
});
</script>

<style scoped lang="scss">
.monitor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

// 全局关键事件 / 告警条
.alert-strip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 14px;
  border-radius: $radius-md;
  font-size: 13px;
  &.ok {
    background: rgba(45, 212, 191, 0.08);
    border: 1px solid rgba(45, 212, 191, 0.3);
    color: $color-positive;
  }
  &.has-alert {
    background: rgba(248, 113, 113, 0.08);
    border: 1px solid rgba(248, 113, 113, 0.35);
    color: $text-secondary;
  }
}
.as-icon {
  flex-shrink: 0;
}
.as-title {
  font-weight: 700;
  color: $color-danger;
}
.as-chip {
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  color: $text-secondary;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid $border-subtle;
  border-left: 3px solid $text-tertiary;
  &.lv-critical { border-left-color: $color-danger; }
  &.lv-warning { border-left-color: $color-warning; }
  b { color: $text-primary; }
}
.as-more {
  font-size: 12px;
  color: $text-tertiary;
}

.dual-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); /* minmax(0,1fr)：子项（图表）可收缩，防止撑破容器出现横向滚动 */
  gap: 14px;
}
.dual-row > *,
.ext-monitor-section > * {
  min-width: 0;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 14px;
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.card-sub {
  font-size: 11px;
  color: $text-tertiary;
  font-weight: 400;
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
.server-info { flex: 1; min-width: 0; }
.server-name {
  font-size: 13px;
  color: $text-primary;
}
.server-sub {
  font-size: 11px;
  margin-top: 2px;
}
.text-danger { color: $color-danger; }
.text-positive { color: $color-positive; }
.server-latency {
  font-size: 13px;
  color: $text-secondary;
  font-weight: 500;
}
.server-empty {
  font-size: 12px;
  color: $text-tertiary;
  padding: 16px 4px;
  text-align: center;
}

.trend-legend {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: $text-secondary;
  margin-bottom: 6px;
  span { display: inline-flex; align-items: center; gap: 4px; }
}
.dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  display: inline-block;
}
.c-ops { background: #2dd4bf; }
.c-mem { background: #fbbf24; }
.c-hit { background: #f472b6; }
.c-cli { background: #a78bfa; }
.trend-loading {
  height: 240px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: $text-tertiary;
}

@media (max-width: 1100px) {
  .dual-row {
    grid-template-columns: 1fr;
  }
}
</style>
