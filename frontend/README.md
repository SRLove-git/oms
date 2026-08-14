# frontend

OMS 前端 Monorepo（pnpm workspace）：

| 应用     | 技术                                         | 默认端口 |
| :------- | :------------------------------------------- | :------- |
| `admin`  | Vue 3 + Arco Design Vue + Pinia + Vue Router | 5173     |
| `portal` | React 19 + Arco Design React + React Router  | 5174     |
| `mobile` | Vue 3 + Arco Design Vue（移动端 H5）         | 5175     |

## 常用命令

```bash
pnpm install          # 安装依赖
pnpm dev              # 同时启动全部应用
pnpm build            # 构建全部
pnpm test             # 运行全部单元测试（Vitest）
pnpm lint             # ESLint 检查
pnpm typecheck        # TypeScript 类型检查
pnpm format           # Prettier 格式化
```

## 体验能力（admin / portal / mobile 一致）

- **国际化**：中文（zh-CN）/ 英文（en-US）切换，语言持久化到 `localStorage('oms-locale')`，Arco 组件 locale 同步切换（admin/mobile 用 vue-i18n，portal 用 react-i18next）。
- **暗色主题**：亮/暗主题切换，通过 `body[arco-theme='dark']` 生效，持久化到 `localStorage('oms-theme')`，启动时自动恢复。
- 应用结构：`src/api`（接口）、`src/components`（组件）、`src/views`（admin）或 `src/pages`（portal）、`src/router`、`src/stores`、`src/styles`、`src/i18n`。

## 开发代理

各应用的 Vite 均将 `/api` 代理到 `http://localhost:8080`（oms-gateway）。后端未启动时页面可正常渲染，接口调用会报网络错误。

## 移动端 H5（mobile）

移动优先的商家端：商品搜索/详情/下单/支付、我的订单（状态操作）、售后申请与列表、我的（语言/主题/退出）。最大宽度 480px 居中，底部 TabBar 适配安全区（`env(safe-area-inset-bottom)`），支持平板拉伸。演示账号 `merchant / merchant123`。
