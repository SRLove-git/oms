<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  items: { label: string; value: number; suffix?: string }[]
  color?: string
}>()

const max = computed(() => Math.max(1, ...props.items.map((item) => item.value)))

function pct(value: number) {
  return `${Math.max(0, Math.min(100, (value / max.value) * 100))}%`
}
</script>

<template>
  <div class="simple-bar">
    <div v-for="item in items" :key="item.label" class="bar-row">
      <span class="bar-label" :title="item.label">{{ item.label }}</span>
      <div class="bar-track">
        <div
          class="bar-fill"
          :style="{ width: pct(item.value), backgroundColor: color ?? 'rgb(var(--arcoblue-6))' }"
        />
      </div>
      <span class="bar-value">{{ item.value }}{{ item.suffix ?? '' }}</span>
    </div>
  </div>
</template>

<style scoped>
.simple-bar {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.bar-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.bar-label {
  width: 90px;
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bar-track {
  flex: 1;
  height: 12px;
  border-radius: 6px;
  background-color: var(--color-fill-2, #f2f3f5);
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  border-radius: 6px;
  transition: width 0.3s ease;
}
.bar-value {
  width: 70px;
  flex-shrink: 0;
  text-align: right;
  color: var(--color-text-2);
}
</style>
