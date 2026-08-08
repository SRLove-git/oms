import { useState } from 'react'
import { Button, Layout, Menu, Message } from '@arco-design/web-react'
import { IconHome, IconList, IconUnorderedList } from '@arco-design/web-react/icon'
import { Outlet, useNavigate } from 'react-router-dom'

import { userStore } from '@/stores/user'

const { Sider, Header, Content } = Layout
const MenuItem = Menu.Item

export default function BasicLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const navigate = useNavigate()

  function handleLogout() {
    userStore.logout()
    Message.success('已退出登录')
    navigate('/login')
  }

  return (
    <Layout className="basic-layout">
      <Sider className="basic-sider" collapsed={collapsed} collapsible width={220}>
        <div className="logo">{collapsed ? 'OMS' : '商家门户'}</div>
        <Menu
          selectedKeys={[window.location.pathname]}
          onClickMenuItem={(key) => navigate(key)}
        >
          <MenuItem key="/">
            <IconHome />
            首页
          </MenuItem>
          <MenuItem key="/products">
            <IconList />
            商品下单
          </MenuItem>
          <MenuItem key="/orders">
            <IconUnorderedList />
            我的订单
          </MenuItem>
        </Menu>
      </Sider>

      <Layout>
        <Header className="basic-header">
          <Button type="text" onClick={() => setCollapsed((value) => !value)}>
            {collapsed ? '展开' : '收起'}
          </Button>
          <div className="header-user">
            <span>{userStore.user?.realName ?? userStore.user?.username}</span>
            <Button type="text" size="small" onClick={handleLogout}>
              退出登录
            </Button>
          </div>
        </Header>
        <Content className="basic-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
