<script setup lang="ts">
const stats = [
  { label: '今日订单', value: 0 },
  { label: '待处理售后', value: 0 },
  { label: '库存预警', value: 0 },
  { label: '待审核资质', value: 0 },
]
</script>

<template>
  <div class="home">
    <a-card :bordered="false" class="welcome-card">
      <template #title>欢迎使用 OMS 管理端</template>
      项目初始化完成，后端接口与前端页面将按迭代计划逐步接入。
    </a-card>

    <a-row :gutter="[16, 16]" class="stat-row">
      <a-col v-for="stat in stats" :key="stat.label" :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false">
          <a-statistic :title="stat.label" :value="stat.value" />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>
