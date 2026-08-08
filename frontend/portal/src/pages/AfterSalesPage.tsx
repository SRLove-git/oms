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

const TYPE_NAMES: Record<number, string> = {
  1: '退货',
  2: '换货',
  3: '维修',
}

const STATUS_NAMES: Record<number, string> = {
  1: '待审核',
  2: '已通过',
  3: '已驳回',
  4: '收货质检',
  5: '退款中',
  6: '已完成',
  7: '已取消',
}

export default function AfterSalesPage() {
  const navigate = useNavigate()
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
  const [selectedItems, setSelectedItems] = useState<Array<{ orderItemId: number; skuId: number; quantity: number }>>([])
  const [submitting, setSubmitting] = useState(false)

  const merchantId = userStore.user?.merchantId

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
      Message.success(`已加载订单 ${order.orderNo}，共 ${order.items.length} 个商品`)
    } catch {
      setOrderItems([])
    }
  }

  async function submitApply() {
    if (!orderNo || selectedItems.length === 0) {
      Message.warning('请选择订单与售后商品')
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
      Message.success('售后申请已提交')
      setApplyVisible(false)
      load()
    } finally {
      setSubmitting(false)
    }
  }

  function doCancel(row: ReturnOrderSummary) {
    Modal.confirm({
      title: '取消售后单',
      content: `确定取消售后单 ${row.returnNo}？`,
      onOk: async () => {
        await cancelReturnOrder(row.returnNo)
        Message.success('已取消')
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
    { title: '售后单号', dataIndex: 'returnNo' },
    { title: '订单号', dataIndex: 'orderNo' },
    {
      title: '类型',
      dataIndex: 'type',
      render: (value: number) => TYPE_NAMES[value] ?? value,
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: (value: number) => (
        <Tag color={value === 6 ? 'green' : value === 3 || value === 7 ? 'red' : 'arcoblue'}>
          {STATUS_NAMES[value] ?? value}
        </Tag>
      ),
    },
    { title: '金额', dataIndex: 'totalAmount' },
    { title: '申请时间', dataIndex: 'createdAt' },
    {
      title: '操作',
      dataIndex: 'operations',
      render: (_: unknown, row: ReturnOrderSummary) =>
        row.status === 1 || row.status === 2 || row.status === 4 ? (
          <Button size="small" status="danger" onClick={() => doCancel(row)}>
            取消申请
          </Button>
        ) : null,
    },
  ]

  return (
    <div>
      <Card
        title="我的售后"
        extra={
          <Button type="primary" onClick={openApply}>
            申请售后
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
        title="申请售后"
        visible={applyVisible}
        onCancel={() => setApplyVisible(false)}
        onOk={submitApply}
        okButtonProps={{ loading: submitting }}
        style={{ width: 640 }}
      >
        <Form layout="vertical">
          <FormItem label="订单号">
            <Input.Group>
              <Input
                value={orderNo}
                onChange={(value) => setOrderNo(value)}
                placeholder="输入已收货/已完成订单号"
                style={{ width: '70%' }}
              />
              <Button style={{ marginLeft: 8 }} onClick={loadOrder}>
                加载商品
              </Button>
            </Input.Group>
          </FormItem>
          <FormItem label="售后类型">
            <Select value={applyType} onChange={(value) => setApplyType(Number(value))}>
              <Select.Option value={1}>退货</Select.Option>
              <Select.Option value={2}>换货</Select.Option>
              <Select.Option value={3}>维修</Select.Option>
            </Select>
          </FormItem>
          <FormItem label="售后原因">
            <Input.TextArea
              value={applyReason}
              onChange={setApplyReason}
              placeholder="请描述售后原因"
              rows={2}
            />
          </FormItem>
          <FormItem label="选择售后商品（默认全量）">
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
                { title: '商品', dataIndex: 'skuName' },
                { title: '数量', dataIndex: 'quantity' },
                { title: '单价', dataIndex: 'unitPrice' },
              ]}
            />
          </FormItem>
        </Form>
      </Modal>
    </div>
  )
}
