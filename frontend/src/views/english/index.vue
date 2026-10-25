<template>
  <div class="english-page">
    <!-- 顶部统计卡 -->
    <div class="stat-row">
      <div class="ld-card stat-cell">
        <div class="stat-label">任务总数</div>
        <div class="stat-value">{{ stat.total_tasks ?? 0 }}</div>
      </div>
      <div class="ld-card stat-cell">
        <div class="stat-label">今日已完成</div>
        <div class="stat-value text-positive">{{ stat.today_done ?? 0 }} / {{ stat.total_tasks ?? 0 }}</div>
      </div>
      <div class="ld-card stat-cell">
        <div class="stat-label">本周打卡</div>
        <div class="stat-value">{{ stat.week_completed ?? 0 }} 次</div>
      </div>
      <div class="ld-card stat-cell">
        <div class="stat-label">平均完成率</div>
        <div class="stat-value">{{ stat.avg_completion_rate ?? 0 }}%</div>
      </div>
    </div>

    <!-- 顶部搜索 + 操作 -->
    <div class="ld-card top-bar">
      <el-select v-model="filterCategory" placeholder="全部类别" clearable @change="load" class="filter">
        <el-option v-for="o in CATEGORY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-input
        v-model="keyword"
        class="search"
        placeholder="搜索任务名称…"
        clearable
        @input="onSearch"
      >
        <template #suffix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <button class="ld-btn ld-btn-primary" @click="openCreate">
        <el-icon><Plus /></el-icon> 新建任务
      </button>
    </div>

    <!-- 任务卡片列表 -->
    <div class="task-grid">
      <div v-for="t in tasks" :key="t.id" class="ld-card task-card">
        <div class="task-head">
          <span class="cat-badge" :class="`cat-${t.category}`">{{ categoryLabel(t.category) }}</span>
          <span class="task-name">{{ t.name }}</span>
          <div class="task-actions">
            <el-icon class="op-icon" @click="openEdit(t)"><Edit /></el-icon>
            <el-icon class="op-icon danger" @click="onDelete(t)"><Delete /></el-icon>
          </div>
        </div>
        <div class="task-goal" v-if="t.dailyGoal">每日目标：{{ t.dailyGoal }}</div>

        <div class="progress-row">
          <ProgressBar
            :label="`完成进度`"
            :percent="taskPercent(t)"
            color="#a78bfa"
          />
        </div>

        <div class="task-meta">
          <span>累计 <b>{{ t.totalCompleted ?? 0 }}</b> 次</span>
          <span>连续 <b class="text-positive">{{ t.streakDays ?? 0 }}</b> 天</span>
          <span>目标 {{ t.targetDays ?? 30 }} 天</span>
        </div>

        <div class="task-foot">
          <span class="last-done">
            {{ t.lastDoneDate ? `最近 ${t.lastDoneDate}` : "未打卡" }}
          </span>
          <button
            class="ld-btn ld-btn-primary punch"
            :disabled="isTodayDone(t) || punchingId === t.id"
            @click="onPunch(t)"
          >
            {{ isTodayDone(t) ? "今日已打卡" : "今日打卡" }}
          </button>
        </div>
      </div>
      <div v-if="tasks.length === 0" class="empty ld-card">暂无任务，点右上「新建任务」开始</div>
    </div>

    <!-- 新建/编辑弹窗 -->
    <div v-if="dialogVisible" class="modal-overlay" @click.self="dialogVisible = false">
      <div class="modal ld-card">
        <div class="modal-title">{{ form.id ? "编辑" : "新建" }}英语任务</div>
        <div class="modal-row">
          <label>任务名称</label>
          <el-input v-model="form.name" placeholder="如 每日单词 50" maxlength="128" />
        </div>
        <div class="modal-row">
          <label>类别</label>
          <el-select v-model="form.category" class="full-width">
            <el-option v-for="o in CATEGORY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </div>
        <div class="modal-row">
          <label>每日目标</label>
          <el-input v-model="form.dailyGoal" placeholder="50 个新词 + 复习" maxlength="128" />
        </div>
        <div class="modal-row">
          <label>目标天数</label>
          <el-input-number v-model="form.targetDays" :min="1" :max="365" />
        </div>
        <div class="modal-footer">
          <button class="ld-btn ld-btn-ghost" @click="dialogVisible = false">取消</button>
          <button class="ld-btn ld-btn-primary" :disabled="submitting" @click="onSubmit">
            {{ submitting ? "保存中…" : "保存" }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search, Plus, Edit, Delete } from "@element-plus/icons-vue";
import ProgressBar from "@/components/ProgressBar.vue";
import {
  fetchEnglishTasks,
  createEnglishTask,
  updateEnglishTask,
  deleteEnglishTask,
  completeEnglishTask,
  fetchEnglishStat,
  CATEGORY_OPTIONS,
  type EnglishTask
} from "@/api/english";

defineOptions({ name: "EnglishView" });

const keyword = ref("");
const filterCategory = ref("");
const tasks = ref<EnglishTask[]>([]);
const stat = ref<any>({});
const punchingId = ref<number | null>(null);

const dialogVisible = ref(false);
const submitting = ref(false);
const form = ref<EnglishTask>({
  name: "",
  category: "vocabulary",
  dailyGoal: "",
  targetDays: 30
});

let searchTimer: any = null;

async function load() {
  try {
    const [listRes, statRes] = await Promise.all([
      fetchEnglishTasks({
        category: filterCategory.value || undefined,
        keyword: keyword.value || undefined
      }),
      fetchEnglishStat()
    ]);
    tasks.value = (listRes as any).data || [];
    stat.value = (statRes as any).data || {};
  } catch {
    /* 拦截器已提示 */
  }
}

function onSearch() {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(load, 250);
}

function categoryLabel(c?: string) {
  const o = CATEGORY_OPTIONS.find((x) => x.value === c);
  return o ? o.label : "其他";
}

function taskPercent(t: EnglishTask) {
  if (!t.targetDays || t.targetDays <= 0) return 0;
  if (!t.totalCompleted) return 0;
  return Math.min(100, Math.round((t.totalCompleted * 100) / t.targetDays));
}

function isTodayDone(t: EnglishTask) {
  if (!t.lastDoneDate) return false;
  const today = new Date().toISOString().substring(0, 10);
  return t.lastDoneDate === today;
}

function openCreate() {
  form.value = { name: "", category: "vocabulary", dailyGoal: "", targetDays: 30 };
  dialogVisible.value = true;
}

function openEdit(t: EnglishTask) {
  form.value = { ...t };
  dialogVisible.value = true;
}

async function onSubmit() {
  if (!form.value.name.trim()) {
    ElMessage.warning("请填写任务名称");
    return;
  }
  submitting.value = true;
  try {
    if (form.value.id) {
      await updateEnglishTask(form.value.id, { ...form.value });
      ElMessage.success("已保存");
    } else {
      await createEnglishTask({ ...form.value });
      ElMessage.success("已创建");
    }
    dialogVisible.value = false;
    await load();
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false;
  }
}

async function onDelete(t: EnglishTask) {
  try {
    await ElMessageBox.confirm(`确定删除「${t.name}」？`, "提示", { type: "warning" });
    await deleteEnglishTask(t.id!);
    ElMessage.success("已删除");
    await load();
  } catch {
    /* 取消或拦截器已提示 */
  }
}

async function onPunch(t: EnglishTask) {
  punchingId.value = t.id!;
  try {
    await completeEnglishTask(t.id!);
    ElMessage.success("打卡成功");
    await load();
  } catch {
    /* 拦截器已提示 */
  } finally {
    punchingId.value = null;
  }
}

onMounted(load);
</script>

<style scoped lang="scss">
.english-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.stat-cell {
  padding: 14px 18px;
}
.stat-label {
  font-size: 12px;
  color: $text-tertiary;
  margin-bottom: 6px;
}
.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: $text-primary;
}

.top-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
}
.filter {
  width: 160px;
}
.search {
  flex: 1;
  max-width: 480px;
}

.task-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}
.task-card {
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.task-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.cat-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 8px;
  background: rgba(167, 139, 250, 0.15);
  color: $accent-light;
}
.cat-vocabulary { background: rgba(167, 139, 250, 0.18); }
.cat-listening  { background: rgba(45, 212, 191, 0.18); color: #2dd4bf; }
.cat-speaking   { background: rgba(251, 191, 36, 0.18); color: #fbbf24; }
.cat-reading    { background: rgba(96, 165, 250, 0.18); color: #60a5fa; }
.cat-writing    { background: rgba(248, 113, 113, 0.18); color: #f87171; }
.cat-grammar    { background: rgba(167, 139, 250, 0.18); color: $accent-light; }
.task-name {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.task-actions {
  display: flex;
  gap: 8px;
  opacity: 0;
  transition: opacity 0.18s;
  pointer-events: none;
}
.task-card:hover .task-actions {
  opacity: 1;
  pointer-events: auto;
}
.op-icon {
  cursor: pointer;
  color: $text-tertiary;
  font-size: 14px;
  &:hover { color: $accent-light; }
  &.danger:hover { color: #f87171; }
}
.task-goal {
  font-size: 12px;
  color: $text-secondary;
}
.task-meta {
  display: flex;
  gap: 18px;
  font-size: 12px;
  color: $text-secondary;
  b { color: $text-primary; font-weight: 600; }
}
.task-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 10px;
  border-top: 1px dashed $border-subtle;
}
.last-done {
  font-size: 12px;
  color: $text-tertiary;
}
.punch {
  padding: 5px 14px;
  font-size: 12px;
}

.empty {
  grid-column: 1 / -1;
  text-align: center;
  color: $text-tertiary;
  padding: 40px 0;
  font-size: 13px;
}

/* 弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(6px);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.modal {
  width: 460px;
  max-width: 100%;
  padding: 22px 24px;
}
.modal-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 18px;
}
.modal-row {
  margin-bottom: 14px;
  label {
    display: block;
    font-size: 12px;
    color: $text-tertiary;
    margin-bottom: 6px;
  }
}
.modal-row .full-width { width: 100%; }
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}
.ld-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid $border-glass;
  background: rgba(255, 255, 255, 0.04);
  color: $text-secondary;
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
  &:hover:not(:disabled) {
    background: rgba(167, 139, 250, 0.1);
    color: $text-primary;
  }
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}
.ld-btn-primary {
  background: linear-gradient(135deg, $accent, $accent-light);
  color: #fff;
  border: none;
  &:hover:not(:disabled) {
    box-shadow: 0 4px 14px rgba(167, 139, 250, 0.35);
    color: #fff;
  }
}
</style>
