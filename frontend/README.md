# frontend

OMS 前端 Monorepo（pnpm workspace）：

| 应用     | 技术                                         | 默认端口 |
| :------- | :------------------------------------------- | :------- |
| `admin`  | Vue 3 + Arco Design Vue + Pinia + Vue Router | 5173     |
| `portal` | React 19 + Arco Design React + React Router  | 5174     |

## 常用命令

```bash
pnpm install          # 安装依赖
pnpm dev              # 同时启动两个应用
pnpm build            # 构建全部
pnpm lint             # ESLint 检查
pnpm typecheck        # TypeScript 类型检查
pnpm format           # Prettier 格式化
```

## 开发代理

两个应用的 Vite 均将 `/api` 代理到 `http://localhost:8080`（oms-gateway）。后端未启动时页面可正常渲染，接口调用会报网络错误。
