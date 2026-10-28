import { vi } from 'vitest'

// 部分 jsdom 版本不向 globalThis 暴露 localStorage，i18n 初始化会直接读取它。
if (typeof globalThis.localStorage === 'undefined') {
  const store = new Map<string, string>()
  const storage = {
    get length() {
      return store.size
    },
    clear: () => store.clear(),
    getItem: (key: string) => store.get(key) ?? null,
    key: (index: number) => Array.from(store.keys())[index] ?? null,
    removeItem: (key: string) => {
      store.delete(key)
    },
    setItem: (key: string, value: string) => {
      store.set(key, String(value))
    },
  } as Storage
  Object.defineProperty(globalThis, 'localStorage', { value: storage, configurable: true })
}

// jsdom 未实现 matchMedia，Arco grid 的响应式布局依赖它
if (typeof window.matchMedia !== 'function') {
  window.matchMedia = (query: string) =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }) as unknown as MediaQueryList
}

// jsdom 未实现 ResizeObserver，部分组件挂载时会创建观察器
if (typeof window.ResizeObserver !== 'function') {
  class MockResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  window.ResizeObserver = MockResizeObserver as unknown as typeof ResizeObserver
}
