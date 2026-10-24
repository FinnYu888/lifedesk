<template>
  <span class="status-badge" :class="`status-${type}`">
    <span class="dot" />
    <slot />
  </span>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    type?: "positive" | "warning" | "danger" | "neutral" | "accent";
  }>(),
  { type: "neutral" }
);
</script>

<style scoped lang="scss">
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.status-positive {
  color: $color-positive;
  background: rgba(45, 212, 191, 0.12);
}
.status-warning {
  color: $color-warning;
  background: rgba(251, 191, 36, 0.12);
}
.status-danger {
  color: $color-danger;
  background: rgba(248, 113, 113, 0.14);
}
.status-neutral {
  color: $text-secondary;
  background: rgba(167, 139, 250, 0.08);
}
.status-accent {
  color: $accent-light;
  background: rgba(124, 58, 237, 0.18);
}
</style>