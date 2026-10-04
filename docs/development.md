# 开发指南

修改玩法前，先确定功能由哪个模块负责，再沿用该模块现有入口。这样地图编辑、手动比赛、正式赛、DAILY 和远程 Bingo 才会得到一致的行为。

## 找到需要修改的代码

| 功能 | 所在位置 |
| --- | --- |
| 比赛协议、manifest、命令和事件 | `championships-common` |
| 不依赖 Bukkit 的 Bingo 判题和计分 | `championships-bingo-engine` |
| Core/Worker 共用的 Bukkit 行为、玩家呈现和调度 | `championships-platform-bukkit` |
| Redis 连接、发布与消费 | `championships-redis` |
| 远程 Bingo 会话、世界和命令 | `championships-bingo-worker` |
| 地图、赛程、队伍、DAILY 和其他游戏 | `championships-core` |
| 登录准入和 UUID 资料同步 | `championships-auth-proxy`、`championships-auth-bridge` |

Core 每个游戏以 `api/game/<game>/` 为功能边界。根目录的 Manager 注册地图和创建实例；`config/` 读取地图选项，`runtime/` 管理场地、回合、事件和清理，`model/` 保存模型，`geometry/` 处理空间算法，`mechanics/` 实现游戏机制。按需要创建子包，无需为空的职责建目录。Riptide 另有 `course/` 负责关卡池与生成，`editor/` 负责建筑草稿和试玩。游戏模型放在所属游戏内，共享的游戏类型、运行模式和阶段位于 `api/game/model/`，对局配对模型位于 `api/schedule/model/`。模型按所属领域组织，不再使用 `api/object/`。

新增玩法接入 `GameTypeEnum`、`GameManager`、对应地图 Manager 和 prepare 流程。启动参数统一通过 `GameStartArguments` / `GameStartService`，正式赛保存 `EventStartSelection`；不得为单个游戏再写一套 all/队伍解析器。复制双队比赛共用批量占用、预热和回滚入口，单实例多分区用 `ArenaSelection` / `StartAllocation`，选择不得修改共享地图配置或拆成多份计分实例。场地生命周期使用 `BaseGameInstance`；双队比赛使用已有 paired 抽象，复制地图使用现有 arena/spatial 工具。不要在命令或 GUI 内再维护一套场地注册、游戏状态或复制坐标算法。地图文件使用小写 `.yml` 后缀；目录扫描和首次 tick 初始化调用 `BaseGameInstanceManager` 的 `loadMapDefinitions` / `deferMapLoad`。公共清理会使旧加载请求失效并通过 `onAreaDetached` 移除副本索引。复制场地通过 `registerMapInstances` 发布实例，查询和删除沿用基类，不另存副本注册表；子类只处理游戏特有的取消操作，不复制通用卸载循环。

开赛占用、区块票据和异步回调都须有明确的所有者。重叠副本的区块票据由 `ArenaChunkPreloader` 统一引用计数，最后一个使用者结束才释放。正式赛停止或重新开始后，旧执行端结果不得结束新赛程；实例 reset/dispose 后的预热、持鱼、重生和补给回调不得重建旧状态。变更启动入口时逐项验证全部 `GameTypeEnum`，包含三个最终对决、双队批量回滚和四种单实例子场地玩法。

## 坐标配置

持久化的是坐标值和世界标识。读取时先得到 `ConfiguredLocation`，使用坐标时再解析为 Bukkit `Location`。解析不加载世界；未加载的世界可以暂时为 null，传送前必须确认其已加载。配置中不要写 `==: org.bukkit.Location`，也不要将 Bukkit `Location` 直接交给 `configuration.set`。

单个点使用原始 YAML 节：

```yaml
spawn:
  world: arena
  x: 10.5
  y: 80
  z: -20.5
  yaw: 90
  pitch: 0
```

`world_key: minecraft:arena` 也可明确指向世界 key。坐标列表沿用 `world:x:y:z:yaw:pitch`；方块选区的 Vector 和只含整数的方块坐标不是出生点，不需要附加朝向。

```java
ConfiguredLocation point = ConfiguredLocation.read(configuration.get("spawn"));
point.requireWorld(configuredWorld); // 需要限制地图世界时检查
Location target = point.resolve(id -> LocationConfig.resolveWorld(server, id));
LocationConfig.write(configuration, "spawn", player.getLocation());
```

简单运行时读取使用 `LocationConfig.readLocation(value, server)`；列表写入使用 `LocationConfig.asString(location)`。这些入口统一校验数值和朝向为有限数。保存未加载世界的点时，`LocationConfig.write` 会保留原配置的世界标识。新增配置字段通过 `ConfigOption` 和 `ConfigurationValueReader` 加载，不在每个游戏中复制字符串解析器。全局与地图配置使用同一字段加载器：先转换整份文档，再发布字段；缺少覆盖值时读取内置默认或字段初值，删除可选点不会继续保留旧传送目标。非法列表项、非有限数字、整数小数和溢出必须报错，不悄悄过滤或截断。

当前 Frostbite 配置为 v2，出生点和补给点都使用完整坐标字符串。导入地图时先把 `x y z [yaw]` 转成上述格式，再校验和发布；运行代码不自动猜测旧格式。

## 持久化和公共服务

SQL、连接池、schema 迁移和 DAO 位于 Core 的 `database/`。按 player、team、rank、daily、bingo 和 map 分包。玩法、命令和菜单调用持久化入口，不执行 SQL、不持有 JDBC 连接。地图改名通过 `MapRecordRenameTransaction` 协调数据库提交与文件/运行时回滚；增加地图标识列时同时更新 `MapRecordRenameMigration`。

数据库 IO 不运行在玩家或服务器 tick 线程。沿用管理器现有写入队列和缓存刷新，不另建线程池绕过顺序保证。Redis 传输逻辑归共享 Redis 模块；Core 的 RedisManager 只协调插件生命周期和业务订阅。

两端共用的玩家装备、效果、传送和 UI 行为应进入平台层，纯判题进入引擎，协议值进入 common。仅某个游戏需要的算法保留在该游戏中。通用方法按职责选择现有入口：`CoreMessages` 管理 Core 身份与消息，`LegacyText` 管理共享文本，`TeleportPositions` 管理传送位置几何，`TeamColors` 管理共享颜色，`LogText` 管理日志标签和时间，`AuthIdentity` 管理共享 UUID 校验。其账户接口校验 Bridge 的用户名规则，实际登录校验使用原版 UUID 算法，不把账户命名限制加到已经由服务器接受的登录档案上。禁止新增万能工具类或只转发到这些入口的兼容包装。

旁观和显隐必须遵守 [旁观契约](spectator-visibility-contract.md)。UUID 是持久身份，Player/Entity 只代表当前连接；身份边界见 [UUID 契约](player-uuid-contract.md)。不要在整理包或提取方法时扩大游戏资格、隐藏策略或线程访问范围。

## 清理与兼容性

删除兼容分支前检查调用方、默认资源和实际运行配置。需要迁移的配置应明确更新版本与部署文件，保留自定义值和发布状态；不要保留无人使用的转发类、旧方法别名或旧配置猜测。数据库 schema 的历史迁移仍负责升级已有数据，不能因为当前安装已迁移就删除。

抽取方法时让算法和 IO 各有入口；只开放跨包调用需要的类型和成员，其他细节保持 private 或包内可见。移动源码后同步 package、import、反射类名、源码检查路径、工具和文档。不要为了测试把运行时内部状态全部公开。

## 格式、测试和交付

Java 使用 UTF-8、LF、四空格缩进，以 Google Java Format 1.33.0 的 AOSP 样式为准。仓库根目录执行：

```bash
tools/format-java.sh
tools/format-java.sh --check
python3 tools/check-source-layout.py
mvn -B -ntp -pl championships-core,championships-bingo-worker -am clean package
```

Java 格式脚本只处理 Maven 模块的生产和测试源码；首次执行会下载固定版本的格式工具到系统临时目录。Python 工具使用 Python 3.11 或更高版本。先创建虚拟环境并安装 `tools/requirements.txt`，再运行 `ruff format tools` 或 `ruff format --check tools`；配置由根目录 `pyproject.toml` 管理。生产源码放 `src/main/java`，JUnit 和测试夹具放 `src/test/java`。测试与被测类同包；多个包共用的夹具放测试源码的 support 包。

按改动范围选择 reactor 模块并带上 `-am`。共享行为须同时检查 Core 与 Worker 的实现、资源、运行配置和测试。包移动需要 clean build，以免旧包的 class 残留在交付 JAR。涉及原生发包、反射或 Folia 线程的行为另按对应契约做原生验证。

成功打包后直接替换受影响的运行 JAR，不创建 JAR 备份。重启对应服务器才能加载新类；仅替换文件不会切换当前已加载代码。开发文档描述当前接入方法，工具文档提供可复现的审查方法，不把开发指南写成变更流水账。

文档使用仓库相对路径或服务端标准相对目录。示例服务名、数据库凭据、地图和玩家名称应明确为示例；不提交个人工作目录、私有服务地址或单次部署日志。
