# 阶段四：测试与验收文档

| 文档 | 说明 |
| :--- | :--- |
| [sit-test-cases.md](./sit-test-cases.md) | SIT 用例清单与执行结果（29/29 通过） |
| [security-test-report.md](./security-test-report.md) | 安全冒烟报告（17/17 通过） |
| [perf-test-report.md](./perf-test-report.md) | 性能压测报告（P99 全部达标） |
| [compliance-review.md](./compliance-review.md) | 合规预评审（GSP/UDI/PCI-DSS） |
| [uat-plan.md](./uat-plan.md) | UAT 计划与验收口径 |
| [acceptance-report.md](./acceptance-report.md) | 验收报告与上线评审结论 |

## 执行方式

```bash
# 前置：基础设施与服务已启动（backend/README.md）
./scripts/sit-test.sh          # SIT 29 项用例
./scripts/security-test.sh     # 安全 17 项用例
N=5000 C=100 ./scripts/benchmark.sh   # 性能压测
```

## 阶段四结论

- SIT：29/29 通过，过程中发现并修复 1 个缺陷（网关缺少 `/api/v1/audit-logs` 路由）。
- 安全冒烟：17/17 通过。
- 性能：订单分页 P99 192ms、销售汇总 P99 55ms、销售趋势 P99 90ms、仓库库存 P99 113ms（目标 ≤500ms/≤1s）。
- 合规：生产前置项（真实渠道资质、HTTPS、K8s 与监控、渗透测试）列入 M6 上线前提，不阻塞当前验收。
- 验收：对照《项目要求》7.8 六项标准，五项已达标，一项（真实渠道生产结算）以上线前提形式关闭。
