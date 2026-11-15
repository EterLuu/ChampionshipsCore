# 测试组织与运行

测试按模块职责维护，以行为覆盖和故障边界为主。源码布局与格式要求见 [开发指南](01-development.md)。

## 验证层次

| 层次 | 入口 | 能证明的范围 |
| --- | --- | --- |
| 静态检查 | `scripts/check-source-layout.py`、`scripts/check-docs.py`、`scripts/check-spectator-contract.py --strict` | 目录、引用和源码接入约束，不执行游戏 |
| Java 回归 | 根目录 Maven `test` | 生产入口的算法、配置、接入和隔离生命周期；Bukkit 替身不等于原生服 |
| Harness 回归 | `championships-test-harness` 内 `npm test` | 客户端处理模拟输入、构造动作和判定报告；不连接 Minecraft 服 |
| 原生验收 | Harness 连接隔离测试服 | 真实协议、线程、地图机制、身份和跨服生命周期，按对应验证矩阵执行 |

`championships-bingo-loadtest` 是可运行的压测插件，Core `api/test/` 和 Worker 的测试会话释放也是运行功能。不能因为名称含 test 就把它们当作废弃 JUnit 工程删除。压测、协议客户端和单元测试各有职责，不相互代替。

## 归属

| 模块 | 重点 |
| --- | --- |
| common | 协议编解码、不可变快照、状态机与确定性 ID |
| bingo-engine | 计分、排名、重放和结果哈希 |
| platform-bukkit | 共享状态、文本、调度和任务机制 |
| redis | 消息字段编解码与 namespace/消费组配置；实际发布和故障恢复另做 Redis 集成验收 |
| Worker | manifest 接入、观察、停止、outbox 和世界生命周期 |
| Core | 游戏、地图、DAILY、赛程、持久化与管理入口 |
| auth-bridge / auth-proxy | 签名、账号事件、准入、身份解析、维护与故障回退 |
| bingo-loadtest | 压力配置、逻辑加载者、指标与资源清理 |

JUnit 与夹具仅放各模块 `src/test/java`，与被测类同包。测试类使用 `*Test.java`，由父 POM 固定版本的 Surefire 发现；目录检查会拒绝无法被默认规则发现的测试文件。共享夹具使用测试 support 包，不从另一测试类借方法，不进入生产 JAR。

Core 和 Worker 的对象分配、继承字段查找、字段设置和接口代理分别复用各自测试 support 包的 `TestObjects`。只有绕过 Bukkit 启动构造器确有必要时才使用 `allocate`，所需状态由用例显式建立；可以正常构造的模型直接调用构造器。游戏专属夹具继续留在该游戏的测试 support 包，不新建跨模块测试框架或生产依赖来共享几行工具。

Harness 的测试与模拟场景放在 `src/__tests__/`，共享模拟输入放在其 `support/` 子目录。`npm run test:scenarios` 按游戏分别报告客户端场景，输入和计分包均为夹具构造，不作为真实游戏积分或服务器协议兼容证据。

测试按被测生产类归属。消息格式、跨服序列化、命令解析、排名窗口和占位符使用各自的测试类；同一功能下的多个小模型可用明确命名的 `@Nested` 分组。需要配置或注册表替身的夹具只作用于依赖它们的分组，不能让纯模型测试一起修改全局状态。

## 用例设计与覆盖

JUnit 报告中的数量是执行次数，参数化方法按每种游戏或输入分别计数。评估覆盖时记录模块、被测行为与输入场景，不能仅依据执行数量。

- 同一接口的相邻输入、上下界、合法/非法值和角色组合集中为输入表，给断言附上输入上下文。整理用例时保留有效输入，必要时使用 `assertAll`，避免前一个场景失败后其余场景未运行。
- 同一配置和种子下的昂贵计划只生成一次，再检查配额、难度、镜像、时间和相邻关卡；不同配置、独立种子、确定性重放和失败上限仍分别覆盖，不用跨测试的可变缓存省略计算。
- 完整记录、集合或输出能表达契约时，使用完整预期值相等，避免重复的逐字段 getter、大小和 `contains` 断言。固定常量与对应行为一起验证。
- 通过被测入口验证输出和副作用。纯假设轨迹、与实现重复的计算、未调用生产入口的断言不能证明运行行为；原生包、碰撞、线程和生命周期按对应契约验收。
- 失败回滚、异步取消、重试幂等、资格和生命周期拥有独立失败原因，保持可单独定位。Core 与 Worker 的共享算法在所属模块验证，两端仍保留各自接入、身份及呈现检查。
- 逐游戏启动和必要的 Paper/Folia 原生验证不因数量较多而删减；参数化覆盖必须包含全部适用 `GameTypeEnum`。

逐项审查清单、统计和单次测试日志放在 `/tmp`。移动或重命名测试类后执行 clean test，避免旧测试 class 和旧报告混入结果。只修改测试和文档不需要替换运行 JAR 或重启服务器。

## 原生服与协议客户端

需要验证 Paper/Folia、BungeeCord、AuthMe、UUID、传送、重生、旁观或游戏结算时，使用 `championships-test-harness` 连接测试服或已明确开启调试入口的生产服。Harness 的 `resident + idle` 适合先建立 64 Bot 长驻基线，游戏动作再按 [无人测试游戏矩阵](../06-unattended-testing/02-game-test-matrix.md) 添加 profile。

```bash
cd championships-test-harness
npm ci --ignore-scripts --no-audit --no-fund
npm test
```

运行前确认数据库、Redis namespace、世界、身份域和正式赛程的使用边界。Core 先创建测试 session 并执行 `lease`，Harness 再连接；测试完成后由 Core 的 `stop --confirm` 和 Worker 的释放流程清理。报告同时查看客户端状态、Core roster、游戏阶段、数据库/Redis 写入和世界状态。

## 隔离和断言

- 异步测试等待真正发生的输出，不将“已落盘”当作“已发布”。超时明确失败，资源可靠关闭。
- 替换静态配置后恢复原值，每个用例重新建立自身输入。Core 使用 `ConfigurationStateExtension` 同时保护测试方法、嵌套分组和整个测试类：方法结束恢复到 `BeforeAll` 的基线，分组/类结束恢复进入前的配置。它恢复字段引用；测试应替换配置集合，不能原地修改捕获的集合。Bukkit 注册表、连接和其他全局对象仍由各自夹具用 `finally` 或 `AfterEach` 恢复。
- 源码接线检查可防止资源引用脱节，但不证明真实 Paper/Folia 的网络或线程正确性。

## 运行

从仓库根目录执行模块及 reactor 依赖测试：

```bash
mvn -B -ntp -pl championships-core,championships-bingo-worker -am test
```

只定位一项回归时指定测试类，并允许 reactor 上游没有同名测试：

```bash
mvn -B -ntp -pl championships-core -am test \
  -Dtest=GameStartServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

全面整理、移动测试或修改公共夹具后运行全部模块的干净测试与静态检查：

```bash
mvn -B -ntp clean test
scripts/format-java.sh --check
python3 scripts/check-source-layout.py
python3 scripts/check-docs.py
python3 scripts/check-spectator-contract.py --strict
```

该入口包含认证和 LoadTest 模块；Harness 另按上文运行 `npm test`。需要交付生产代码时才执行 `mvn -B -ntp clean package` 并按开发指南部署。检查失败/错误/跳过和覆盖范围，不把用例数下降本身当作优化依据。耗时比较需控制环境并重复采样。
