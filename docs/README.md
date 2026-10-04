# 文档索引

首次安装从 [项目 README](../README.md) 开始。文档示例使用仓库相对路径、服务端标准目录与示例名称；数据库凭据、地图和代理服务名需按实际部署填写。

## 安装与运行

| 文档 | 内容 |
| --- | --- |
| [Core 上手](../README.md#快速开始) | 运行要求、数据库、构建、加载检查 |
| [Bingo Worker](../championships-bingo-worker/README.md) | Folia、Redis、代理和世界重置接入 |
| [跨服架构](bingo-remote-architecture.md) | 权威边界、manifest、状态机、重放与故障恢复 |
| [Bingo 容量验证](bingo-64-player-performance-report.md) | 压力模型、指标与端到端验收 |
| [认证部署](auth-deployment.md) | 可选 AuthBridge 与 AuthProxy 安装 |

## 游戏管理

| 文档 | 内容 |
| --- | --- |
| [游戏指南](games.md) | 游戏标识、玩法与决赛规则 |
| [命令参考](commands.md) | 权限、队伍、管理、赛事命令 |
| [比赛启动](game-start.md) | 选图、选队、自动分配与子场地 |
| [地图编辑](map-editing.md) | 草稿、世界绑定、校验、发布与 BuildMart 蓝图 |
| [自由游玩](daily-mode.md) | DAILY 队列、同行小队、战绩和占位符 |
| [积分与展示](presentation.md) | 权重、投票、观战、聊天、PAPI 与侧栏 |
| [Riptide 编排](../tools/riptide-stage-mechanics.md) | 关卡池、木筏、淘汰和地图试玩 |

## 开发契约

- [开发指南](development.md)：模块、源码布局、坐标、数据库、格式和测试。
- [旁观契约](spectator-visibility-contract.md)：角色、实体显隐、完整 Tab、线程和生命周期。
- [UUID 契约](player-uuid-contract.md)：登录身份、离线录入、冲突与迁移。
- [认证同步协议](auth-bridge-protocol.md)：HMAC、账号事件、ACK 和控制任务。
- [地图改名契约](map-rename-contract.md)：数据库和资产事务。
- [仓库开发约定](../AGENTS.md)：贡献与自动化修改的约束。

## 验证工具

- [LoadTest](../championships-bingo-loadtest/README.md)：隔离 Folia 的区块和实体压力。
- [HITW 目录与评级](../tools/riptide-hitw/README.md)：来源、碰撞几何和地图评级。
- [Riptide 移动模拟](../tools/riptide-dodge-sim/README.md)：参数验证与轨迹回放。
- [BuildMart 蓝图检查](../tools/buildmart-review-2026-09-20.md)、[遮挡检查](../tools/buildmart-visibility-2026-09-30.md)。
- [Riptide 编辑性能](../tools/riptide-editor-performance-2026-09-28.md)、[测试组织](../tools/test-cleanup-2026-09-21.md)。
- [旁观验证](../tools/spectator-visibility-audit-2026-10-03.md)、[启动验证](../tools/unified-start-audit-2026-10-04.md)。

带日期的既有文件名保留用于链接兼容；内容提供通用检查方法，不包含私人服务器的部署记录。
