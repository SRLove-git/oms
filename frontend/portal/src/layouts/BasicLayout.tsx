import { useState } from 'react'
import { Button, Layout, Menu } from '@arco-design/web-react'
import { IconHome } from '@arco-design/web-react/icon'
import { Outlet, useNavigate } from 'react-router-dom'

const { Sider, Header, Content } = Layout
const MenuItem = Menu.Item

export default function BasicLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const navigate = useNavigate()

  return (
    <Layout className="basic-layout">
      <Sider className="basic-sider" collapsed={collapsed} collapsible width={220}>
        <div className="logo">{collapsed ? 'OMS' : '商家门户'}</div>
        <Menu selectedKeys={['/']} onClickMenuItem={(key) => navigate(key)}>
          <MenuItem key="/">
            <IconHome />
            首页
          </MenuItem>
        </Menu>
      </Sider>

      <Layout>
        <Header className="basic-header">
          <Button type="text" onClick={() => setCollapsed((value) => !value)}>
            {collapsed ? '展开' : '收起'}
          </Button>
        </Header>
        <Content className="basic-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
