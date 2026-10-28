<template>
  <div class="ld-card oss-panel">
    <div class="oss-header">
      <div class="oss-title">
        <span class="oss-dot" :class="healthy ? 'dot-positive' : 'dot-danger'" />
        <span>文件存储 · OSS</span>
        <el-tag v-if="data.collecting" size="small" type="warning" effect="plain">统计中…</el-tag>
        <el-tag v-else-if="healthy" size="small" type="success" effect="plain">运行正常</el-tag>
        <el-tag v-else size="small" type="danger" effect="plain">异常</el-tag>
      </div>
      <div class="oss-actions">
        <span class="oss-fetched">{{ addr }} · {{ data.fetchedAt ? "统计于 " + data.fetchedAt : "首次统计约需数分钟" }}</span>
        <button class="ld-btn ld-btn-ghost sm" :disabled="loading" title="触发后台重新统计" @click="load(true)">
          <el-icon><Refresh /></el-icon>
        </button>
      </div>
    </div>

    <div v-if="loaded && !healthy && !data.collecting" class="oss-error">
      <el-icon :size="18"><WarningFilled /></el-icon>
      <span>{{ data.errMsg || "无法连接对象存储端点" }}</span>
    </div>

    <!-- 总览指标 -->
    <div class="oss-metrics">
      <div class="oss-metric">
        <span class="mm-value">{{ data.bucketOk ?? "—" }}<i>/{{ data.bucketTotal ?? "—" }}</i></span>
        <span class="mm-label">可访问桶/监控桶</span>
      </div>
      <div class="oss-metric">
        <span class="mm-value">{{ data.collecting ? "…" : num(data.totalObjects) }}</span>
        <span class="mm-label">对象总数</span>
      </div>
      <div class="oss-metric">
        <span class="mm-value">{{ data.collecting ? "…" : size(data.totalSizeBytes) }}</span>
        <span class="mm-label">存储总容量</span>
      </div>
      <div class="oss-metric">
        <span class="mm-value">{{ data.collecting ? "…" : avgObj }}</span>
        <span class="mm-label">平均对象大小</span>
      </div>
      <div class="oss-metric">
        <span class="mm-value">{{ maxBucket ? maxBucket.sizeHuman : "…" }}</span>
        <span class="mm-label">最大桶 · {{ maxBucket ? maxBucket.name : "—" }}</span>
      </div>
      <div class="oss-metric">
        <span class="mm-value">{{ avgLatency ? avgLatency + "ms" : "…" }}</span>
        <span class="mm-label">平均响应延迟</span>
      </div>
    </div>

    <!-- 图表 + 关键事件 -->
    <div class="oss-insight">
      <div class="oss-chart-box">
        <div class="oss-sub-title">各桶存储容量占比</div>
        <VChart v-if="pieOption" :option="pieOption" autoresize style="height: 220px" />
        <div v-else class="chart-empty">{{ data.collecting ? "统计中…" : "无桶数据" }}</div>
      </div>
      <div class="oss-events-box">
        <div class="oss-sub-title">
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

    <!-- 桶明细 -->
    <div class="oss-sub-title">桶明细</div>
    <table class="oss-table">
      <thead>
        <tr>
          <th>桶名称</th>
          <th>状态</th>
          <th>对象数</th>
          <th>存储容量</th>
          <th>响应延迟</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="b in data.buckets" :key="b.name">
          <td class="strong">{{ b.name }}</td>
          <td>
            <template v-if="b.ok"><span class="b-dot dot-positive" /> 正常</template>
            <template v-else><span class="b-dot dot-danger" /> {{ b.errMsg || "不可访问" }}</template>
          </td>
          <td>{{ b.ok ? (b.objects != null ? num(b.objects) : "统计中…") : "—" }}</td>
          <td>{{ b.ok ? (b.sizeBytes != null ? size(b.sizeBytes) : "统计中…") : "—" }}</td>
          <td>{{ b.ok ? b.latencyMs + "ms" : "—" }}</td>
        </tr>
        <tr v-if="!data.buckets?.length">
          <td colspan="5" class="empty">暂无数据{{ data.collecting ? "，对象规模较大，全量统计约需数分钟" : "" }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from "vue";
import { Refresh, WarningFilled } from "@element-plus/icons-vue";
import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { PieChart } from "echarts/charts";
import { TooltipComponent, LegendComponent } from "echarts/components";
import { fetchOssOverview, refreshOssOverview } from "@/api/extMonitor";

use([CanvasRenderer, PieChart, TooltipComponent, LegendComponent]);

const props = defineProps<{ addr?: string }>();

const emit = defineEmits<{
  (e: "status", payload: { running: boolean; metric: string; sub: string }): void;
}>();

defineOptions({ name: "OssPanel" });

const data = ref<any>({});
const loading = ref(false);
const loaded = ref(false);
let timer: number | null = null;

const healthy = computed(() => !!data.value.ok && (data.value.bucketOk ?? 0) === (data.value.bucketTotal ?? 1));

const avgObj = computed(() => {
  const o = Number(data.value.totalObjects) || 0;
  if (!o) return "—";
  return size((Number(data.value.totalSizeBytes) || 0) / o);
});

const maxBucket = computed(() => {
  const list = (data.value.buckets || []).filter((b: any) => b.ok && b.sizeBytes != null);
  if (!list.length) return null;
  const m = list.reduce((a: any, b: any) => (b.sizeBytes > a.sizeBytes ? b : a));
  return { name: m.name, sizeHuman: size(m.sizeBytes) };
});

const avgLatency = computed(() => {
  const list = (data.value.buckets || []).filter((b: any) => b.latencyMs != null);
  if (!list.length) return null;
  return Math.round(list.reduce((s: number, b: any) => s + b.latencyMs, 0) / list.length);
});

function num(v: any): string {
  const n = Number(v) || 0;
  return n.toLocaleString("en-US");
}
function size(bytes: any): string {
  const b = Number(bytes) || 0;
  if (b >= 1073741824) return (b / 1073741824).toFixed(2) + " GB";
  if (b >= 1048576) return (b / 1048576).toFixed(1) + " MB";
  if (b >= 1024) return (b / 1024).toFixed(1) + " KB";
  return b + " B";
}

/** 各桶存储占比饼图 */
const pieOption = computed(() => {
  const list = (data.value.buckets || []).filter((b: any) => b.ok && b.sizeBytes != null && Number(b.sizeBytes) > 0);
  if (!list.length) return null;
  return {
    tooltip: { trigger: "item", formatter: (p: any) => `${p.name}<br/>${size(p.value)} (${p.percent}%)` },
    legend: { bottom: 0, textStyle: { color: "#a8a0c8", fontSize: 11 } },
    series: [
      {
        type: "pie",
        radius: ["42%", "68%"],
        center: ["50%", "44%"],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: "rgba(0,0,0,0.2)", borderWidth: 2 },
        label: { color: "#c9c3e6", fontSize: 11, formatter: "{b}\n{d}%" },
        data: list.map((b: any, i: number) => ({
          name: b.name,
          value: Number(b.sizeBytes) || 0,
          itemStyle: { color: ["#2dd4bf", "#a78bfa", "#fbbf24", "#f472b6", "#60a5fa"][i % 5] }
        }))
      }
    ]
  };
});

/** 关键事件 */
const events = computed(() => {
  const list: { level: "critical" | "warning"; text: string }[] = [];
  (data.value.buckets || []).forEach((b: any) => {
    if (!b.ok) {
      list.push({ level: "critical", text: `桶 ${b.name} 不可访问${b.errMsg ? "（" + b.errMsg + "）" : ""}` });
    }
  });
  if (data.value.collecting) {
    list.push({ level: "warning", text: "全量对象统计进行中（规模较大，约数分钟）" });
  }
  return list;
});

function reportStatus() {
  const okB = data.value.bucketOk ?? 0;
  const total = data.value.bucketTotal ?? 0;
  const running = !!data.value.ok && !data.value.collecting && okB === total;
  emit("status", {
    running,
    metric: data.value.collecting ? "统计中" : size(data.value.totalSizeBytes),
    sub: data.value.collecting
      ? "统计中 · 全量对象扫描进行中（约数分钟）"
      : running
        ? `运行中 · ${okB}/${total} 桶 · ${num(data.value.totalObjects)} 对象`
        : `异常 · ${okB}/${total} 桶可访问`
  });
}

async function load(force = false) {
  loading.value = true;
  try {
    const r = force ? await refreshOssOverview() : await fetchOssOverview();
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
  // OSS 统计耗时长：前端 60s 轮询一次即可（后端长缓存 + 后台统计）
  timer = window.setInterval(() => load(), 60000);
});
onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<style scoped lang="scss">
.oss-panel {
  margin-bottom: 18px;
}
.oss-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.oss-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
}
.oss-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.dot-positive { background: $color-positive; }
.dot-danger { background: $color-danger; }
.oss-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.oss-fetched {
  font-size: 11px;
  color: $text-tertiary;
}
.ld-btn.sm {
  padding: 4px 8px;
}
.oss-error {
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
.oss-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); /* 自适应换列，窄屏不溢出 */
  gap: 10px;
  margin-bottom: 16px;
}
.oss-metrics > * {
  min-width: 0;
}
.oss-metric {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 12px;
  border-radius: $radius-sm;
  border: 1px solid $border-subtle;
}
.mm-value {
  font-size: 18px;
  font-weight: 700;
  color: $text-primary;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.oss-insight {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px; /* 图表列可收缩，防 ECharts 撑破 */
  gap: 14px;
  margin-bottom: 16px;
}
.oss-insight > * {
  min-width: 0;
}
.oss-chart-box,
.oss-events-box {
  border: 1px solid $border-subtle;
  border-radius: $radius-sm;
  padding: 12px;
}
.oss-sub-title {
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
.oss-table {
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
.b-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-right: 4px;
}
.empty {
  text-align: center;
  color: $text-tertiary;
  padding: 18px 0 !important;
}
@media (max-width: 1400px) {
  .oss-metrics {
    grid-template-columns: repeat(3, 1fr);
  }
  .oss-insight {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 640px) {
  .oss-metrics {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
