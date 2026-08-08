<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Message } from '@arco-design/web-vue'

import {
  pageMessages,
  pageTemplates,
  retryMessage,
  saveTemplate,
  type MessageRecord,
  type TemplateRecord,
} from '@/api/integrations'

const CHANNEL_NAMES: Record<string, string> = {
  sms: '短信',
  email: '邮件',
  in_app: '站内信',
  wechat: '微信模板',
}

const loading = ref(false)
const messages = ref<MessageRecord[]>([])
const messageTotal = ref(0)
const page = ref(1)
const pageSize = ref(10)

const templates = ref<TemplateRecord[]>([])
const templateForm = ref({
  code: '',
  name: '',
  channel: 'in_app',
  scene: '',
  titleTemplate: '',
  contentTemplate: '',
})

async function loadMessages() {
  loading.value = true
  try {
    const res = await pageMessages({ page: page.value, size: pageSize.value })
    messages.value = res.records
    messageTotal.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadTemplates() {
  const res = await pageTemplates({ page: 1, size: 100 })
  templates.value = res.records
}

async function doSaveTemplate() {
  await saveTemplate({
    code: templateForm.value.code,
    name: templateForm.value.name,
    channel: templateForm.value.channel,
    scene: templateForm.value.scene,
    titleTemplate: templateForm.value.titleTemplate,
    contentTemplate: templateForm.value.contentTemplate,
  })
  Message.success('模板已保存')
  templateForm.value = {
    code: '',
    name: '',
    channel: 'in_app',
    scene: '',
    titleTemplate: '',
    contentTemplate: '',
  }
  loadTemplates()
}

async function doRetry(row: MessageRecord) {
  await retryMessage(row.id)
  Message.success('已重试')
  loadMessages()
}

function onPageChange(value: number) {
  page.value = value
  loadMessages()
}

onMounted(() => {
  loadMessages()
  loadTemplates()
})
</script>

<template>
  <a-row :gutter="16">
    <a-col :span="10">
      <a-card :bordered="false" title="通知模板">
        <a-form :model="templateForm" layout="vertical">
          <a-form-item label="模板编码">
            <a-input v-model="templateForm.code" placeholder="如 AFTER_SALES_APPLIED" />
          </a-form-item>
          <a-form-item label="名称">
            <a-input v-model="templateForm.name" />
          </a-form-item>
          <a-form-item label="渠道">
            <a-select v-model="templateForm.channel">
              <a-option value="sms">短信</a-option>
              <a-option value="email">邮件</a-option>
              <a-option value="in_app">站内信</a-option>
              <a-option value="wechat">微信模板</a-option>
            </a-select>
          </a-form-item>
          <a-form-item label="场景">
            <a-input v-model="templateForm.scene" placeholder="如 AFTER_SALES_APPLIED" />
          </a-form-item>
          <a-form-item label="标题模板">
            <a-input v-model="templateForm.titleTemplate" />
          </a-form-item>
          <a-form-item label="内容模板">
            <a-textarea v-model="templateForm.contentTemplate" :rows="3" />
          </a-form-item>
          <a-button type="primary" @click="doSaveTemplate">保存模板</a-button>
        </a-form>
        <a-table
          class="template-table"
          row-key="id"
          :data="templates"
          :pagination="false"
          size="small"
        >
          <template #columns>
            <a-table-column title="编码" data-index="code" />
            <a-table-column title="渠道" :width="80">
              <template #cell="{ record }">{{ CHANNEL_NAMES[record.channel] || record.channel }}</template>
            </a-table-column>
            <a-table-column title="场景" data-index="scene" />
          </template>
        </a-table>
      </a-card>
    </a-col>
    <a-col :span="14">
      <a-card :bordered="false" title="发送记录">
        <a-table row-key="id" :loading="loading" :data="messages" :pagination="false" :scroll="{ x: 800 }">
          <template #columns>
            <a-table-column title="消息号" data-index="messageNo" :width="180" />
            <a-table-column title="渠道" :width="80">
              <template #cell="{ record }">{{ CHANNEL_NAMES[record.channel] || record.channel }}</template>
            </a-table-column>
            <a-table-column title="接收方" data-index="receiver" :width="140" />
            <a-table-column title="标题" data-index="title" />
            <a-table-column title="状态" :width="90">
              <template #cell="{ record }">
                <a-tag :color="record.status === 1 ? 'green' : 'red'">
                  {{ record.status === 1 ? '成功' : '失败' }}
                </a-tag>
              </template>
            </a-table-column>
            <a-table-column title="操作" :width="90" fixed="right">
              <template #cell="{ record }">
                <a-button v-if="record.status !== 1" size="mini" @click="doRetry(record)">重试</a-button>
              </template>
            </a-table-column>
          </template>
        </a-table>
        <a-pagination
          class="pagination"
          :total="messageTotal"
          :current="page"
          :page-size="pageSize"
          show-total
          @change="onPageChange"
        />
      </a-card>
    </a-col>
  </a-row>
</template>

<style scoped>
.template-table {
  margin-top: 16px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
