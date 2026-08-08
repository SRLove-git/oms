# ADR-0001：Monorepo 结构与后端技术栈选型

- 状态：已接受
- 日期：2026-08-08
- 关联：《项目要求.md》7.7 项目规范

## 背景

OMS 包含 6 个业务微服务、1 个 API 网关、3 个前端应用及部署配置。需要统一的仓库边界、依赖版本管理与工程规范，避免多仓库间的版本漂移与联调成本。

## 决策

1. **Monorepo 单仓**，目录划分为 `backend/`、`frontend/`、`docs/`、`deploy/`、`scripts/`。
2. **后端技术栈**：Spring Boot 4.1.0 + Spring Cloud 2025.1.2 + Spring Cloud Alibaba 2025.1.0.0，JDK 21；MyBatis-Plus + Flyway + MySQL 8；RocketMQ 异步解耦；Nacos 注册/配置中心；Sentinel 流量治理。
3. **前端**：pnpm workspace 管理 `admin`（Vue 3 + Arco Design Vue）与 `portal`（React 19 + Arco Design React）。
4. **数据一致性**：核心写链路通过本地事务 + 事务消息/幂等处理保证最终一致；后续按需引入 Seata。

## 版本说明

- Spring Cloud Alibaba 2025.1.0.0 适配 Spring Boot 4.x 与 Spring Cloud 2025.1.x，Nacos 配置使用 `spring.config.import`（已废弃 bootstrap）。
- 本地 JDK 为 26，构建目标 `--release 21`，保证工具链与 CI 一致。

## 后果

- 正向：单一来源的依赖版本、统一 CI、跨服务重构与全局搜索成本低。
- 成本：仓库体积增长、模块间变更互相影响，需依赖规范的分支与 PR 流程约束（见 CONTRIBUTING.md）。
