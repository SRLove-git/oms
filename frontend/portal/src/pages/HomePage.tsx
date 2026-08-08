import { Card, Grid, Statistic } from '@arco-design/web-react'

const { Row, Col } = Grid

const stats = [
  { label: '本月销售额', value: '--' },
  { label: '进行中订单', value: '--' },
  { label: '待处理售后', value: '--' },
  { label: '库存预警', value: '--' },
]

export default function HomePage() {
  return (
    <div>
      <Card title="欢迎使用商家门户" style={{ marginBottom: 16 }}>
        项目初始化完成，订单、库存、售后等模块将按迭代计划逐步开放。
      </Card>
      <Row gutter={[16, 16]}>
        {stats.map((stat) => (
          <Col xs={24} sm={12} lg={6} key={stat.label}>
            <Card>
              <Statistic title={stat.label} value={stat.value} />
            </Card>
          </Col>
        ))}
      </Row>
    </div>
  )
}
