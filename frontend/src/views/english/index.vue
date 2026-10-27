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

    <!-- 看板：每日打卡趋势 + 目标里程碑（修改每日目标 = 新里程碑 = 进入下一阶段） -->
    <div class="ld-card board-card">
      <div class="board-head">
        <div class="board-title">
          30 天打卡趋势
          <span v-if="board.stageCount" class="stage-badge">第 {{ board.stageCount }} 阶段</span>
        </div>
        <span class="board-tip">虚线竖线为每日目标变更里程碑（进入下一阶段的分割点）</span>
      </div>
      <VChart :option="boardOption" autoresize style="height: 260px" />
      <!-- 目标里程碑时间线 -->
      <div v-if="board.milestones.length" class="ms-block">
        <div class="ms-title">目标里程碑（{{ board.stageCount || 1 }} 个阶段）</div>
        <div class="ms-list">
          <div v-for="(st, i) in boardStages" :key="i" class="ms-row">
            <span class="ms-stage">阶段 {{ i + 1 }}</span>
            <span class="ms-date">{{ st.date }} 起</span>
            <span class="ms-items">{{ st.text }}</span>
          </div>
        </div>
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
        <div class="task-goal" v-if="t.dailyGoal">
          每日目标：{{ t.dailyGoal }}
          <span v-if="taskStage(t)" class="task-stage">第 {{ taskStage(t).no }} 阶段 · {{ taskStage(t).since }} 起</span>
        </div>

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
          <label>每日目标 <span class="goal-hint">（修改并保存将作为新里程碑，进入下一阶段）</span></label>
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
import { ref, computed, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search, Plus, Edit, Delete } from "@element-plus/icons-vue";
import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { BarChart, LineChart } from "echarts/charts";
import { GridComponent, TooltipComponent, LegendComponent, MarkLineComponent } from "echarts/components";
import ProgressBar from "@/components/ProgressBar.vue";
import {
  fetchEnglishTasks,
  createEnglishTask,
  updateEnglishTask,
  deleteEnglishTask,
  completeEnglishTask,
  fetchEnglishStat,
  fetchEnglishBoard,
  CATEGORY_OPTIONS,
  type EnglishTask,
  type GoalMilestone
} from "@/api/english";

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, MarkLineComponent]);

defineOptions({ name: "EnglishView" });

const keyword = ref("");
const filterCategory = ref("");
const tasks = ref<EnglishTask[]>([]);
const stat = ref<any>({});
const punchingId = ref<number | null>(null);

// ===== 看板：每日打卡趋势 + 每日目标里程碑 =====
const board = ref<{ days: any[]; milestones: GoalMilestone[]; stageCount?: number }>({
  days: [],
  milestones: []
});

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
    const [listRes, statRes, boardRes] = await Promise.all([
      fetchEnglishTasks({
        category: filterCategory.value || undefined,
        keyword: keyword.value || undefined
      }),
      fetchEnglishStat(),
      fetchEnglishBoard(30)
    ]);
    tasks.value = (listRes as any).data || [];
    stat.value = (statRes as any).data || {};
    board.value = (boardRes as any).data || { days: [], milestones: [] };
  } catch {
    /* 拦截器已提示 */
  }
}

/** 里程碑按阶段（生效日期）分组，同一天的多个任务目标变更合并为一个阶段 */
const boardStages = computed(() => {
  const byDate = new Map<string, string[]>();
  for (const ms of board.value.milestones || []) {
    const date = (ms.startDate || "").substring(5) || "?";
    const text = `${ms.label || "任务"}：${ms.goalValue}`;
    if (!byDate.has(date)) byDate.set(date, []);
    byDate.get(date)!.push(text);
  }
  return Array.from(byDate.entries()).map(([date, items]) => ({ date, text: items.join(" · ") }));
});

/** 任务当前阶段：该任务目标变更记录数（即第 N 次目标 = 第 N 阶段），及本阶段起始日 */
function taskStage(t: EnglishTask): { no: number; since: string } | null {
  const ms = (board.value.milestones || []).filter((m) => m.scope === String(t.id));
  if (!ms.length) return null;
  return {
    no: ms.length,
    since: (ms[ms.length - 1].startDate || "").substring(5)
  };
}

/** 30 天趋势图：柱=每日打卡数，线=完成率%，里程碑竖线标记阶段分割点 */
const boardOption = computed(() => {
  const days = board.value.days || [];
  const x = days.map((d: any) => d.date);
  // 里程碑竖线（与阶段编号对应）
  const msLines: any[] = [];
  (boardStages.value || []).forEach((st, i) => {
    if (x.includes(st.date)) {
      msLines.push({
        xAxis: st.date,
        lineStyle: { color: "#c4b5fd", type: "dashed", width: 1.5 },
        label: { formatter: `阶段${i + 1}`, color: "#c4b5fd", fontSize: 10, position: "insideEndTop" }
      });
    }
  });
  return {
    tooltip: { trigger: "axis" },
    legend: { data: ["打卡数", "完成率%"], textStyle: { color: "#a8a0c8" } },
    grid: { left: 40, right: 46, top: 36, bottom: 28 },
    xAxis: { type: "category", data: x, axisLabel: { color: "#a8a0c8", fontSize: 10 } },
    yAxis: [
      { type: "value", name: "打卡", minInterval: 1, axisLabel: { color: "#a8a0c8", fontSize: 10 }, nameTextStyle: { color: "#a8a0c8" } },
      { type: "value", name: "%", max: 100, axisLabel: { color: "#a8a0c8", fontSize: 10 }, nameTextStyle: { color: "#a8a0c8" } }
    ],
    series: [
      {
        name: "打卡数",
        type: "bar",
        data: days.map((d: any) => d.done),
        itemStyle: { color: "#a78bfa", borderRadius: [3, 3, 0, 0] },
        barMaxWidth: 16,
        markLine: {
          symbol: "none",
          silent: true,
          data: msLines
        }
      },
      {
        name: "完成率%",
        type: "line",
        yAxisIndex: 1,
        smooth: true,
        data: days.map((d: any) => d.rate),
        lineStyle: { color: "#2dd4bf", width: 2 },
        itemStyle: { color: "#2dd4bf" }
      }
    ]
  };
});

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

/* ===== 看板：打卡趋势 + 目标里程碑 ===== */
.board-card {
  padding: 16px 18px;
}
.board-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 6px;
}
.board-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
}
.stage-badge {
  font-size: 11px;
  font-weight: 500;
  color: $accent-light;
  background: rgba(167, 139, 250, 0.15);
  border: 1px solid $border-accent;
  border-radius: 10px;
  padding: 2px 10px;
  margin-left: 8px;
  vertical-align: middle;
}
.board-tip {
  font-size: 11px;
  color: $text-tertiary;
}
.ms-block {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed $border-subtle;
}
.ms-title {
  font-size: 12px;
  font-weight: 600;
  color: $text-secondary;
  margin-bottom: 6px;
}
.ms-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ms-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 12px;
  color: $text-secondary;
}
.ms-stage {
  color: $accent-light;
  font-weight: 600;
  white-space: nowrap;
}
.ms-date {
  color: $text-tertiary;
  white-space: nowrap;
}
.ms-items {
  color: $text-secondary;
}
.task-stage {
  font-size: 11px;
  color: $accent-light;
  background: rgba(167, 139, 250, 0.12);
  border-radius: 8px;
  padding: 1px 8px;
  margin-left: 8px;
}
.goal-hint {
  font-weight: 400;
  color: $text-tertiary;
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
