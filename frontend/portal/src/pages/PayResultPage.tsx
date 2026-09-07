import { Button, Card, Result, Space } from '@arco-design/web-react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router-dom'

export default function PayResultPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  const status = searchParams.get('status')
  const paymentNo = searchParams.get('paymentNo') ?? ''
  const orderNo = searchParams.get('orderNo') ?? ''
  const isSuccess = status === 'success'

  return (
    <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 64 }}>
      <Card>
        <Result
          status={isSuccess ? 'success' : 'error'}
          title={isSuccess ? t('payResult.success') : t('payResult.cancel')}
          subTitle={
            <>
              {paymentNo && (
                <div>
                  {t('payResult.paymentNo')}：{paymentNo}
                </div>
              )}
              {orderNo && (
                <div>
                  {t('payResult.orderNo')}：{orderNo}
                </div>
              )}
            </>
          }
          extra={
            <Space>
              {isSuccess && (
                <Button type="primary" onClick={() => navigate('/orders')}>
                  {t('payResult.viewOrder')}
                </Button>
              )}
              <Button onClick={() => navigate('/products')}>
                {t('payResult.continueShopping')}
              </Button>
            </Space>
          }
        />
      </Card>
    </div>
  )
}
