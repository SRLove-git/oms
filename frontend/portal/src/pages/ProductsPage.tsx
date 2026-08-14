import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Form,
  Grid,
  InputNumber,
  Message,
  Modal,
  Space,
} from '@arco-design/web-react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { createOrder } from '@/api/orders'
import { pageSkus } from '@/api/skus'
import type { SkuRecord } from '@/api/skus'
import { userStore } from '@/stores/user'

const FormItem = Form.Item
const { Row, Col } = Grid

export default function ProductsPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [loading, setLoading] = useState(false)
  const [list, setList] = useState<SkuRecord[]>([])
  const [orderVisible, setOrderVisible] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [current, setCurrent] = useState<SkuRecord | null>(null)
  const [form] = Form.useForm()

  async function load() {
    setLoading(true)
    try {
      const res = await pageSkus({ page: 1, size: 100 })
      setList(res.records.filter((sku) => sku.status === 1))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  function openOrder(sku: SkuRecord) {
    setCurrent(sku)
    form.setFieldsValue({ quantity: 1 })
    setOrderVisible(true)
  }

  async function submitOrder() {
    if (!current || !userStore.user?.merchantId) {
      Message.warning(t('products.needMerchantLogin'))
      return
    }
    const values = await form.validate()
    setSubmitting(true)
    try {
      const order = await createOrder({
        merchantId: userStore.user.merchantId,
        orderType: 1,
        items: [{ skuId: current.id, quantity: values.quantity }],
      })
      Message.success(t('products.orderSuccess', { orderNo: order.orderNo }))
      setOrderVisible(false)
      navigate('/orders')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Card title={t('products.title')} loading={loading}>
      <Row gutter={[16, 16]}>
        {list.map((sku) => (
          <Col xs={24} sm={12} lg={8} key={sku.id}>
            <Card title={sku.name} style={{ marginBottom: 16 }}>
              <p>
                {t('products.sku')}：{sku.skuNo}
              </p>
              <p>
                {t('products.spec')}：{sku.spec || '-'}
              </p>
              <Space>
                <span style={{ color: 'rgb(var(--red-6))', fontSize: 18 }}>¥{sku.price}</span>
                <Button type="primary" onClick={() => openOrder(sku)}>
                  {t('products.orderNow')}
                </Button>
              </Space>
            </Card>
          </Col>
        ))}
      </Row>

      <Modal
        visible={orderVisible}
        title={t('products.orderTitle', { name: current?.name ?? '' })}
        onCancel={() => setOrderVisible(false)}
        onOk={submitOrder}
        confirmLoading={submitting}
      >
        <Form form={form} layout="vertical">
          <FormItem label={t('products.quantity')} field="quantity" initialValue={1}>
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </FormItem>
        </Form>
      </Modal>
    </Card>
  )
}
