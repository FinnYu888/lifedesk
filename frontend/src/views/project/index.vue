<template>
  <div class="project">
    <!-- 统计卡行 -->
    <div class="stat-row">
      <StatCard label="活跃项目" :value="stat.active_projects ?? 0" note="3 本周活跃" noteType="positive" />
      <StatCard label="本周提交" :value="stat.week_commits ?? 0" :note="stat.week_commits_trend || ''" noteType="positive" />
      <StatCard label="待合并 PR" :value="stat.pending_prs ?? 0" note="2 项待评审" noteType="positive" />
      <StatCard label="新仓库" :value="stat.new_repos ?? 0" note="本周 +1" noteType="neutral" />
    </div>

    <div class="dual-row">
      <!-- 活跃项目列表 -->
      <div class="ld-card">
        <div class="card-title">活跃项目</div>
        <div v-for="p in active" :key="p.id" class="proj-row">
          <span class="color-dot" :class="dotCls(p)" />
          <div class="proj-info">
            <div class="proj-name">
              {{ p.name }}
              <span v-if="p.tech" class="tech-tag">{{ p.tech }}</span>
            </div>
            <div class="proj-desc">{{ p.description }}</div>
          </div>
          <div class="proj-right">
            <div class="proj-progress" :class="progressCls(p)">{{ p.progress }}%</div>
            <div class="proj-time text-tertiary">{{ p.lastActive }}</div>
          </div>
        </div>
      </div>

      <!-- 里程碑 -->
      <div class="ld-card">
        <div class="card-title">里程碑进度</div>
        <ProgressBar
          v-for="m in milestones"
          :key="m.id"
          :label="m.name"
          :percent="m.progress ?? 0"
          :color="milestoneColor(m.progress ?? 0)"
        />
        <div class="m-note text-tertiary">里程碑 v1.2 预计 11 月初发布</div>
      </div>

      <!-- 最近提交活动 -->
      <div class="ld-card">
        <div class="card-title">最近提交活动</div>
        <div v-for="c in commits" :key="c.id" class="commit-row">
          <span class="commit-dot" :style="{ background: commitColor(c.type) }" />
          <div class="commit-info">
            <div class="commit-author">{{ c.author }}</div>
            <div class="commit-msg">{{ c.message }}</div>
          </div>
          <span class="commit-time text-tertiary">{{ c.timeAgo }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import StatCard from "@/components/StatCard.vue";
import ProgressBar from "@/components/ProgressBar.vue";
import { fetchProjectStat, fetchProjectActive, fetchProjectMilestones, fetchProjectCommits } from "@/api/project";

defineOptions({ name: "ProjectView" });

const stat = ref<any>({});
const active = ref<any[]>([]);
const milestones = ref<any[]>([]);
const commits = ref<any[]>([]);

async function load() {
  try {
    const [s, a, m, c] = await Promise.all([
      fetchProjectStat(),
      fetchProjectActive(),
      fetchProjectMilestones(),
      fetchProjectCommits()
    ]);
    stat.value = s.data;
    active.value = a.data;
    milestones.value = m.data;
    commits.value = c.data;
  } catch {}
}

function dotCls(p: any) {
  if (p.status === "pending") return "dot-warning";
  return "dot-positive";
}

function progressCls(p: any) {
  if (p.progress >= 80) return "text-positive";
  if (p.progress >= 60) return "text-warning";
  return "text-warning";
}

function milestoneColor(p: number) {
  if (p >= 100) return "#2dd4bf";
  if (p >= 80) return "#7c3aed";
  return "#fbbf24";
}

function commitColor(type?: string) {
  switch (type) {
    case "feat":
      return "#7c3aed";
    case "fix":
      return "#f87171";
    case "chore":
      return "#6b7280";
    case "refactor":
      return "#2dd4bf";
    case "docs":
      return "#a78bfa";
    default:
      return "#6b7280";
  }
}

onMounted(load);
</script>

<style scoped lang="scss">
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}

.dual-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 14px;
}

.proj-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid $border-subtle;
  &:last-child {
    border-bottom: none;
  }
}
.color-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.dot-positive {
  background: $color-positive;
}
.dot-warning {
  background: $color-warning;
}
.proj-info {
  flex: 1;
  min-width: 0;
}
.proj-name {
  font-size: 14px;
  color: $text-primary;
  font-weight: 500;
}
.tech-tag {
  display: inline-block;
  padding: 1px 8px;
  margin-left: 6px;
  font-size: 11px;
  background: rgba(167, 139, 250, 0.12);
  color: $accent-light;
  border-radius: 8px;
}
.proj-desc {
  font-size: 12px;
  color: $text-secondary;
  margin-top: 2px;
}
.proj-right {
  text-align: right;
}
.proj-progress {
  font-size: 14px;
  font-weight: 600;
}
.proj-time {
  font-size: 11px;
  margin-top: 2px;
}

.m-note {
  margin-top: 10px;
  font-size: 12px;
}

.commit-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px solid $border-subtle;
  &:last-child {
    border-bottom: none;
  }
}
.commit-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.commit-info {
  flex: 1;
  min-width: 0;
}
.commit-author {
  font-size: 13px;
  color: $text-primary;
}
.commit-msg {
  font-size: 12px;
  color: $text-secondary;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.commit-time {
  font-size: 11px;
  flex-shrink: 0;
}
</style>