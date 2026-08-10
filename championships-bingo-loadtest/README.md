# Championships Bingo LoadTest

这是一次性 Folia 区块/实体压力插件，用逻辑移动者模拟 Bingo 玩家附近的 view-distance 窗口，并在停留者周围按自然刷怪距离带生成混合怪物与动物。它用于定位区块生成、region 热点和实体 tick 的容量边界，不是 Minecraft 协议机器人，也不验证代理、Redis、任务完成或正式积分链路。

> 警告：测试会生成并保存大量新区块，最多主动创建数万实体。只在备份完毕、允许丢弃的世界运行；生产赛事服不得安装此插件。停止测试会清理插件管理的实体和 chunk ticket，但不会还原已经生成或修改的地图。

详细结果、当前已验证范围和 64 人推荐配置见 [性能分析报告](../docs/bingo-64-player-performance-report.md)。

## 构建与使用

```bash
mvn -pl championships-bingo-loadtest -am clean package
```

将 `championships-bingo-loadtest/target/championships-bingo-loadtest-1.3-SNAPSHOT.jar` 临时放进测试服 `plugins/` 后重启。默认 `auto-start: false`，由拥有 `championships.loadtest.admin` 权限的管理员执行：

```text
/chunkstress start
/chunkstress status
/chunkstress stop
```

也可只为下一次重启把 `auto-start` 设为 `true`。插件加载后会立即把它持久化回 `false`，避免后续重启再次破坏地图。测试结束、清理日志完成后关闭服务并移除 JAR。

## 压力模型

- `stage-walkers` 是逻辑玩家数量，不会创建 Bukkit `Player` 或网络连接。
- `mixed` 阶段一半以 `stage-speed-blocks-per-second` 飞行加载新区块，另一半停留/低速分散并承载实体。
- 每分钟可在集中与分散布局间切换，模拟队友菜单传送后再次分头行动。
- 实体按 70/80 怪物、10/80 动物的比例轮换，启用 AI，并分布在停留者的最小/最大生成距离环带；不会用一万多个村民制造单一人工热点。
- 实体目标是整个目标世界的实体数，不只是插件管理数；达到目标后停止补充。
- 区块加载、实体创建和清理分别受并发、每 tick 提交量和批次预算约束。

当前默认配置用于短阶梯测试（8/32/64，目标 4,000/8,000/16,000）。长时间 10,000 实体稳定性测试应单独改阶段时长和目标，保留 TPS、失败数、磁盘和 pending 保护线。

## 关键配置

| 配置 | 作用 |
| --- | --- |
| `world` / `view-distance` | 被破坏的目标世界与每个逻辑玩家的区块窗口 |
| `movement-period-ticks` | 移动和窗口更新周期 |
| `team-anchor-radius-blocks` | 八个测试锚点相对世界中心的半径；这是压测模型，不代表正式 16 队布局 |
| `stationary-player-separation-blocks` | 同组停留者之间的分散距离 |
| `layout-switch-interval-seconds` | 集中/分散布局切换周期，`0` 为不切换 |
| `stage-walkers` / `stage-duration-seconds` | 各阶段逻辑玩家数与持续时间，列表长度必须一致 |
| `stage-modes` | `mixed`、`flight` 或 `dwell`；可留空使用默认 `flight` |
| `stage-speed-blocks-per-second` | 各阶段飞行者速度，上限 100 b/s |
| `stage-target-world-entities` | 各阶段整个世界的目标实体数，上限 50,000 |
| `max-concurrent-loads` / `max-submissions-per-tick` | 区块请求并发和提交节流 |
| `entity-spawns-per-tick` | 补足实体目标的速率，而非最终上限 |
| `limits.*` | 加载总量、pending、失败、磁盘和 scheduler TPS 自动停止线 |

`stage-modes`、速度和实体目标若非空，长度必须与 `stage-walkers` 相同。配置解析会拒绝越界值；仍应从低阶段开始，先验证 `/chunkstress stop` 和清理流程。

## 指标与结果判读

日志中的 `CHUNK_STRESS STATUS`/`STOP` 会报告完成与失败加载、pending、世界/管理实体、加载延迟分位数、scheduler TPS 和磁盘。判读时还必须同时采集 Folia 每个 region 的 TPS/MSPT、chunk 和 entity 数：全服平均 TPS 或 30% CPU 不能证明健康，一个 region 仍可能已经严重过载。

当前实现固定使用 8 个宏观锚点，无法精确表示正式 64 人四人队的 16 队拓扑；也没有真实玩家的网络、背包、AI 仇恨和协议成本。因此本插件通过后，仍需用协议客户端或真人完成整局远程 Bingo 验收。
