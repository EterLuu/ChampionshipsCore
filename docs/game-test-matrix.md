# 无人测试游戏矩阵

这张表说明每个游戏的 16 队测试拓扑、适合的 Harness profile 和当前验证重点。通用的 64 Bot 登录、16×4 名册、resident 长驻、Test Event 轮次和 Core 清理已经由 Core 基线覆盖；逐游戏仍需使用自己的地图、动作和结算规则验证。

| 游戏 | 命令 token | 16 队拓扑 | 建议验证内容 |
| --- | --- | --- | --- |
| Bingo | `bingo` | 远程 Worker，16 队×4 | manifest、任务动作、计分、死亡/重生、`RELEASED` 和三世界重建 |
| TNTRun | `tntrun` | 多队单实例 | resident、自然倒计时、坠落淘汰、结算和第二场；Core 两轮 Event 已完成原生基线 |
| ParkourTag | `parkourtag` | 双队副本 | 8 个副本、追逐、淘汰、轮间和重连 |
| BattleBox | `battlebox` | 双队副本 | 8 个副本、战斗结束、轮间和队伍恢复 |
| LaserShowdown | `lasershowdown` | 多队实例 | 武器、死亡、复活保护、结算和旁观 |
| SkyWars | `skywars` | 多队实例 | 资源、战斗、淘汰、地图恢复和结算 |
| TGTTOS | `tgttos` | 多队实例 | 跑图、完成顺序、传送、轮间和地图切换 |
| DragonEggCarnival | `dragoneggcarnival` | 决赛拓扑 | 前二队伍准入、目标、结束和观众恢复 |
| ParkourWarrior | `parkourwarrior` | 多队或内部物理分区 | 房间、检查点、完成顺序和结算 |
| HotyCodyDusky | `hotycodydusky` | 多队实例 | 区域完成、淘汰、轮间和重连 |
| BuildMart | `buildmart` | 多队实例 | 材料采集、摆放、提交、补货和订单结算 |
| Dodgebolt | `dodgebolt` | 决赛固定两队 | 队伍准入、箭矢、回合、胜负和结束 |
| AceRace | `acerace` | 多队实例 | 跑图、检查点、排名、重生和地图恢复 |
| RiptideRush | `riptide` | 多队实例 | 五阶段动作、木筏、淘汰、重连和轮间 |
| FrostbiteFrenzy | `frostbite` | 多队实例 | 冻结/解冻、装备、死亡和轮间 |
| LaserBox | `laserbox` | 双队副本 | 副本配对、射击、两目标规则、结束和复位 |
| SulfurSoccer | `sulfursoccer` | 决赛或双队 | 试踢、开球、珍珠、得分、点球和暂停 |

## 运行顺序

1. 用 `resident + idle` 确认代理、认证、队伍、传送、旁观和退出。
2. 用游戏自己的准备和结束流程确认实例、玩家模式、装备、计时和结算。
3. 需要操作时为 Harness 增加 profile；profile 只发送客户端动作，不复制游戏规则。
4. 检查数据库、Redis、世界和日志中的预期写入，再执行 stop 和第二场。

双队项目需要八个可用副本。DragonEggCarnival、Dodgebolt 和 SulfurSoccer 使用决赛队伍拓扑，不能把 16 队直接塞进一个实例；测试应先完成前二队伍的准入，再验证决赛流程。

远程 Bingo 还需要同一 match 的 `READY → STARTED → FINISHED/ABORTED → RELEASED`、三世界 UUID 变化、Core 临时队伍清零和下一场解锁。详细状态机见 [Bingo 跨服架构](bingo-remote-architecture.md)。
