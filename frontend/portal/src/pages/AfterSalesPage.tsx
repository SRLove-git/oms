import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Form,
  Input,
  Message,
  Modal,
  Select,
  Table,
  Tag,
} from '@arco-design/web-react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { userStore } from '@/stores/user'
import {
  applyReturnOrder,
  cancelReturnOrder,
  pageReturnOrders,
  type ReturnOrderSummary,
} from '@/api/aftersales'
import { getOrder, type OrderItemRecord } from '@/api/orders'

const FormItem = Form.Item

export default function AfterSalesPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [list, setList] = useState<ReturnOrderSummary[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize] = useState(10)
  const [loading, setLoading] = useState(false)

  const [applyVisible, setApplyVisible] = useState(false)
  const [applyType, setApplyType] = useState(1)
  const [applyReason, setApplyReason] = useState('')
  const [orderNo, setOrderNo] = useState('')
  const [orderItems, setOrderItems] = useState<OrderItemRecord[]>([])
  const [selectedItems, setSelectedItems] = useState<
    Array<{ orderItemId: number; skuId: number; quantity: number }>
  >([])
  const [submitting, setSubmitting] = useState(false)

  const merchantId = userStore.user?.merchantId

  const typeName = (type: number) => t(`aftersales.type${type}`, { defaultValue: `#${type}` })
  const statusName = (status: number) =>
    t(`aftersales.status${status}`, { defaultValue: `#${status}` })

  async function load() {
    setLoading(true)
    try {
      const res = await pageReturnOrders({ page, size: pageSize })
      setList(res.records)
      setTotal(res.total)
    } finally {
      setLoading(false)
    }
  }

  async function openApply() {
    setApplyVisible(true)
    setOrderItems([])
    setSelectedItems([])
    setOrderNo('')
    setApplyReason('')
    setApplyType(1)
  }

  async function loadOrder() {
    try {
      const order = await getOrder(orderNo)
      setOrderItems(order.items)
      setSelectedItems([])
      Message.success(t('aftersales.loaded', { orderNo: order.orderNo, total: order.items.length }))
    } catch {
      setOrderItems([])
    }
  }

  async function submitApply() {
    if (!orderNo || selectedItems.length === 0) {
      Message.warning(t('aftersales.selectRequired'))
      return
    }
    setSubmitting(true)
    try {
      await applyReturnOrder({
        orderNo,
        type: applyType,
        reason: applyReason,
        items: selectedItems,
      })
      Message.success(t('aftersales.submitted'))
      setApplyVisible(false)
      load()
    } finally {
      setSubmitting(false)
    }
  }

  function doCancel(row: ReturnOrderSummary) {
    Modal.confirm({
      title: t('aftersales.cancelTitle'),
      content: t('aftersales.cancelContent', { returnNo: row.returnNo }),
      onOk: async () => {
        await cancelReturnOrder(row.returnNo)
        Message.success(t('common.cancelled'))
        load()
      },
    })
  }

  useEffect(() => {
    if (!merchantId) {
      navigate('/login')
      return
    }
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [merchantId, page])

  const columns = [
    { title: t('aftersales.returnNo'), dataIndex: 'returnNo' },
    { title: t('aftersales.orderNo'), dataIndex: 'orderNo' },
    {
      title: t('aftersales.type'),
      dataIndex: 'type',
      render: (value: number) => typeName(value),
    },
    {
      title: t('aftersales.status'),
      dataIndex: 'status',
      render: (value: number) => (
        <Tag color={value === 6 ? 'green' : value === 3 || value === 7 ? 'red' : 'arcoblue'}>
          {statusName(value)}
        </Tag>
      ),
    },
    { title: t('aftersales.amount'), dataIndex: 'totalAmount' },
    { title: t('aftersales.createdAt'), dataIndex: 'createdAt' },
    {
      title: t('aftersales.actions'),
      dataIndex: 'operations',
      render: (_: unknown, row: ReturnOrderSummary) =>
        row.status === 1 || row.status === 2 || row.status === 4 ? (
          <Button size="small" status="danger" onClick={() => doCancel(row)}>
            {t('aftersales.cancelApply')}
          </Button>
        ) : null,
    },
  ]

  return (
    <div>
      <Card
        title={t('aftersales.title')}
        extra={
          <Button type="primary" onClick={openApply}>
            {t('aftersales.apply')}
          </Button>
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          data={list}
          pagination={{
            total,
            current: page,
            pageSize,
            onChange: (value) => setPage(value),
          }}
        />
      </Card>

      <Modal
        title={t('aftersales.applyTitle')}
        visible={applyVisible}
        onCancel={() => setApplyVisible(false)}
        onOk={submitApply}
        okButtonProps={{ loading: submitting }}
        style={{ width: 640 }}
      >
        <Form layout="vertical">
          <FormItem label={t('aftersales.orderNo')}>
            <Input.Group>
              <Input
                value={orderNo}
                onChange={(value) => setOrderNo(value)}
                placeholder={t('aftersales.orderNoPlaceholder')}
                style={{ width: '70%' }}
              />
              <Button style={{ marginLeft: 8 }} onClick={loadOrder}>
                {t('aftersales.loadItems')}
              </Button>
            </Input.Group>
          </FormItem>
          <FormItem label={t('aftersales.typeLabel')}>
            <Select value={applyType} onChange={(value) => setApplyType(Number(value))}>
              <Select.Option value={1}>{t('aftersales.type1')}</Select.Option>
              <Select.Option value={2}>{t('aftersales.type2')}</Select.Option>
              <Select.Option value={3}>{t('aftersales.type3')}</Select.Option>
            </Select>
          </FormItem>
          <FormItem label={t('aftersales.reasonLabel')}>
            <Input.TextArea
              value={applyReason}
              onChange={setApplyReason}
              placeholder={t('aftersales.reasonPlaceholder')}
              rows={2}
            />
          </FormItem>
          <FormItem label={t('aftersales.itemsLabel')}>
            <Table
              rowKey="id"
              size="small"
              pagination={false}
              data={orderItems}
              rowSelection={{
                type: 'checkbox',
                selectedRowKeys: selectedItems.map((item) => item.orderItemId),
                onChange: (_keys, rows) =>
                  setSelectedItems(
                    rows.map((row) => ({
                      orderItemId: row.id,
                      skuId: row.skuId,
                      quantity: row.quantity,
                    })),
                  ),
              }}
              columns={[
                { title: t('aftersales.product'), dataIndex: 'skuName' },
                { title: t('aftersales.quantity'), dataIndex: 'quantity' },
                { title: t('aftersales.unitPrice'), dataIndex: 'unitPrice' },
              ]}
            />
          </FormItem>
        </Form>
      </Modal>
    </div>
  )
}
