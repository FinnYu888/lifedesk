<template>
  <div class="monitor">
    <div class="stat-row">
      <StatCard label="在线服务" :value="stat.online_services ?? 0" note="运行良好" noteType="positive" />
      <StatCard label="告警" :value="stat.alerts ?? 0" :note="stat.alerts_note || ''" noteType="danger" />
      <StatCard label="平均延迟" :value="(stat.avg_latency ?? 0) + 'ms'" :note="(stat.avg_latency_delta || 0) + 'ms'" noteType="positive" />
    </div>

    <div class="dual-row">
      <!-- 服务器状态列表 -->
      <div class="ld-card">
        <div class="card-title">服务器状态</div>
        <div v-for="s in servers" :key="s.id" class="server-row" :class="{ alert: s.status === 'alert' || s.status === 'checking' }">
          <span class="server-dot" :class="serverDotCls(s.status)" />
          <div class="server-info">
            <div class="server-name">{{ s.name }}</div>
            <div class="server-sub" :class="serverTextCls(s.status)">{{ statusLabel(s.status) }}</div>
          </div>
          <div class="server-latency">{{ s.latency ? s.latency + (s.latencyUnit || 'ms') : '—' }}</div>
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { LineChart } from "echarts/charts";
import { GridComponent, TooltipComponent, LegendComponent } from "echarts/components";
import StatCard from "@/components/StatCard.vue";
import { fetchMonitorOverview } from "@/api/monitor";

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent]);

defineOptions({ name: "MonitorView" });

const stat = ref<any>({});
const servers = ref<any[]>([]);
const alerts = ref<any[]>([]);
const perf = ref<any[]>([]);

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
        lineStyle: { color: "#7c3aed", width: 2 },
        itemStyle: { color: "#7c3aed" }
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