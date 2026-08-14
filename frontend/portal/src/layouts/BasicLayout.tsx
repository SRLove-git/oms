import { useState } from 'react'
import { Button, Dropdown, Layout, Menu, Message } from '@arco-design/web-react'
import {
  IconHome,
  IconLanguage,
  IconList,
  IconMoon,
  IconRefresh,
  IconSun,
  IconUnorderedList,
} from '@arco-design/web-react/icon'
import { useTranslation } from 'react-i18next'
import { Outlet, useNavigate } from 'react-router-dom'

import { appStore, useAppStore } from '@/stores/app'
import type { Locale } from '@/stores/app'
import { userStore } from '@/stores/user'

const { Sider, Header, Content } = Layout
const MenuItem = Menu.Item

export default function BasicLayout() {
  const { t } = useTranslation()
  const { locale, theme } = useAppStore()
  const [collapsed, setCollapsed] = useState(false)
  const navigate = useNavigate()

  function handleLogout() {
    userStore.logout()
    Message.success(t('common.logoutSuccess'))
    navigate('/login')
  }

  return (
    <Layout className="basic-layout">
      <Sider className="basic-sider" collapsed={collapsed} collapsible width={220}>
        <div className="logo">{collapsed ? 'OMS' : t('layout.portalName')}</div>
        <Menu selectedKeys={[window.location.pathname]} onClickMenuItem={(key) => navigate(key)}>
          <MenuItem key="/">
            <IconHome />
            {t('layout.home')}
          </MenuItem>
          <MenuItem key="/products">
            <IconList />
            {t('layout.products')}
          </MenuItem>
          <MenuItem key="/orders">
            <IconUnorderedList />
            {t('layout.orders')}
          </MenuItem>
          <MenuItem key="/after-sales">
            <IconRefresh />
            {t('layout.afterSales')}
          </MenuItem>
        </Menu>
      </Sider>

      <Layout>
        <Header className="basic-header">
          <Button type="text" onClick={() => setCollapsed((value) => !value)}>
            {collapsed ? t('common.expand') : t('common.collapse')}
          </Button>
          <div className="header-tools">
            <Dropdown
              trigger="click"
              droplist={
                <Menu onClickMenuItem={(key) => appStore.setLocale(key as Locale)}>
                  <MenuItem key="zh-CN">简体中文</MenuItem>
                  <MenuItem key="en-US">English</MenuItem>
                </Menu>
              }
            >
              <Button type="text" icon={<IconLanguage />}>
                {locale === 'zh-CN' ? '中文' : 'EN'}
              </Button>
            </Dropdown>
            <Button
              type="text"
              icon={theme === 'dark' ? <IconSun /> : <IconMoon />}
              aria-label={theme === 'dark' ? t('layout.switchToLight') : t('layout.switchToDark')}
              onClick={() => appStore.toggleTheme()}
            />
          </div>
          <div className="header-user">
            <span>{userStore.user?.realName ?? userStore.user?.username}</span>
            <Button type="text" size="small" onClick={handleLogout}>
              {t('common.logout')}
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
