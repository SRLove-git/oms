# k8s

生产 K8s 编排目录（规划中）。

计划包含：

- `base/`：各服务 Deployment、Service、ConfigMap、Secret、HPA
- `overlays/`：dev / staging / prod 环境差异
- 探针：liveness/readiness 对接 `/actuator/health`
- 资源：所有容器显式声明 CPU / 内存 requests 与 limits
- 发布：不可变镜像 tag（与 Git commit 关联），滚动更新

对应项目要求 7.5（Docker 容器化部署要求）与 SCHEDULE 阶段 5。
