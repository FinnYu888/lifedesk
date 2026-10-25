<template>
  <div class="dashboard">
    <div class="greeting ld-card">
      <div>
        <div class="greeting-text">{{ greeting?.greeting || "你好，林深" }}</div>
        <div class="greeting-sub">{{ greeting?.summary || "今日 3 场日程 · 1 个会议" }}</div>
      </div>
      <div class="text-positive">{{ greeting?.date }}</div>
    </div>

    <div class="module-grid">
      <div
        v-for="m in modules"
        :key="m.path"
        class="module-card ld-card"
        @click="goTo(m.path)"
      >
        <div class="m-label">{{ m.label }}</div>
        <div class="m-value">{{ m.value }}</div>
        <div class="m-note" :class="m.noteCls">{{ m.note }}</div>
      </div>
    </div>

    <div class="health-section">
      <div class="section-head">
        <span class="section-title">个人健康</span>
        <span class="section-note">{{ dateStr }} · 本周达成率 82% · 目标 65kg</span>
      </div>

      <div class="health-grid">
        <div class="ld-card">
          <div class="hc-title">健身动作·本周完成</div>
          <div class="hc-flex">
            <RingChart :percent="71" :size="100" />
            <div class="hc-detail">
              <div class="detail-row"><span>胸</span><StatusBadge type="positive">4/4 已完成</StatusBadge></div>
              <div class="detail-row"><span>背</span><StatusBadge type="positive">4/4 已完成</StatusBadge></div>
              <div class="detail-row"><span>腿</span><StatusBadge type="warning">3/5 待升级</StatusBadge></div>
            </div>
          </div>
        </div>

        <div class="ld-card">
          <div class="hc-title">饮食控制·每周打卡</div>
          <div class="dots"><span v-for="n in 7" :key="n" class="dot" :class="n <= 5 ? 'on' : ''" /></div>
          <div class="diet-today">
            <span class="meal" @click="quickMeal('早')">早</span>
            <span class="meal" @click="quickMeal('午')">午</span>
            <span class="meal" @click="quickMeal('晚')">晚</span>
            <span class="meal" @click="quickMeal('加餐')">加餐</span>
          </div>
        </div>

        <div class="ld-card">
          <div class="hc-title">卡路里·摄入/消耗</div>
          <div class="calo-box">
            <div class="text-warning">本周摄入均值 <b>1920 kcal</b></div>
            <div class="text-positive">本周消耗均值 <b>2380 kcal</b></div>
            <div class="calo-net">今日净剩 <b class="text-positive">-320 kcal</b> · 赤字</div>
          </div>
        </div>

        <div class="ld-card">
          <div class="hc-title">体重·体脂变化趋势</div>
          <VChart :option="weightChartOption" autoresize style="height: 160px" />
        </div>

        <div class="ld-card">
          <div class="hc-title">运动计划达成率</div>
          <ProgressBar label="力量训练" :percent="84" color="#a78bfa" />
          <ProgressBar label="有氧" :percent="65" color="#a78bfa" />
          <ProgressBar label="柔韧" :percent="40" color="#fbbf24" />
          <ProgressBar label="拉伸放松" :percent="90" color="#2dd4bf" />
        </div>

        <div class="ld-card">
          <div class="hc-title">每日步数·目标 10,000</div>
          <div class="step-flex">
            <RingChart :percent="84" :size="100" color="#a78bfa" />
            <div>
              <div class="step-num">8,420</div>
              <div class="text-secondary step-sub">本周共 7,820 · 距目标 +1,205</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { LineChart } from "echarts/charts";
import { GridComponent, TooltipComponent } from "echarts/components";

import RingChart from "@/components/RingChart.vue";
import ProgressBar from "@/components/ProgressBar.vue";
import StatusBadge from "@/components/StatusBadge.vue";

import { fetchGreeting, fetchModuleStat } from "@/api/dashboard";
import { recordHealth } from "@/api/health";

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent]);

defineOptions({ name: "DashboardView" });

const router = useRouter();
const greeting = ref<any>(null);
const moduleStat = ref<any>(null);

const dateStr =
  new Date().toLocaleDateString("zh-CN", { month: "numeric", day: "numeric" }).replace("/", "月") + "日";

const noteTypeClass = (type?: string) => (type ? `text-${type}` : "");

const modules = computed(() => {
  const s = moduleStat.value;
  if (!s) {
    return [
      { path: "/project", label: "项目管理", value: 0, note: "", noteCls: "" },
      { path: "/task", label: "任务工单", value: 0, note: "", noteCls: "" },
      { path: "/monitor", label: "系统监控", value: 0, note: "", noteCls: "" },
      { path: "/schedule", label: "日程会议", value: 0, note: "", noteCls: "" },
      { path: "/skill", label: "Skills 工坊", value: 0, note: "", noteCls: "" },
      { path: "/bookmark", label: "收藏与笔记", value: 0, note: "0 收藏 · 0 笔记", noteCls: "" },
      { path: "/english", label: "英语提升", value: "0%", note: "今日 0/0 已完成", noteCls: "" },
      { path: "/health", label: "个人健康", value: "82%", note: "周达成率 ↑", noteCls: "text-positive" }
    ];
  }
  const mk = (key: string) => s[key] || {};
  return [
    { path: "/project", label: "项目管理", value: mk("project").count ?? 0, note: mk("project").note ?? "", noteCls: noteTypeClass(mk("project").noteType) },
    { path: "/task", label: "任务工单", value: mk("task").count ?? 0, note: mk("task").note ?? "", noteCls: "text-danger" },
    { path: "/monitor", label: "系统监控", value: mk("monitor").count ?? 0, note: mk("monitor").note ?? "", noteCls: "text-warning" },
    { path: "/schedule", label: "日程会议", value: mk("schedule").count ?? 0, note: mk("schedule").note ?? "", noteCls: "" },
    { path: "/skill", label: "Skills 工坊", value: mk("skill").count ?? 0, note: mk("skill").note ?? "", noteCls: "" },
    { path: "/bookmark", label: "收藏与笔记", value: mk("favorite").count ?? 0, note: mk("favorite").note ?? "0 收藏 · 0 笔记", noteCls: "" },
    { path: "/english", label: "英语提升", value: mk("english").count ?? "0%", note: mk("english").note ?? "今日 0/0 已完成", noteCls: "text-positive" },
    { path: "/health", label: "个人健康", value: "82%", note: "周达成率 ↑", noteCls: "text-positive" }
  ];
});

function goTo(path: string) {
  router.push(path);
}

async function loadAll() {
  try {
    const [g, m] = await Promise.all([fetchGreeting(), fetchModuleStat()]);
    greeting.value = g.data;
    moduleStat.value = m.data;
  } catch {
    // 错误已由拦截器提示
  }
}

async function quickMeal(meal: string) {
  try {
    const key = meal === "早" ? "breakfast" : meal === "午" ? "lunch" : meal === "晚" ? "dinner" : "snack";
    await recordHealth({ recordType: "diet", [key]: true });
    ElMessage.success(`${meal}已打卡`);
  } catch {
    // 错误已提示
  }
}

const weightChartOption = computed(() => ({
  tooltip: { trigger: "axis" },
  grid: { left: 30, right: 12, top: 10, bottom: 22 },
  xAxis: {
    type: "category",
    data: ["10.19", "10.20", "10.21", "10.22", "10.23", "10.24"],
    axisLabel: { color: "#a8a0c8", fontSize: 10 }
  },
  yAxis: {
    type: "value",
    min: 65,
    max: 66,
    axisLabel: { color: "#a8a0c8", fontSize: 10 }
  },
  series: [
    {
      data: [65.8, 65.7, 65.6, 65.5, 65.3, 65.2],
      type: "line",
      smooth: true,
      symbol: "circle",
      symbolSize: 6,
      lineStyle: { color: "#a78bfa", width: 2 },
      itemStyle: { color: "#a78bfa" }
    }
  ]
}));

onMounted(loadAll);
</script>

<style scoped lang="scss">
.greeting {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.greeting-text {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 6px;
}
.greeting-sub {
  color: $text-secondary;
  font-size: 13px;
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 22px;
}
.module-card {
  cursor: pointer;
}
.m-label {
  font-size: 13px;
  color: $text-tertiary;
  margin-bottom: 8px;
}
.m-value {
  font-size: 28px;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 4px;
}
.m-note {
  font-size: 12px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin: 14px 0 12px;
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
}
.section-note {
  font-size: 13px;
  color: $text-secondary;
}

.health-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}
.hc-title {
  font-size: 14px;
  font-weight: 500;
  color: $text-primary;
  margin-bottom: 12px;
}
.hc-flex {
  display: flex;
  gap: 14px;
  align-items: center;
}
.hc-detail {
  flex: 1;
}
.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 5px 0;
  font-size: 13px;
  color: $text-secondary;
}

.dots {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}
.dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: rgba(167, 139, 250, 0.15);
  &.on {
    background: $accent;
  }
}
.diet-today {
  display: flex;
  gap: 8px;
}
.meal {
  flex: 1;
  text-align: center;
  padding: 6px 0;
  background: rgba(167, 139, 250, 0.08);
  border-radius: 8px;
  font-size: 13px;
  color: $text-secondary;
  cursor: pointer;
  transition: all 0.15s;
  &:hover {
    background: rgba(167, 139, 250, 0.2);
    color: $text-primary;
  }
}
.calo-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 13px;
}
.calo-net {
  padding-top: 8px;
  border-top: 1px dashed $border-subtle;
}
.step-flex {
  display: flex;
  align-items: center;
  gap: 14px;
}
.step-num {
  font-size: 24px;
  font-weight: 700;
}
.step-sub {
  font-size: 12px;
}
</style>