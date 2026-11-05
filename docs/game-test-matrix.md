# 无人测试游戏矩阵

这份矩阵是 Core、Worker 和 `championships-test-harness` 共同使用的测试契约。每一行规定四件事：Bot 在各阶段发送什么客户端输入、在哪些阶段注入掉线并重连、如何验证机制和积分。Harness 只产生真实客户端输入并记录观察结果；游戏规则、淘汰、积分和结算仍由 Core 或 Bingo Worker 决定。

通用阶段为 `WAITING → VOTING → PREPARATION → COUNTDOWN → PROGRESS → END`。某个游戏没有投票或准备阶段时，阶段仍记录为跳过。服务端标题、ActionBar、系统消息和投票窗口才参与阶段文本识别；实体、计分板和队伍包只作为观察证据，不能把已进入比赛的 Bot 回退到 `WAITING`。死亡/重生、实体、计分板和队伍包由 Harness 的 `PhaseTracker` 归一为阶段事件。

## Profile 和运行方式

`src/matrix.js` 是这份文档的可执行契约，逐一声明每个游戏各阶段动作、掉线阶段及重连后必须保留的身份/队伍/世界/回合/结算状态、机制证据、积分信号和生命周期检查项；`src/profiles/index.js` 将它与具体客户端 profile 合并。每个游戏还声明 `signatureActions`：`actions` 是合法动作词汇，`signatureActions` 是必须在 `PROGRESS` 中实际触发的机制动作，报告校验默认按 signature 断言。`src/packets.js` 是唯一的动作发包入口。profile 可以组合移动、跳跃、冲刺、视角、挥手、使用物品、攻击、方块挖掘/放置、实体交互、投票点击和窗口操作。profile 不直接判断胜负，也不伪造服务端分数。

游戏动作是**状态驱动**而不是盲目轮询：实体目标来自服务端实体包、方块目标来自 `block_change`、武器/道具来自玩家背包槽位；攻击和交互先做 4.5 格距离判定，超出范围改为转向移动；出手前按目标坐标计算 yaw/pitch 瞄准；战斗类先切换真实武器槽；BuildMart 只在显式开启世界修改时对已观察方块挖掘/放置。因此每个 Bot 的动作会随服务端世界状态变化，而不是每 tick 固定把全部动作发一遍。

CI 的单次连接测试使用 `window`；需要真实比赛生命周期时使用自动 Core session。阶段掉线回归通过 `CC_TEST_DISCONNECT_PHASE` 注入一次断线，Harness 使用既有 reconnect 逻辑重新登录，并在报告中记录 `phaseTransitions`、`injectedDisconnects`、`deaths`、`respawnPackets` 和 `mechanismPackets`。

每个玩家报告还会写入 `reconnectHistory`：每次注入断线记录断线时阶段、断线前后 UUID、重连后的世界就绪状态，以及重连后收到的队伍/模式资料。死亡和复活包也进入阶段历史与重生计数，便于区分“死亡后重连”和普通网络重连。设置 `CC_TEST_EXPECT_RECONNECT_PHASES` 后，CI 会逐阶段要求该记录存在、身份未变且世界已恢复；这使矩阵中的阶段重连保留项成为实际失败条件，而不是只统计重连次数。

需要覆盖多个阶段时使用逗号分隔的 `CC_TEST_DISCONNECT_PHASES=PREPARATION,PROGRESS,END`；每个阶段只注入一次，Bot 会在每次重连后保留最近观察到的比赛阶段。

```text
CC_TEST_MODE=window \
CC_TEST_ACTION=game \
CC_TEST_GAME=<token> \
CC_TEST_DISCONNECT_PHASE=PROGRESS \
CC_TEST_RECONNECT_ATTEMPTS=1 \
CC_TEST_EXPECT_PHASES=WAITING,PROGRESS,END \
CC_TEST_EXPECT_MECHANISMS=weapon-combat \
CC_TEST_EXPECT_SCORE_OBJECTIVE=points \
npm run connect
```

当 `CC_TEST_EXPECT_PHASES` 包含 `PROGRESS` 时，Harness 会从 `src/matrix.js` 自动要求当前游戏的机制标签、动作族和 `serverEvidence` 出现在报告中，并要求这些服务端证据确实发生在 `PROGRESS`；包含 `END` 时至少要求收到一个 `END` 阶段的服务端计分板包。`CC_TEST_EXPECT_ACTIONS`、`CC_TEST_EXPECT_SERVER_EVIDENCE` 和 `CC_TEST_EXPECT_SCORE_PACKETS` 可按地图实际能力覆盖要求。动作证据只证明客户端确实发出了合法输入；`serverEvidence` 只证明客户端观察到了对应服务端包，积分、淘汰、世界变化和结算仍必须由 Core/Worker 的事件、计分板、Redis 或正式状态核对。

契约中的 `phaseActions` 固定等待/投票/准备/倒计时/进行/结束各阶段的动作边界；`reconnect` 为矩阵中的每个掉线阶段声明重连后必须保留的身份、队伍、世界、模式、回合或结算状态。实际报告仍需由服务端队伍包、模式包、世界位置、回合状态和积分结果完成断言。

管理员手动测试使用 resident。Bot 会根据服务端当前阶段自动在等待、投票、倒计时和游戏阶段切换动作；管理员仍通过 Core 的正常开赛、旁观和结束命令控制流程。

```text
CC_TEST_MODE=resident \
CC_TEST_ACTION=game \
CC_TEST_GAME=<token> \
CC_TEST_STOP_FILE=/tmp/cc-game-test-harness/<token>.finished \
npm run connect
```

每个游戏至少执行以下五个检查：

1. `WAITING/VOTING/COUNTDOWN`：Bot 保持在线、完成投票窗口交互（如存在），进入正确出生点和模式。
2. `PROGRESS`：profile 的动作触发该游戏的真实机制，检查实体、物品、世界变化和玩法事件。
3. `PROGRESS` 掉线：分别在准备结束前、比赛进行中、死亡/淘汰后注入断线；重连后核对身份、队伍、模式、装备和当前回合。
4. `END`：核对结算状态、玩家/队伍积分和广播，确保只结算一次。
5. 第二场：检查地图、临时队伍、装备、计分和 Worker/world slot 已恢复，上一场的延迟回调没有改动新场。

## 游戏矩阵

| 游戏 | token / 拓扑 | resident profile 动作 | 阶段与机制验证 | 掉线/重连场景 | 积分与结束断言 | 当前证据 |
| --- | --- | --- | --- | --- | --- | --- |
| Bingo | `bingo` / 远程 Worker，16×4 | `bingo-walk`，地面移动、任务交互基线 | manifest 冻结；任务动作；死亡/重生；三世界 | READY、任务进行中、死亡后、RELEASED 前 | 任务完成、个人/队伍分、`FINISHED/ABORTED → RELEASED`、三世界 UUID 变化 | Harness profile/通用生命周期；完整远程闭环待原生验收 |
| TNTRun | `tntrun` / 多队单实例 | walk、sprint、周期跳跃、挥手 | 地板破坏、坠落淘汰、旁观 | COUNTDOWN、PROGRESS、淘汰后、第二场前 | 淘汰顺序、队伍排名、结算只发生一次、第二场可启动 | `astra` 两轮 Event 原生基线已完成；动作/掉线矩阵待补齐 |
| ParkourTag | `parkourtag` / 双队副本 | walk、sprint、跳跃、追逐视角、道具使用、右键追逐按钮 | 追逐者分配、触碰淘汰、轮间恢复 | PREPARATION 分配后、追逐中、淘汰后 | 生存时间、追逐者得分、轮间累计和队伍恢复 | 可执行 profile；原生行为待验收 |
| BattleBox | `battlebox` / 双队副本 | 视角、跳跃、冲刺、攻击/挥手、物品使用、右键套件告示牌 | 武器使用、伤害、回合结束、装备清理 | COUNTDOWN、战斗中、死亡/回合结束后 | 回合胜负、击杀/队伍分、复位后第二回合 | 可执行 profile；原生行为待验收 |
| LaserShowdown | `lasershowdown` / 多队实例 | 视角、挥手左键射击、右键使用/瞄准 | 激光发射、命中、死亡保护、旁观 | PROGRESS、死亡后、复活保护期间 | 命中/淘汰分、结算排名和旁观恢复 | 可执行 profile；原生行为待验收 |
| SkyWars | `skywars` / 多队实例 | 移动、sprint、跳跃、使用物品 | 资源拾取、战斗、虚空/淘汰、重置 | PREPARATION、资源阶段、战斗中、淘汰后 | 淘汰分、队伍排名、地图恢复和第二场 | 可执行 profile；原生行为待验收 |
| TGTTOS | `tgttos` / 多队实例 | sprint、跳跃、跑图移动、终点视角 | 完成顺序、终点检测、传送和轮间切图 | COUNTDOWN、跑图中、到达终点后 | 完成顺序分、轮间累计、地图切换 | 可执行 profile；原生行为待验收 |
| DragonEggCarnival | `dragoneggcarnival` / 决赛拓扑 | 移动、跳跃、目标交互 | 前二队伍准入、目标拾取/提交、观众恢复 | 准入前、目标进行中、结束后 | 目标分、决赛胜负、非准入队伍不能进入 | 可执行 profile；原生行为待验收 |
| ParkourWarrior | `parkourwarrior` / 分区实例 | sprint、跳跃、检查点方向 | 房间进入、检查点、完成顺序 | 房间分配后、检查点中、完成/失败后 | 检查点和完成顺序分、房间清理 | 可执行 profile；原生行为待验收 |
| HotyCodyDusky | `hotycodydusky` / 多队实例 | 移动、sprint、跳跃、区域观察 | 区域完成、淘汰、轮间状态 | 区域开始前、区域进行中、淘汰后 | 区域分、队伍累计、重连后当前区域 | 可执行 profile；原生行为待验收 |
| BuildMart | `buildmart` / 多队实例 | 移动、建造挥手、采集/提交动作基线 | 材料采集、摆放、提交、独立补货、订单结算 | 采集前、采集中、提交前后、补货等待中 | 蓝图匹配分、订单完成顺序、补货不重复、第二场清理 | 可执行 profile；生产蓝图审计已完成，比赛行为待验收 |
| Dodgebolt | `dodgebolt` / 决赛固定两队 | 视角、弓右键蓄力、箭矢交互 | 队伍准入、取箭、射击、回合结束 | 准入后、回合中、死亡/回合结束后 | 每回合胜负、总胜负、箭矢和队伍恢复 | 可执行 profile；原生行为待验收 |
| AceRace | `acerace` / 多队实例 | sprint、跳跃、跑图、检查点视角 | 检查点、排名、重生、地图恢复 | COUNTDOWN、检查点间、重生后、结束后 | 检查点/完成排名分、第二场路线恢复 | 可执行 profile；原生行为待验收 |
| RiptideRush | `riptide` / 多队实例 | sprint、跳跃、阶段移动 | 五阶段、木筏、离筏淘汰、落水规则 | 阶段切换、木筏中、落水/淘汰后、轮间 | 生存/前三名分、阶段结算、重连后回合状态 | 可执行 profile；规则配置已精简，原生行为待验收 |
| FrostbiteFrenzy | `frostbite` / 多队实例 | 视角、使用物品、冻结周期观察 | 冻结/解冻、装备清理、死亡恢复 | 冻结前、冻结中、解冻后、死亡后 | 生存/淘汰分、靴子清理、轮间装备恢复 | 可执行 profile；装备修复已完成，原生行为待验收 |
| LaserBox | `laserbox` / 双队副本 | 视角、挥手左键射击、右键使用/瞄准 | 副本配对、射击、最多两名目标、复位 | 配对后、射击中、死亡后、结束复位 | 命中/淘汰分、两目标上限、第二场副本恢复 | 可执行 profile；两目标规则已部署，原生行为待验收 |
| SulfurSoccer | `sulfursoccer` / 决赛或双队 | 移动、sprint、跳跃、物品使用 | 试踢、开球、珍珠冷却、击球、得分/点球 | 试踢、正式开球、比赛中、暂停/点球后 | 进球、点球、胜负和珍珠冷却；第二场清理 | 可执行 profile；规则部署已完成，原生行为待验收 |

## CI 结果判定

CI 读取 Harness JSON 报告和 Core/Test Event 状态，至少要求：

没有运行中的 Minecraft 服时，先执行 `npm run test:scenarios`，它对 17 个 profile 运行阶段、动作、重连、计分包和结束状态的协议级场景；接着在真实地图上执行本矩阵的原生断言。

- roster 与预期 Bot 完全一致；所有 Bot 在终点仍已登录、在线、世界就绪；意外断线、死亡或重复 UUID 使测试失败。
- `phaseTransitions` 包含矩阵要求的阶段；`injectedDisconnects` 等于预期注入次数；重连后的 `playLogins`、传送确认、队伍和身份与断线前一致。
- `mechanismPackets` 包含当前 profile 的机制标签；服务端结果必须由 Core/Worker 的事件、积分、世界或 Redis 状态核对，客户端动作包本身不能作为得分证据。
- 单局结束后执行 `stop --confirm`，检查临时队伍、lease、世界 slot 和延迟任务清零；Test Event 再启动第二轮确认没有旧状态泄漏。

当前只有通用 resident 生命周期和 TNTRun `astra` 两轮 Event 有原生运行证据；2026-11-03 通过 cc-bungeecord 容器普通 listener `25566` 完成 64 Bot 的连接、认证、UUID、队伍、传送和长驻基线，并完成单 Bot `tntrun` 的 `WAITING` 阶段感知 smoke（含受限实体、物品和消息样本），但没有启动比赛，因此不增加任何游戏机制、阶段掉线、积分或第二场证据。`npm run test:scenarios` 已为全部 17 个 profile 提供协议级阶段/动作/重连/计分场景证据。其余行在完成对应地图的原生运行、积分核对和第二场验收前，不能标记为已完成。
