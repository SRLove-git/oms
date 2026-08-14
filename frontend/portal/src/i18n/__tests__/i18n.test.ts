import { describe, expect, it } from 'vitest'

import i18n from '@/i18n'
import enUS from '@/i18n/locales/en-US'
import zhCN from '@/i18n/locales/zh-CN'

function flattenKeys(obj: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(obj).flatMap(([key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key
    if (typeof value === 'object' && value !== null) {
      return flattenKeys(value as Record<string, unknown>, path)
    }
    return [path]
  })
}

describe('i18n', () => {
  it('zh-CN and en-US resources have identical key sets', () => {
    expect(flattenKeys(zhCN).sort()).toEqual(flattenKeys(enUS).sort())
  })

  it('t() returns the current language and changes after changeLanguage', async () => {
    await i18n.changeLanguage('zh-CN')
    expect(i18n.t('login.title')).toBe('商家门户登录')
    expect(i18n.t('orders.status1')).toBe('待支付')

    await i18n.changeLanguage('en-US')
    expect(i18n.t('login.title')).toBe('Merchant Portal Login')
    expect(i18n.t('orders.status1')).toBe('Pending Payment')
  })

  it('falls back to zh-CN for an unknown language', async () => {
    await i18n.changeLanguage('fr-FR')
    expect(i18n.t('login.title')).toBe('商家门户登录')

    // restore default for other tests in this file
    await i18n.changeLanguage('zh-CN')
  })
})
