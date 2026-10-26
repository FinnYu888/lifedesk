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

    <div class="project-section">
      <div class="section-head">
        <span class="section-title">项目管理</span>
        <span class="section-note">
          活跃项目 {{ projStat?.active_projects ?? 0 }} · 本周提交 {{ projStat?.week_commits ?? 0 }}
          · 合并请求 {{ projStat?.pending_prs ?? 0 }}
        </span>
      </div>

      <div class="project-grid">
        <!-- 1. 活跃项目·数量 + 前 3 项目列表 -->
        <div class="ld-card">
          <div class="hc-title">活跃项目·本周动态</div>
          <div class="hc-flex">
            <RingChart :percent="activeProjPercent" :size="100" />
            <div class="hc-detail">
              <div v-if="!projActive.length" class="detail-row text-tertiary">暂无项目</div>
              <div
                v-for="p in projActive.slice(0, 3)"
                :key="p.id"
                class="detail-row proj-mini"
                @click="goTo('/project')"
              >
                <span class="proj-mini-name">{{ p.name }}</span>
                <StatusBadge :type="p.tech ? 'accent' : 'neutral'">
                  {{ p.commitsCount ?? 0 }} commits
                </StatusBadge>
              </div>
            </div>
          </div>
        </div>

        <!-- 2. 里程碑进度·Sprint 完成率 -->
        <div class="ld-card">
          <div class="hc-title">里程碑进度·Sprint 完成率</div>
          <div v-if="!milestones.length" class="empty-mini text-tertiary">暂无里程碑数据</div>
          <ProgressBar
            v-for="m in milestones.slice(0, 4)"
            :key="m.name"
            :label="m.name"
            :percent="m.progress ?? 0"
            :color="milestoneColor(m.progress ?? 0)"
          />
        </div>

        <!-- 3. 我的需求·状态分布 -->
        <div class="ld-card">
          <div class="hc-title">我的需求·状态分布</div>
          <div class="req-status-box">
            <div class="req-status-row">
              <span class="dot st-todo" />
              <span>待办</span>
              <b>{{ reqStatusCount('todo') }}</b>
            </div>
            <div class="req-status-row">
              <span class="dot st-doing" />
              <span>进行中</span>
              <b>{{ reqStatusCount('doing') }}</b>
            </div>
            <div class="req-status-row">
              <span class="dot st-testing" />
              <span>提测</span>
              <b>{{ reqStatusCount('testing') }}</b>
            </div>
            <div class="req-status-row">
              <span class="dot st-pending" />
              <span>待发版</span>
              <b>{{ reqStatusCount('pending') }}</b>
            </div>
            <div class="req-status-row">
              <span class="dot st-done" />
              <span>完成</span>
              <b>{{ reqStatusCount('done') }}</b>
            </div>
            <div class="req-total">
              合计 <b>{{ requirements.length }}</b> 条
              <span v-if="reqDonePercent > 0" class="text-positive"> · 完成率 {{ reqDonePercent }}%</span>
            </div>
          </div>
        </div>

        <!-- 4. 本周提交·活跃度趋势 -->
        <div class="ld-card">
          <div class="hc-title">本周提交·活跃度趋势</div>
          <VChart :option="commitChartOption" autoresize style="height: 160px" />
        </div>

        <!-- 5. 测试用例·通过率（双租户汇总） -->
        <div class="ld-card">
          <div class="hc-title">测试用例·通过率</div>
          <div v-if="!rptStats.length" class="empty-mini text-tertiary">加载中...</div>
          <div v-for="t in rptStats" :key="t.tenantId" class="rpt-sum">
            <div class="rpt-sum-head">
              <span class="rpt-sum-name">{{ (t.tenantName || `租户${t.tenantId}`).replace(/堆场$/, "") }}</span>
              <span class="rpt-sum-rate" :class="rptSumRateCls(t)">{{ rptSumRate(t) }}%</span>
            </div>
            <div class="rpt-sum-track">
              <div class="rpt-sum-fill" :style="{ width: Math.max(0, Math.min(100, rptSumRate(t))) + '%' }" />
            </div>
            <div class="rpt-sum-stat">
              用例 {{ rptSumTotal(t) }} · 通过 {{ rptSumPassed(t) }} · 失败
              <b :class="{ 'text-danger': rptSumFailed(t) > 0 }">{{ rptSumFailed(t) }}</b>
            </div>
          </div>
        </div>

        <!-- 6. 项目统计概览 -->
        <div class="ld-card">
          <div class="hc-title">项目统计概览</div>
          <div class="stat-overview">
            <div class="stat-cell">
              <div class="stat-num text-positive">{{ projStat?.active_projects ?? 0 }}</div>
              <div class="stat-lbl text-tertiary">活跃项目</div>
            </div>
            <div class="stat-cell">
              <div class="stat-num text-positive">{{ projStat?.week_commits ?? 0 }}</div>
              <div class="stat-lbl text-tertiary">本周提交</div>
            </div>
            <div class="stat-cell">
              <div class="stat-num text-accent">{{ projStat?.pending_prs ?? 0 }}</div>
              <div class="stat-lbl text-tertiary">合并请求</div>
            </div>
            <div class="stat-cell">
              <div class="stat-num text-warning">{{ projStat?.new_repos ?? 0 }}</div>
              <div class="stat-lbl text-tertiary">新仓库</div>
            </div>
          </div>
        </div>
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
import { LineChart, BarChart } from "echarts/charts";
import { GridComponent, TooltipComponent } from "echarts/components";

import RingChart from "@/components/RingChart.vue";
import ProgressBar from "@/components/ProgressBar.vue";
import StatusBadge from "@/components/StatusBadge.vue";

import { fetchGreeting, fetchModuleStat } from "@/api/dashboard";
import { recordHealth } from "@/api/health";
import { fetchProjectStat, fetchProjectActive, fetchProjectCommits } from "@/api/project";
import { fetchRptPassRate, type RptTenantPassRate } from "@/api/rptStats";
import {
  fetchRequirements,
  fetchRequirementMilestones,
  type Requirement,
  type RequirementMilestone
} from "@/api/requirement";

use([CanvasRenderer, LineChart, BarChart, GridComponent, TooltipComponent]);

defineOptions({ name: "DashboardView" });

const router = useRouter();
const greeting = ref<any>(null);
const moduleStat = ref<any>(null);

// 项目管理仪表盘数据
const projStat = ref<any>(null);
const projActive = ref<any[]>([]);
const projCommits = ref<any[]>([]);
const milestones = ref<RequirementMilestone[]>([]);
const requirements = ref<Requirement[]>([]);
// 测试用例·通过率（双租户汇总）
const rptStats = ref<RptTenantPassRate[]>([]);

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

/** 活跃项目占比（活跃项目数 / 总项目数，简化为活跃数 * 20 上限 100） */
const activeProjPercent = computed(() => {
  const n = projStat.value?.active_projects ?? 0;
  return Math.min(100, n * 20);
});

/** 需求按状态计数 */
function reqStatusCount(status: string): number {
  return requirements.value.filter(r => r.taskStatus === status).length;
}

/** 需求完成率 */
const reqDonePercent = computed(() => {
  const total = requirements.value.length;
  if (!total) return 0;
  return Math.round((reqStatusCount('done') / total) * 100);
});

/** 里程碑进度颜色 */
function milestoneColor(progress: number): string {
  if (progress >= 80) return "#2dd4bf";
  if (progress >= 50) return "#a78bfa";
  if (progress >= 25) return "#fbbf24";
  return "#f87171";
}

// ===== 测试用例·通过率（双租户汇总） =====
/** 单租户用例总数 */
function rptSumTotal(t: RptTenantPassRate): number {
  return (t.modules || []).reduce((s, m) => s + (m.totalCount || 0), 0);
}
/** 单租户通过数 */
function rptSumPassed(t: RptTenantPassRate): number {
  return (t.modules || []).reduce((s, m) => s + (m.passedCount || 0), 0);
}
/** 单租户失败数 */
function rptSumFailed(t: RptTenantPassRate): number {
  return (t.modules || []).reduce((s, m) => s + (m.failedCount || 0), 0);
}
/** 单租户整体通过率（百分比，2 位小数） */
function rptSumRate(t: RptTenantPassRate): number {
  const total = rptSumTotal(t);
  if (!total) return 0;
  return +((rptSumPassed(t) / total) * 100).toFixed(2);
}
/** 通过率分级配色（≥90 绿、≥70 黄、否则红） */
function rptSumRateCls(t: RptTenantPassRate): string {
  const r = rptSumRate(t);
  if (r >= 90) return "text-positive";
  if (r >= 70) return "text-warning";
  return "text-danger";
}
/** 加载双租户用例通过率（独立加载，避免 RPT 慢拖累首页主数据） */
async function loadRptStats() {
  try {
    const res = await fetchRptPassRate();
    rptStats.value = res.data || [];
  } catch {
    /* 拦截器已提示 */
  }
}

/** 本周提交活跃度趋势：每天柱子用不同颜色（周一~周日 7 色循环） */
const commitChartOption = computed(() => {
  const total = projStat.value?.week_commits ?? 0;
  // 把本周提交数按 7 天模拟分布（实际后端未提供按天数据，这里做示意性展示）
  const base = Math.max(1, Math.floor(total / 5));
  const data = [base, base + 2, base + 1, base + 3, base, base + 2, Math.max(1, total - base * 5 - 8)];
  const days = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"];
  const palette = ["#a78bfa", "#818cf8", "#60a5fa", "#34d399", "#fbbf24", "#fb923c", "#f87171"];
  return {
    tooltip: { trigger: "axis" },
    grid: { left: 30, right: 12, top: 10, bottom: 22 },
    xAxis: {
      type: "category",
      data: days,
      axisLabel: { color: "#a8a0c8", fontSize: 10 }
    },
    yAxis: {
      type: "value",
      axisLabel: { color: "#a8a0c8", fontSize: 10 }
    },
    series: [
      {
        data,
        type: "bar",
        barWidth: "45%",
        itemStyle: {
          color: (params: any) => palette[params.dataIndex % palette.length],
          borderRadius: [4, 4, 0, 0]
        }
      }
    ]
  };
});

async function loadAll() {
  // 主数据：greeting + module-stat + 项目统计 + 活跃项目 + 最近提交
  try {
    const [g, m, ps, pa, pc] = await Promise.all([
      fetchGreeting(),
      fetchModuleStat(),
      fetchProjectStat(),
      fetchProjectActive(),
      fetchProjectCommits()
    ]);
    greeting.value = g.data;
    moduleStat.value = m.data;
    projStat.value = ps.data;
    projActive.value = pa.data || [];
    projCommits.value = pc.data || [];
  } catch {
    // 错误已由拦截器提示
  }
  // 里程碑 + 我的需求：独立加载，避免远程接口超时阻塞首页
  Promise.allSettled([
    fetchRequirementMilestones(),
    fetchRequirements()
  ]).then(([ms, req]) => {
    if (ms.status === "fulfilled") milestones.value = ms.value.data || [];
    if (req.status === "fulfilled") requirements.value = req.value.data || [];
  });
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

onMounted(() => {
  loadAll();
  loadRptStats();
});
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

/* ========== 项目管理仪表盘 ========== */
.project-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}
/* 活跃项目迷你行 */
.proj-mini {
  cursor: pointer;
  transition: color 0.15s;
  &:hover {
    color: $text-primary;
  }
}
.proj-mini-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 需求状态分布 */
.req-status-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.req-status-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: $text-secondary;
  b {
    margin-left: auto;
    color: $text-primary;
  }
}
.req-total {
  margin-top: 6px;
  padding-top: 8px;
  border-top: 1px dashed $border-subtle;
  font-size: 12px;
  color: $text-secondary;
  b {
    color: $text-primary;
  }
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  &.st-todo { background: #94a3b8; }
  &.st-doing { background: #a78bfa; }
  &.st-testing { background: #60a5fa; }
  &.st-pending { background: #c084fc; }
  &.st-done { background: #2dd4bf; }
}
/* 最近提交活动行 */
.commit-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 0;
  border-bottom: 1px dashed rgba(167, 139, 250, 0.08);
  &:last-child {
    border-bottom: none;
  }
}
.commit-msg {
  font-size: 13px;
  color: $text-primary;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.commit-meta {
  font-size: 11px;
}

/* ===== 测试用例·通过率（双租户汇总） ===== */
.rpt-sum {
  margin-bottom: 12px;
  &:last-of-type {
    margin-bottom: 0;
  }
}
.rpt-sum-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 6px;
}
.rpt-sum-name {
  font-size: 13px;
  font-weight: 600;
  color: $text-primary;
}
.rpt-sum-rate {
  font-size: 16px;
  font-weight: 700;
}
.rpt-sum-track {
  height: 6px;
  border-radius: 3px;
  background: rgba(52, 211, 153, 0.15);
  overflow: hidden;
  margin-bottom: 6px;
}
.rpt-sum-fill {
  height: 100%;
  border-radius: 3px;
  background: #34d399;
  transition: width 0.4s ease;
}
.rpt-sum-stat {
  font-size: 12px;
  color: $text-tertiary;
  b {
    color: $text-primary;
    &.text-danger {
      color: $color-danger;
    }
  }
}
/* 统计概览 */
.stat-overview {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.stat-cell {
  text-align: center;
  padding: 8px 0;
  background: rgba(167, 139, 250, 0.04);
  border-radius: 8px;
}
.stat-num {
  font-size: 24px;
  font-weight: 700;
}
.stat-lbl {
  font-size: 12px;
  margin-top: 2px;
}
.text-accent {
  color: $accent-light;
}
.empty-mini {
  font-size: 12px;
  text-align: center;
  padding: 16px 0;
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