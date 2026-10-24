<template>
  <div class="ring-chart" :style="{ width: size + 'px', height: size + 'px' }">
    <svg :width="size" :height="size" viewBox="0" class="ring">
      <circle :cx="r" :cy="r" :r="radius" fill="none" :stroke="trackColor" :stroke-width="stroke" />
      <circle
        :cx="r"
        :cy="r"
        :r="radius"
        fill="none"
        :stroke="color"
        :stroke-width="stroke"
        stroke-linecap="round"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="dashoffset"
        :transform="`rotate(-90 ${r} ${r})`"
      />
    </svg>
    <div class="ring-center">
      <slot>
        <span class="ring-text">{{ percent }}%</span>
      </slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

defineOptions({ name: "RingChart" });

const props = withDefaults(
  defineProps<{
    percent: number;
    size?: number;
    stroke?: number;
    color?: string;
    trackColor?: string;
  }>(),
  {
    size: 100,
    stroke: 8,
    color: "#7c3aed",
    trackColor: "rgba(167,139,250,0.12)"
  }
);

const r = computed(() => props.size / 2);
const radius = computed(() => r.value - props.stroke / 2);
const circumference = computed(() => 2 * Math.PI * radius.value);
const dashoffset = computed(() => {
  const p = Math.max(0, Math.min(100, props.percent));
  return circumference.value * (1 - p / 100);
});
</script>

<style scoped lang="scss">
.ring-chart {
  position: relative;
}
.ring {
  display: block;
}
.ring-center {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 22px;
  color: $text-primary;
}
</style>