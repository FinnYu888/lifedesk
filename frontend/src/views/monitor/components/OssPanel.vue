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
        <span class="oss-fetched">{{ addr }} · {{ data.fetchedAt ? '统计于 ' + data.fetchedAt : '首次统计约需数分钟' }}</span>
        <button class="ld-btn ld-btn-ghost sm" :disabled="loading" title="触发后台重新统计" @click="load(true)">
          <el-icon><Refresh /></el-icon>
        </button>
      </div>
    </div>

    <div v-if="loaded && !healthy && !data.collecting" class="oss-error">
      <el-icon :size="18"><WarningFilled /></el-icon>
      <span>{{ data.errMsg || '无法连接对象存储端点' }}</span>
    </div>

    <!-- 总览指标 -->
    <div class="oss-metrics">
      <div class="oss-metric">
        <span class="mm-value">{{ data.bucketOk ?? '—' }}<i>/{{ data.bucketTotal ?? '—' }}</i></span>
        <span class="mm-label">可访问桶/监控桶</span>
      </div>
      <div class="oss-metric">
        <span class="mm-value">{{ data.collecting ? '…' : num(data.totalObjects) }}</span>
        <span class="mm-label">对象总数</span>
      </div>
      <div class="oss-metric">
        <span class="mm-value">{{ data.collecting ? '…' : size(data.totalSizeBytes) }}</span>
        <span class="mm-label">存储总容量</span>
      </div>
      <div class="oss-metric">
        <span class="mm-value">{{ data.collecting ? '…' : avgObj }}</span>
        <span class="mm-label">平均对象大小</span>
      </div>
      <div class="oss-metric">
        <span class="mm-value">{{ maxBucket ? maxBucket.sizeHuman : '…' }}</span>
        <span class="mm-label">最大桶 · {{ maxBucket ? maxBucket.name : '—' }}</span>
      </div>
      <div class="oss-metric">
        <span class="mm-value">{{ avgLatency ? avgLatency + 'ms' : '…' }}</span>
        <span class="mm-label">平均响应延迟</span>
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
            <template v-else><span class="b-dot dot-danger" /> {{ b.errMsg || '不可访问' }}</template>
          </td>
          <td>{{ b.ok ? (b.objects != null ? num(b.objects) : '统计中…') : '—' }}</td>
          <td>{{ b.ok ? (b.sizeBytes != null ? size(b.sizeBytes) : '统计中…') : '—' }}</td>
          <td>{{ b.ok ? b.latencyMs + 'ms' : '—' }}</td>
        </tr>
        <tr v-if="!data.buckets?.length">
          <td colspan="5" class="empty">暂无数据{{ data.collecting ? '，对象规模较大，全量统计约需数分钟' : '' }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from "vue";
import { Refresh, WarningFilled } from "@element-plus/icons-vue";
import { fetchOssOverview, refreshOssOverview } from "@/api/extMonitor";

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

function reportStatus() {
  const okB = data.value.bucketOk ?? 0;
  const total = data.value.bucketTotal ?? 0;
  const running = !!data.value.ok && !data.value.collecting && okB === total;
  emit("status", {
    running,
    metric: data.value.collecting ? "统计中" : size(data.value.totalSizeBytes),
    sub: data.value.collecting
      ? `统计中 · 全量对象扫描进行中（约数分钟）`
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
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
  margin-bottom: 16px;
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
.oss-sub-title {
  font-size: 13px;
  font-weight: 600;
  color: $text-secondary;
  margin-bottom: 8px;
}
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
    white-space: nowrap;
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
}
</style>
