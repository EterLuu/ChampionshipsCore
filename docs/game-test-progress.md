# 无人测试当前状态

本文描述当前代码提供的测试能力和接下来适合补充的验证范围。它记录产品状态，不记录每一次临时运行的过程；单次运行的原始日志放在隔离环境的报告目录中。

## 已具备的能力

| 能力 | 当前状态 |
| --- | --- |
| Core 单局 `TEST` | 已接入测试会话、16×4 名册、临时队伍、签名 lease、开发者入队/离队/旁观和统一清理 |
| Core `TEST_EVENT` | 已按 profile 顺序推进多轮，轮间保留测试名册和 standings，正常结束后清理会话 |
| resident Harness | 已支持无限等待窗口、stop file、8 worker 承载 64 Bot 和完整连接报告 |
| 阶段感知动作 Harness | 已支持等待/投票/准备/倒计时/进行/结束识别、17 个游戏 profile、统一动作发包、阶段动作边界、服务端证据、计分板快照和阶段断线注入 |
| 可执行游戏矩阵契约 | `championships-test-harness/src/matrix.js` 为 17 个游戏声明动作族、机制检查、阶段掉线点和积分信号；CI 进度/结束断言会自动消费该契约 |
| 阶段/重连契约 | 每个 profile 额外声明各阶段动作边界、服务端证据和每个掉线阶段的身份、队伍、世界、模式、回合/结算保留项；装备菜单选择与世界修改均受 profile/运行模式约束 |
| 无网络 profile 场景 | `npm run test:scenarios` 已对全部 17 个 profile 验证阶段、投票点击、动作包、重连、计分板和结束状态 |
| cc-web Bot | 已支持 provision、`CUSTOM_UUID` profile、`lease web`、CIDR 同步和 AuthMe/UUID 校验 |
| 离线隔离夹具 | 支持按 `OfflinePlayer:<name>` 生成签名 lease，适合没有 cc-web 的临时环境 |
| 远程 Bingo | 已接入 TEST/TEST_EVENT manifest、Worker admission/release 和测试 ownership；完整世界重置闭环仍按远程矩阵继续验收 |
| 正式功能兼容 | 没有活动测试会话时，正式 `GAME`、`EVENT`、`DAILY` 继续使用原有队伍、赛程、计分和 Redis 路径 |

## 最近的原生基线

隔离 `phase1bn` 环境完成了一次 Core Test Event 与 resident 联合验收：

- session `a381e256-b59b-4ded-9cc3-47f51e8634a9` 达到 64 名已认证玩家和 16 个临时队伍；
- `smoke` profile 连续运行两轮 `TNTRun astra`，状态从 `RUNNING / round=0` 进入 `round=1`，随后自然变为 `COMPLETED`，standings 为 64；
- resident Harness 直到 Event 完成后才收到 stop file，64 个 Bot 的 `connected`、`loggedIn`、`activeAtFinish` 均为 64，8 个 worker 均以 `control-file` 正常结束；
- 执行 `/cc game test stop --confirm` 后，Core 返回无活动测试会话，临时队伍和测试身份 lease 已清理。

这次运行使用离线隔离夹具的签名 UUID。cc-web `CUSTOM_UUID` 的 64 Bot 登录和 Core READY 另有独立原生验证；两种身份来源都经过服务器实际 UUID 校验，使用方式见 [无人测试设计](game-test-research.md)。

当前生产链路另有一次通过 `cc-bungeecord:25566` 的真实 smoke：cc-web provision、AuthMe、AuthProxy/Bungee、Paper 登录、传送确认和 keep-alive 均成功，单个 Bot 在 `WAITING` 保持在线 15 秒。2026-11-03 又以同一入口完成 64 Bot 批量基线：64/64 连接、认证、Paper 登录、世界就绪和在线保持成功，8 个 worker 均正常结束，所有 Bot 的服务器 UUID 在本次运行内唯一且未发生重连漂移；cc-web PostgreSQL 核对 `autobot00..63` 为 64 个唯一 `accounts`/`bot_accounts`，没有重复创建账号。随后用 `CC_TEST_ACTION=game CC_TEST_GAME=tntrun` 完成单 Bot 阶段感知 smoke，`WAITING` 阶段动作和实体/物品/消息样本证据已记录；最新报告为 `/tmp/cc-native-bungee-25566-game-waiting-final.json`。这些运行仍没有启动比赛，因此不计入任一游戏的机制、积分、结算或第二场证据。这里的 `25566` 是 cc-bungeecord 容器内的普通 listener；宿主映射端口是 25567，25565 listener 要求 PROXY protocol，不能拿宿主端口直接替代该测试入口。

## 代码和配置入口

Core 会话、名册、身份和命令位于 `championships-core` 的 `api/test` 与 `command/game` 包；默认 Event profile 位于 `src/main/resources/test/events/`。Harness 位于 `championships-test-harness`，不打包进任何生产插件。Core 与 Worker 的 `test.enabled` 默认关闭；生产与调试共用时可显式开启，但测试写入当前数据域且远程 Worker 仍受单自然世界 slot 限制。

## 验证矩阵

当前通用生命周期已经覆盖登录、16×4 READY、比赛启动、自然结束、stop 清理和 resident 长驻。Harness 已具备逐游戏动作、阶段观察和掉线回归脚手架；逐游戏真实机制、完整远程 Bingo 世界重建、活动中 reload/crash 接管、正式赛事并存和开发者全部状态字段的原生验证仍按 [无人测试游戏矩阵](game-test-matrix.md) 推进。

扩展测试时保持以下顺序：

1. 用 `resident + idle` 验证连接、认证、队伍、传送、旁观和退出。
2. 用真实游戏实例验证准备、比赛、死亡/重生、结算和第二场解锁。
3. 为需要操作的游戏增加 Harness profile，并检查游戏自己的结果和持久化写入。
4. 对远程 Bingo 额外检查同一 match 的 `READY → STARTED → FINISHED/ABORTED → RELEASED`、三世界重建、Core 临时队伍清零和下一场解锁。

命令、权限和 lease 格式以 [命令参考](commands.md) 为准；协议和部署升级见 [Bingo 跨服架构](bingo-remote-architecture.md) 与 [认证部署](auth-deployment.md)。
