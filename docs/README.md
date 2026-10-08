# 文档索引

首次安装从 [项目 README](../README.md) 开始。使用指南说明操作与当前行为，开发契约定义接入边界，验证指南说明检查方法。文档示例使用仓库相对路径、服务端标准目录与示例名称；数据库凭据、地图和代理服务名需按实际部署填写。

## 安装与运行

| 文档 | 内容 |
| --- | --- |
| [Core 上手](../README.md#快速开始) | 运行要求、数据库、构建、加载检查 |
| [Bingo Worker](../championships-bingo-worker/README.md) | Folia、Redis、代理和世界重置接入 |
| [跨服架构](bingo-remote-architecture.md) | 权威边界、manifest、状态机、重放与故障恢复 |
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
| [Riptide 编排](riptide-stage-mechanics.md) | 关卡池、木筏、淘汰和地图试玩 |

## 开发与契约

| 文档 | 内容 |
| --- | --- |
| [开发指南](development.md) | 模块、源码布局、坐标、数据库、格式与文档维护 |
| [测试组织](testing.md) | 用例归属、覆盖、隔离与运行命令 |
| [旁观契约](spectator-visibility-contract.md) | 角色、实体显隐、完整 Tab、线程与生命周期 |
| [UUID 契约](player-uuid-contract.md) | 登录身份、离线录入、冲突与迁移 |
| [认证同步协议](auth-bridge-protocol.md) | HMAC、账号事件、ACK 与控制任务 |
| [地图改名契约](map-rename-contract.md) | 数据库与资产事务 |
| [HITW 目录](riptide-hitw-catalog.md) | 资源来源、建筑快照、通行几何与难度 |
| [仓库开发约定](../AGENTS.md) | 贡献与自动化修改约束 |

## 检查与验证

| 文档 | 内容 |
| --- | --- |
| [维护脚本](../scripts/README.md) | Java 格式、源码布局与 BuildMart 只读审查 |
| [比赛启动验证](game-start-validation.md) | 全游戏启动、分区、占用与回滚 |
| [旁观验证](spectator-visibility-validation.md) | 逐游戏资格、模式包、Tab、线程与生命周期 |
| [BuildMart 蓝图验证](buildmart-blueprint-validation.md) | 材料、完成判定、支撑与六面遮挡 |
| [Riptide 编辑性能](riptide-editor-performance.md) | 展示校验、完整规划、缓存与采样 |
| [Bingo 容量验证](bingo-performance-validation.md) | 压力模型、指标与端到端验收 |
| [LoadTest](../championships-bingo-loadtest/README.md) | 隔离 Folia 的区块与实体压力 |

文档以代码和默认资源为依据；部署自定义值以实际配置为准。验证指南不代表某一部署已通过验收。单次部署记录、模拟输出和审查证据保存在 `/tmp`，不作为使用指南或开发契约。
