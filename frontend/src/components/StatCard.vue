<template>
  <div class="stat-card ld-card">
    <div class="stat-label">{{ label }}</div>
    <div class="stat-value">{{ value }}</div>
    <div class="stat-note" :class="noteCls">{{ note }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

defineOptions({ name: "StatCard" });

const props = withDefaults(
  defineProps<{
    label: string;
    value: string | number;
    note?: string;
    noteType?: "positive" | "warning" | "danger" | "neutral";
  }>(),
  { note: "", noteType: "neutral" }
);

const noteCls = computed(() => `text-${props.noteType}`);
</script>

<style scoped lang="scss">
.stat-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.stat-label {
  font-size: 12px;
  color: $text-tertiary;
}
.stat-value {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.1;
  color: $text-primary;
}
.stat-note {
  font-size: 13px;
}
.text-positive {
  color: $color-positive;
}
.text-warning {
  color: $color-warning;
}
.text-danger {
  color: $color-danger;
}
.text-neutral {
  color: $text-secondary;
}
</style>