import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Card,
  Descriptions,
  Message,
  Modal,
  Pagination,
  Space,
  Table,
} from '@arco-design/web-react'

import {
  callbackMock,
  cancelOrder,
  getOrder,
  pageOrders,
  payOrder,
  signOrder,
} from '@/api/orders'
import type { OrderDetail, OrderSummary } from '@/api/orders'

const STATUS_NAMES: Record<number, string> = {
  1: '待支付',
  2: '已支付',
  3: '已审核',
  4: '已发货',
  5: '已签收',
  6: '已完成',
  7: '已取消',
}

export default function OrdersPage() {
  const [loading, setLoading] = useState(false)
  const [list, setList] = useState<OrderSummary[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize] = useState(10)
  const [detail, setDetail] = useState<OrderDetail | null>(null)
  const [detailVisible, setDetailVisible] = useState(false)
  const [paying, setPaying] = useState<string | null>(null)
  const [payModalVisible, setPayModalVisible] = useState(false)
  const [payInfo, setPayInfo] = useState<{ paymentNo: string; amount: string } | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await pageOrders({ page, size: pageSize })
      setList(res.records)
      setTotal(res.total)
    } finally {
      setLoading(false)
    }
  }, [page, pageSize])

  useEffect(() => {
    load()
  }, [load])

  async function showDetail(orderNo: string) {
    setDetail(await getOrder(orderNo))
    setDetailVisible(true)
  }

  async function handlePay(orderNo: string) {
    setPaying(orderNo)
    try {
      const result = await payOrder(orderNo)
      setPayInfo({ paymentNo: result.paymentNo, amount: result.amount })
      setPayModalVisible(true)
    } finally {
      setPaying(null)
    }
  }

  async function simulatePay() {
    if (!payInfo) {
      return
    }
    await callbackMock({
      paymentNo: payInfo.paymentNo,
      channelTxnNo: `TXN${Date.now()}`,
      amount: payInfo.amount,
      status: 'SUCCESS',
    })
    Message.success('支付成功')
    setPayModalVisible(false)
    load()
  }

  function handleCancel(orderNo: string) {
    Modal.confirm({
      title: '取消订单',
      content: '确定取消该订单？将释放预占库存。',
      onOk: async () => {
        await cancelOrder(orderNo, '商户取消')
        Message.success('已取消')
        load()
      },
    })
  }

  async function handleSign(orderNo: string) {
    await signOrder(orderNo)
    Message.success('已确认签收')
    load()
  }

  return (
    <Card title="我的订单">
      <Table
        rowKey="id"
        loading={loading}
        data={list}
        pagination={false}
        scroll={{ x: 900 }}
        columns={[
          { title: '订单号', dataIndex: 'orderNo', width: 180 },
          {
            title: '状态',
            width: 90,
            render: (_, record) => STATUS_NAMES[record.status],
          },
          { title: '金额', dataIndex: 'totalAmount', width: 110 },
          { title: '商品数', dataIndex: 'itemCount', width: 90 },
          { title: '创建时间', dataIndex: 'createdAt', width: 180 },
          {
            title: '操作',
            width: 280,
            render: (_, record) => (
              <Space>
                <Button size="mini" onClick={() => showDetail(record.orderNo)}>
                  详情
                </Button>
                {record.status === 1 && (
                  <>
                    <Button size="mini" type="primary" loading={paying === record.orderNo} onClick={() => handlePay(record.orderNo)}>
                      去支付
                    </Button>
                    <Button size="mini" status="danger" onClick={() => handleCancel(record.orderNo)}>
                      取消
                    </Button>
                  </>
                )}
                {record.status === 4 && (
                  <Button size="mini" type="primary" onClick={() => handleSign(record.orderNo)}>
                    确认签收
                  </Button>
                )}
              </Space>
            ),
          },
        ]}
      />
      <Pagination
        className="pagination"
        total={total}
        current={page}
        pageSize={pageSize}
        showTotal
        onChange={(value) => setPage(value)}
      />

      <Modal
        visible={detailVisible}
        title="订单详情"
        footer={null}
        style={{ width: 640 }}
        onCancel={() => setDetailVisible(false)}
      >
        {detail && (
          <>
            <Descriptions
              column={2}
              data={[
                { label: '订单号', value: detail.orderNo },
                { label: '状态', value: STATUS_NAMES[detail.status] },
                { label: '应付金额', value: `${detail.payAmount} ${detail.currency}` },
                { label: '创建时间', value: detail.createdAt ?? '-' },
              ]}
            />
            <Table
              rowKey="id"
              data={detail.items}
              pagination={false}
              style={{ marginTop: 16 }}
              columns={[
                { title: 'SKU', dataIndex: 'skuId', width: 80 },
                { title: '商品', dataIndex: 'skuName' },
                { title: '数量', dataIndex: 'quantity', width: 80 },
                { title: '单价', dataIndex: 'unitPrice', width: 100 },
                { title: '小计', dataIndex: 'totalPrice', width: 110 },
              ]}
            />
          </>
        )}
      </Modal>

      <Modal
        visible={payModalVisible}
        title="模拟支付"
        okText="模拟支付成功"
        onCancel={() => setPayModalVisible(false)}
        onOk={simulatePay}
      >
        支付单号：{payInfo?.paymentNo}
        <br />
        支付金额：{payInfo?.amount} CNY
      </Modal>
    </Card>
  )
}
