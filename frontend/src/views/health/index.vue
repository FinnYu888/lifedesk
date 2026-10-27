<template>
  <div class="health-page">
    <!-- 顶部 3 张统计卡 -->
    <div class="stat-row">
      <StatCard label="周完成率" :value="(stat.week_rate ?? 82) + '%'" :note="stat.week_rate_delta || '+6%'" noteType="positive" />
      <StatCard label="今日步数" :value="(stat.steps_today ?? 0).toLocaleString()" :note="stepsPercent + '% 完成'" noteType="neutral" />
      <StatCard label="体重" :value="(stat.weight ?? 65.2)" :note="'↓ ' + Math.abs(stat.weight_delta ?? -0.6) + 'kg 本周'" noteType="positive" />
    </div>

    <!-- 每日目标设置：修改任意目标 = 生成新里程碑，进入下一阶段 -->
    <div class="ld-card goal-card">
      <div class="card-head">
        <div class="card-title">每日目标
          <span class="goal-stage-badge">第 {{ goalStage }} 阶段</span>
        </div>
        <span class="goal-tip">修改目标并保存即生成新里程碑（进入下一阶段），看板按各阶段目标计算达成率</span>
      </div>
      <div class="goal-grid">
        <div v-for="m in HEALTH_METRICS" :key="m.key" class="goal-item">
          <div class="gi-head">
            <span class="gi-label">{{ m.label }}</span>
            <span class="gi-actual" :style="{ color: m.color }">{{ actualOf(m.key) }}</span>
          </div>
          <ProgressBar :label="`目标 ${goalOf(m.key)} ${m.unit}`" :percent="percentOf(m.key)" :color="m.color" />
          <div class="gi-edit">
            <el-input-number
              v-model="goalForm[m.key]"
              :min="m.min"
              :max="m.max"
              :step="m.step"
              size="small"
              :controls="false"
              :placeholder="String(m.default)"
            />
            <span class="gi-unit">{{ m.unit }}</span>
            <el-button
              size="small"
              type="primary"
              :loading="goalSavingKey === m.key"
              :disabled="goalForm[m.key] == null || goalForm[m.key] === goalOf(m.key)"
              @click="onSaveGoal(m)"
            >设为目标</el-button>
          </div>
        </div>
      </div>
      <!-- 里程碑时间线：每日目标变更历史 -->
      <div v-if="goalMilestones.length" class="goal-ms">
        <div class="goal-ms-title">目标里程碑（{{ goalStage }} 个阶段）</div>
        <div class="goal-ms-list">
          <div v-for="(ms, i) in goalMilestoneStages" :key="i" class="goal-ms-row">
            <span class="gms-stage">阶段 {{ i + 1 }}</span>
            <span class="gms-date">{{ ms.date }} 起</span>
            <span class="gms-items">{{ ms.text }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 今日卡路里平衡大卡 -->
    <div class="ld-card balance-card">
      <div class="card-title">今日卡路里平衡</div>
      <div class="balance-row">
        <div class="balance-col">
          <div class="bal-label">摄入</div>
          <div class="bal-val intake">{{ summary.intake_today ?? 0 }} <span class="unit">kcal</span></div>
          <ProgressBar :label="`目标 ${goalOf('intake')} kcal`" :percent="Math.min(100, Math.round(((summary.intake_today ?? 0) / goalOf('intake')) * 100))" color="#fbbf24" />
        </div>
        <div class="balance-divider">−</div>
        <div class="balance-col">
          <div class="bal-label">消耗</div>
          <div class="bal-val burn">{{ summary.burn_today ?? 0 }} <span class="unit">kcal</span></div>
          <ProgressBar :label="`目标 ${goalOf('burn')} kcal`" :percent="Math.min(100, Math.round(((summary.burn_today ?? 0) / goalOf('burn')) * 100))" color="#2dd4bf" />
        </div>
        <div class="balance-divider">=</div>
        <div class="balance-col">
          <div class="bal-label">净值</div>
          <div class="bal-val" :class="netClass">{{ netSign }}{{ Math.abs(summary.net ?? 0) }} <span class="unit">kcal</span></div>
          <div class="bal-comment">{{ netComment }}</div>
        </div>
      </div>
    </div>

    <div class="health-grid">
      <!-- 1. 今日运动记录（可填写） -->
      <div class="ld-card">
        <div class="card-head">
          <div class="card-title">今日运动记录</div>
          <el-button type="primary" size="small" :icon="Plus" @click="openWorkoutDialog">添加运动</el-button>
        </div>
        <div class="ring-flex">
          <RingChart :percent="weekWorkoutPercent" :size="100" />
        </div>
        <div class="record-list">
          <div v-if="!summary.workouts_today?.length" class="empty">今天还没记录运动，点右上角添加</div>
          <div v-for="w in summary.workouts_today" :key="w.id" class="record-row">
            <div class="rec-main">
              <span class="rec-name">{{ w.exerciseName }}</span>
              <span class="rec-meta">
                {{ w.sets ? w.sets + '组 × ' + (w.reps || 0) + '次' : '' }}
                {{ w.weightKg ? ' · ' + w.weightKg + 'kg' : '' }}
                {{ w.durationMin ? ' · ' + w.durationMin + 'min' : '' }}
              </span>
            </div>
            <div class="rec-right">
              <span class="rec-burn">−{{ w.caloriesBurned }} kcal</span>
              <el-icon class="del-icon" @click="onDelWorkout(w.id)"><Close /></el-icon>
            </div>
          </div>
        </div>
      </div>

      <!-- 2. 今日饮食记录（可填写，按餐次分组） -->
      <div class="ld-card">
        <div class="card-head">
          <div class="card-title">今日饮食记录</div>
          <el-button type="primary" size="small" :icon="Plus" @click="openDietDialog">添加食物</el-button>
        </div>
        <div v-for="m in meals" :key="m.key" class="meal-group">
          <div class="meal-title">
            <span>{{ m.label }}</span>
            <span class="meal-sum">{{ mealSum(m.key) }} kcal</span>
          </div>
          <div v-if="!mealList(m.key).length" class="empty small">无</div>
          <div v-for="d in mealList(m.key)" :key="d.id" class="record-row">
            <div class="rec-main">
              <span class="rec-name">{{ d.foodName }}</span>
              <span class="rec-meta">{{ d.amountG }} {{ foodUnit(d.foodName) }}</span>
            </div>
            <div class="rec-right">
              <span class="rec-intake">+{{ d.caloriesIntake }} kcal</span>
              <el-icon class="del-icon" @click="onDelDiet(d.id)"><Close /></el-icon>
            </div>
          </div>
        </div>
      </div>

      <!-- 3. 7 天卡路里平衡趋势 -->
      <div class="ld-card">
        <div class="card-title">7 天卡路里平衡趋势</div>
        <VChart :option="trendOption" autoresize style="height: 240px" />
      </div>

      <!-- 4. 体重·体脂 -->
      <div class="ld-card">
        <div class="card-title">体重·体脂变化趋势</div>
        <VChart :option="weightOption" autoresize style="height: 220px" />
      </div>

      <!-- 5. 运动计划达成率 -->
      <div class="ld-card">
        <div class="card-title">运动计划达成率</div>
        <ProgressBar
          v-for="e in exercises"
          :key="e.id"
          :label="e.category"
          :percent="e.rate ?? 0"
          :color="progressColor(e.rate ?? 0)"
        />
      </div>

      <!-- 6. 心率/睡眠/水分 -->
      <div class="ld-card">
        <div class="card-title">心率·睡眠·水分</div>
        <div class="vital-block">
          <div class="vital-row">
            <span class="vital-label">心率</span>
            <span class="vital-val">{{ vital?.heartRate ?? 88 }} bpm</span>
          </div>
          <div class="vital-row">
            <span class="vital-label">睡眠</span>
            <span class="vital-val">{{ vital?.sleepHours ?? 7.5 }} h</span>
          </div>
          <ProgressBar label="睡眠目标" :percent="Math.round(((vital?.sleepHours ?? 0) / goalOf('sleep')) * 100)" color="#a78bfa" />
          <div class="vital-row">
            <span class="vital-label">水分</span>
            <span class="vital-val">{{ ((vital?.waterMl ?? 0) / 1000).toFixed(1) }} / {{ (goalOf('water') / 1000).toFixed(1) }} L</span>
          </div>
          <ProgressBar label="水分摄入" :percent="Math.round(((vital?.waterMl ?? 0) / goalOf('water')) * 100)" color="#2dd4bf" />
        </div>
      </div>
    </div>

    <!-- 添加运动弹窗 -->
    <el-dialog v-model="workoutDialog" title="添加运动记录" width="460px" :close-on-click-modal="false">
      <el-form label-width="84px" label-position="left">
        <el-form-item label="动作">
          <el-select v-model="workoutForm.exerciseId" placeholder="选择运动动作" filterable style="width: 100%" @change="onWorkoutPick">
            <el-option-group v-for="g in exerciseDictByPart" :key="g.part" :label="g.part">
              <el-option v-for="d in g.list" :key="d.id" :label="d.name" :value="d.id" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="组数">
          <el-input-number v-model="workoutForm.sets" :min="0" :max="20" :step="1" />
        </el-form-item>
        <el-form-item label="每组次数">
          <el-input-number v-model="workoutForm.reps" :min="0" :max="50" :step="1" />
        </el-form-item>
        <el-form-item label="重量 kg">
          <el-input-number v-model="workoutForm.weightKg" :min="0" :max="300" :step="2.5" :precision="1" />
        </el-form-item>
        <el-form-item label="时长 min">
          <el-input-number v-model="workoutForm.durationMin" :min="0" :max="240" :step="5" />
        </el-form-item>
        <el-form-item label="预计消耗">
          <div class="preview-kcal burn">−{{ workoutPreviewKcal }} kcal</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="workoutDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitWorkout">提交</el-button>
      </template>
    </el-dialog>

    <!-- 添加饮食弹窗 -->
    <el-dialog v-model="dietDialog" title="添加饮食记录" width="460px" :close-on-click-modal="false">
      <el-form label-width="84px" label-position="left">
        <el-form-item label="食物">
          <el-select v-model="dietForm.foodId" placeholder="选择食物" filterable style="width: 100%" @change="onDietPick">
            <el-option-group v-for="g in foodDictByCategory" :key="g.category" :label="g.category">
              <el-option v-for="f in g.list" :key="f.id" :label="f.name + ' (' + f.kcal_per_100g + 'kcal/100g)'" :value="f.id" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="餐次">
          <el-radio-group v-model="dietForm.meal">
            <el-radio-button label="breakfast">早餐</el-radio-button>
            <el-radio-button label="lunch">午餐</el-radio-button>
            <el-radio-button label="dinner">晚餐</el-radio-button>
            <el-radio-button label="snack">加餐</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="摄入量">
          <el-input-number v-model="dietForm.amountG" :min="1" :max="2000" :step="10" :precision="1" />
          <span class="ml8 text-tertiary">{{ dietForm.unit }}</span>
        </el-form-item>
        <el-form-item label="预计摄入">
          <div class="preview-kcal intake">+{{ dietPreviewKcal }} kcal</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dietDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitDiet">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Close } from "@element-plus/icons-vue";
import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { BarChart, LineChart } from "echarts/charts";
import { GridComponent, TooltipComponent, LegendComponent, MarkLineComponent } from "echarts/components";
import StatCard from "@/components/StatCard.vue";
import RingChart from "@/components/RingChart.vue";
import ProgressBar from "@/components/ProgressBar.vue";
import {
  fetchHealthOverview,
  fetchExerciseDict,
  fetchFoodDict,
  addWorkout,
  addDiet,
  deleteWorkout,
  deleteDiet,
} from "@/api/health";
import { fetchDailyGoals, saveDailyGoal, type DailyGoal } from "@/api/dailyGoal";

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, MarkLineComponent]);

defineOptions({ name: "HealthView" });

// ===== 状态 =====
const stat = ref<any>({});
const summary = ref<any>({ workouts_today: [], diet_today: {} });
const trend = ref<any[]>([]);
const weight = ref<any[]>([]);
const exercises = ref<any[]>([]);
const vital = ref<any | null>(null);
const exerciseDict = ref<any[]>([]);
const foodDict = ref<any[]>([]);

const meals = [
  { key: "breakfast", label: "早餐" },
  { key: "lunch", label: "午餐" },
  { key: "dinner", label: "晚餐" },
  { key: "snack", label: "加餐" },
];

// 运动字典按部位分组
const exerciseDictByPart = computed(() => groupBy(exerciseDict.value, "part"));
// 食物字典按类别分组
const foodDictByCategory = computed(() => groupBy(foodDict.value, "category"));

function groupBy(list: any[], key: string) {
  const map: Record<string, any[]> = {};
  for (const it of list) {
    const k = it[key];
    if (!map[k]) map[k] = [];
    map[k].push(it);
  }
  return Object.entries(map).map(([k, v]) => ({ [key]: k, list: v } as any));
}

// 本周完成率（pending/done 简单算）
const weekWorkoutPercent = computed(() => {
  const ws = summary.value?.workouts_today || [];
  if (!ws.length) return 0;
  const done = ws.filter((w: any) => w.status === "done").length;
  return Math.round((done / ws.length) * 100);
});

// 净值颜色与文案
const netClass = computed(() => {
  const n = summary.value.net ?? 0;
  return n > 0 ? "intake" : "burn";
});
const netSign = computed(() => ((summary.value.net ?? 0) > 0 ? "+" : ""));
const netComment = computed(() => {
  const n = summary.value.net ?? 0;
  if (n > 300) return "今日摄入超标，建议增加运动或减少加餐";
  if (n > 0) return "略有盈余，注意控制";
  if (n === 0) return "收支平衡";
  return "热量缺口，利于减脂";
});

function mealList(key: string) {
  return summary.value?.diet_today?.[key] || [];
}
function mealSum(key: string) {
  return mealList(key).reduce((s: number, d: any) => s + (d.caloriesIntake || 0), 0);
}
function foodUnit(name: string) {
  const f = foodDict.value.find((x: any) => x.name === name);
  return f?.default_unit || "g";
}

// ===== 每日目标（修改 = 新里程碑，进入下一阶段） =====
interface MetricDef {
  key: string;
  label: string;
  unit: string;
  default: number;
  min: number;
  max: number;
  step: number;
  color: string;
}

const HEALTH_METRICS: MetricDef[] = [
  { key: "steps", label: "步数", unit: "步", default: 10000, min: 1000, max: 100000, step: 500, color: "#a78bfa" },
  { key: "intake", label: "摄入", unit: "kcal", default: 1800, min: 800, max: 6000, step: 50, color: "#fbbf24" },
  { key: "burn", label: "消耗", unit: "kcal", default: 2200, min: 500, max: 8000, step: 50, color: "#2dd4bf" },
  { key: "sleep", label: "睡眠", unit: "h", default: 8, min: 4, max: 14, step: 0.5, color: "#a78bfa" },
  { key: "water", label: "水分", unit: "ml", default: 2500, min: 500, max: 8000, step: 100, color: "#2dd4bf" }
];

const goalBoard = ref<{ current: DailyGoal[]; milestones: DailyGoal[]; stageCount?: number }>({
  current: [],
  milestones: []
});
const goalForm = reactive<Record<string, number | null>>({});
const goalSavingKey = ref<string | null>(null);

/** 某指标当前生效目标（未设置用默认值） */
function goalOf(key: string): number {
  const def = HEALTH_METRICS.find((m) => m.key === key)!;
  const cur = goalBoard.value.current.find((g) => g.scope === key);
  const v = cur ? parseFloat(cur.goalValue) : NaN;
  return isNaN(v) ? def.default : v;
}

/** 某指标今日实际值（来自当日完成记录） */
function actualOf(key: string): string {
  const def = HEALTH_METRICS.find((m) => m.key === key)!;
  let v = 0;
  switch (key) {
    case "steps": v = stat.value.steps_today ?? 0; break;
    case "intake": v = summary.value.intake_today ?? 0; break;
    case "burn": v = summary.value.burn_today ?? 0; break;
    case "sleep": v = vital.value?.sleepHours ?? 0; break;
    case "water": v = vital.value?.waterMl ?? 0; break;
  }
  return `${v} ${def.unit}`;
}

function percentOf(key: string): number {
  const g = goalOf(key);
  if (g <= 0) return 0;
  const def = HEALTH_METRICS.find((m) => m.key === key)!;
  let v = 0;
  switch (key) {
    case "steps": v = stat.value.steps_today ?? 0; break;
    case "intake": v = summary.value.intake_today ?? 0; break;
    case "burn": v = summary.value.burn_today ?? 0; break;
    case "sleep": v = vital.value?.sleepHours ?? 0; break;
    case "water": v = vital.value?.waterMl ?? 0; break;
  }
  return Math.min(100, Math.round((v / g) * 100));
}

const stepsPercent = computed(() => percentOf("steps"));

/** 阶段数（后端按目标变更日期去重；兜底前端算） */
const goalStage = computed(() => {
  if (goalBoard.value.stageCount) return goalBoard.value.stageCount;
  return goalMilestoneStages.value.length || 1;
});

/** 里程碑按阶段（生效日期）分组：同一天的多个目标变更合并为一个阶段 */
const goalMilestoneStages = computed(() => {
  const byDate = new Map<string, string[]>();
  for (const ms of goalBoard.value.milestones) {
    const date = (ms.startDate || "").substring(5) || "?";
    const def = HEALTH_METRICS.find((m) => m.key === ms.scope);
    const label = def ? `${def.label} ${ms.goalValue}${def.unit}` : `${ms.label || ms.scope}：${ms.goalValue}`;
    if (!byDate.has(date)) byDate.set(date, []);
    byDate.get(date)!.push(label);
  }
  return Array.from(byDate.entries()).map(([date, items]) => ({ date, text: items.join(" · ") }));
});

async function loadGoals() {
  try {
    const r: any = await fetchDailyGoals("health");
    goalBoard.value = r.data || { current: [], milestones: [] };
    // 目标输入框预填当前生效值
    for (const m of HEALTH_METRICS) {
      goalForm[m.key] = goalOf(m.key);
    }
  } catch {
    /* 拦截器已提示 */
  }
}

async function onSaveGoal(m: MetricDef) {
  const v = goalForm[m.key];
  if (v == null) return;
  goalSavingKey.value = m.key;
  try {
    await saveDailyGoal({
      module: "health",
      scope: m.key,
      label: m.label,
      goalValue: String(v),
      unit: m.unit
    });
    ElMessage.success(`已设为新的每日${m.label}目标，进入下一阶段`);
    await loadGoals();
  } catch {
    /* 拦截器已提示 */
  } finally {
    goalSavingKey.value = null;
  }
}

// ===== 趋势图 =====
const trendOption = computed(() => {
  const x = trend.value.map((t: any) => t.date);
  // 里程碑竖线：目标变更日期落在 7 天窗口内的（摄入/消耗目标），去重后标记为阶段分割点
  const msLines: any[] = [];
  const seenDate = new Set<string>();
  let stageNo = 0;
  const stageDates: string[] = [];
  for (const ms of goalBoard.value.milestones) {
    if (ms.scope !== "intake" && ms.scope !== "burn") continue;
    const d = (ms.startDate || "").substring(5);
    if (!d || seenDate.has(d)) continue;
    seenDate.add(d);
    stageDates.push(d);
  }
  stageDates.sort();
  for (const d of stageDates) {
    stageNo += 1;
    if (x.includes(d)) {
      msLines.push({
        xAxis: d,
        lineStyle: { color: "#c4b5fd", type: "dashed", width: 1.5 },
        label: { formatter: `阶段${stageNo}`, color: "#c4b5fd", fontSize: 10, position: "insideEndTop" }
      });
    }
  }
  return {
    tooltip: { trigger: "axis" },
    legend: { data: ["摄入", "消耗", "净值"], textStyle: { color: "#a8a0c8" } },
    grid: { left: 40, right: 16, top: 36, bottom: 28 },
    xAxis: { type: "category", data: x, axisLabel: { color: "#a8a0c8", fontSize: 10 } },
    yAxis: { type: "value", axisLabel: { color: "#a8a0c8", fontSize: 10 } },
    series: [
      {
        name: "摄入",
        type: "bar",
        data: trend.value.map((t: any) => t.intake),
        itemStyle: { color: "#fbbf24" },
        markLine: {
          symbol: "none",
          silent: true,
          data: [
            {
              yAxis: goalOf("intake"),
              lineStyle: { color: "#fbbf24", type: "dashed", width: 1 },
              label: { formatter: `摄入目标 ${goalOf("intake")}`, color: "#fbbf24", fontSize: 10, position: "insideStartTop" }
            },
            ...msLines
          ]
        }
      },
      {
        name: "消耗",
        type: "bar",
        data: trend.value.map((t: any) => t.burn),
        itemStyle: { color: "#2dd4bf" },
        markLine: {
          symbol: "none",
          silent: true,
          data: [
            {
              yAxis: goalOf("burn"),
              lineStyle: { color: "#2dd4bf", type: "dashed", width: 1 },
              label: { formatter: `消耗目标 ${goalOf("burn")}`, color: "#2dd4bf", fontSize: 10, position: "insideEndTop" }
            }
          ]
        }
      },
      {
        name: "净值",
        type: "line",
        smooth: true,
        data: trend.value.map((t: any) => t.net),
        lineStyle: { color: "#a78bfa", width: 2 },
        itemStyle: { color: "#a78bfa" }
      }
    ]
  };
});

const weightOption = computed(() => {
  const x = weight.value.map((w: any) => w.recordDate?.substring(5) || "");
  return {
    tooltip: { trigger: "axis" },
    legend: { data: ["体重", "体脂"], textStyle: { color: "#a8a0c8" } },
    grid: { left: 36, right: 36, top: 30, bottom: 26 },
    xAxis: { type: "category", data: x, axisLabel: { color: "#a8a0c8", fontSize: 10 } },
    yAxis: [
      { type: "value", name: "kg", min: 64, max: 67, axisLabel: { color: "#a8a0c8", fontSize: 10 } },
      { type: "value", name: "%", min: 17, max: 19, axisLabel: { color: "#a8a0c8", fontSize: 10 } },
    ],
    series: [
      { name: "体重", type: "line", smooth: true, data: weight.value.map((w: any) => w.weight), lineStyle: { color: "#a78bfa", width: 2 }, itemStyle: { color: "#a78bfa" } },
      { name: "体脂", type: "line", smooth: true, yAxisIndex: 1, data: weight.value.map((w: any) => w.fatRate), lineStyle: { color: "#2dd4bf", width: 2 }, itemStyle: { color: "#2dd4bf" } },
    ],
  };
});

function progressColor(rate: number) {
  if (rate >= 80) return "#a78bfa";
  if (rate >= 60) return "#a78bfa";
  return "#fbbf24";
}

// ===== 弹窗：添加运动 =====
const workoutDialog = ref(false);
const submitting = ref(false);
const workoutForm = reactive<{ exerciseId: number | null; sets: number; reps: number; weightKg: number; durationMin: number; met: number; kcalPerSet: number }>({
  exerciseId: null,
  sets: 0,
  reps: 0,
  weightKg: 0,
  durationMin: 0,
  met: 5,
  kcalPerSet: 8,
});

function openWorkoutDialog() {
  workoutForm.exerciseId = null;
  workoutForm.sets = 0;
  workoutForm.reps = 0;
  workoutForm.weightKg = 0;
  workoutForm.durationMin = 0;
  workoutForm.met = 5;
  workoutForm.kcalPerSet = 8;
  workoutDialog.value = true;
}

function onWorkoutPick(id: number) {
  const d = exerciseDict.value.find((x: any) => x.id === id);
  if (d) {
    workoutForm.met = d.met || 5;
    workoutForm.kcalPerSet = d.kcal_per_set || 8;
    if (d.part === "有氧") {
      workoutForm.durationMin = workoutForm.durationMin || 30;
    } else {
      workoutForm.sets = workoutForm.sets || 4;
      workoutForm.reps = workoutForm.reps || 10;
    }
  }
}

// 预计消耗：与后端一致
const workoutPreviewKcal = computed(() => {
  if (workoutForm.durationMin > 0 && workoutForm.met > 0) {
    const w = workoutForm.weightKg > 0 ? workoutForm.weightKg : (stat.value.weight ?? 65);
    return Math.round(workoutForm.met * w * (workoutForm.durationMin / 60) * 10) / 10;
  }
  if (workoutForm.sets > 0 && workoutForm.kcalPerSet > 0) {
    return Math.round(workoutForm.kcalPerSet * workoutForm.sets * 10) / 10;
  }
  if (workoutForm.durationMin > 0) return workoutForm.durationMin * 6;
  return 0;
});

async function submitWorkout() {
  if (!workoutForm.exerciseId) {
    ElMessage.warning("请选择运动动作");
    return;
  }
  submitting.value = true;
  try {
    await addWorkout({
      exerciseId: workoutForm.exerciseId!,
      sets: workoutForm.sets,
      reps: workoutForm.reps,
      weightKg: workoutForm.weightKg,
      durationMin: workoutForm.durationMin,
    });
    ElMessage.success("运动记录已添加，自动计算消耗");
    workoutDialog.value = false;
    await loadOverview();
  } catch (e) {
    // 错误已由拦截器弹窗
  } finally {
    submitting.value = false;
  }
}

async function onDelWorkout(id: number) {
  try {
    await ElMessageBox.confirm("确定删除这条运动记录？", "提示", { type: "warning" });
    await deleteWorkout(id);
    ElMessage.success("已删除");
    await loadOverview();
  } catch (e) {
    // cancel
  }
}

// ===== 弹窗：添加饮食 =====
const dietDialog = ref(false);
const dietForm = reactive<{ foodId: number | null; meal: string; amountG: number; kcalPer100g: number; unit: string }>({
  foodId: null,
  meal: "breakfast",
  amountG: 100,
  kcalPer100g: 0,
  unit: "g",
});

function openDietDialog() {
  dietForm.foodId = null;
  dietForm.meal = "breakfast";
  dietForm.amountG = 100;
  dietForm.kcalPer100g = 0;
  dietForm.unit = "g";
  dietDialog.value = true;
}

function onDietPick(id: number) {
  const f = foodDict.value.find((x: any) => x.id === id);
  if (f) {
    dietForm.kcalPer100g = f.kcal_per_100g || 0;
    dietForm.unit = f.default_unit || "g";
    dietForm.amountG = f.default_amount || 100;
  }
}

const dietPreviewKcal = computed(() => {
  if (!dietForm.kcalPer100g || !dietForm.amountG) return 0;
  return Math.round(dietForm.kcalPer100g * dietForm.amountG / 100 * 10) / 10;
});

async function submitDiet() {
  if (!dietForm.foodId) {
    ElMessage.warning("请选择食物");
    return;
  }
  submitting.value = true;
  try {
    await addDiet({
      foodId: dietForm.foodId!,
      meal: dietForm.meal as any,
      amountG: dietForm.amountG,
    });
    ElMessage.success("饮食记录已添加，自动计算摄入");
    dietDialog.value = false;
    await loadOverview();
  } catch (e) {
    // noop
  } finally {
    submitting.value = false;
  }
}

async function onDelDiet(id: number) {
  try {
    await ElMessageBox.confirm("确定删除这条饮食记录？", "提示", { type: "warning" });
    await deleteDiet(id);
    ElMessage.success("已删除");
    await loadOverview();
  } catch (e) {
    // cancel
  }
}

// ===== 加载 =====
async function loadOverview() {
  try {
    const r = await fetchHealthOverview();
    const d = r.data;
    stat.value = d.stat || {};
    summary.value = d.today_summary || { workouts_today: [], diet_today: {} };
    trend.value = d.trend || [];
    weight.value = d.weight || [];
    exercises.value = d.exercise || [];
    vital.value = d.vital;
    exerciseDict.value = d.exercise_dict || [];
    foodDict.value = d.food_dict || [];
  } catch (e) {
    // 兜底：单独调字典与汇总接口
    try {
      const [dict1, dict2, sum] = await Promise.all([fetchExerciseDict(), fetchFoodDict(), fetchHealthOverview()]);
      exerciseDict.value = dict1.data || [];
      foodDict.value = dict2.data || [];
    } catch (e2) {
      // noop
    }
  }
}

onMounted(() => {
  loadOverview();
  loadGoals();
});
</script>

<style scoped lang="scss">
.stat-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 14px;
}
.health-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 14px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.card-head .card-title {
  margin-bottom: 0;
}
.balance-card {
  margin-bottom: 14px;
}

/* ===== 每日目标卡 ===== */
.goal-card {
  margin-bottom: 14px;
}
.goal-stage-badge {
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
.goal-tip {
  font-size: 11px;
  color: $text-tertiary;
}
.goal-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}
.goal-item {
  border: 1px solid $border-subtle;
  border-radius: 10px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.gi-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.gi-label {
  font-size: 13px;
  font-weight: 600;
  color: $text-primary;
}
.gi-actual {
  font-size: 13px;
  font-weight: 600;
}
.gi-edit {
  display: flex;
  align-items: center;
  gap: 6px;
  .el-input-number {
    width: 92px;
  }
}
.gi-unit {
  font-size: 11px;
  color: $text-tertiary;
  margin-right: 2px;
}
.goal-ms {
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px dashed $border-subtle;
}
.goal-ms-title {
  font-size: 12px;
  font-weight: 600;
  color: $text-secondary;
  margin-bottom: 6px;
}
.goal-ms-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.goal-ms-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 12px;
  color: $text-secondary;
}
.gms-stage {
  color: $accent-light;
  font-weight: 600;
  white-space: nowrap;
}
.gms-date {
  color: $text-tertiary;
  white-space: nowrap;
}
.gms-items {
  color: $text-secondary;
}
.balance-row {
  display: flex;
  align-items: stretch;
  gap: 18px;
}
.balance-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.balance-divider {
  font-size: 24px;
  color: $text-tertiary;
  display: flex;
  align-items: center;
}
.bal-label {
  font-size: 12px;
  color: $text-secondary;
}
.bal-val {
  font-size: 22px;
  font-weight: 700;
  color: $text-primary;
}
.bal-val.intake {
  color: #fbbf24;
}
.bal-val.burn {
  color: #2dd4bf;
}
.unit {
  font-size: 12px;
  font-weight: 500;
  color: $text-tertiary;
  margin-left: 2px;
}
.bal-comment {
  font-size: 12px;
  color: $text-secondary;
  margin-top: 2px;
}
.ring-flex {
  display: flex;
  justify-content: center;
  margin: 4px 0 10px;
}
.record-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.record-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 8px;
  border-radius: 6px;
  background: rgba(167, 139, 250, 0.05);
  font-size: 13px;
}
.rec-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.rec-name {
  font-weight: 600;
  color: $text-primary;
}
.rec-meta {
  font-size: 11px;
  color: $text-tertiary;
}
.rec-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rec-burn {
  color: #2dd4bf;
  font-weight: 600;
  font-size: 12px;
}
.rec-intake {
  color: #fbbf24;
  font-weight: 600;
  font-size: 12px;
}
.del-icon {
  cursor: pointer;
  color: $text-tertiary;
  font-size: 14px;
  &:hover {
    color: #ef4444;
  }
}
.empty {
  color: $text-tertiary;
  font-size: 12px;
  padding: 8px 0;
  text-align: center;
  &.small {
    padding: 2px 0;
    text-align: left;
  }
}
.meal-group {
  margin-bottom: 10px;
  &:last-child {
    margin-bottom: 0;
  }
}
.meal-title {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  color: $text-secondary;
  padding-bottom: 4px;
  border-bottom: 1px dashed rgba(167, 139, 250, 0.18);
  margin-bottom: 4px;
}
.meal-sum {
  color: #fbbf24;
  font-weight: 500;
}
.vital-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.vital-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.vital-label {
  font-size: 13px;
  color: $text-secondary;
}
.vital-val {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
}
.preview-kcal {
  font-size: 18px;
  font-weight: 700;
  &.intake {
    color: #fbbf24;
  }
  &.burn {
    color: #2dd4bf;
  }
}
.ml8 {
  margin-left: 8px;
}
</style>
