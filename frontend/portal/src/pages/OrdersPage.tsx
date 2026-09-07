import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Card,
  Descriptions,
  Message,
  Modal,
  Pagination,
  Radio,
  Space,
  Table,
} from '@arco-design/web-react'
import { useTranslation } from 'react-i18next'

import { callbackMock, cancelOrder, getOrder, pageOrders, payOrder, signOrder } from '@/api/orders'
import type { OrderDetail, OrderSummary } from '@/api/orders'

export default function OrdersPage() {
  const { t } = useTranslation()
  const [loading, setLoading] = useState(false)
  const [list, setList] = useState<OrderSummary[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize] = useState(10)
  const [detail, setDetail] = useState<OrderDetail | null>(null)
  const [detailVisible, setDetailVisible] = useState(false)
  const [payModalVisible, setPayModalVisible] = useState(false)
  const [payingOrderNo, setPayingOrderNo] = useState<string | null>(null)
  const [payChannel, setPayChannel] = useState('mock')
  const [paySubmitting, setPaySubmitting] = useState(false)

  const statusName = (status: number) => t(`orders.status${status}`, { defaultValue: `#${status}` })

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

  function openPay(orderNo: string) {
    setPayingOrderNo(orderNo)
    setPayChannel('mock')
    setPayModalVisible(true)
  }

  async function confirmPay() {
    if (!payingOrderNo) {
      return
    }
    setPaySubmitting(true)
    try {
      const result = await payOrder(payingOrderNo, payChannel)
      if (payChannel !== 'mock' && result.payUrl) {
        window.location.href = result.payUrl
        return
      }
      await callbackMock({
        paymentNo: result.paymentNo,
        channelTxnNo: `TXN${Date.now()}`,
        amount: result.amount,
        status: 'SUCCESS',
      })
      Message.success(t('orders.paySuccess'))
      setPayModalVisible(false)
      load()
    } finally {
      setPaySubmitting(false)
    }
  }

  function handleCancel(orderNo: string) {
    Modal.confirm({
      title: t('orders.cancelTitle'),
      content: t('orders.cancelContent'),
      onOk: async () => {
        await cancelOrder(orderNo, t('orders.cancelReason'))
        Message.success(t('common.cancelled'))
        load()
      },
    })
  }

  async function handleSign(orderNo: string) {
    await signOrder(orderNo)
    Message.success(t('orders.signSuccess'))
    load()
  }

  return (
    <Card title={t('orders.title')}>
      <Table
        rowKey="id"
        loading={loading}
        data={list}
        pagination={false}
        scroll={{ x: 900 }}
        columns={[
          { title: t('orders.orderNo'), dataIndex: 'orderNo', width: 180 },
          {
            title: t('orders.status'),
            width: 90,
            render: (_, record) => statusName(record.status),
          },
          { title: t('orders.amount'), dataIndex: 'totalAmount', width: 110 },
          { title: t('orders.itemCount'), dataIndex: 'itemCount', width: 90 },
          { title: t('orders.createdAt'), dataIndex: 'createdAt', width: 180 },
          {
            title: t('orders.actions'),
            width: 280,
            render: (_, record) => (
              <Space>
                <Button size="mini" onClick={() => showDetail(record.orderNo)}>
                  {t('orders.detail')}
                </Button>
                {record.status === 1 && (
                  <>
                    <Button
                      size="mini"
                      type="primary"
                      onClick={() => openPay(record.orderNo)}
                    >
                      {t('orders.pay')}
                    </Button>
                    <Button
                      size="mini"
                      status="danger"
                      onClick={() => handleCancel(record.orderNo)}
                    >
                      {t('orders.cancel')}
                    </Button>
                  </>
                )}
                {record.status === 4 && (
                  <Button size="mini" type="primary" onClick={() => handleSign(record.orderNo)}>
                    {t('orders.confirmSign')}
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
        title={t('orders.detailTitle')}
        footer={null}
        style={{ width: 640 }}
        onCancel={() => setDetailVisible(false)}
      >
        {detail && (
          <>
            <Descriptions
              column={2}
              data={[
                { label: t('orders.orderNo'), value: detail.orderNo },
                { label: t('orders.status'), value: statusName(detail.status) },
                { label: t('orders.payAmount'), value: `${detail.payAmount} ${detail.currency}` },
                { label: t('orders.createdAt'), value: detail.createdAt ?? '-' },
              ]}
            />
            <Table
              rowKey="id"
              data={detail.items}
              pagination={false}
              style={{ marginTop: 16 }}
              columns={[
                { title: t('orders.sku'), dataIndex: 'skuId', width: 80 },
                { title: t('orders.product'), dataIndex: 'skuName' },
                { title: t('orders.quantity'), dataIndex: 'quantity', width: 80 },
                { title: t('orders.unitPrice'), dataIndex: 'unitPrice', width: 100 },
                { title: t('orders.subtotal'), dataIndex: 'totalPrice', width: 110 },
              ]}
            />
          </>
        )}
      </Modal>

      <Modal
        visible={payModalVisible}
        title={t('orders.payTitle')}
        okText={t('orders.payNow')}
        confirmLoading={paySubmitting}
        onCancel={() => setPayModalVisible(false)}
        onOk={confirmPay}
      >
        <Radio.Group type="button" value={payChannel} onChange={(value) => setPayChannel(value)}>
          <Radio value="mock">{t('orders.channelMock')}</Radio>
          <Radio value="visa">{t('orders.channelVisa')}</Radio>
          <Radio value="mastercard">{t('orders.channelMastercard')}</Radio>
        </Radio.Group>
      </Modal>
    </Card>
  )
}
