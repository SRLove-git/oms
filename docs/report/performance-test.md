# 性能压测说明（阶段三 WBS-3.2）

## 目标（项目要求 7.2）

| 场景 | 目标 |
| :--- | :--- |
| 下单、支付回调等核心接口 | P99 ≤ 500ms |
| 普通查询（含报表） | P99 ≤ 1s |
| 峰值容量 | 按业务规模调整，规划 5,000 QPS |

## 已做的性能优化

1. **报表结果缓存**：销售/库存/支付报表接口使用 Redis 缓存（key 前缀 `oms:report:`，默认 TTL 5 分钟，`oms.report.cache-ttl-seconds` 可配）。缓存故障自动降级回数据库查询。
2. **报表索引**（Flyway 迁移）：
   - `order`：`idx_paid_at`、`idx_status_paid`
   - `order_item`：`idx_order_sku`
   - `order_payment`：`idx_status_updated`
   - `inventory`：`idx_warehouse`、`idx_sku`
   - `inventory_transaction`：`idx_sku_created`、`idx_biz_type_created`
   - `payment_transaction`：`idx_channel_created`、`idx_status_created`
   - `reconciliation_record`：`idx_biz_date`
   - `return_order` / `repair_order` / `refund_record`：报表查询索引
3. **库存流水口径修正**：扣减（出库）时 `inventory_transaction` 记录实际出库数量（`change_quantity = -take`，before/after 记录预占量变化），支撑周转率报表且保证流水守恒。
4. **冷热分离**：180 天前终态订单自动归档，热表规模可控。

## 压测方法

前置：基础设施与全部服务已启动（见 backend/README.md）。

```bash
# 默认 2000 请求 / 50 并发（读侧接口）
./scripts/benchmark.sh

# 自定义请求数与并发
N=10000 C=200 ./scripts/benchmark.sh
```

脚本依次压测：订单分页列表、销售报表汇总、销售趋势、仓库库存报表，并输出吞吐与延迟分布（`ab` 提供 99% 分位）。当前以本机单实例为目标即可；达到 5,000 QPS 级别需要：

- 多实例水平扩展（服务已支持，`SERVER_PORT` 独立 + Nacos 注册）
- MySQL 只读副本或报表库分离（报表走读写分离数据源）
- 消息削峰与限流（Sentinel 已接入）

## 结论判定

- 单机压测下 P99 ≤ 500ms（核心）/ ≤ 1s（查询）视为达标；
- 未达标时优先检查慢查询（`EXPLAIN` 是否命中新增索引）、缓存命中率、连接池水位。
