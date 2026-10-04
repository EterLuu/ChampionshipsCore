# Bingo 容量与性能验证

本文为远程 Bingo 的容量规划提供测试流程。64 人采用 16 队×4 人作为目标场景，实际容量需在所用硬件、Folia 版本、地图与插件组合上验证；仓库不保证固定人数下的 TPS 或加载延迟。

部署前先阅读 [Worker README](../championships-bingo-worker/README.md) 和 [跨服架构](bingo-remote-architecture.md)。

## 测试准备

记录可复现的环境信息：Core/Worker 的版本或提交、Java 与 Folia 版本、代理与插件列表、CPU 配额、JVM 内存/GC、磁盘与网络、世界边界、预生成范围及脱敏配置。

使用可丢弃的独立世界，准备停止和清理流程。正式世界先完成三维度预生成；重置创建新世界后应重新准备，不能依赖上一局的区块缓存。

Worker 当前边界为 16000×16000 格。预生成范围应覆盖允许活动区域、视距边缘及维度传送需求；按实际 world border 核对，不照搬其他部署的半径。

## 两层验证

| 层次 | 方法 | 覆盖范围 |
| --- | --- | --- |
| 区块和实体压力 | [LoadTest](../championships-bingo-loadtest/README.md) 的逻辑加载者 | 加载窗口、移动、AI 实体、region 热点和清理 |
| 端到端比赛 | 真实协议客户端或真人 | 网络、追踪、背包/统计观察、UI、代理、Redis、数据库、结算和重置 |

逻辑加载者不是实际玩家，不覆盖完整任务与网络成本。先从低压力阶梯测试起，再运行目标人数的完整比赛。每轮从相同干净地图开始，每次只调整一类参数。

## 调整方向

| 方向 | 操作与判断依据 |
| --- | --- |
| 世界 | 优先预生成，检查安全散布和边界外生成；比赛中不运行预生成工具 |
| 视距 | 在玩法可接受范围内比较 `view-distance` 与 `simulation-distance`；记录任务和飞行体验 |
| 区块 I/O | 查看加载 P95/P99、pending 和磁盘延迟；队列持续增长时先查吞吐瓶颈 |
| Folia 线程 | 按可用 CPU 分配 tick、chunk、I/O、网络和 GC 预算；多 region 饱和才有增加 tick 线程的依据 |
| 空间布局 | 使用实际队伍布局，模拟队友传送、集中采集和重新分散，观察 region 合并 |
| 实体 | 同时查看各 region 数量、AI、掉落物与投射物；不以世界总数替代热点判断 |
| Worker 观察 | 检查每玩家每 tick 合并、已完成任务跳过、UI 刷新合并和心跳 |

视距为 10 时，理想方形窗口为 `(2×10+1)²=441` 区块；视距为 8 时为 289，理论面积减少约 34.5%。实际加载量还取决于重叠、服务端加载规则与区块票据，应以采样为准。

本仓库的 LoadTest 默认使用 8 个宏观锚点、8/32/64 加载者阶梯和 4000/8000/16000 世界实体目标。这些是压测参数，不是正式比赛的推荐实体数，也不代表已达到目标人数的验收结果。

## 必须采集的指标

- Folia：region 数、活跃玩家所在 region 的 TPS/MSPT、chunks、entities 和利用率。
- 区块：load/generate rate、P95/P99、失败、pending、过期请求和票据。
- Worker：任务观察合并率、UI 更新、心跳、outbox、事件序列与 completion 序列。
- Redis/数据库：pending、reclaim、DLQ、重放、事务时延与重复积分。
- 系统：CPU 配额、GC pause、heap/direct memory、磁盘延迟和网络吞吐。

LoadTest 结果位于服务端 `plugins/ChampionshipsBingoLoadTest/results.jsonl`，日志输出 `CHUNK_STRESS`。保存配置、结果与 profiler 报告，比较同一版本同一场景的重复运行。

## 端到端验收

以下是建议的初始验收目标，需根据赛事体验要求确定最终门槛：

| 项目 | 检查目标 |
| --- | --- |
| 时长 | 完整配置比赛时长，加准备、结算和回收；默认 Bingo 比赛为 12 分钟 |
| 玩家 | 目标人数的真实连接；64 人场景为 16 队×4 人 |
| Tick | 活跃 region 尽量保持 20 TPS，记录持续低于 18 TPS 的窗口与原因 |
| 区块 | 预生成范围内 P95 小于 1 秒、P99 小于 3 秒，队列不持续增长 |
| 错误 | 无 Watchdog、Folia ownership 错误、加载或生成失败 |
| 玩法 | 卡片、菜单、散布、队友传送、进度、重生、跨维度、旁观与完整 Tab 正常 |
| 积分 | completion 序列连续，重投不重复计分，结果哈希验证成功 |
| 故障 | Redis、Worker、Core、数据库与代理故障可按设计中止或恢复 |
| 回收 | 玩家返回 Core，临时任务/实体/票据释放，监督流程重建世界 |
| 第二局 | 重新开局无旧 ownership、旧回调、残留资源或脏世界污染 |

通过逻辑压测后仍需完成此层验收。完成后移除 LoadTest JAR，冻结正式版本和配置。原生包、线程与旁观验证另遵守 [旁观契约](spectator-visibility-contract.md)。

## 参考资料

- [Folia FAQ](https://docs.papermc.io/folia/faq/)
- [Folia region overview](https://docs.papermc.io/folia/reference/overview/)
- [Paper global configuration](https://docs.papermc.io/paper/reference/global-configuration/)
- [Paper world configuration](https://docs.papermc.io/paper/reference/world-configuration/)
- [Paper profiling](https://docs.papermc.io/paper/profiling/)
