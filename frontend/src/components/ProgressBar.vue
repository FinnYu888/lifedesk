<template>
  <div class="progress-bar">
    <div class="progress-head">
      <span class="progress-label">{{ label }}</span>
      <span class="progress-pct" :style="{ color: color }">{{ percent }}%</span>
    </div>
    <div class="progress-track">
      <div
        class="progress-fill"
        :style="{
          width: Math.max(0, Math.min(100, percent)) + '%',
          background: color
        }"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: "ProgressBar" });

withDefaults(
  defineProps<{
    label: string;
    percent: number;
    color?: string;
  }>(),
  { color: "#7c3aed" }
);
</script>

<style scoped lang="scss">
.progress-bar {
  margin-bottom: 12px;
}
.progress-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 6px;
}
.progress-label {
  font-size: 13px;
  color: $text-secondary;
}
.progress-pct {
  font-size: 13px;
  font-weight: 600;
}
.progress-track {
  height: 8px;
  background: rgba(167, 139, 250, 0.1);
  border-radius: 4px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.4s ease;
}
</style>