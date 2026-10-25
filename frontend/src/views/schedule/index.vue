<template>
  <div class="schedule">
    <div class="stat-row">
      <StatCard label="今日会议" :value="stat.today_meetings ?? 0" note="下一场 14:00" noteType="positive" />
      <StatCard label="本周会议" :value="stat.week_meetings ?? 0" note="已排满" noteType="warning" />
      <StatCard label="待处理" :value="stat.pending ?? 0" note="2 项今日" noteType="danger" />
    </div>

    <div class="dual-row">
      <!-- 今日时间轴 -->
      <div class="ld-card">
        <div class="card-title">今日时间轴</div>
        <div v-for="m in todayList" :key="m.id" class="timeline-row" :class="{ current: m.status === 'current' }">
          <div class="time-col">
            <div class="time-start">{{ fmtTime(m.startTime) }}</div>
            <div class="time-end">{{ fmtTime(m.endTime) }}</div>
          </div>
          <div class="timeline-content">
            <div class="meeting-title">{{ m.title }}</div>
            <div class="meeting-meta">
              <span>{{ m.location }}</span>
              <span>· {{ m.durationMin }} 分钟</span>
              <span>· {{ m.attendees }} 位参会者</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 本周日历 -->
      <div class="ld-card">
        <div class="card-title">本周日历 · {{ weekRange }}</div>
        <div class="week-grid">
          <div v-for="(d, i) in weekDays" :key="i" class="week-col">
            <div class="week-name">{{ d.name }}</div>
            <div class="week-day">{{ d.day }}</div>
            <div class="week-dots">
              <span v-for="(dot, j) in d.dots" :key="j" class="week-dot" :class="dot" />
            </div>
          </div>
        </div>
        <div class="legend">
          <span class="legend-item"><i class="lg-dot positive" />会议</span>
          <span class="legend-item"><i class="lg-dot accent" />进行中</span>
          <span class="legend-item"><i class="lg-dot neutral" />已结束</span>
          <span class="legend-item"><i class="lg-dot empty" />无安排</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import StatCard from "@/components/StatCard.vue";
import { fetchScheduleStat, fetchScheduleToday, fetchScheduleWeek } from "@/api/schedule";

defineOptions({ name: "ScheduleView" });

const stat = ref<any>({});
const todayList = ref<any[]>([]);
const weekList = ref<any[]>([]);

const weekRange = computed(() => {
  const today = new Date();
  const day = today.getDay() || 7;
  const monday = new Date(today);
  monday.setDate(today.getDate() - day + 1);
  const sunday = new Date(monday);
  sunday.setDate(monday.getDate() + 6);
  const fmt = (d: Date) => `${d.getMonth() + 1}.${String(d.getDate()).padStart(2, "0")}`;
  return `${fmt(monday)} — ${fmt(sunday)}`;
});

const weekDays = computed(() => {
  const names = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"];
  const today = new Date();
  const day = today.getDay() || 7;
  const monday = new Date(today);
  monday.setDate(today.getDate() - day + 1);
  return names.map((name, i) => {
    const date = new Date(monday);
    date.setDate(monday.getDate() + i);
    const dayList = weekList.value.filter(m => {
      const md = new Date(m.startTime);
      return md.toDateString() === date.toDateString();
    });
    const dots = dayList.length > 0
      ? dayList.map(m => m.status === "current" ? "accent" : m.status === "done" ? "neutral" : "positive")
      : ["empty"];
    return {
      name,
      day: date.getDate(),
      dots
    };
  });
});

function fmtTime(s: string) {
  if (!s) return "";
  return s.substring(11, 16);
}

async function load() {
  try {
    const [s, t, w] = await Promise.all([fetchScheduleStat(), fetchScheduleToday(), fetchScheduleWeek()]);
    stat.value = s.data;
    todayList.value = t.data;
    weekList.value = w.data;
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
.timeline-row {
  display: flex;
  gap: 14px;
  padding: 12px;
  margin-bottom: 8px;
  border-radius: $radius-sm;
  border: 1px solid $border-subtle;
  &.current {
    border-color: $border-accent;
    background: rgba(167, 139, 250, 0.08);
  }
}
.time-col {
  width: 50px;
  text-align: right;
  font-size: 12px;
}
.time-start {
  color: $accent-light;
  font-weight: 600;
}
.time-end {
  color: $text-tertiary;
  margin-top: 4px;
}
.timeline-content {
  flex: 1;
}
.meeting-title {
  font-size: 14px;
  color: $text-primary;
  font-weight: 500;
  margin-bottom: 4px;
}
.meeting-meta {
  font-size: 12px;
  color: $text-secondary;
}

.week-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8px;
  margin-bottom: 14px;
}
.week-col {
  text-align: center;
  padding: 8px 4px;
  border-radius: $radius-sm;
  background: rgba(167, 139, 250, 0.05);
}
.week-name {
  font-size: 11px;
  color: $text-tertiary;
}
.week-day {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
  margin: 4px 0;
}
.week-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin: 1px;
}
.dot-positive { background: $color-positive; }
.dot-accent { background: $accent; }
.dot-neutral { background: $color-neutral; }
.dot-empty {
  border: 1px solid $text-tertiary;
  background: transparent;
}

.legend {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 11px;
  color: $text-secondary;
  padding-top: 10px;
  border-top: 1px solid $border-subtle;
}
.lg-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: middle;
}
.lg-dot.positive { background: $color-positive; }
.lg-dot.accent { background: $accent; }
.lg-dot.neutral { background: $color-neutral; }
.lg-dot.empty { border: 1px solid $text-tertiary; }
</style>