# Core / Bingo Worker 测试整理（2026-09-21）

范围为 Core、Bingo Worker 及其四个 reactor 依赖：common、bingo-engine、platform-bukkit、redis。整理前共 134 个测试类、503 项 JUnit 测试。AuthBridge、AuthProxy、独立负载测试插件不在本次修改范围。

## 整理结果

- 协议编解码、事务 ID 和状态机测试归到 common；bingo-engine 专注计分、重放、排名和玩法规则。保留畸形输入拒绝、不可变快照和哈希稳定性，删除已被整个对象相等断言涵盖的逐字段重复断言。
- Worker 删除共享规则介绍压缩、颜色转换和船只里程的重复用例：分别由 common 的时间线测试、platform-bukkit 的文本测试，以及 Worker 的全部坐骑 × 数据来源矩阵覆盖。保留 Worker 自身的模板替换、占位符、排名、停止、任务判定和重置测试。
- `DurableEventOutboxTest` 等到发布器实际收到事件后再断言调用次数，避免“文件已落盘但尚未调用发布器”的竞态；超时明确失败，资源通过 try-with-resources 关闭。
- 激流提取同包 `RiptideTestFixtures`，集中独立配置和内存世界准备；所有测试之间的夹具调用依赖均移除。
- 激流难度、数学分段和镜像检查共用原来的 200 个赛道样本，减少 200 次重复规划；目录测试保留 200 个种子和每张漏选蓝图的定向补选验证，将同种子重复生成的幂等性验证集中到一个样本，减少 199 次重复规划。另有独立的负数种子重放测试保留。
- GUI 保留菜单结构、引用键、中英文键集合和必要占位符；删除手写的源码拼接检查、标点/措辞限制和 Worker 源码禁止汉字检查。迁移测试继续检查旧默认值升级、自定义值保留及幂等性，不再重复写死 GUI/message 最新版本号。
- 配置替换测试使用 `ConfigurationStateExtension` 在测试类结束时恢复原静态字段，避免影响后续测试；淘汰消息测试在每个用例开始前清理自身输入。
- 删除必然成立或已被前一断言涵盖的检查，去掉仅输出统计却不判定结果的日志，并直接断言赛道耗时预算。

## 保留理由

BuildMart 的保护/蓝图状态/黄金结算、激流的实际移动/判题/分组/迁移、DAILY 的分队/战绩、数据库迁移与持久化失败恢复，以及 Redis 的消息和消费组隔离，均保留原有独立行为覆盖。platform-bukkit 与 redis 经盘点没有需要删减的重复用例，因此未为减少文件数而合并它们。

`WorkerWorldLifecycleContractTest` 和 `RiptideRushPresentationTest` 的源码接线检查暂时保留：它们是当前对部分服务器接线约束的唯一轻量检查，不能等同于真实 Paper/Folia 集成测试，也不以删除它们来宣称覆盖不变。配置键引用扫描同样保留，用来防止资源与调用端脱节。

所有 JUnit 测试和新夹具仍在各模块 `src/test/java`，包名与目录一致；生产目录没有 JUnit 测试。没有新增跳过测试或缩小 Surefire 发现范围。

## 验证

基线命令：`mvn -B -o -pl championships-core,championships-bingo-worker -am test`，503 项全部通过，Maven 总耗时约 3 分 54 秒。基线日志：`/tmp/cc-test-cleanup-baseline.log`。

相同命令复测：495 项全部通过，0 failure / error / skipped，总耗时约 2 分 46 秒。用例数变化为 common 17→19、bingo-engine 9→6、platform-bukkit 31→31、redis 6→6、Worker 48→44、Core 392→389。日志：`/tmp/cc-test-cleanup-final.log`。耗时为本机单次观测，不是基准测试结论。

最后移出生成器测试中的内存世界夹具后，针对生成/揭示、静态配置替换和异步发件箱执行随机类顺序复跑（`surefire.runOrder=random`，种子 `20260921`）：34 项全部通过，约 8.6 秒。日志：`/tmp/cc-test-cleanup-isolation.log`。测试目录/包名、生产目录无 JUnit、测试之间无夹具调用依赖以及 `git diff --check` 检查均通过。

本次仅修改测试及本文档。生产代码、默认资源、运行配置和 JAR 均未修改，无需部署或重启 cc-core / cc-bingo。
