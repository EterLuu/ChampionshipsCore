# 无人测试设计

无人测试让开发者用真实 Minecraft 客户端连接参加比赛。Core 负责会话、队伍、比赛和结算，Bot Harness 负责连接代理、完成认证并保持在线。这样测试经过与真人相同的 Paper、BungeeCord、AuthMe、UUID、传送、重生和发包流程，游戏代码也继续使用现有的 `Player`、队伍和实例接口。

## 组成部分

一次测试由四个部分协作完成：

| 部分 | 职责 |
| --- | --- |
| Core `TestSessionManager` | 创建 16 队×4 名册、登记 lease、创建临时队伍、启动单局或 Test Event、推进轮次并清理会话 |
| Bot Harness | 启动真实协议客户端，处理 AuthMe 对话、服务器传送、重生、keep-alive 和连接报告 |
| AuthProxy / AuthBridge | 按身份服务的准入结果完成代理侧 UUID 注入、CIDR 检查、AuthMe 同步和登录确认 |
| Bingo Worker | 仅在 `bingo.execution-mode: REMOTE` 时执行 Bingo；接收 Core 冻结的 manifest，并回传到达、结算和释放事件 |

Harness 是 Core 全部游戏通用的客户端工具，Bingo 只是它需要远程 Worker 的一种比赛类型。BuildMart、Riptide、LaserBox 等游戏都由 Core 的正常实例和玩法处理。

## 会话生命周期

管理员先创建会话，再让 Bot 连接并完成认证：

```text
/cc game test single start <game> <map> [profile] [difficulty] [remix]
/cc game test event start <event-profile> [difficulty] [remix]
/cc game test lease <signed-token|web>
/cc game test status
/cc game test run
```

`single` 使用 `GameRunMode.TEST`，`event` 使用 `GameRunMode.TEST_EVENT`。Test Event 从 `plugins/ChampionshipsCore/test/events/<profile>.yml` 读取回合，按文件顺序启动每一轮。它复用游戏的准备、倒计时、玩法、结算和地图恢复流程，但由测试会话自己推进轮次，不接管正式 `ScheduleManager`。

会话进入 `READY` 后，Core 要看到 64 个已认证玩家和 16 个临时队伍；`run` 才会启动比赛。正常结束走游戏自己的 `endGame()`，中止或 `stop --confirm` 走统一恢复流程。实例恢复完成后撤销 lease、断开 Bot、移除临时队伍并释放会话。

## 开发者角色

创建者默认不占用 Bot 名册。准备阶段可以选择：

```text
/cc game test join <1-16>
/cc game test leave
```

`join` 用开发者替换指定队伍的一个 Bot，队伍仍保持四人；`leave` 释放席位，Bot 重新取得 lease 后再连接补位。开赛后名册冻结。开发者也可以使用：

```text
/cc game test spectate
/cc game test spectate leave
```

旁观走统一 `SpectatorManager`，不占 64 个比赛席位，使用真实 `GameMode.SPECTATOR` 和完整 Tab 展示。

## 身份与 UUID

Bot 的 UUID 始终由登录链路提供，Harness 不提交 UUID。Core 在 lease 中保存期望值，登录时再将它与 Paper 实际观察到的 UUID、AuthProxy profile 和 AuthMe 登录结果逐一核对。

生产或真实集成测试使用 cc-web 管理的 Bot：

1. Harness 通过 `TEST_HARNESS` HMAC 客户端调用 `/api/internal/test/bots/provision`，取得账号密码。
2. Core 执行 `/cc game test lease web`，从 cc-web 获取当前 profile UUID 和签名 lease。
3. AuthProxy 把同一 profile UUID 注入代理连接，Paper、AuthBridge 和 Core 继续使用服务器实际观察值。

cc-web 的 `CUSTOM_UUID` 是网站账号 UUID；它与服务端的 `online-mode=false` 是两个概念。后者只表示后端通过可信代理接入，不能据此判断 Bot 使用了离线 UUID。

离线 UUID 只用于隔离夹具。`npm run lease` 按 `OfflinePlayer:<name>` 计算签名 lease，适合没有 cc-web 的一次性 Paper/Bungee 环境；它不代表生产身份方案。测试 Bot 命中有效 lease 后走测试身份路径，以代理和 Bukkit 提供的 UUID 为准；普通玩家仍按 Core 的 `identity.mode` 校验在线 UUID，并按同一模式解析未在线玩家。

AuthProxy 与 AuthBridge 共同检查 Bot 的允许 CIDR、账号状态、封禁/维护状态和 UUID。lease 过期、nonce 重放、session/generation 不匹配、UUID 不一致或 AuthMe 未确认时，登录会话直接结束；普通玩家继续走原有身份路径。

## 常驻 Bot

需要让 Bot 作为正常比赛玩家保持在线时使用 Harness 的 resident 模式：

```text
CC_TEST_MODE=resident \
CC_TEST_ACTION=idle \
CC_TEST_COUNT=64 \
CC_TEST_CLIENTS_PER_PROCESS=8 \
CC_TEST_STOP_FILE=/tmp/cc-game-test-harness/run.finished \
CC_TEST_REPORT=/tmp/cc-game-test-harness/resident.json \
npm run connect
```

resident 使用无限等待窗口，直到外部创建 stop file。Bot 不发送游戏动作，Core 仍按真实玩家处理队伍、传送、玩法和计分；所有连接由 8 个 Node worker 承载。报告包含每个 Bot 的登录、世界位置、传送确认、重生、keep-alive、断线和退出状态。

Harness 也可以在隔离环境中自动驱动完整会话。设置 `CC_TEST_CORE_AUTO_SESSION=true`、RCON 参数和 `CC_TEST_CORE_SESSION_MODE=single|event` 后，协调进程依次执行 Core 的 `single/event start`、`lease web`、`status`、`run` 和 `stop --confirm`；它等待 64 个 Bot 和 16 个临时队伍进入 `READY`，Event 或单局进入终态后创建 stop file，使 resident worker 正常退出。该开关默认关闭，RCON 只连接测试服。cc-web Bot 默认保留为持久账号，只有显式设置 `CC_TEST_WEB_DISABLE_ON_FINISH=true` 才调用停用接口。

## 运行模式与数据

测试会话使用独立的 `GameExecutionContext` 和 `SettlementPolicy`。测试结果、事件、Rank、Redis 消息和远程 match 状态可以正常写入测试数据域，便于检查真实行为；正式 Schedule 的全局状态仍只由正式 Event 使用。测试队伍使用 transient team，不写入正式队伍关系。

Core 和 Worker 的 `test.enabled` 默认都是 `false`。只有隔离部署显式打开后才能创建或接收测试会话。开关只负责准入，不会自动创建沙箱；测试服仍需使用独立世界、数据库、Redis namespace、代理和身份域。

远程 Bingo 使用内部协议 v10，Core、Worker、AuthBridge 和 AuthProxy 需要配套版本。Worker 的一场测试比赛占用三个世界，结束时等待所有玩家返回、写入 release journal、发布 `RELEASED`，再由外部监督流程重建世界。

## 代码位置

| 代码 | 位置 |
| --- | --- |
| 测试会话与名册 | `championships-core/src/main/java/ink/ziip/championshipscore/api/test/` |
| 测试命令 | `championships-core/src/main/java/ink/ziip/championshipscore/command/game/TestMainCommand.java` |
| 测试 Event profile | `championships-core/src/main/resources/test/events/` |
| 测试身份与 lease | `championships-common/src/main/java/ink/ziip/championshipscore/auth/`、Core `api/test/auth/` |
| Remote Bingo admission/release | `championships-core/src/main/java/ink/ziip/championshipscore/api/game/bingo/execution/`、`championships-bingo-worker/src/main/java/ink/ziip/championshipscore/worker/` |
| 协议客户端 | `championships-test-harness/src/` |

## 扩展游戏测试

首先用 `resident + idle` 验证登录、队伍、传送、旁观和自然生命周期，再为确实需要动作的游戏添加 Harness profile。动作脚本只描述客户端操作，不复制游戏规则；游戏规则、胜负和结算仍由 Core 或 Worker 决定。每个游戏的拓扑、动作和验证状态登记在 [无人测试游戏矩阵](game-test-matrix.md) 中。

完整命令见 [命令参考](commands.md)，身份细节见 [UUID 契约](player-uuid-contract.md)，远程世界释放见 [Bingo 跨服架构](bingo-remote-architecture.md)。
