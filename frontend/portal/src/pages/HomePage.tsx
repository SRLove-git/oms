import { useEffect, useState } from 'react'
import { Card, Grid, Statistic } from '@arco-design/web-react'

import { pageOrders } from '@/api/orders'
import { pageSkus } from '@/api/skus'
import { userStore } from '@/stores/user'

const { Row: GridRow, Col: GridCol } = Grid

export default function HomePage() {
  const [orderTotal, setOrderTotal] = useState(0)
  const [skuTotal, setSkuTotal] = useState(0)

  useEffect(() => {
    pageOrders({ page: 1, size: 1 }).then((res) => setOrderTotal(res.total))
    pageSkus({ page: 1, size: 1 }).then((res) => setSkuTotal(res.total))
  }, [])

  return (
    <div>
      <Card title={`欢迎，${userStore.user?.realName ?? userStore.user?.username}`} style={{ marginBottom: 16 }}>
        P0 核心交易链路已就绪：浏览商品 → 下单预占库存 → 模拟支付 → 审核发货 → 签收完成。
      </Card>
      <GridRow gutter={[16, 16]}>
        <GridCol xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title="我的订单" value={orderTotal} />
          </Card>
        </GridCol>
        <GridCol xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title="在售商品" value={skuTotal} />
          </Card>
        </GridCol>
      </GridRow>
    </div>
  )
}
