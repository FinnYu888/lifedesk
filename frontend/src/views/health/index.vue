<template>
  <div class="health-page">
    <div class="stat-row">
      <StatCard label="周完成率" :value="(stat.week_rate ?? 82) + '%'" :note="stat.week_rate_delta || '+6%'" noteType="positive" />
      <StatCard label="今日步数" :value="(stat.steps_today ?? 8420).toLocaleString()" :note="(stat.steps_percent ?? 84) + '% 完成'" noteType="neutral" />
      <StatCard label="体重" :value="(stat.weight ?? 65.2)" :note="'↓ ' + Math.abs(stat.weight_delta ?? -0.6) + 'kg 本周'" noteType="positive" />
    </div>

    <div class="health-grid">
      <!-- 健身动作 -->
      <div class="ld-card">
        <div class="card-title">健身动作·本周完成</div>
        <div class="ring-flex">
          <RingChart :percent="71" :size="120" />
        </div>
        <div class="parts">
          <div v-for="w in workouts" :key="w.id" class="part-row">
            <span>{{ w.part }}</span>
            <StatusBadge :type="w.status === 'done' ? 'positive' : 'warning'">
              {{ w.doneCount }}/{{ w.planCount }} {{ w.status === "done" ? "已完成" : "待升级" }}
            </StatusBadge>
          </div>
        </div>
      </div>

      <!-- 饮食打卡 -->
      <div class="ld-card">
        <div class="card-title">饮食控制·每周打卡</div>
        <div class="dots">
          <span v-for="(d, i) in weekDots" :key="i" class="dot" :class="d ? 'on' : ''" />
        </div>
        <div class="diet-stat">5/7 已打卡 · 合格率 71%</div>
      </div>

      <!-- 卡路里 -->
      <div class="ld-card">
        <div class="card-title">卡路里·摄入/消耗</div>
        <VChart :option="calorieOption" autoresize style="height: 220px" />
      </div>

      <!-- 体重 -->
      <div class="ld-card">
        <div class="card-title">体重·体脂变化趋势</div>
        <VChart :option="weightOption" autoresize style="height: 220px" />
      </div>

      <!-- 运动计划 -->
      <div class="ld-card">
        <div class="card-title">运动计划达成率</div>
        <ProgressBar
          v-for="e in exercises"
          :key="e.id"
          :label="e.category"
          :percent="e.rate ?? 0"
          :color="progressColor(e.rate ?? 0)"
        />
        <div class="exercise-note text-tertiary">4 项达标 · 综合 -8%</div>
      </div>

      <!-- 心率/睡眠/水分 -->
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
          <ProgressBar
            label="睡眠 8h 目标"
            :percent="Math.round(((vital?.sleepHours ?? 7.5) / 8) * 100)"
            color="#a78bfa"
          />
          <div class="vital-row">
            <span class="vital-label">水分</span>
            <span class="vital-val">{{ ((vital?.waterMl ?? 1600) / 1000).toFixed(1) }} / {{ ((vital?.waterGoalMl ?? 2500) / 1000).toFixed(1) }} L</span>
          </div>
          <ProgressBar
            label="水分摄入"
            :percent="Math.round(((vital?.waterMl ?? 1600) / (vital?.waterGoalMl ?? 2500)) * 100)"
            color="#2dd4bf"
          />
          <div class="vital-comment text-secondary">静息心率正常 · 睡眠良好 · 水分 {{ Math.round(((vital?.waterMl ?? 1600) / (vital?.waterGoalMl ?? 2500)) * 100) }}%</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { BarChart, LineChart } from "echarts/charts";
import { GridComponent, TooltipComponent, LegendComponent } from "echarts/components";
import StatCard from "@/components/StatCard.vue";
import RingChart from "@/components/RingChart.vue";
import ProgressBar from "@/components/ProgressBar.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import { fetchHealthOverview } from "@/api/health";

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent]);

defineOptions({ name: "HealthView" });

const stat = ref<any>({});
const workouts = ref<any[]>([]);
const diet = ref<any[]>([]);
const calorie = ref<any[]>([]);
const weight = ref<any[]>([]);
const exercises = ref<any[]>([]);
const steps = ref<any[]>([]);
const vital = ref<any | null>(null);

const weekDots = computed(() => {
  return diet.value.map((d: any) => !!(d.breakfast || d.lunch || d.dinner || d.snack));
});

const calorieOption = computed(() => {
  const x = calorie.value.map((c: any) => c.recordDate?.substring(5) || "");
  return {
    tooltip: { trigger: "axis" },
    legend: { data: ["摄入", "消耗"], textStyle: { color: "#a8a0c8" } },
    grid: { left: 36, right: 16, top: 30, bottom: 26 },
    xAxis: { type: "category", data: x, axisLabel: { color: "#a8a0c8", fontSize: 10 } },
    yAxis: { type: "value", axisLabel: { color: "#a8a0c8", fontSize: 10 } },
    series: [
      {
        name: "摄入",
        type: "bar",
        data: calorie.value.map((c: any) => c.intake),
        itemStyle: { color: "#fbbf24" }
      },
      {
        name: "消耗",
        type: "bar",
        data: calorie.value.map((c: any) => c.burn),
        itemStyle: { color: "#2dd4bf" }
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
      { type: "value", name: "kg", min: 65, max: 66, axisLabel: { color: "#a8a0c8", fontSize: 10 } },
      { type: "value", name: "%", min: 17, max: 19, axisLabel: { color: "#a8a0c8", fontSize: 10 } }
    ],
    series: [
      {
        name: "体重",
        type: "line",
        smooth: true,
        data: weight.value.map((w: any) => w.weight),
        lineStyle: { color: "#7c3aed", width: 2 },
        itemStyle: { color: "#a78bfa" }
      },
      {
        name: "体脂",
        type: "line",
        smooth: true,
        yAxisIndex: 1,
        data: weight.value.map((w: any) => w.fatRate),
        lineStyle: { color: "#2dd4bf", width: 2 },
        itemStyle: { color: "#2dd4bf" }
      }
    ]
  };
});

function progressColor(rate: number) {
  if (rate >= 80) return "#7c3aed";
  if (rate >= 60) return "#a78bfa";
  return "#fbbf24";
}

async function load() {
  try {
    const r = await fetchHealthOverview();
    const d = r.data;
    stat.value = d.stat;
    workouts.value = d.workouts || [];
    diet.value = d.diet || [];
    calorie.value = d.calorie || [];
    weight.value = d.weight || [];
    exercises.value = d.exercise || [];
    steps.value = d.steps || [];
    vital.value = d.vital;
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
.ring-flex {
  display: flex;
  justify-content: center;
  margin: 6px 0 12px;
}
.parts {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.part-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  font-size: 13px;
  color: $text-secondary;
}

.dots {
  display: flex;
  gap: 12px;
  margin-bottom: 10px;
  justify-content: space-between;
}
.dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(167, 139, 250, 0.15);
  &.on {
    background: $accent;
  }
}
.diet-stat {
  font-size: 13px;
  color: $text-secondary;
  text-align: center;
}
.exercise-note {
  margin-top: 8px;
  font-size: 12px;
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
.vital-comment {
  font-size: 12px;
  margin-top: 6px;
}
</style>