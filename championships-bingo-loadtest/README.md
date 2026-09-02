# Championships Bingo LoadTest

Bingo LoadTest 用逻辑移动者重现远程 Bingo 的区块加载窗口和实体压力。它把玩家附近的 view-distance 窗口展开到可复现的场景中，并在停留者周围按自然刷怪距离带生成怪物与动物，帮助定位区块生成、region 热点和实体 tick 的容量边界。代理、Redis、任务完成和正式积分链路随后由正式部署验收覆盖。

> 警告：测试会生成并保存大量新区块，最多主动创建数万实体。请在备份完毕、允许丢弃的世界运行；生产赛事服保持干净，安装此插件后测试结束即移除。停止测试会清理插件管理的实体和 chunk ticket，测试生成的新区块与地形修改保留在世界中。

详细结果、已验证范围和 64 人推荐配置见 [性能指南](../docs/bingo-64-player-performance-report.md)。

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

也可只为下一次重启把 `auto-start` 设为 `true`；插件加载后会立即把它持久化回 `false`。测试结束、清理日志完成后关闭服务并移除 JAR。

## 压力模型

- `stage-walkers` 是逻辑玩家数量，以区块加载和实体观察为模型，重点还原 Folia 的加载与调度压力。
- `mixed` 阶段一半以 `stage-speed-blocks-per-second` 飞行加载新区块，另一半停留或低速分散并承载实体。
- 每分钟可在集中与分散布局间切换，模拟队友菜单传送后再次分头行动。
- 实体按 70/80 怪物、10/80 动物的比例轮换，启用 AI，并分布在停留者的最小/最大生成距离环带。
- 实体目标是整个目标世界的实体数；达到目标后停止补充。
- 区块加载、实体创建和清理分别受并发、每 tick 提交量和批次预算约束。

随 JAR 发布的默认配置用于短阶梯测试（8/32/64，目标 4,000/8,000/16,000）。长时间 10,000 实体稳定性测试单独调整阶段时长和目标，并保留 TPS、失败数、磁盘和 pending 保护线。

## 关键配置

| 配置 | 作用 |
| --- | --- |
| `world` / `view-distance` | 被破坏的目标世界与每个逻辑玩家的区块窗口 |
| `movement-period-ticks` | 移动和窗口更新周期 |
| `team-anchor-radius-blocks` | 八个测试锚点相对世界中心的半径；正式 16 队布局使用 16 个锚点 |
| `stationary-player-separation-blocks` | 同组停留者之间的分散距离 |
| `layout-switch-interval-seconds` | 集中/分散布局切换周期，`0` 为不切换 |
| `stage-walkers` / `stage-duration-seconds` | 各阶段逻辑玩家数与持续时间，列表长度一致 |
| `stage-modes` | `mixed`、`flight` 或 `dwell`；可留空使用默认 `flight` |
| `stage-speed-blocks-per-second` | 各阶段飞行者速度，上限 100 b/s |
| `stage-target-world-entities` | 各阶段整个世界的目标实体数，上限 50,000 |
| `max-concurrent-loads` / `max-submissions-per-tick` | 区块请求并发和提交节流 |
| `entity-spawns-per-tick` | 每 tick 补足实体目标的速率 |
| `limits.*` | 加载总量、pending、失败、磁盘和 scheduler TPS 自动停止线 |

`stage-modes`、速度和实体目标若非空，长度必须与 `stage-walkers` 相同。配置解析拒绝越界值；正式阶梯从低压力开始，先验证 `/chunkstress stop` 和清理流程。

## 指标与结果判读

日志中的 `CHUNK_STRESS STATUS`/`STOP` 报告完成与失败加载、pending、世界/管理实体、加载延迟分位数、scheduler TPS 和磁盘。判读时同时采集 Folia 每个 region 的 TPS/MSPT、chunk 和 entity 数，把整机容量结论拆到具体的 region 热点。

LoadTest 使用 8 个宏观锚点模拟区块与实体负载。完整生产验收在此基础上使用 64 个协议客户端或真人运行整局远程 Bingo。
