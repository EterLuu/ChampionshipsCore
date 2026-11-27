# Action profiles

Profile 只描述协议客户端发送的动作，不复制 Core 或 Worker 的游戏规则。动作经过 `src/packets.js` 的统一 helper 发出，阶段由 `src/phase.js` 根据服务端标题、ActionBar、系统消息和窗口包推断。

- `idle`：完成连接、AuthMe 对话和保持在线，适合 resident 基线；
- `look`：周期发送视角包，检查动作包和实体线程，不改变位置；
- `bingo-walk`：兼容旧脚本，在受限路径发送带 `onGround` 标志的移动包；
- `game`：根据游戏标题和开赛提示选择玩法。也可以通过 `CC_TEST_GAME` 固定玩法。profile 可组合移动、跳跃、冲刺、投票窗口点击、物品使用、实体交互、攻击、视角和机制记录。

游戏 profile 的游戏机制动作只在 `PROGRESS` 阶段执行；`WAITING`、`VOTING`、`PREPARATION`、`COUNTDOWN` 会保持客户端 tick 和低风险观察输入，`END` 停止游戏动作。服务端没有发出可识别阶段信号时，Bot 保持等待状态，避免在错误阶段破坏地图或库存。

每个 profile 都与 `src/matrix.js` 中的可执行契约合并，契约声明各阶段 `actions`、`signatureActions`（用于证明机制确实跑通的动作用子集）、掉线阶段、重连保留项、机制标签、服务端证据、积分信号和生命周期检查项。`actions` 是合法动作词汇，`signatureActions` 是该游戏必须在 `PROGRESS` 中实际触发的机制动作，`validateReport` 默认按 signature 断言而不是要求每个动作每 tick 都出现。

人机验证的场景驱动使用 [VerificationPickaxe](verification-pickaxe.js) 操作仓库镐和工地刻刀。每个连接创建一个实例，选定目标后调用 `start(方块坐标, 'WAREHOUSE' 或 'JOINT')`，随后每个客户端 tick 在移动结算前调用 `tick(位置和视角)`。工地目标在当前位置不可见或超出刻刀距离时，bot 会沿实际碰撞面换位，可经灰块叉车正面的楼梯走到车顶。自动游戏模式在工地看板显示当前轮次、已获刻刀且允许世界修改时，从灰块与白块完全推进后重叠的位置选块。用背包材料增长确认仓库采集，用服务端发回的灰块变空气确认工地挖掘。场景结束时调用 `cancel()`。

## 游戏行为脚本

`PROGRESS` 阶段不再按 20 tick 机械地把全部动作发一遍，而是按游戏读取服务端已经给出的状态再决定动作：

- 目标来自服务端：实体位置取自 `spawn_entity`/`sync_entity_position`/`rel_entity_move`/`entity_move_look`/`entity_teleport`，方块取自 `block_change`，物品取自玩家背包 `window_items`/`set_slot`/`set_player_inventory`；Harness 从不凭空编造坐标或目标。
- 攻击/交互先做**距离判定**：超出 4.5 格不挥空，改为转向并朝目标移动；进入范围才 `attack`/`use_entity`。
- 出手前**瞄准**：按目标实际坐标计算 yaw/pitch 并先发 `look`，而不是随机扫视角。
- 战斗类先**选武器**：从背包 36–44 槽位挑选剑/斧/弓/三叉戟等并发送 `held_item_slot`，没有背包信息时安全跳过。
- 移动类按 profile 的 `stride`/`jumpEvery` 做有界跑动、冲刺和跳跃；追击类朝目标三维位置推进。

具体机制：BattleBox 选武器→瞄准→近战攻击；LaserShowdown/LaserBox 瞄准→左键射击→右键技能；Dodgebolt 找箭、瞄准、拉弓并松手放箭；SkyWars 追击→投掷物→近战；BuildMart 对服务端观察到的方块交替挖掘/放置，仅在显式开启世界修改时执行；SulfurSoccer 只锁定 `sulfur_cube` 并踢球/掷珍珠；Bingo 按就近实体、可挖方块、否则 roaming 三态切换；DragonEggCarnival 走近目标实体后交互；ParkourTag 用羽毛或末影之眼识别角色，追击者拍人，逃脱者避开追击者；其余跑图游戏执行移动/冲刺/跳跃/视角。

HotyCodyDusky 根据第五格、其他玩家及替身的头盔、道具展示物与交互实体做决定。空闲时分散跑动，接近持鱼人时逃跑，持鱼后追人并使用第五格技能。拾取前检查两个道具格，已穿戴背心的标记占一格。CraftingFeast 会走近正在点餐的顾客并右键接单。

准备阶段的方块交互使用服务端已观察到的坐标，可覆盖 BattleBox 套件告示牌和 ParkourTag 追逐按钮；`block-dig`/`block-place` 只有在 CI 或 Core 自动会话显式启用世界修改、且服务端先发出合法 `block_change` 目标后才使用该坐标，管理员 resident 测试不会误破坏未知方块。客户端动作计数与按阶段的 `serverEvidence` 分开，避免把等待阶段的包或发包意图当成比赛机制结果。

CI 可设置 `CC_TEST_DISCONNECT_PHASE` 在阶段转换后注入一次断线，使用 `CC_TEST_RECONNECT_ATTEMPTS` 检查重连身份、队伍、模式、装备和当前回合。报告包含 `phaseTransitions`、`mechanismPackets`、`injectedDisconnects`、死亡和重生计数。

```text
CC_TEST_ACTION=game \
CC_TEST_GAME=buildmart \
CC_TEST_DISCONNECT_PHASE=PROGRESS \
npm run connect
```

profile 不判断胜负，也不创建或伪造分数。积分、淘汰、世界变化和结算必须由 Core/Worker 的事件、存储、Redis 或正式结果状态核对。新增或细化游戏动作时，同步更新 [无人测试游戏矩阵](../../../docs/06-unattended-testing/02-game-test-matrix.md) 和对应的 Harness 单测。
