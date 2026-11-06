# 文档索引

本目录按用途分类，子目录与文件名带编号前缀，便于按序浏览。首次安装从 [项目 README](../README.md) 开始；使用指南说明操作与当前行为，开发契约定义接入边界，验证指南说明检查方法。文档示例使用仓库相对路径、服务端标准目录与示例名称；数据库凭据、地图和代理服务名需按实际部署填写。

```text
docs/
  01-getting-started/    安装与运行
  02-games/              游戏与地图
  03-reference/          参考手册
  04-development/        开发与契约
  05-validation/         检查与验证
  06-unattended-testing/ 无人测试
```

## 1. 安装与运行（01-getting-started）

| 编号 | 文档 | 内容 |
| --- | --- | --- |
| 1.1 | [Core 上手](../README.md#快速开始) | 运行要求、数据库、构建、加载检查 |
| 1.2 | [Bingo Worker](../championships-bingo-worker/README.md) | Folia、Redis、代理和世界重置接入 |
| 1.3 | [认证部署](01-getting-started/01-auth-deployment.md) | 可选 AuthBridge 与 AuthProxy 安装 |
| 1.4 | [跨服架构](01-getting-started/02-bingo-remote-architecture.md) | 权威边界、manifest、状态机、重放与故障恢复 |

## 2. 游戏与地图（02-games）

| 编号 | 文档 | 内容 |
| --- | --- | --- |
| 2.1 | [游戏指南](02-games/01-games.md) | 游戏标识、玩法与决赛规则 |
| 2.2 | [比赛启动](02-games/02-game-start.md) | 选图、选队、自动分配与子场地 |
| 2.3 | [地图编辑](02-games/03-map-editing.md) | 草稿、世界绑定、校验、发布与 BuildMart 蓝图 |
| 2.4 | [自由游玩](02-games/04-daily-mode.md) | DAILY 队列、同行小队、战绩和占位符 |
| 2.5 | [积分与展示](02-games/05-presentation.md) | 权重、投票、观战、聊天、PAPI 与侧栏 |
| 2.6 | [Riptide 编排](02-games/06-riptide-stage-mechanics.md) | 关卡池、木筏、淘汰和地图试玩 |
| 2.7 | [HITW 目录](02-games/07-riptide-hitw-catalog.md) | 资源来源、建筑快照、通行几何与难度 |
| 2.8 | [Riptide 规则体验场地](02-games/08-riptide-introduction.md) | 教学世界、试玩入口与交互机制 |

## 3. 参考手册（03-reference）

| 编号 | 文档 | 内容 |
| --- | --- | --- |
| 3.1 | [命令参考](03-reference/01-commands.md) | 权限、队伍、管理、赛事命令 |
| 3.2 | [Laser 枪械接入](03-reference/02-laser-weapons.md) | 枪械材质、连射与选枪菜单 |
| 3.3 | [认证同步协议](03-reference/03-auth-bridge-protocol.md) | HMAC、账号事件、ACK 与控制任务 |

## 4. 开发与契约（04-development）

| 编号 | 文档 | 内容 |
| --- | --- | --- |
| 4.1 | [开发指南](04-development/01-development.md) | 模块、源码布局、坐标、数据库、格式与文档维护 |
| 4.2 | [测试组织](04-development/02-testing.md) | 用例归属、覆盖、隔离与运行命令 |
| 4.3 | [旁观契约](04-development/03-spectator-visibility-contract.md) | 角色、实体显隐、完整 Tab、线程与生命周期 |
| 4.4 | [UUID 契约](04-development/04-player-uuid-contract.md) | 登录身份、离线录入、冲突与迁移 |
| 4.5 | [地图改名契约](04-development/05-map-rename-contract.md) | 数据库与资产事务 |

## 5. 检查与验证（05-validation）

| 编号 | 文档 | 内容 |
| --- | --- | --- |
| 5.1 | [比赛启动验证](05-validation/01-game-start-validation.md) | 全游戏启动、分区、占用与回滚 |
| 5.2 | [旁观验证](05-validation/02-spectator-visibility-validation.md) | 逐游戏资格、模式包、Tab、线程与生命周期 |
| 5.3 | [Bingo 容量验证](05-validation/03-bingo-performance-validation.md) | 压力模型、指标与端到端验收 |
| 5.4 | [BuildMart 蓝图验证](05-validation/04-buildmart-blueprint-validation.md) | 材料、完成判定、支撑与六面遮挡 |
| 5.5 | [Riptide 编辑性能](05-validation/05-riptide-editor-performance.md) | 展示校验、完整规划、缓存与采样 |

## 6. 无人测试（06-unattended-testing）

| 编号 | 文档 | 内容 |
| --- | --- | --- |
| 6.1 | [无人测试设计](06-unattended-testing/01-game-test-research.md) | 会话、身份、resident 与自动会话 |
| 6.2 | [无人测试游戏矩阵](06-unattended-testing/02-game-test-matrix.md) | 各游戏的测试拓扑、动作与验证入口 |
| 6.3 | [无人测试支持范围](06-unattended-testing/03-game-test-progress.md) | 已支持能力、验证顺序与原生验收边界 |

## 相关入口

- [仓库开发约定](../AGENTS.md) — 贡献与自动化修改约束
- [维护脚本](../scripts/README.md) — Java 格式、源码布局与 BuildMart 只读审查
- [LoadTest](../championships-bingo-loadtest/README.md) — 隔离 Folia 的区块与实体压力

文档以当前代码和默认资源为准，部署时请以实际配置中的自定义值为准。验证指南说明如何检查行为；运行报告和临时日志保存在 `/tmp`，不会替代使用指南与开发契约。
