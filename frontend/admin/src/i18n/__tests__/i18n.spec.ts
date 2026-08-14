import { beforeEach, describe, expect, it } from 'vitest'

import i18n, { getInitialLocale, isSupportedLocale } from '@/i18n'
import enUS from '@/i18n/locales/en-US'
import zhCN from '@/i18n/locales/zh-CN'

describe('i18n', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'zh-CN'
  })

  it('zh-CN 消息包含布局与登录页关键 key', () => {
    expect(zhCN.menu.orders).toBe('订单管理')
    expect(zhCN.menu.reports).toBe('报表中心')
    expect(zhCN.login.title).toBe('OMS 管理端登录')
    expect(zhCN.common.logout).toBe('退出登录')
    expect(zhCN.app.name).toBe('OMS 管理端')
  })

  it('en-US 消息包含布局与登录页关键 key', () => {
    expect(enUS.menu.orders).toBe('Orders')
    expect(enUS.menu.reports).toBe('Reports')
    expect(enUS.login.title).toBe('OMS Admin Login')
    expect(enUS.common.logout).toBe('Sign Out')
    expect(enUS.app.name).toBe('OMS Admin')
  })

  it('两个 locale 都能通过 t() 取到布局关键 key', () => {
    i18n.global.locale.value = 'zh-CN'
    expect(i18n.global.t('menu.orders')).toBe('订单管理')
    expect(i18n.global.t('menu.home')).toBe('首页')
    expect(i18n.global.t('login.title')).toBe('OMS 管理端登录')
    expect(i18n.global.t('common.logout')).toBe('退出登录')

    i18n.global.locale.value = 'en-US'
    expect(i18n.global.t('menu.orders')).toBe('Orders')
    expect(i18n.global.t('menu.home')).toBe('Home')
    expect(i18n.global.t('login.title')).toBe('OMS Admin Login')
    expect(i18n.global.t('common.logout')).toBe('Sign Out')
  })

  it('切换语言后 t() 结果随之变化', () => {
    const before = i18n.global.t('menu.home')
    expect(before).toBe('首页')

    i18n.global.locale.value = 'en-US'
    const after = i18n.global.t('menu.home')
    expect(after).toBe('Home')
    expect(after).not.toBe(before)
  })

  it('isSupportedLocale 与 getInitialLocale 正常工作', () => {
    expect(isSupportedLocale('zh-CN')).toBe(true)
    expect(isSupportedLocale('en-US')).toBe(true)
    expect(isSupportedLocale('fr-FR')).toBe(false)

    localStorage.setItem('oms-locale', 'en-US')
    expect(getInitialLocale()).toBe('en-US')
    localStorage.removeItem('oms-locale')
    expect(getInitialLocale()).toBe('zh-CN')
  })
})
