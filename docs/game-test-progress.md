# 无人测试当前状态

本文描述当前代码提供的测试能力和接下来适合补充的验证范围。它记录产品状态，不记录每一次临时运行的过程；单次运行的原始日志放在隔离环境的报告目录中。

## 已具备的能力

| 能力 | 当前状态 |
| --- | --- |
| Core 单局 `TEST` | 已接入测试会话、16×4 名册、临时队伍、签名 lease、开发者入队/离队/旁观和统一清理 |
| Core `TEST_EVENT` | 已按 profile 顺序推进多轮，轮间保留测试名册和 standings，正常结束后清理会话 |
| resident Harness | 已支持无限等待窗口、stop file、8 worker 承载 64 Bot 和完整连接报告 |
| cc-web Bot | 已支持 provision、`CUSTOM_UUID` profile、`lease web`、CIDR 同步和 AuthMe/UUID 校验 |
| 离线隔离夹具 | 支持按 `OfflinePlayer:<name>` 生成签名 lease，适合没有 cc-web 的临时环境 |
| 远程 Bingo | 已接入 TEST/TEST_EVENT manifest、Worker admission/release 和独立测试数据域；完整世界重置闭环仍按远程矩阵继续验收 |
| 正式功能兼容 | 没有活动测试会话时，正式 `GAME`、`EVENT`、`DAILY` 继续使用原有队伍、赛程、计分和 Redis 路径 |

## 最近的原生基线

隔离 `phase1bn` 环境完成了一次 Core Test Event 与 resident 联合验收：

- session `a381e256-b59b-4ded-9cc3-47f51e8634a9` 达到 64 名已认证玩家和 16 个临时队伍；
- `smoke` profile 连续运行两轮 `TNTRun astra`，状态从 `RUNNING / round=0` 进入 `round=1`，随后自然变为 `COMPLETED`，standings 为 64；
- resident Harness 直到 Event 完成后才收到 stop file，64 个 Bot 的 `connected`、`loggedIn`、`activeAtFinish` 均为 64，8 个 worker 均以 `control-file` 正常结束；
- 执行 `/cc game test stop --confirm` 后，Core 返回无活动测试会话，临时队伍和测试身份 lease 已清理。

这次运行使用离线隔离夹具的签名 UUID。cc-web `CUSTOM_UUID` 的 64 Bot 登录和 Core READY 另有独立原生验证；两种身份来源都经过服务器实际 UUID 校验，使用方式见 [无人测试设计](game-test-research.md)。

## 代码和配置入口

Core 会话、名册、身份和命令位于 `championships-core` 的 `api/test` 与 `command/game` 包；默认 Event profile 位于 `src/main/resources/test/events/`。Harness 位于 `championships-test-harness`，不打包进任何生产插件。Core 与 Worker 的 `test.enabled` 默认关闭，测试部署必须使用独立数据域。

## 验证矩阵

当前通用生命周期已经覆盖登录、16×4 READY、比赛启动、自然结束、stop 清理和 resident 长驻。逐游戏动作、完整远程 Bingo 世界重建、活动中 reload/crash 接管、正式赛事并存和开发者全部状态字段的原生验证仍按 [无人测试游戏矩阵](game-test-matrix.md) 推进。

扩展测试时保持以下顺序：

1. 用 `resident + idle` 验证连接、认证、队伍、传送、旁观和退出。
2. 用真实游戏实例验证准备、比赛、死亡/重生、结算和第二场解锁。
3. 为需要操作的游戏增加 Harness profile，并检查游戏自己的结果和持久化写入。
4. 对远程 Bingo 额外检查同一 match 的 `READY → STARTED → FINISHED/ABORTED → RELEASED`、三世界重建、Core 临时队伍清零和下一场解锁。

命令、权限和 lease 格式以 [命令参考](commands.md) 为准；协议和部署升级见 [Bingo 跨服架构](bingo-remote-architecture.md) 与 [认证部署](auth-deployment.md)。
