# Championships Test Harness

Harness 是 ChampionshipsCore 的通用真实协议客户端工具。它连接 Paper、Folia 或 BungeeCord 测试环境，也可以连接已明确开启调试入口的生产链路，完成 AuthMe 登录、跨服、传送确认、重生和 keep-alive，并输出每个 Bot 的连接状态。Core 继续负责队伍、玩法、计分和结算；Harness 不复制任何游戏规则。

Harness 使用支持 Minecraft 26.3 的 `@wp2508/minecraft-protocol` 1.69.5，不依赖 Bukkit、BungeeCord、AuthMe 或 Core API，也不会打包进生产插件。它适合 1 个或 64 个测试身份，身份来源由认证链路决定。

## 安装

需要 Node.js 22 或更高版本：

```text
cd championships-test-harness
npm ci --ignore-scripts --no-audit --no-fund
```

运行回归测试：

```text
npm test
```

## 固定窗口连接

```text
CC_TEST_HOST=127.0.0.1 \
CC_TEST_PORT=25565 \
CC_TEST_VERSION=26.3 \
CC_TEST_VIEW_DISTANCE=2 \
CC_TEST_COUNT=64 \
CC_TEST_CLIENTS_PER_PROCESS=8 \
CC_TEST_INTERVAL_MS=2200 \
CC_TEST_HOLD_MS=600000 \
CC_TEST_RECONNECT_ATTEMPTS=3 \
CC_TEST_RECONNECT_DELAY_MS=3000 \
CC_TEST_ACTION=idle \
CC_TEST_REPORT=/tmp/cc-game-test-harness/reports/bots.json \
npm run connect
```

`CC_TEST_HOST` 应指向测试或受控生产 BungeeCord。`CC_TEST_MODE=window` 会在固定窗口结束；`idle` 只验证连接和基础状态，`look`、`walk` 是协议动作 profile，`bingo-walk` 保留给旧脚本。`game` 通过矩阵 profile 按服务端阶段发送客户端动作。

`CC_TEST_HOST` 与 `CC_TEST_PORT` 指向代理提供给 Harness 的测试监听。应选择能接受普通客户端登录、并把真实来源地址传给后端的监听；如果某个监听要求 PROXY protocol，必须由可信代理补齐来源信息，不能用等价端口直接替代。

管理员手动测试时可以让 resident Bot 按当前游戏发送动作基线：

```text
CC_TEST_MODE=resident \
CC_TEST_ACTION=game \
CC_TEST_GAME=tntrun \
CC_TEST_STOP_FILE=/tmp/cc-game-test-harness/run.finished \
npm run connect
```

`CC_TEST_GAME` 使用 Core 的命令 token，例如 `bingo`、`tntrun`、`parkourtag`、`riptide` 或 `laserbox`。profile 先识别等待、投票、准备、倒计时、进行和结束，再选择动作；它只产生客户端输入，管理员仍通过 Core 的普通开赛和观战命令控制比赛。

`CC_TEST_CLIENTS_PER_PROCESS` 默认为 8。64 个连接由 8 个 Node worker 承载，协调进程汇总精确名册、退出码、重连、传送确认、死亡/重生、keep-alive 和事件循环延迟。任何 worker 缺少报告、Bot 名称重复/遗漏、提前断线或结束时不在世界中，汇总报告都会返回非零退出码。

## cc-web Bot 账号

使用 cc-web 管理的持久 Bot 时，设置 `CC_TEST_IDENTITY_MODE=cc-web`：

```text
CC_TEST_IDENTITY_MODE=cc-web \
CC_TEST_WEB_BASE_URL=https://<cc-web-host> \
CC_TEST_WEB_KEY_ID=test-harness \
CC_TEST_WEB_HMAC_SECRET=<独立的至少32字节密钥> \
CC_TEST_BOT_ALLOWED_CIDRS=10.20.0.0/16 \
CC_TEST_BOT_PERSISTENT=true \
npm run connect
```

Harness 通过 `TEST_HARNESS` HMAC 客户端调用 `/api/internal/test/bots/provision`，只取得账号密码。它不生成、不提交 UUID，也不接受 UUID 环境变量。Core 创建 session 后执行 `/cc game test lease web`，由 Core 从 cc-web 获取 profile UUID 和签名 lease。

`CC_TEST_BOT_ALLOWED_CIDRS` 应与代理和 AuthBridge 的实际网络一致。cc-web 为每个 Bot 返回的 CIDR 会参与准入检查，账号被停用、撤销或没有有效 CIDR 时，Proxy、Bridge、Yggdrasil 和 Core 都停止接受该 Bot。

Provision 接口成功后默认等待 `CC_TEST_WEB_PROVISION_SETTLE_MS=15000` 毫秒，让身份服务、AuthMe 和 Bridge 消费新的密码；调用方已确认同步完成时可设为 `0`。Bridge 按轮询周期消费 outbox，过短的 settle 会让首次登录在 provision 尚未 ACK 时被拒绝。

设置 `CC_TEST_WEB_DISABLE_ON_FINISH=true` 时，无论是单进程还是分片运行，Harness 在写完报告后都会停用本次 slot；默认值为 `false`，便于 resident 管理员反复使用同一 Bot 池。

方块挖掘/放置动作默认关闭；`CC_TEST_ALLOW_WORLD_MUTATION=true` 只允许固定窗口或 Core 自动会话，resident 管理员手动测试不能打开它。

`npm run lease` 只用于没有 cc-web 的离线隔离夹具：它按 `OfflinePlayer:<用户名>` 计算签名 UUID。生产或 cc-web 集成测试使用 `lease web`，由身份服务提供当前 UUID。

## 比赛常驻 Bot

让 Bot 作为正常比赛玩家保持在线时使用 resident 模式：

```text
CC_TEST_MODE=resident \
CC_TEST_ACTION=idle \
CC_TEST_COUNT=64 \
CC_TEST_CLIENTS_PER_PROCESS=8 \
CC_TEST_STOP_FILE=/tmp/cc-game-test-harness/run.finished \
CC_TEST_REPORT=/tmp/cc-game-test-harness/reports/resident.json \
npm run connect
```

resident 使用无限等待窗口，直到外部创建 stop file。文件必须是本次运行唯一、启动时不存在的绝对路径。服务端 probe 确认比赛阶段和在线人数后创建它；所有 worker 先写报告，再正常断开。`windowEndReason=control-file` 表示 Harness 由该文件结束。

resident 的 `idle` 不发送游戏动作；`game` 会按 `CC_TEST_GAME` 使用对应动作 profile，并根据服务端阶段自动暂停或恢复。Core 仍把 Bot 当作真实玩家处理队伍、传送、玩法、计分、死亡和重生。客户端处理服务端位置、`teleport_confirm`、客户端 `tick_end`、configuration 变更和 keep-alive；收到死亡后发送一次重生请求，等待新的世界位置再恢复连接状态。报告还包含阶段转换、机制动作、计分板快照和注入断线结果，并保留受限的实体、方块、窗口/物品和阶段消息样本，便于把客户端输入与服务端观察分开核对。

CI 可以使用 `CC_TEST_DISCONNECT_PHASE=PROGRESS` 注入一次掉线；需要覆盖多个阶段时使用逗号分隔的 `CC_TEST_DISCONNECT_PHASES=PREPARATION,PROGRESS,END`。`CC_TEST_EXPECT_PHASES`、`CC_TEST_EXPECT_MECHANISMS`、`CC_TEST_EXPECT_ACTIONS`、`CC_TEST_EXPECT_SCORE_OBJECTIVE` 和 `CC_TEST_EXPECT_RECONNECTS` 可以把报告证据变成失败条件。

## 身份、UUID 与报告

Bot 的 UUID 由 AuthProxy、BungeeCord、Paper 和身份平台提供。Core lease 只保存期望值，用于与 Paper 实际观察值和 AuthMe 登录结果核对。Harness 报告会记录服务器 UUID、登录状态、世界位置、传送确认、死亡/重生包和断线原因。

退出码为 0 代表每个 Bot 完成协议登录，结束时仍在线、已经收到可用世界位置且没有处于死亡状态。AuthMe dialog 提交次数只表示客户端发出了提交；是否准入由 AuthMe、AuthProxy、AuthBridge 和 Core 的服务器状态共同决定。

## 准备测试环境

本地编排器先确认 MariaDB、Redis、Core、Worker 和代理健康，再启动 Harness。Paper 与依赖服务应在同一 Docker network 中运行；使用宿主端口时，从实际 Paper network namespace 检查可达性。首次使用可先用 1、4、8 个 Bot 检查 AuthMe 注册或预注册账号，再提升到 64 个。

每次运行使用独立的报告目录和 stop file；生产调试模式还必须提前确认测试写入、Bot 身份和正式赛程资源安排。结束后保留报告和服务端日志，确认 Core `status` 已回到无活动会话，再清理临时环境。Harness 不删除 Core reservation 或 Worker release journal，也不修改认证门禁。

动作 profile 的职责说明见 [`src/profiles/README.md`](src/profiles/README.md)，Core 会话和命令见 [无人测试设计](../docs/06-unattended-testing/01-game-test-research.md) 与 [命令参考](../docs/03-reference/01-commands.md)。

## Core test interface orchestration

Set `CC_TEST_CORE_AUTO_SESSION=true` to let the coordinator drive a Core test
session through its RCON console. This mode requires `resident`, `idle` or `game`, the
cc-web identity mode, and the complete `autobot00` through `autobot63` roster:

```text
CC_TEST_MODE=resident \
CC_TEST_ACTION=game \
CC_TEST_GAME=tntrun \
CC_TEST_IDENTITY_MODE=cc-web \
CC_TEST_CORE_AUTO_SESSION=true \
CC_TEST_CORE_RCON_HOST=<core-host> \
CC_TEST_CORE_RCON_PORT=25575 \
CC_TEST_CORE_RCON_PASSWORD=<private-rcon-password> \
CC_TEST_CORE_SESSION_MODE=event \
CC_TEST_CORE_EVENT_PROFILE=smoke \
CC_TEST_STOP_FILE=/tmp/cc-game-test-harness/run.finished \
npm run connect
```

For a single game use `CC_TEST_CORE_SESSION_MODE=single` together with
`CC_TEST_CORE_GAME`, `CC_TEST_CORE_MAP`, and optionally `CC_TEST_CORE_PROFILE`,
`CC_TEST_CORE_DIFFICULTY`, and `CC_TEST_CORE_REMIX`. The coordinator executes
`single/event start`, `lease web`, `status`, `run`, and `stop --confirm` through
RCON. It waits for `READY`, 64 bound seats, and 16 transient teams before
starting the game. When Core reaches `COMPLETED`, it writes the stop file so all
Bot workers disconnect cleanly. Run the Harness inside the Core/Bungee private
network or use an equally restricted route; never publish RCON publicly. The
default remains manual Harness operation.

`CC_TEST_WEB_DISABLE_ON_FINISH=true` is an explicit opt-in to call cc-web's
`/api/internal/test/bots/disable` endpoint after cleanup. Persistent Bot
accounts remain active by default. The Harness never supplies a UUID: cc-web
provisions credentials and Core obtains the current UUID and signed leases with
`lease web`.

CI can run the complete no-network profile contract with `npm run test:scenarios`; it executes all 17 profiles through voting, preparation, countdown, progress, reconnect, scoreboard observation and end-state transitions. `src/matrix.js` is the executable contract behind the game matrix: it declares each profile's legal action families per phase and its `signatureActions` (the mechanic-defining actions), disconnect points with reconnect preservation checks, server evidence and score signals. Game actions are state-driven: profiles target server-observed entities/blocks, check the 4.5-block reach before attacking, aim by real coordinates, select real hotbar slots, and only mutate the world when explicitly allowed. When `CC_TEST_EXPECT_PHASES` includes `PROGRESS`, the report validator automatically requires that profile's declared mechanism, signature action families and server evidence; when it includes `END`, it requires at least one server scoreboard observation. Set `CC_TEST_EXPECT_ACTIONS`, `CC_TEST_EXPECT_SERVER_EVIDENCE` or `CC_TEST_EXPECT_SCORE_PACKETS` explicitly to narrow or extend those requirements for a map. Set `CC_TEST_EXPECT_RECONNECT_PHASES=PREPARATION,PROGRESS,END` to require phase-specific reconnect records; each record must preserve the UUID and restore a ready world.

`CC_TEST_CORE_AUTO_SESSION=true` 会自动填充当前 profile 的 `WAITING,PROGRESS,END`、机制、动作和至少一个计分板包断言；显式设置对应环境变量仍可覆盖这些默认值。
