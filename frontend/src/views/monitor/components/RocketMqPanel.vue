<template>
  <div class="ld-card mq-panel">
    <div class="mq-header">
      <div class="mq-title">
        <span class="mq-dot" :class="healthy ? 'dot-positive' : 'dot-danger'" />
        <span>RocketMQ · 消息队列</span>
        <el-tag v-if="healthy" size="small" type="success" effect="plain">运行正常</el-tag>
        <el-tag v-else size="small" type="danger" effect="plain">异常</el-tag>
      </div>
      <div class="mq-actions">
        <span class="mq-fetched">更新于 {{ data.fetchedAt || "—" }}</span>
        <button class="ld-btn ld-btn-ghost sm" :disabled="loading" @click="load(true)">
          <el-icon><Refresh /></el-icon>
        </button>
      </div>
    </div>

    <!-- 采集失败提示 -->
    <div v-if="loaded && !healthy && !data.cluster" class="mq-error">
      <el-icon :size="18"><WarningFilled /></el-icon>
      <span>{{ data.errMsg || "无法连接 RocketMQ Dashboard" }}</span>
    </div>

    <template v-if="data.cluster">
      <!-- 总览指标 -->
      <div class="mq-metrics">
        <div class="mq-metric">
          <span class="mm-value">{{ c.brokerNodeCount }}</span>
          <span class="mm-label">Broker 节点</span>
        </div>
        <div class="mq-metric">
          <span class="mm-value">{{ num(c.bizTopicTotal) }}<i>/{{ num(c.topicTotal) }}</i></span>
          <span class="mm-label">业务/全部 Topic</span>
        </div>
        <div class="mq-metric">
          <span class="mm-value">{{ c.onlineGroups }}<i>/{{ c.groupTotal }}</i></span>
          <span class="mm-label">在线/全部消费组</span>
        </div>
        <div class="mq-metric">
          <span class="mm-value">{{ num(c.todayPut) }}</span>
          <span class="mm-label">今日收消息</span>
        </div>
        <div class="mq-metric">
          <span class="mm-value">{{ num(c.todayGet) }}</span>
          <span class="mm-label">今日发消息</span>
        </div>
        <div class="mq-metric">
          <span class="mm-value">{{ c.totalConsumeTps }}</span>
          <span class="mm-label">消费 TPS</span>
        </div>
        <div class="mq-metric" :class="{ warn: c.maxDiskRatio >= 0.7 }">
          <span class="mm-value">{{ pct(c.maxDiskRatio) }}</span>
          <span class="mm-label">最大磁盘占用</span>
        </div>
        <div class="mq-metric" :class="{ warn: c.totalLag > 0 }">
          <span class="mm-value">{{ num(c.totalLag) }}</span>
          <span class="mm-label">消息积压总量</span>
        </div>
      </div>

      <!-- 图表 + 关键事件 -->
      <div class="mq-insight">
        <div class="mq-chart-box">
          <div class="mq-sub-title">各 Broker 今日收发消息量</div>
          <VChart v-if="barOption" :option="barOption" autoresize style="height: 230px" />
          <div v-else class="chart-empty">无节点数据</div>
        </div>
        <div class="mq-events-box">
          <div class="mq-sub-title">
            关键事件
            <span class="ev-count" :class="{ 'has': events.length }">{{ events.length }}</span>
          </div>
          <div v-if="!events.length" class="ev-empty">近期无异常事件 🎉</div>
          <div v-for="(e, i) in events" :key="i" class="ev-item" :class="'lv-' + e.level">
            <span class="ev-dot" />
            <span class="ev-text">{{ e.text }}</span>
          </div>
        </div>
      </div>

      <div class="mq-tables">
        <!-- Broker 节点明细 -->
        <div class="mq-table-box">
          <div class="mq-sub-title">Broker 节点明细</div>
          <table class="mq-table">
            <thead>
              <tr>
                <th>节点</th>
                <th>状态</th>
                <th>版本</th>
                <th>写 TPS</th>
                <th>读 TPS</th>
                <th>今日收/发</th>
                <th>磁盘占用</th>
                <th>写延迟 P99</th>
                <th>运行时长</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="b in data.brokers" :key="b.name + '#' + b.brokerId">
                <td class="strong">{{ b.name }}#{{ b.brokerId }}</td>
                <td>
                  <template v-if="b.master">
                    <span class="b-dot" :class="b.active ? 'dot-positive' : 'dot-danger'" />
                    {{ b.active ? "在线" : "离线" }}
                  </template>
                  <span v-else class="slave-tag">备节点</span>
                </td>
                <td>{{ b.version || "—" }}</td>
                <td>{{ b.putTps }}</td>
                <td>{{ b.getFoundTps }}</td>
                <td>{{ num(b.todayPut) }} / {{ num(b.todayGet) }}</td>
                <td class="disk-cell">
                  <el-progress
                    :percentage="Math.round(b.diskRatio * 100)"
                    :stroke-width="7"
                    :color="b.diskRatio >= 0.7 ? '#f87171' : '#2dd4bf'"
                  />
                </td>
                <td>{{ b.putLatency99 }}ms</td>
                <td>{{ b.runtime || "—" }}</td>
              </tr>
              <tr v-if="!data.brokers?.length">
                <td colspan="9" class="empty">无节点数据</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 消费积压 Top -->
        <div class="mq-table-box">
          <div class="mq-sub-title">消费积压 Top 8</div>
          <table class="mq-table">
            <thead>
              <tr>
                <th>消费组</th>
                <th>在线实例</th>
                <th>消费 TPS</th>
                <th>积压消息</th>
                <th>等待时长</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="g in data.topLag" :key="g.group">
                <td class="strong group-name" :title="g.group">{{ g.group }}</td>
                <td>{{ g.consumerCount }}</td>
                <td>{{ g.consumeTps }}</td>
                <td class="lag-num">{{ num(g.lag) }}</td>
                <td :class="{ 'lag-num': g.delaySec >= 300 || g.delaySec === -1 }">
                  {{ g.delaySec === -1 ? "停滞" : delayText(g.delaySec) }}
                </td>
              </tr>
              <tr v-if="!data.topLag?.length">
                <td colspan="5" class="empty">无积压，消费健康 🎉</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from "vue";
import { Refresh, WarningFilled } from "@element-plus/icons-vue";
import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { BarChart } from "echarts/charts";
import { GridComponent, TooltipComponent, LegendComponent } from "echarts/components";
import { fetchRocketMqOverview, refreshRocketMqOverview } from "@/api/extMonitor";

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent, LegendComponent]);

defineOptions({ name: "RocketMqPanel" });

const emit = defineEmits<{
  (e: "status", payload: { running: boolean; metric: string; sub: string }): void;
}>();

const data = ref<any>({});
const loading = ref(false);
const loaded = ref(false);
let timer: number | null = null;

const c = computed(() => data.value.cluster || {});
const healthy = computed(() => !!data.value.cluster?.allActive);

function num(v: any): string {
  const n = Number(v) || 0;
  return n.toLocaleString("en-US");
}
function pct(v: any): string {
  return Math.round((Number(v) || 0) * 1000) / 10 + "%";
}
function delayText(sec: number): string {
  if (!sec || sec < 0) return "—";
  if (sec >= 3600) return Math.round(sec / 3600) + "h";
  if (sec >= 60) return Math.round(sec / 60) + "m";
  return sec + "s";
}

/** 各 Broker 今日收/发柱状图 */
const barOption = computed(() => {
  const brokers = data.value.brokers || [];
  if (!brokers.length) return null;
  return {
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
    legend: { data: ["今日收", "今日发"], textStyle: { color: "#a8a0c8", fontSize: 11 } },
    grid: { left: 8, right: 12, top: 30, bottom: 20, containLabel: true },
    xAxis: {
      type: "category",
      data: brokers.map((b: any) => b.name + "#" + b.brokerId),
      axisLabel: { color: "#a8a0c8", fontSize: 10, rotate: brokers.length > 4 ? 30 : 0 }
    },
    yAxis: {
      type: "value",
      axisLabel: { color: "#a8a0c8", fontSize: 10 }
    },
    series: [
      {
        name: "今日收",
        type: "bar",
        data: brokers.map((b: any) => b.todayPut),
        itemStyle: { color: "#2dd4bf", borderRadius: [3, 3, 0, 0] }
      },
      {
        name: "今日发",
        type: "bar",
        data: brokers.map((b: any) => b.todayGet),
        itemStyle: { color: "#a78bfa", borderRadius: [3, 3, 0, 0] }
      }
    ]
  };
});

/** 关键事件（异常聚合） */
const events = computed(() => {
  const list: { level: "critical" | "warning"; text: string }[] = [];
  const cl = data.value.cluster || {};
  (data.value.brokers || []).forEach((b: any) => {
    if (b.master && !b.active) {
      list.push({ level: "critical", text: `Broker ${b.name}#${b.brokerId} 离线` });
    }
    if (b.diskRatio >= 0.7) {
      list.push({ level: "warning", text: `Broker ${b.name}#${b.brokerId} 磁盘占用 ${pct(b.diskRatio)}` });
    }
    if (b.putLatency99 >= 200) {
      list.push({ level: "warning", text: `Broker ${b.name} 写延迟 P99 ${b.putLatency99}ms` });
    }
  });
  if (cl.totalLag > 0) {
    list.push({ level: "warning", text: `消息积压 ${num(cl.totalLag)} 条待消费` });
  }
  if (cl.maxDiskRatio >= 0.7 && !list.some((e) => e.text.includes("磁盘"))) {
    list.push({ level: "warning", text: `集群最大磁盘占用 ${pct(cl.maxDiskRatio)}` });
  }
  return list;
});

/** 向父级（服务器状态列表）上报实时状态 */
function reportStatus() {
  const ok = loaded.value && healthy.value && !!data.value.cluster;
  let metric = "—";
  if (ok) {
    const master = (data.value.brokers || []).find((b: any) => b.master) || data.value.brokers?.[0];
    metric = master ? `${master.putTps} msg/s` : "—";
  }
  emit("status", {
    running: ok,
    metric,
    sub: ok
      ? `运行中 · ${c.value.brokerNodeCount} 节点 · ${num(c.value.todayPut)} 收今日`
      : `异常 · ${data.value.errMsg || "Dashboard 不可达"}`
  });
}

async function load(force = false) {
  loading.value = true;
  try {
    const r = force ? await refreshRocketMqOverview() : await fetchRocketMqOverview();
    data.value = r.data || {};
    loaded.value = true;
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false;
    reportStatus();
  }
}

onMounted(() => {
  load();
  timer = window.setInterval(() => load(), 30000);
});
onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<style scoped lang="scss">
.mq-panel {
  margin-bottom: 18px;
}
.mq-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.mq-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
}
.mq-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.dot-positive { background: $color-positive; }
.dot-danger { background: $color-danger; }
.mq-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.mq-fetched {
  font-size: 11px;
  color: $text-tertiary;
}
.ld-btn.sm {
  padding: 4px 8px;
}
.mq-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: $radius-sm;
  border: 1px solid rgba(248, 113, 113, 0.4);
  background: rgba(248, 113, 113, 0.06);
  color: $color-danger;
  font-size: 13px;
  margin-bottom: 12px;
}
.mq-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(110px, 1fr)); /* 自适应换列，窄屏不溢出 */
  gap: 10px;
  margin-bottom: 16px;
}
.mq-metrics > * {
  min-width: 0;
}
.mq-metric {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 12px;
  border-radius: $radius-sm;
  border: 1px solid $border-subtle;
  &.warn .mm-value {
    color: $color-danger;
  }
}
.mm-value {
  font-size: 18px;
  font-weight: 700;
  color: $text-primary;
  i {
    font-style: normal;
    font-size: 11px;
    color: $text-tertiary;
    font-weight: 400;
  }
}
.mm-label {
  font-size: 11px;
  color: $text-tertiary;
}

// 图表 + 关键事件 并排
.mq-insight {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px; /* 图表列可收缩，防 ECharts 撑破 */
  gap: 14px;
  margin-bottom: 16px;
}
.mq-insight > * {
  min-width: 0;
}
.mq-chart-box,
.mq-events-box {
  border: 1px solid $border-subtle;
  border-radius: $radius-sm;
  padding: 12px;
}
.mq-sub-title {
  font-size: 13px;
  font-weight: 600;
  color: $text-secondary;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.chart-empty,
.ev-empty {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $text-tertiary;
  font-size: 12px;
}
.ev-empty {
  height: auto;
  padding: 18px 0;
}
.ev-count {
  font-size: 11px;
  padding: 0 7px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
  color: $text-tertiary;
  &.has {
    background: rgba(248, 113, 113, 0.18);
    color: $color-danger;
  }
}
.ev-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px dashed $border-subtle;
  font-size: 12px;
  color: $text-secondary;
  &:last-child { border-bottom: none; }
}
.ev-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-top: 5px;
  flex-shrink: 0;
  background: $color-warning;
}
.lv-critical .ev-dot { background: $color-danger; }
.lv-critical .ev-text { color: $color-danger; }

.mq-tables {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
  gap: 14px;
}
.mq-tables > * {
  min-width: 0;
}
.mq-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  th {
    text-align: left;
    padding: 6px 8px;
    color: $text-tertiary;
    font-weight: 500;
    border-bottom: 1px solid $border-subtle;
    white-space: nowrap;
  }
  td {
    padding: 7px 8px;
    color: $text-secondary;
    border-bottom: 1px solid $border-subtle;
    /* 允许换行：窄屏按比例收缩，不产生横向滚动 */
    &.strong {
      color: $text-primary;
      font-weight: 500;
    }
  }
  tr:last-child td {
    border-bottom: none;
  }
}
.group-name {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}
.lag-num {
  color: $color-danger !important;
  font-weight: 600;
}
.b-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-right: 4px;
}
.slave-tag {
  color: $text-tertiary;
}
.disk-cell {
  width: 90px;
  :deep(.el-progress__text) {
    font-size: 11px !important;
    color: $text-tertiary;
  }
}
.empty {
  text-align: center;
  color: $text-tertiary;
  padding: 18px 0 !important;
}
@media (max-width: 1400px) {
  .mq-metrics {
    grid-template-columns: repeat(4, 1fr);
  }
  .mq-tables {
    grid-template-columns: 1fr;
  }
  .mq-insight {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 640px) {
  .mq-metrics {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
