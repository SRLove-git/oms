import type { RouteLocationNormalized } from 'vue-router'

import { i18n } from '@/i18n'

export function updateDocumentTitle(to: RouteLocationNormalized) {
  const title = to.meta.title as string | undefined
  document.title = title
    ? `${i18n.global.t(title)} - ${i18n.global.t('app.name')}`
    : i18n.global.t('app.name')
}
