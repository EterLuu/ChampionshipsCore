# 无人测试支持范围

无人测试用真实 Minecraft 协议客户端代替手动组队，让开发者走完整的代理、认证、Paper、队伍、游戏和结算链路。本文说明当前支持的能力、推荐的验证顺序以及仍需原生验收的范围；会话设计与身份见 [无人测试设计](game-test-research.md)，逐游戏拓扑见 [无人测试游戏矩阵](game-test-matrix.md)，命令见 [命令参考](commands.md)。

## 支持的能力

| 能力 | 说明 |
| --- | --- |
| Core 单局 `TEST` | 测试会话、16 队×4 名册、临时队伍、签名 lease、开发者入队/离队/旁观和统一清理 |
| Core `TEST_EVENT` | 按 profile 顺序推进多轮，轮间保留测试名册和 standings，正常结束后清理会话 |
| resident Harness | 无限等待窗口、stop file、8 个 worker 承载 64 Bot，输出完整连接报告 |
| 阶段感知动作 Harness | 识别等待/投票/准备/倒计时/进行/结束，覆盖全部游戏 profile；动作按服务端实体、方块和背包状态驱动，先瞄准和距离判定再出手，战斗类选择真实武器槽 |
| 可执行游戏矩阵契约 | 每个游戏声明动作族、机制动作（signature）、机制检查、阶段掉线点、服务端证据和积分信号，作为报告校验依据 |
| 阶段与重连契约 | 每个 profile 声明各阶段动作边界、服务端证据，以及每个掉线阶段重连后应保留的身份、队伍、世界、模式和回合/结算状态 |
| 场景测试 | `npm run test:scenarios` 在无网络条件下对全部 profile 验证阶段、投票点击、动作包、重连、计分板和结束状态 |
| cc-web Bot 身份 | provision、`CUSTOM_UUID` profile、`lease web`、CIDR 同步与 AuthMe/UUID 校验 |
| 离线隔离夹具 | 按 `OfflinePlayer:<name>` 生成签名 lease，适合没有 cc-web 的临时环境 |
| 远程 Bingo | TEST/TEST_EVENT manifest、Worker admission/release 与测试 ownership；完整世界重置闭环按远程矩阵继续验收 |
| 正式功能兼容 | 没有活动测试会话时，正式 `GAME`、`EVENT`、`DAILY` 继续使用原有队伍、赛程、计分和 Redis 路径 |

## 推荐的验证顺序

1. 用 `resident + idle` 验证连接、认证、队伍、传送、旁观和退出。
2. 用真实游戏实例验证准备、比赛、死亡/重生、结算和第二场解锁。
3. 用 `game` profile 与 `CC_TEST_GAME`，为需要操作的游戏验证阶段动作、机制和重连。
4. 对远程 Bingo 额外检查同一 match 的 `READY → STARTED → FINISHED/ABORTED → RELEASED`、三世界重建、Core 临时队伍清零和下一场解锁。

## 协议级与原生验证

`npm run test:scenarios` 为全部 profile 提供协议级阶段、动作、重连、计分和结束场景证据，可在没有运行服务端的环境执行。它证明 Harness 能按契约产生动作并核对报告，不替代真实地图上的原生验收。

原生机制、积分、阶段掉线、第二场和远程世界重建必须在对应地图上实际开局验证。未完成该验收的游戏在矩阵中保持“可执行 profile；原生行为待验收”，不标记为已完成。

## 代码与配置入口

Core 会话、名册、身份和命令位于 `championships-core` 的 `api/test` 与 `command/game` 包；默认 Event profile 位于 `src/main/resources/test/events/`。Harness 位于 `championships-test-harness`，不打包进任何生产插件。Core 与 Worker 的 `test.enabled` 默认关闭；生产与调试共用时可显式开启，但测试写入属于当前数据域，远程 Worker 仍受单自然世界 slot 限制。

扩展测试时保持上述顺序，并在以下位置同步修改：

- 游戏动作与阶段边界：`championships-test-harness/src/matrix.js`、`src/profiles/`；
- 报告校验：`championships-test-harness/src/validation.js`；
- Core 会话与命令：`championships-core` 的 `api/test`、`command/game`、`src/main/resources/test/events/`；
- 文档：本页、[游戏矩阵](game-test-matrix.md) 和 Harness README。

协议和部署升级见 [Bingo 跨服架构](bingo-remote-architecture.md) 与 [认证部署](auth-deployment.md)。
