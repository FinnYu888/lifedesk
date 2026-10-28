<template>
  <div class="ld-card mysql-panel">
    <div class="mysql-header">
      <div class="mysql-title">
        <span class="mysql-dot" :class="healthy ? 'dot-positive' : 'dot-danger'" />
        <span>MySQL · 数据库</span>
        <el-tag v-if="healthy" size="small" type="success" effect="plain">运行正常</el-tag>
        <el-tag v-else size="small" type="danger" effect="plain">异常</el-tag>
      </div>
      <div class="mysql-actions">
        <span class="mysql-fetched">{{ addr }} · 更新于 {{ data.fetchedAt || '—' }}</span>
        <button class="ld-btn ld-btn-ghost sm" :disabled="loading" @click="load(true)">
          <el-icon><Refresh /></el-icon>
        </button>
      </div>
    </div>

    <div v-if="loaded && !healthy && !data.server?.version" class="mysql-error">
      <el-icon :size="18"><WarningFilled /></el-icon>
      <span>{{ data.errMsg || '无法连接 MySQL' }}</span>
    </div>

    <template v-if="data.server">
      <!-- 总览指标 -->
      <div class="mysql-metrics">
        <div class="mysql-metric">
          <span class="mm-value">{{ s.version || '—' }}</span>
          <span class="mm-label">版本 · 已运行{{ s.uptimeDays ?? '—' }}天</span>
        </div>
        <div class="mysql-metric" :class="{ warn: g.threadsConnected / (g.maxConnections || 1) > 0.8 }">
          <span class="mm-value">{{ g.threadsConnected ?? '—' }}<i>/{{ g.maxConnections ?? '—' }}</i></span>
          <span class="mm-label">当前连接/上限</span>
        </div>
        <div class="mysql-metric">
          <span class="mm-value">{{ g.threadsRunning ?? '—' }}</span>
          <span class="mm-label">活跃线程</span>
        </div>
        <div class="mysql-metric">
          <span class="mm-value">{{ s.avgQps ?? '—' }}</span>
          <span class="mm-label">平均 QPS</span>
        </div>
        <div class="mysql-metric" :class="{ warn: data.dbUp !== data.dbTotal }">
          <span class="mm-value">{{ data.dbUp }}<i>/{{ data.dbTotal }}</i></span>
          <span class="mm-label">正常库/监控库</span>
        </div>
        <div class="mysql-metric">
          <span class="mm-value">{{ size(data.totalSizeBytes) }}</span>
          <span class="mm-label">数据总容量</span>
        </div>
        <div class="mysql-metric">
          <span class="mm-value">{{ num(data.totalTables) }}</span>
          <span class="mm-label">数据表总数</span>
        </div>
        <div class="mysql-metric" :class="{ warn: g.abortedConnects > 100 }">
          <span class="mm-value">{{ num(g.abortedConnects) }}</span>
          <span class="mm-label">异常连接累计</span>
        </div>
      </div>

      <!-- 库明细 -->
      <div class="mysql-sub-title">数据库明细（{{ data.dbUp }}/{{ data.dbTotal }} 正常）</div>
      <table class="mysql-table">
        <thead>
          <tr>
            <th>数据库</th>
            <th>状态</th>
            <th>表数量</th>
            <th>数据大小</th>
            <th>碎片空间</th>
            <th>响应</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="d in data.databases" :key="d.db">
            <td class="strong">{{ d.db }}</td>
            <td>
              <span v-if="d.ok" class="b-dot dot-positive" /> {{ d.ok ? '正常' : (d.errMsg || '连接失败') }}
            </td>
            <td>{{ d.ok ? num(d.tables) : '—' }}</td>
            <td>{{ d.ok ? size(d.sizeBytes) : '—' }}</td>
            <td>{{ d.ok ? size(d.freeBytes) : '—' }}</td>
            <td>{{ d.ok ? d.latencyMs + 'ms' : '—' }}</td>
          </tr>
          <tr v-if="!data.databases?.length">
            <td colspan="6" class="empty">无数据</td>
          </tr>
        </tbody>
      </table>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from "vue";
import { Refresh, WarningFilled } from "@element-plus/icons-vue";
import { fetchMysqlOverview, refreshMysqlOverview } from "@/api/extMonitor";

const props = defineProps<{ addr?: string }>();

const emit = defineEmits<{
  (e: "status", payload: { running: boolean; metric: string; sub: string }): void;
}>();

defineOptions({ name: "MysqlPanel" });

const data = ref<any>({});
const loading = ref(false);
const loaded = ref(false);
let timer: number | null = null;

const s = computed(() => data.value.server || {});
const g = computed(() => data.value.global || {});
const healthy = computed(() => !!data.value.allUp);

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
  const up = data.value.dbUp ?? 0;
  const total = data.value.dbTotal ?? 0;
  const ok = loaded.value && healthy.value;
  emit("status", {
    running: ok,
    metric: ok ? `${s.value.avgQps} qps` : "—",
    sub: ok
      ? `运行中 · ${up}/${total} 库正常 · 总 ${size(data.value.totalSizeBytes)}`
      : `异常 · ${up}/${total} 库正常`
  });
}

async function load(force = false) {
  loading.value = true;
  try {
    const r = force ? await refreshMysqlOverview() : await fetchMysqlOverview();
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
.mysql-panel {
  margin-bottom: 18px;
}
.mysql-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.mysql-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
}
.mysql-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.dot-positive { background: $color-positive; }
.dot-danger { background: $color-danger; }
.mysql-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.mysql-fetched {
  font-size: 11px;
  color: $text-tertiary;
}
.ld-btn.sm {
  padding: 4px 8px;
}
.mysql-error {
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
.mysql-metrics {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}
.mysql-metric {
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
  white-space: nowrap;
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
.mysql-sub-title {
  font-size: 13px;
  font-weight: 600;
  color: $text-secondary;
  margin-bottom: 8px;
}
.mysql-table {
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
  .mysql-metrics {
    grid-template-columns: repeat(4, 1fr);
  }
}
</style>
