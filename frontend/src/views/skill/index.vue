<template>
  <div class="skill-page">
    <div class="stat-row">
      <StatCard label="技能成就" :value="stat.total_skills ?? 0" note="持续积累" noteType="neutral" />
      <StatCard label="需提升" :value="stat.improving ?? 0" note="系统设计 / 性能优化" noteType="warning" />
      <StatCard label="已掌握" :value="stat.mastered ?? 0" note="分布式" noteType="positive" />
    </div>

    <div class="card-grid">
      <div v-for="s in skills" :key="s.id" class="skill-card ld-card">
        <div class="skill-icon" :class="iconClass(s.icon)">{{ iconText(s.icon) }}</div>
        <div class="skill-name">{{ s.name }}</div>
        <div class="skill-badge" :class="badgeClass(s.level)">{{ s.stage }}</div>
        <ProgressBar label="当前等级进度" :percent="s.progress ?? 0" :color="progressColor(s.level ?? 0)" />
        <div class="skill-desc">{{ s.description }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import StatCard from "@/components/StatCard.vue";
import ProgressBar from "@/components/ProgressBar.vue";
import { fetchSkillStat, fetchSkillList } from "@/api/skill";

defineOptions({ name: "SkillView" });

const stat = ref<any>({});
const skills = ref<any[]>([]);

function iconClass(icon?: string) {
  return `icon-${icon || "star"}`;
}
function iconText(icon?: string) {
  switch (icon) {
    case "diamond": return "◆";
    case "doubleRing": return "◎";
    case "triangle": return "▲";
    case "square": return "■";
    case "star": return "★";
    default: return "★";
  }
}
function badgeClass(level: number) {
  if (level >= 5) return "badge-positive";
  if (level >= 4) return "badge-warning";
  if (level >= 3) return "badge-accent";
  if (level >= 2) return "badge-neutral";
  return "badge-accent-light";
}
function progressColor(level: number) {
  if (level >= 5) return "#2dd4bf";
  if (level >= 4) return "#fbbf24";
  if (level >= 3) return "#7c3aed";
  return "#6b7280";
}

async function load() {
  try {
    const [s, l] = await Promise.all([fetchSkillStat(), fetchSkillList()]);
    stat.value = s.data;
    skills.value = l.data;
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
.card-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
}
.skill-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}
.skill-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  margin-bottom: 12px;
}
.icon-diamond { background: rgba(45, 212, 191, 0.15); color: $color-positive; }
.icon-doubleRing { background: rgba(45, 212, 191, 0.15); color: $color-positive; }
.icon-triangle { background: rgba(251, 191, 36, 0.15); color: $color-warning; }
.icon-square { background: rgba(107, 114, 128, 0.18); color: $color-neutral; }
.icon-star { background: rgba(124, 58, 237, 0.18); color: $accent-light; }

.skill-name {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 6px;
}
.skill-badge {
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 11px;
  margin-bottom: 14px;
}
.badge-positive { color: $color-positive; background: rgba(45, 212, 191, 0.12); }
.badge-warning { color: $color-warning; background: rgba(251, 191, 36, 0.12); }
.badge-accent { color: $accent-light; background: rgba(124, 58, 237, 0.18); }
.badge-neutral { color: $text-secondary; background: rgba(167, 139, 250, 0.08); }
.badge-accent-light { color: $accent-light; background: rgba(167, 139, 250, 0.15); }
.skill-desc {
  margin-top: 10px;
  font-size: 12px;
  color: $text-secondary;
}
</style>