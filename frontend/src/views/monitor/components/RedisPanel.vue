<template>
  <div class="ld-card redis-panel">
    <div class="redis-header">
      <div class="redis-title">
        <span class="redis-dot" :class="healthy ? 'dot-positive' : 'dot-danger'" />
        <span>Redis · 缓存</span>
        <el-tag v-if="healthy" size="small" type="success" effect="plain">运行正常</el-tag>
        <el-tag v-else size="small" type="danger" effect="plain">异常</el-tag>
      </div>
      <div class="redis-actions">
        <span class="redis-fetched">{{ addr }} · 更新于 {{ data.fetchedAt || '—' }}</span>
        <button class="ld-btn ld-btn-ghost sm" :disabled="loading" @click="load(true)">
          <el-icon><Refresh /></el-icon>
        </button>
      </div>
    </div>

    <!-- 采集失败提示 -->
    <div v-if="loaded && !healthy && !data.server" class="redis-error">
      <el-icon :size="18"><WarningFilled /></el-icon>
      <span>{{ data.errMsg || '无法连接 Redis' }}</span>
    </div>

    <template v-if="data.server">
      <!-- 总览指标 -->
      <div class="redis-metrics">
        <div class="redis-metric">
          <span class="mm-value">{{ s.version }}</span>
          <span class="mm-label">Redis 版本 · {{ s.role }}</span>
        </div>
        <div class="redis-metric">
          <span class="mm-value">{{ cli.connected }}<i>/{{ cli.maxClients }}</i></span>
          <span class="mm-label">连接数/上限</span>
        </div>
        <div class="redis-metric">
          <span class="mm-value">{{ st.opsPerSec }}</span>
          <span class="mm-label">OPS / 秒</span>
        </div>
        <div class="redis-metric">
          <span class="mm-value">{{ m.used }}</span>
          <span class="mm-label">内存使用 · 上限{{ m.max }}</span>
        </div>
        <div class="redis-metric" :class="{ warn: st.hitRate < 50 }">
          <span class="mm-value">{{ st.hitRate }}%</span>
          <span class="mm-label">缓存命中率</span>
        </div>
        <div class="redis-metric">
          <span class="mm-value">{{ num(data.totalKeys) }}</span>
          <span class="mm-label">总键数（db{{ dbKeys }}）</span>
        </div>
        <div class="redis-metric" :class="{ warn: st.rejectedConnections > 0 }">
          <span class="mm-value">{{ num(st.rejectedConnections) }}</span>
          <span class="mm-label">拒绝连接</span>
        </div>
        <div class="redis-metric" :class="{ warn: st.evictedKeys > 0 }">
          <span class="mm-value">{{ num(st.evictedKeys) }}</span>
          <span class="mm-label">淘汰键</span>
        </div>
      </div>

      <div class="redis-tables">
        <!-- 运行信息 -->
        <div class="redis-table-box">
          <div class="redis-sub-title">运行信息</div>
          <table class="redis-table">
            <thead>
              <tr>
                <th>指标</th>
                <th>值</th>
                <th>说明</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td class="strong">运行时长</td>
                <td>{{ s.uptimeDays }} 天</td>
                <td class="dim">自启动至今</td>
              </tr>
              <tr>
                <td class="strong">内存峰值</td>
                <td>{{ m.peak }}</td>
                <td class="dim">历史峰值占用</td>
              </tr>
              <tr>
                <td class="strong">内存碎片率</td>
                <td :class="{ 'warn-text': m.fragRatio > 1.5 }">{{ m.fragRatio }}</td>
                <td class="dim">>1.5 表示碎片偏高</td>
              </tr>
              <tr>
                <td class="strong">阻塞客户端</td>
                <td>{{ cli.blocked }}</td>
                <td class="dim">BLPOP 等阻塞等待数</td>
              </tr>
              <tr>
                <td class="strong">累计命令数</td>
                <td>{{ num(st.totalCommands) }}</td>
                <td class="dim">启动以来处理总量</td>
              </tr>
              <tr>
                <td class="strong">命中 / 未命中</td>
                <td>{{ num(st.hits) }} / {{ num(st.misses) }}</td>
                <td class="dim">keyspace hits / misses</td>
              </tr>
              <tr>
                <td class="strong">过期键</td>
                <td>{{ num(st.expiredKeys) }}</td>
                <td class="dim">累计自动过期删除</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Database 明细 -->
        <div class="redis-table-box">
          <div class="redis-sub-title">Database 明细（db{{ dbKeys }}）</div>
          <table class="redis-table">
            <thead>
              <tr>
                <th>Database</th>
                <th>键数量</th>
                <th>设置过期</th>
                <th>平均 TTL</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="d in data.databases" :key="d.db">
                <td class="strong">db{{ d.db }}</td>
                <td>{{ num(d.keys) }}</td>
                <td>{{ num(d.expires) }}</td>
                <td>{{ d.avgTtl || '—' }}</td>
              </tr>
              <tr v-if="!data.databases?.length">
                <td colspan="4" class="empty">无数据</td>
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
import { fetchRedisOverview, refreshRedisOverview } from "@/api/extMonitor";

const props = defineProps<{ addr?: string }>();

const emit = defineEmits<{
  (e: "status", payload: { running: boolean; metric: string; sub: string }): void;
}>();

defineOptions({ name: "RedisPanel" });

const data = ref<any>({});
const loading = ref(false);
const loaded = ref(false);
let timer: number | null = null;

const s = computed(() => data.value.server || {});
const cli = computed(() => data.value.clients || {});
const m = computed(() => data.value.memory || {});
const st = computed(() => data.value.stats || {});
const healthy = computed(() => !!data.value.ok);
const dbKeys = computed(() => (data.value.databases || []).map((d: any) => d.db).join("/"));

function num(v: any): string {
  const n = Number(v) || 0;
  return n.toLocaleString("en-US");
}

async function load(force = false) {
  loading.value = true;
  try {
    const r = force ? await refreshRedisOverview() : await fetchRedisOverview();
    data.value = r.data || {};
    loaded.value = true;
    const ok = healthy.value;
    emit("status", {
      running: ok,
      metric: ok ? `${st.value.opsPerSec} ops/s` : "—",
      sub: ok
        ? `运行中 · v${s.value.version} · ${num(data.value.totalKeys)} keys`
        : `异常 · ${data.value.errMsg || "连接失败"}`
    });
  } catch {
    emit("status", { running: false, metric: "—", sub: "异常 · 请求失败" });
  } finally {
    loading.value = false;
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
.redis-panel {
  margin-bottom: 18px;
}
.redis-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.redis-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
}
.redis-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.dot-positive { background: $color-positive; }
.dot-danger { background: $color-danger; }
.redis-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.redis-fetched {
  font-size: 11px;
  color: $text-tertiary;
}
.ld-btn.sm {
  padding: 4px 8px;
}
.redis-error {
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
.redis-metrics {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}
.redis-metric {
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
.redis-tables {
  display: grid;
  grid-template-columns: 3fr 2fr;
  gap: 14px;
}
.redis-sub-title {
  font-size: 13px;
  font-weight: 600;
  color: $text-secondary;
  margin-bottom: 8px;
}
.redis-table {
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
    &.strong {
      color: $text-primary;
      font-weight: 500;
    }
    &.dim {
      color: $text-tertiary;
    }
    &.warn-text {
      color: $color-danger;
      font-weight: 600;
    }
  }
  tr:last-child td {
    border-bottom: none;
  }
}
.empty {
  text-align: center;
  color: $text-tertiary;
  padding: 18px 0 !important;
}
@media (max-width: 1400px) {
  .redis-metrics {
    grid-template-columns: repeat(4, 1fr);
  }
  .redis-tables {
    grid-template-columns: 1fr;
  }
}
</style>
