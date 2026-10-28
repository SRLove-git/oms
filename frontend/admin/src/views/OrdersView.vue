<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'

import {
  auditOrder,
  cancelJtShipment,
  cancelOrder,
  completeOrder,
  createJtShipment,
  getJtConnote,
  getOrder,
  pageOrders,
  shipOrder,
  signOrder,
} from '@/api/orders'
import type { OrderDetail, OrderSummary } from '@/api/orders'
import { getLogisticsByOrder } from '@/api/integrations'
import type { LogisticsRecord } from '@/api/integrations'

const STATUS_NAMES: Record<number, string> = {
  1: '待支付',
  2: '已支付',
  3: '已审核',
  4: '已发货',
  5: '已签收',
  6: '已完成',
  7: '已取消',
  8: '售后处理中',
}

const LOGISTICS_STATUS_NAMES: Record<string, string> = {
  created: '已建单',
  pending_pickup: '待揽收',
  in_transit: '运输中',
  out_for_delivery: '派送中',
  signed: '已签收',
  cancelled: '已取消',
  returned: '退回中',
  exception: '异常',
}

const loading = ref(false)
const list = ref<OrderSummary[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const statusFilter = ref<number | undefined>(undefined)
const detailVisible = ref(false)
const detail = ref<OrderDetail | null>(null)
const shipVisible = ref(false)
const shipping = ref(false)
const logisticsVisible = ref(false)
const logisticsLoading = ref(false)
const logistics = ref<LogisticsRecord | null>(null)
const logisticsOrderStatus = ref<number | null>(null)
const labelLoading = ref(false)
const cancelShipmentLoading = ref(false)
const shipForm = reactive({
  orderNo: '',
  trackingNo: '',
  carrier: 'J&T Express SG',
  contactName: '',
  phoneNumber: '',
  address: '',
  postcode: '',
  weight: 500,
  weightUnit: 'G' as 'G' | 'Kg',
  serviceCode: 'DOM123',
})

async function load() {
  loading.value = true
  try {
    const res = await pageOrders({
      status: statusFilter.value,
      page: page.value,
      size: pageSize.value,
    })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function showDetail(orderNo: string) {
  detail.value = await getOrder(orderNo)
  detailVisible.value = true
}

async function doAudit(orderNo: string) {
  await auditOrder(orderNo)
  Message.success('审核通过')
  load()
}

async function doSign(orderNo: string) {
  await signOrder(orderNo)
  Message.success('已确认签收')
  load()
}

async function doComplete(orderNo: string) {
  await completeOrder(orderNo)
  Message.success('订单已完成')
  load()
}

function doCancel(orderNo: string) {
  Modal.confirm({
    title: '取消订单',
    content: '确定取消该订单？待支付订单将释放库存。',
    onOk: async () => {
      await cancelOrder(orderNo, '管理员取消')
      Message.success('已取消')
      load()
    },
  })
}

async function openShip(orderNo: string) {
  shipForm.orderNo = orderNo
  shipForm.trackingNo = ''
  shipForm.carrier = 'J&T Express SG'
  const order = await getOrder(orderNo)
  shipForm.contactName = order.consignee || ''
  shipForm.phoneNumber = order.phone || ''
  shipForm.address = order.address || ''
  const postcodeMatch = order.address?.match(/\b\d{6}\b/)
  shipForm.postcode = postcodeMatch?.[0] || ''
  shipVisible.value = true
}

async function submitShip() {
  if (shipForm.carrier === 'J&T Express SG' && !shipForm.trackingNo.trim()) {
    if (!shipForm.contactName.trim() || !shipForm.phoneNumber.trim() || !shipForm.address.trim()) {
      Message.error('请填写收件人、联系电话和收货地址')
      return
    }
    if (!/^\d{6}$/.test(shipForm.postcode.trim())) {
      Message.error('请输入 6 位新加坡邮编')
      return
    }
    if (!(Number(shipForm.weight) > 0)) {
      Message.error('包裹重量必须大于 0')
      return
    }
  }
  shipping.value = true
  try {
    let trackingNo = shipForm.trackingNo.trim()
    if (shipForm.carrier === 'J&T Express SG' && !trackingNo) {
      const shipment = await createJtShipment({
        reference_number: shipForm.orderNo,
        service_code: shipForm.serviceCode,
        consignee_details: {
          contact_name: shipForm.contactName,
          phone_number: shipForm.phoneNumber,
          address: shipForm.address,
          postcode: shipForm.postcode,
        },
        item_details: [
          {
            weight: Number(shipForm.weight),
            weight_unit: shipForm.weightUnit,
            description: `OMS order ${shipForm.orderNo}`,
            quantity: 1,
          },
        ],
      })
      trackingNo = shipment.tracking_id
    }
    if (!trackingNo) {
      Message.error('请输入运单号')
      return
    }
    await shipOrder(shipForm.orderNo, trackingNo, shipForm.carrier)
    Message.success(`已发货，运单号 ${trackingNo}`)
    shipVisible.value = false
    load()
  } finally {
    shipping.value = false
  }
}

async function showLogistics(orderNo: string, orderStatus: number) {
  logisticsOrderStatus.value = orderStatus
  logisticsLoading.value = true
  try {
    logistics.value = await getLogisticsByOrder(orderNo)
    logisticsVisible.value = true
  } catch {
    logistics.value = null
  } finally {
    logisticsLoading.value = false
  }
}

async function printLabel() {
  const current = logistics.value
  if (!current || current.carrier !== 'J&T Express SG') {
    return
  }
  labelLoading.value = true
  try {
    const connote = await getJtConnote(current.trackingNo, 'a6')
    if (!connote.connote_label_url) {
      Message.error('极兔未返回面单地址')
      return
    }
    window.open(connote.connote_label_url, '_blank', 'noopener,noreferrer')
  } finally {
    labelLoading.value = false
  }
}

function cancelShipment() {
  const current = logistics.value
  if (!current || current.carrier !== 'J&T Express SG') {
    return
  }
  if (logisticsOrderStatus.value !== 3) {
    Message.warning('已发货订单请走售后/退货流程，不能直接取消运单')
    return
  }
  Modal.confirm({
    title: '取消极兔运单',
    content: `确定取消运单 ${current.trackingNo}？取消后需重新建单才能发货。`,
    onOk: async () => {
      cancelShipmentLoading.value = true
      try {
        await cancelJtShipment(current.trackingNo, '管理员取消运单')
        Message.success('极兔运单已取消')
        await showLogistics(current.orderNo, 3)
      } finally {
        cancelShipmentLoading.value = false
      }
    },
  })
}

function onPageChange(value: number) {
  page.value = value
  load()
}

function onSizeChange(value: number) {
  pageSize.value = value
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <a-card :bordered="false" title="订单管理">
    <a-space class="toolbar" :size="12">
      <a-select
        v-model="statusFilter"
        placeholder="全部状态"
        allow-clear
        :options="
          Object.entries(STATUS_NAMES).map(([value, label]) => ({ value: Number(value), label }))
        "
        style="width: 160px"
        @change="load"
      />
      <a-button type="primary" @click="load">查询</a-button>
    </a-space>

    <a-table row-key="id" :loading="loading" :data="list" :pagination="false" :scroll="{ x: 900 }">
      <template #columns>
        <a-table-column title="订单号" data-index="orderNo" :width="180" />
        <a-table-column title="商户 ID" data-index="merchantId" :width="90" />
        <a-table-column title="类型" :width="80">
          <template #cell="{ record }">{{ record.orderType === 2 ? 'B2C' : 'B2B' }}</template>
        </a-table-column>
        <a-table-column title="状态" :width="90">
          <template #cell="{ record }">{{ STATUS_NAMES[record.status] }}</template>
        </a-table-column>
        <a-table-column title="金额" data-index="totalAmount" :width="110" />
        <a-table-column title="商品数" data-index="itemCount" :width="90" />
        <a-table-column title="创建时间" data-index="createdAt" :width="180" />
        <a-table-column title="操作" :width="380" fixed="right">
          <template #cell="{ record }">
            <a-space>
              <a-button size="mini" @click="showDetail(record.orderNo)">详情</a-button>
              <a-button
                v-if="[3, 4, 5, 6].includes(record.status)"
                size="mini"
                @click="showLogistics(record.orderNo, record.status)"
              >
                物流
              </a-button>
              <a-button
                v-if="record.status === 2"
                size="mini"
                type="primary"
                @click="doAudit(record.orderNo)"
              >
                审核
              </a-button>
              <a-button
                v-if="record.status === 3"
                size="mini"
                type="primary"
                @click="openShip(record.orderNo)"
              >
                发货
              </a-button>
              <a-button
                v-if="record.status === 4"
                size="mini"
                type="primary"
                @click="doSign(record.orderNo)"
              >
                签收
              </a-button>
              <a-button
                v-if="record.status === 5"
                size="mini"
                type="primary"
                @click="doComplete(record.orderNo)"
              >
                完成
              </a-button>
              <a-button
                v-if="record.status === 1"
                size="mini"
                status="danger"
                @click="doCancel(record.orderNo)"
              >
                取消
              </a-button>
            </a-space>
          </template>
        </a-table-column>
      </template>
    </a-table>

    <a-pagination
      class="pagination"
      :total="total"
      :current="page"
      :page-size="pageSize"
      show-total
      show-page-size
      @change="onPageChange"
      @page-size-change="onSizeChange"
    />

    <a-modal v-model:visible="detailVisible" title="订单详情" :footer="false" :width="720">
      <a-descriptions v-if="detail" :column="2" bordered>
        <a-descriptions-item label="订单号">{{ detail.orderNo }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ STATUS_NAMES[detail.status] }}</a-descriptions-item>
        <a-descriptions-item label="应付金额">
          {{ detail.payAmount }} {{ detail.currency }}
        </a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ detail.createdAt }}</a-descriptions-item>
        <a-descriptions-item label="超时时间">{{ detail.timeoutAt }}</a-descriptions-item>
        <a-descriptions-item label="备注">{{ detail.remark || '-' }}</a-descriptions-item>
        <a-descriptions-item label="收件人">{{ detail.consignee || '-' }}</a-descriptions-item>
        <a-descriptions-item label="联系电话">{{ detail.phone || '-' }}</a-descriptions-item>
        <a-descriptions-item label="收货地址" :span="2">
          {{
            detail.address || '-'
          }}
        </a-descriptions-item>
      </a-descriptions>

      <a-table
        v-if="detail"
        row-key="id"
        :data="detail.items"
        :pagination="false"
        class="detail-table"
      >
        <template #columns>
          <a-table-column title="SKU" data-index="skuId" :width="80" />
          <a-table-column title="商品" data-index="skuName" />
          <a-table-column title="数量" data-index="quantity" :width="80" />
          <a-table-column title="单价" data-index="unitPrice" :width="100" />
          <a-table-column title="小计" data-index="totalPrice" :width="110" />
        </template>
      </a-table>

      <a-timeline v-if="detail" class="detail-timeline">
        <a-timeline-item v-for="(log, index) in detail.logs" :key="index" :label="log.createdAt">
          {{ STATUS_NAMES[log.toStatus] }}（{{ log.operatorName || '-' }}）{{ log.remark || '' }}
        </a-timeline-item>
      </a-timeline>
    </a-modal>

    <a-modal v-model:visible="logisticsVisible" title="物流信息" :footer="false" :width="640">
      <template v-if="logistics">
        <a-descriptions :column="2" bordered size="small">
          <a-descriptions-item label="承运商">{{ logistics.carrier }}</a-descriptions-item>
          <a-descriptions-item label="运单号">{{ logistics.trackingNo }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            {{ LOGISTICS_STATUS_NAMES[logistics.status] || logistics.status }}
          </a-descriptions-item>
          <a-descriptions-item label="订单号">{{ logistics.orderNo }}</a-descriptions-item>
          <a-descriptions-item label="更新时间" :span="2">
            {{ logistics.updatedAt }}
          </a-descriptions-item>
        </a-descriptions>

        <a-space style="margin-top: 16px">
          <a-button
            v-if="logistics.carrier === 'J&T Express SG'"
            type="primary"
            :loading="labelLoading"
            @click="printLabel"
          >
            打印 A6 面单
          </a-button>
          <a-button
            v-if="logistics.carrier === 'J&T Express SG' && logisticsOrderStatus === 3"
            status="danger"
            :loading="cancelShipmentLoading"
            :disabled="['signed', 'cancelled', 'returned'].includes(logistics.status)"
            @click="cancelShipment"
          >
            取消运单
          </a-button>
          <span v-if="logistics.carrier !== 'J&T Express SG'" style="color: #86909c; font-size: 12px">
            非极兔运单
          </span>
          <span
            v-else-if="logisticsOrderStatus !== 3"
            style="color: #86909c; font-size: 12px"
          >
            已发货订单请走售后/退货流程
          </span>
        </a-space>

        <a-timeline v-if="logistics.trace.length" class="detail-timeline">
          <a-timeline-item v-for="(trace, index) in logistics.trace" :key="index">
            {{ trace }}
          </a-timeline-item>
        </a-timeline>
      </template>
    </a-modal>

    <a-modal v-model:visible="shipVisible" title="订单发货" :ok-loading="shipping" @ok="submitShip">
      <a-form layout="vertical" :model="shipForm">
        <a-form-item label="承运商">
          <a-select
            v-model="shipForm.carrier"
            :options="
              ['J&T Express SG', 'SF', 'STO', 'ZTO', 'YTO'].map((c) => ({ value: c, label: c }))
            "
          />
        </a-form-item>
        <template v-if="shipForm.carrier === 'J&T Express SG'">
          <a-alert type="info" style="margin-bottom: 16px">
            留空运单号时，系统会通过 J&T API 创建运单并自动回填。
          </a-alert>
          <a-form-item label="现有运单号（可选）">
            <a-input
              v-model="shipForm.trackingNo"
              placeholder="已有运单可直接填写；新建运单请留空"
            />
          </a-form-item>
          <a-form-item label="收件人">
            <a-input v-model="shipForm.contactName" />
          </a-form-item>
          <a-form-item label="联系电话">
            <a-input v-model="shipForm.phoneNumber" />
          </a-form-item>
          <a-form-item label="收货地址">
            <a-textarea v-model="shipForm.address" :auto-size="{ minRows: 2, maxRows: 4 }" />
          </a-form-item>
          <a-form-item label="新加坡邮编">
            <a-input v-model="shipForm.postcode" :max-length="6" placeholder="6 位邮编" />
          </a-form-item>
          <a-space fill>
            <a-form-item label="包裹重量">
              <a-input-number v-model="shipForm.weight" :min="0.01" />
            </a-form-item>
            <a-form-item label="重量单位">
              <a-select
                v-model="shipForm.weightUnit"
                :options="['G', 'Kg'].map((c) => ({ value: c, label: c }))"
                style="width: 110px"
              />
            </a-form-item>
            <a-form-item label="服务代码">
              <a-select
                v-model="shipForm.serviceCode"
                :options="['DOM123', 'DOMN'].map((c) => ({ value: c, label: c }))"
                style="width: 130px"
              />
            </a-form-item>
          </a-space>
        </template>
        <a-form-item v-else label="运单号">
          <a-input v-model="shipForm.trackingNo" placeholder="请输入运单号" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.detail-table {
  margin-top: 16px;
}

.detail-timeline {
  margin-top: 16px;
}
</style>
