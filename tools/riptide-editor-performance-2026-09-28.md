# Riptide 编辑模式 TPS 排查与优化（2026-09-28）

## 原因与证据

Core 侧栏每 20 tick 在服务器线程刷新一次。编辑模式原先通过
`CoreSidebarManager.renderEdit -> RiptideRushPrepareFlow.validate -> RiptideCoursePlanner.plan`
执行完整赛道规划。规划器包含 160 次尝试预算，因总耗时超限可扩至 640 次；重试中还会多次解析包含建筑快照的关卡池。

读取实际 `plugins/ChampionshipsCore/riptiderush/raft.yml`，437 项关卡、预览种子 1：

| 离线测量项 | 优化前 | 优化后 |
| --- | --- | --- |
| 预热后完整规划 | 821–954 ms | 497–705 ms |
| 连续读取关卡池 13 次 | 5.25–7.44 ms | <0.01 ms |
| 侧栏基础校验 + 配置进度（1000 次） | 原刷新会执行完整规划 | 中位数 0.097 ms，P95 0.322 ms，P99 0.427 ms |

同一预览种子的结果均为 43 个物理关卡、预计 5752 tick。以上是同机离线测量，不是线上 MSPT；侧栏测量仅覆盖校验和进度计算，不含文字渲染及发包。运行日志曾记录 TPS 9.8，与每 20 tick 额外阻塞约一秒相符。退出编辑不再进入该调用链。

## 实现

- 新增 `PrepareFlowDefinition.validateForDisplay`，其他玩法保持原有校验行为；Riptide 的周期展示只检查必填步骤、几何、速度/材料/种子、建筑尺寸与池容量。主动校验和发布仍执行完整搜索。基础检查失败时不再继续搜索。
- 将规划器的输入校验与搜索拆开，共用相同输入约束。侧栏的缺项数不代表赛道已通过完整编排验证。
- 每份 Riptide 配置持有不可变关卡池及解析结果。反射加载或回滚替换原始列表后，按列表身份重新解析；公开 setter 校验成功后整体替换。调用者不能通过列表、行或嵌套建筑数据修改缓存。
- 固定目录的正向和镜像通道列表各构建一次；自建或编辑过的建筑仍按精确快照匹配，不套用旧洞口数据。
- 无新增循环任务、异步 Bukkit 访问或静态会话缓存；不改变抽取顺序、随机数消耗、生成器版本和地图配置格式。

## 验证与部署

新增回归覆盖：周期检查不执行赛道搜索、完整校验仍拒绝不可能限时、编辑立即可见、池数据不可变、重载及回滚失效、无效替换保留旧池、正向/镜像元数据复用。已有保存失败回滚、规划确定性和实际运行池多种子测试继续执行。

全量测试发现原生成器测试把 49 项内置变体按 15 格间距排入 500 格航道，超出清理范围。测试航道现按变体数扩展，并断言每个关卡在范围内；生产清理范围和方块生成未改动。

构建命令：

```sh
mvn -B -o -pl championships-core -am package \
  -Driptide.runtime.config=/home/minecraft/minecraft/cc-core/plugins/ChampionshipsCore/riptiderush/raft.yml
```

最终 Core 398 项、reactor 依赖 62 项，共 460 项测试通过，0 failure/error/skipped，打包成功。已直接替换 `cc-core/plugins/ChampionshipsCore-1.3-SNAPSHOT.jar`，无备份 JAR。尚未重启；须通过现有管理流程重启 cc-core 后重新进入地图编辑模式验证 TPS/MSPT。实际配置和发布状态未改，不需要重新发布地图。

本次只涉及 Core 地图编辑及 Riptide 编排。Bingo Worker 不提供该编辑流程，也不实现 Riptide；共享侧栏布局、配置、占位符、协议及 Worker 代码均未改动。

离线探针与原始日志：`/tmp/RiptideEditProbe.java`、`/tmp/RiptideDisplayProbe.java`、`/tmp/riptide-edit-probe.log`、`/tmp/riptide-after-probe.log`、`/tmp/riptide-display-probe.log`、`/tmp/riptide-package.log`。
