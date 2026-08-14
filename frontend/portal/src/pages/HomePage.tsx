import { useEffect, useState } from 'react'
import { Card, Grid, Statistic } from '@arco-design/web-react'
import { useTranslation } from 'react-i18next'

import { pageOrders } from '@/api/orders'
import { pageSkus } from '@/api/skus'
import { userStore } from '@/stores/user'

const { Row: GridRow, Col: GridCol } = Grid

export default function HomePage() {
  const { t } = useTranslation()
  const [orderTotal, setOrderTotal] = useState(0)
  const [skuTotal, setSkuTotal] = useState(0)

  const displayName = userStore.user?.realName ?? userStore.user?.username ?? ''

  useEffect(() => {
    pageOrders({ page: 1, size: 1 }).then((res) => setOrderTotal(res.total))
    pageSkus({ page: 1, size: 1 }).then((res) => setSkuTotal(res.total))
  }, [])

  return (
    <div>
      <Card title={t('home.welcome', { name: displayName })} style={{ marginBottom: 16 }}>
        {t('home.desc')}
      </Card>
      <GridRow gutter={[16, 16]}>
        <GridCol xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title={t('home.myOrders')} value={orderTotal} />
          </Card>
        </GridCol>
        <GridCol xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title={t('home.onSaleProducts')} value={skuTotal} />
          </Card>
        </GridCol>
      </GridRow>
    </div>
  )
}
