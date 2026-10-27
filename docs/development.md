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

Core 每个游戏以 `api/game/<game>/` 为功能边界。根目录的 Manager 注册地图和创建实例；`config/` 读取地图选项，`runtime/` 管理场地、回合、事件和清理，`model/` 保存模型，`geometry/` 处理空间算法，`mechanics/` 实现游戏机制。按需要创建子包，无需为空的职责建目录。Riptide 另有 `course/` 负责关卡池与生成，`editor/` 负责建筑草稿和试玩。游戏模型放在所属游戏内，共享的游戏类型、运行模式和阶段位于 `api/game/model/`，对局配对模型位于 `api/schedule/model/`。模型按所属领域组织，禁止放入通用的 `api/object/` 包。

Laser 类游戏统一使用中立的 `api/game/laser/` 枪械包：`model/LaserGun` 定义枪械数值，`runtime/LaserWeaponState` 管理每个对局的弹药、换弹、连射及射击截止时间，`mechanics/LaserShotEngine` 负责射线检测和霰弹合并，`presentation/` 负责枪械物品、弹药显示、激光、开镜与击杀反馈。接入方式及生命周期见 [Laser 枪械接入](laser-weapons.md)。

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

Core 生产 Java 源码不包含中文，包括注释。玩家提示、命令说明、编辑器文案和日志文本放在 `message.yml`；菜单结构与按钮文案继续由 `gui.yml` 管理，地图规则由地图配置和 `schedule-message.yml` 管理。`message.yml` 的 `code-text` 按类名和调用入口分组，使用 `MessageConfig.codeText(key, arguments...)` 读取；`%arg1%`、`%arg2%` 等表示原调用的动态参数，参数只替换一次。此入口保留原始颜色编码，由调用方沿用既有颜色转换方式。缺少覆盖键时使用内置默认，显式空字符串保留；配置重载原子替换文本表，删除覆盖键会恢复默认。新增文案优先使用有明确含义的键，不在 Java 中加入中文兜底。

旁观和显隐必须遵守 [旁观契约](spectator-visibility-contract.md)。UUID 是持久身份，Player/Entity 只代表当前连接；身份边界见 [UUID 契约](player-uuid-contract.md)。不要在整理包或提取方法时扩大游戏资格、隐藏策略或线程访问范围。

## GUI 与语言配置

Core 通知中的中性玩家名由 `code-text.core-messages.player-name` / `player-with-team` 完整模板负责；它们不同于参赛身份、TAB 和聊天使用的共享 `presentation.player` 模板，不能用团队颜色替代中性姓名。个人加分 actionbar、足球首次开球副标题及 Core `bingo` 扩展的排名行分别使用 `base-game-instance.points-actionbar`、`sulfur-soccer-area.kickoff-countdown-subtitle` 和 `bingo-placeholder.rank-entry`。格式化保留原始数字语义、颜色、缺失排名回退和非首次开球空副标题；不能借文案调整改变计分、排名、身份查询或比赛状态。

`/cc team list` 使用 `code-text.team-list-sub-command` 的 `header`、完整 `row`、`summary`、`empty` 和 `names-separator`，由参数传入队伍、在线/离线名单及计数。整行与汇总只插值一次，名称中同名 token 保留。v69 从旧 `on-command-1..7` / `format-names-1` 明确迁移实际运行值后删除旧键，不能直接用默认行覆盖旧自定义标签。DAILY 的 `%prefix%` 是既有显式嵌套约定：先对正文执行一次前缀展开，再用公共模板工具处理外层 `%message%` / `%prefix%`；不要把这项兼容行为误删，也不要扩展为任意参数递归替换。

GUI 的菜单标题、按钮材质、槽位、状态、lore 和高亮通过 `gui.yml`、`GuiConfig`、`ConfiguredGui` 及现有 `GuiMenu` 入口管理。键表达用途，不新增 `text-001` 等编号键；相同文本但不同用途可以独立配置，不按字面内容强制合并。读取多行 lore 使用列表入口，禁止将 `lore.2` 当字符串路径。按钮动作依据所属菜单和当前状态判断，不能依赖可配置名称或材质识别功能。

运行文件是对内置资源的覆盖。验证和渲染必须使用同一份有效配置：先接入内置默认，再检查菜单尺寸、内容/边框/底部槽位、按钮引用、标题、lore 和 glint 的类型。只改一个按钮字段时，其余字段继承原配置；缩小菜单时也要校验继承的槽位。Bukkit 带显式 fallback 的 `getString(path, fallback)` 会绕过资源默认，不能用它读取已挂载 defaults 的字段。显式空字符串和空列表与缺键不同，重载时不得混淆。

冻结物品模板同样必须继承内置 `use`，不能因运行文件省略引用而丢失共享按钮字段；只有显式空 `use` 才取消该引用。Core 冻结给 Worker 的 Bingo 卡片和玩家菜单仍通过现有 presentation schema 传递有效值，Worker 不重新解析 Core GUI，也不新增第二份默认样式。

完整 GUI 审查以 `MenuId` 的窗口和模板父节点为清单，关联实际渲染、点击及原生验证；字段缺省数不能当作缺陷数。对运行文件省略的 footer，逐项验证内置默认、显式父级物品槽位、当前窗口物品槽位及显式 footer 的优先级，并验证已打开窗口的路由快照。不要为追求配置文件键数相同而补写默认 footer，避免遮蔽已有父级覆盖。原生验证说明类来自当前打包或部署 JAR，不能由 reactor 的 `target/classes` 遮盖；配置校验、原生窗口/物品断言和真实客户端验收分别记录。

分页容量、条目偏移和点击目标必须来自同一份内容槽位列表，不能显示读取 `layout.content`，点击仍按固定 36/45 或原始槽号计算。可配置列表使用 `MenuLayout` 保存本次打开背包的尺寸、内容、边框和控件槽位快照；固定控件占用的槽位不计入内容容量，边框不能覆盖条目。菜单持有者保存实际渲染的对象标识，点击不得依靠另一份实时排序重新猜测目标。配置重载后，已打开菜单继续使用其原槽位路由，重新打开时读取新布局。子菜单自己的页码与返回上一级的页码分别保存。颜色、目标队伍等列表也须处理自定义内容区容量变小的情况，不能静默截断。

Core 和 Worker 的玩家选择窗口共用 `PlayerListPresentation` 与平台 `PlayerListMenu`；Core 的 `MenuLayout` 配置适配层和共享窗口使用同一份 `InventoryMenuLayout` 分页算法。Core 将 `spectator.menus.player-teleport-selector` 的有效标题、布局、所有物品模板和完整 lore 冻结到 manifest 的 `gui.spectator-players.*` 字段，Worker 不另写图标或 45 格路由。旧 manifest 通过只读兼容资源保留原语言和布局；只缺少整个新前缀时才允许兼容，部分字段或未知版本必须报错。新配置在准备阶段验证接收端原生物品材质，不修改已持久化 manifest 或哈希。

动态 GUI 行通过 `GuiText.row` 读取各菜单的 `.presentation.detail-row` / `.presentation.description-row`，参数 `%message%` 保留原详情或 Bingo 语言组件的显式样式和字面 token。样式归 GUI，行内容归原领域，不能把样式覆盖写在 Java 中，也不能把详情文本重复复制到 GUI 资源。空模板、局部覆盖、删除恢复默认必须一致；物品自身非斜体仍沿用共用工厂规范。Core 的本地反馈与地图编辑牌子通过 `CoreMessages.styled` 使用 `code-text.bingo-area`、`area-list-gui`、`parkour-tag-chaser-button-step` 的完整包装模板，保留已有消息键及运行自定义正文。牌子颜色和粗体由模板决定，物理牌子位置、朝向、蜡封、发光与编辑生命周期仍归原步骤；不改 Worker 已冻结的同伴传送语言。

旁观热栏与操作反馈的样式同样属于配置。Core 的 `spectator.feedback.*` 用 `%message%` 嵌入原消息组件，保留消息中的显式颜色，不在 Java 中强制覆盖根样式。Worker 热栏由 Core 的 `games.bingo.hotbar.spectator` 配置，`teleport` / `speed` 包含槽位、材质、标题、完整 lore 和 glint，`card.slot` 为只读卡保留槽位，`speed.feedback` 控制操作反馈。Core 先解析当前 Bingo 语言的 `%name%`、`%hint%`、`%faster%`、`%slower%` 和 `%message%`，保留运行时 `%speed%` 与冻结的 `%prefix%`，普通参数单次插值、不递归解释前缀内容，再通过 common 的 `SpectatorControlsPresentation` 冻结为 `gui.spectator-controls.*`；Worker 使用平台 `InventoryItems` 渲染，不另拼颜色或说明。卡与两种控件必须占用不同的 0–8 槽位；Core 外部旁观热栏也检查冲突，互斥的参赛者入口不与外部场地入口比较。新快照的字段缺失或版本错误须拒绝，接收端 PREPARE 校验原生物品材质。完全没有新前缀的历史快照才读取兼容资源，并复现原先的根样式覆盖，不改写旧快照或哈希。PDC 动作、旁观身份、资格、速度边界、恢复和传送仍由原管理器/会话拥有，展示配置不得改变这些契约。

组合展示中的分隔符和坐标格式也必须有明确的资源所有者。Riptide 踩色提示使用 `code-text.riptide-presentation.floor-separator`，仍直接追加原目标翻译组件，并保持加速破盾提示优先于普通破盾；配置不能改变玩法判断。地图编辑坐标显示使用 `code-text.gui-text.coordinate` 的三个整数参数，不改变坐标存储或解析。大厅 Bingo 奇遇结果标题由 `code-text.daily-bingo-vote-controller.remix-title` / `remix-subtitle` 决定完整布局，`%title%`、`%name%`、`%description%` 来自当前 Bingo 语言，通过平台 `ComponentTemplates` 一次嵌入并保留显式颜色；不要把副标题拆为固定 Java 连接片段。该播报发生在 Core 大厅投票，Worker 对局不重复该流程；不要为纯展示新增随机选择、投票或对局状态。

世界中的文字实体外观统一由 `presentation/display/TextDisplayStyle` 应用，配置位于 `message.yml` 的 `presentation.world-text`。`defaults` 提供朝向 `billboard`、对齐 `alignment`、正整数 `line-width`、`shadowed`、`see-through`、`default-background`、`background`、`text-opacity`、`block-light` 和 `sky-light`；四个 `profiles` 分别对应 `ace-race-name`、`build-mart-materials`、`riptide-instruction`、`riptide-color-target`。生效优先级为运行 profile、内置 profile、运行 defaults、内置 defaults，局部覆盖和删除恢复都使用同一读取入口。背景使用 `#AARRGGBB` 或 `native`，透明度为 0–255；两种光照均为 -1 时沿用环境光，否则各为 0–15。错误字段、枚举、类型、范围或混用环境光与固定光照必须在整组配置发布前拒绝。样式快照不可变，不保存实体或另起刷新任务。

样式应用不得修改实体文本、位置、剔除边界、移动插值、默认查看权限、持久性、无敌/重力、所有者标签或清理规则。Ace Race 姓名仍默认隐藏，由原距离/冲刺与旁观显隐流程控制；姓名的 `code-text.ace-race-area.racer-name` 模板保留队色，未分队时使用模板样式。Riptide 的 `code-text.riptide-introduction-run.color-target` 只渲染 `%block%` 翻译组件，仍追加在原提示后，保留提示根样式继承。BuildMart 材料统计与列布局仍由 `MaterialDisplayFormat` 拥有，默认宽度 4096 避免自动折行干扰原列布局；16 格剔除边界与按钮位置偏移属于实体几何。新实体使用当前样式快照，BuildMart 已有实体在原订单更新入口同时更新样式；Ace Race 和教学实体在原场地重建时采用新样式，不为配置引入全服扫描或新的生命周期。Worker 不创建这四类 Core 游戏资产；以后扩展到 Worker 时须冻结完整配置并验证接收端行为。

玩家选择窗口同时绑定原所有者、原 Player 连接、原会话 token 或角色快照及当前背包。翻页重读当前名单，夹取页码并清空上一页目标；选择须核对目标仍在原名单。关闭事件可能改变身份，因此关闭后、分派选择前再次检查连接和原会话。选择直接回到原所有者，禁止经当前 registry 把旧窗口目标转交新局。旁观身份和区块预热、最新请求、最终传送仍由现有 Core `SpectatorManager` / Worker 会话统一入口负责，菜单不得另建旁观状态。

Bingo 队友传送使用同一个玩家列表窗口，配置入口为 `games.bingo.menus.teammate-teleport`，其有效配置冻结为 `gui.teammates.*`；条目继续读取 `items.teammate`，翻页等控件继承共享按钮。v57 仅为该菜单预留页脚并缩小默认内容区，不覆盖原队友图标、标题、lore 或其他菜单。Core 的旁观监听器只分派自己拥有的共享窗口，Bingo 监听器只分派原 Bingo 实例的窗口，防止一次点击被处理两遍。旧 Worker manifest 保留队友语言、配色和物品样式；旧自适应单页布局会截断名单，改为资源定义的完整分页布局，并明确将这项修正与旧样式兼容区分。

队友传送请求由原 `BingoArea` / `WorkerMatchSession` 的 `PlayerTargetTeleports` 管理，不能由物品 PDC 或当前 registry 猜测目标。源和目标都必须是原回合的正常游戏玩家、同队、当前连接、存活且位于 Bingo 世界；不得把旁观者作为参赛者传送的源或目标。目标实体线程先捕获位置，异步预热区块后再次验证目标，在查看者实体线程紧邻 `teleportAsync` 前核对源资格、目标角色快照和最新请求。成功提示同样返回查看者实体线程并重查资格。新的选择覆盖旧请求；离场、断线、死亡、真实模式变化和结束/reset/dispose 取消相关出入请求，旧实例清理不能取消新实例请求。Core 保留原回合引用与游戏 generation；Worker 保留原 roster 的 PlayerSnapshot。该服务只保存待传送请求，不拥有或复制旁观身份、显隐策略或游戏 roster。Folia 验证必须包含真实跨区域实体调度，不得以纯背包代理测试声称线程验证完成。

日常 Bingo 投票由 `DailyBingoVoteController` 保存本次投票、资格和结果，`DailyBingoVoteMenu` 只保存窗口及实际选项。模式、难度、线数和创世分别使用 `daily.menus.bingo-vote.screens` 的四份 `MenuLayout`；标题和按钮仍引用所选 Bingo 语言，完整 lore 通过独立一行 `%description%` 插入全部本地化说明，然后单次展开参数。默认模式/难度/线数在 11–15、创世在 9–17；这些默认值只作为布局兜底，点击必须使用本窗口保存的对象。阶段前后页与选项前后页是不同控件；内容区容量改变时所有选项可分页访问，不能改变三步投票流程或截断创世候选。刷新票数和选项分页保留原背包与槽位快照，阶段切换创建新窗口。积分模式与极难难度继续检查实际队伍资格；有本地基础材质的选项须显式配置其 locked 材质，以符合“本地字段覆盖共享按钮”的继承规则。

结果播报使用固定 `Locale.ROOT` 构造模式/难度语言键，不依赖服务器系统语言。随机模式抽取后仍保留其随机来源，使用既有 `vote.result_random` 模板播报实际抽中的模式。

投票窗口绑定原控制器、本次 `CompletableFuture`、当前 Player 连接和正在查看的背包，旧阶段、旧连接、旧控制器与旧投票不能投票、翻页或关闭新窗口。计时任务同时绑定原投票和阶段；取消与完成先关闭所属窗口、释放旧状态，再完成 future，避免完成回调启动的新投票被旧清理覆盖。日常投票与 Remix 配置通过不可变 `DailyVoteSettings` 一次替换；启动和内容重载共用解析。`bingo/config.yml` 的 `daily-vote.seconds` 是三个普通阶段共享倒计时，`genesis-seconds` 与 `genesis-options` 决定创世时长和抽样数，抽样仍来自已筛选的任务池；候选数不受 GUI 一页容量限制。BossBar 以本阶段开始时保存的真实时长计算进度，不能复用普通投票时长计算创世进度。

LaserBox 与 LaserShowdown 共用 `LaserGunMenu` 和 `LaserGunItems`。选枪窗口由 `laser.menus.gun-selection` 配置尺寸、内容、边框、分页控件和各枪样式；`buttons.laser-gun` 统一完整 lore 与 selected 状态，单枪可覆盖字段。默认仍显示五种武器，内容区变小时全部武器必须可分页访问，翻页不能关闭窗口或提前结束本次生命的选择宽限。窗口保存 `MenuLayout` 和实际枪械目标，点击不能按 `10 + 枚举序号` 推断。所有点击绑定当前连接、原背包和游戏提供的回合/角色/生命资格；选择回调打开别的窗口时，只结束原菜单，不能关闭新窗口，关闭回调最多执行一次。

两个激光玩法的击杀、复活和换弹反馈共用 `LaserCombatMessages`，沿用 `laserbox` 语言键。默认文案由正常配置加载提供，游戏类不另写英文兜底；显式空文案保持为空，删除覆盖后恢复内置资源。击杀消息对玩家身份、武器名称及其他参数只展开一次，参数内的占位符不能被后续替换。拾取提示的颜色由对应 `laserbox.item-*` 完整文本决定；旧默认黄色应迁入资源，不在代码中隐式补色。倒计时取整、标题时长与原游戏资格由原有调用路径保持，展示整理不得改变复活或战斗状态。

激光玩法的实体装备展示由 `laser.equipment` 管理：枪械继承 `buttons.laser-gun` 的完整名称和 lore 模板，弹药可配置名称、完整 lore 和 glint；两种玩法的选择器与 LaserBox 补给物品共用 `LaserEquipmentItems`，完整颜色与 lore 留在各自配置路径。枪械/弹药保留原生斜体默认，菜单和功能物品沿用各自原有样式。`code-text.laser-equipment` 提供共享物品名称，`laser-gun-controls` 提供枪械操作说明；参数只插入一次，显式空列表不追加固定说明。

实际枪械材质与原生连射组件、换弹冷却绑定，弹药材质与装备清理绑定；配置加载须校验实际装备及所用状态的材质仍等于机制定义，不能接受后静默忽略或让模板破坏玩法。选枪菜单图标与 PDC 识别的功能物品可独立改材质。模板不得改变枪械属性、不可破坏状态、隐藏属性标记、补给 PDC、弹药数量或游戏资格。迁移旧编号语言键时先核对默认及运行值，将完整说明和原有隐式颜色迁入 GUI；运行 GUI 与消息版本仅在打包及原生验证成功后随对应 JAR 发布。

武器名称、描述、弹匣/射速摘要、选择反馈及战斗 HUD 继续使用 `message.yml` 的既有 `laserbox` 译文，不在选枪 GUI 或 Java 中重建一份语言兜底。GUI 标题默认 `%title%`、枪名默认 `%name%`，引用这些既有译文以保留运行覆盖。真实枪械参数与装备材质仍由模型和机制拥有，菜单图标的材质覆盖不改变发给玩家的武器；物品持久标记、耐久和属性沿用共用工厂。参数使用 `MessageTemplate` 单次展开，枪名中含 `%ammo%` 等文本时不得再次求值。

Frostbite 实际道具统一由 `frostbite/presentation/FrostbiteItemPresentation` 创建，运行场地负责何时发放和使用。名称/描述仍读取既有 `frostbite.item.<类型>` 覆盖；完整名称与拾取提示模板分别来自 `code-text.frostbite-item.display-name` / `pickup-hint`，完整多行 lore 来自 `frostbite.item-presentation.lore`，支持 `%title%`、`%description%` 和显式空列表。参数只插入一次，不强制追加颜色或说明；真实材质、不可破坏、道具类型 PDC 和每把弓的独立 UUID 属于玩法身份，整理展示时必须保持。

BuildMart 材料标签的 `buildmart.material-display` 在 `message.yml` 中定义正整数 `columns`、整体 `style`、完整 `entry` 和列间 `separator`；默认仍是白色、每行两项及“材料 × 数量”。`MaterialDisplayFormat` 在发布配置前校验整份快照，非法列数/模板类型不能替换已接受配置，删除覆盖恢复内置默认，空条目和空分隔符保留。条目通过共享 `ComponentTemplates` 插入 `%material%` 的 Adventure 翻译节点和 `%count%`，不能将客户端材料译名提前转换为纯文本或再展开材料名里的参数。换行按同一快照的列数生成，方块材料计数、普通/黄金区域规则、实体所属场地和清理仍归原机制入口。

队伍命令信息的整行标题和成员行分别由 `code-text.team-manager.info-header` / `info-member` 管理，队伍颜色与名字仅作为参数，不在代码中另加边框。侧栏缺少整个 `boards.daily-lobby` 时从随插件发布的 `scoreboards.yml` 读取 DAILY 模板及缺省样式，再应用运行样式覆盖；不能在 Java 中另写品牌、页脚和内容。存在但类型错误、空标题或空行列表继续报错，不能把非法配置当作缺省；已有运行模板优先。旧 `code-text.sidebar-configuration.load-*` 值保留供历史配置回退参考，当前 DAILY 内容只由 `scoreboards.yml` 拥有。初次侧栏配置失败时不显示代码内虚构的品牌标题。

地图状态/编辑侧栏的条目、发布标记、阶段分隔符、游戏循环配色和完成/未完成颜色由 `scoreboards.yml` 的 `presentation.map-status` / `presentation.map-edit` 拥有。`MapSidebarPresentation` 在加载时形成不可变快照；缺省键只继承打包资源，显式空字符串保留，类型错误及空配色列表拒绝，重载失败保留旧快照。完整条目使用单次参数替换，地图名与发布标记中的同名 token 不再展开；四条地图显示上限和游戏枚举顺序仍是现有布局/排序逻辑。修改这些 Core 编辑器专用字段须核对全部游戏和运行配置，并证明 `bingoWorkerFields()` 完全不变；不能借此覆盖 Worker 的比赛侧栏或既有 `values` 自定义值。

地图编辑快捷栏在 `PrepareSession` 建立时保存一份 `MenuLayout`，刷新控件、保护槽位及创造模式选取方块都使用该会话布局。快捷栏只有 0–8 九个协议槽位；即使没有窗口 `size/layout`，也必须校验控件不越界、不重叠，不能按普通背包的 54 格接受配置。WorldEdit 选区斧保持不带编辑器动作标记，无选区步骤时该槽位可用于建造；蓝图工作台的提交/取消复用当前会话的保存/退出槽位。状态卡的 `pending-entry.lore` 对每条未完成步骤完整展开，显式空列表保留，步骤名称中的参数文本只插值一次。盖章等延迟输入须再次核对当前连接、原编辑会话及权限，已经结束的输入不得写入旧地图或操作新窗口。

锦标赛投票窗口同样保存 `MenuLayout`，配置内容容量不足时分页，不向未配置槽位补入候选。所有候选均可访问，页码在自动刷新或候选减少时夹取；显示、点击、前后页与关闭共用原窗口的控件快照。空态只在没有候选时覆盖其配置位置，不算固定控件；它不得与常驻按钮冲突。投票进度条的长度及填充/空白符号由 `voting.menus.ballot.presentation` 提供，计算仍采用原舍入规则。窗口绑定原 VoteMenu、玩家当前连接和正在查看的背包，旧管理器清理或旧点击不能处理新窗口；管理员预览仍不能投票。

日常战绩与排行榜各自有 `screens.overview` 和 `screens.detail`，由 `DailyMenuPage` 共用分页与导航。支持的居中页面可将 `layout.content` 写成“条目数 → 槽位列表”；例如 `"1": [22]`、`"2": [21, 23]`。最大列表扣除固定控件后决定每页容量，其余列表只控制尾页排列；数量键必须等于列表长度，所有槽位均须有效且唯一。固定控件占用了某个居中位置时，该页回退到完整内容列表中的可用槽位，不覆盖按钮。普通列表如 `[16, 10]` 仍按原顺序分页，完全替代居中表。渲染必须使用 `MenuLayout.pageSlots`，点击保存实际对象或使用带总数的 `index`；仅在 `MenuId.supportsContentArrangements` 声明已接入的菜单开放这种配置，不能给仍按固定顺序点击的页面启用。空态位置也不能覆盖固定按钮。

日常成绩卡片共用 `DailyRecordItem` 和 `buttons.daily-record` 的游戏图标/颜色状态。完整 lore 中单独一行 `%records%` 插入动态成绩，其他行完整保留；先识别模板中的行标记，再单次展开参数，玩家名或地图名中相同文本不得被当成第二个标记。动态成绩行和固定说明使用各自的语义模板，不按旧 lore 的第零行、第二行硬拆，修改模板长度不得丢行。v48 将日常菜单旧根布局迁入两个 `screens`，默认布局改为居中表，已有自定义槽位列表保留；摘要、榜首行和地图卡片分别有独立模板，父级按钮槽位继续可覆盖。

日常大厅、游戏选择和组队窗口同样由 `DailyMenuPage` 保存布局；游戏卡片与在线候选人按配置容量完整分页，自动刷新名单时夹取页码并重建实际目标，不能按在线人数截断。固定功能菜单只配置各 `items.<用途>.slot`，不保留无效的 `layout.content`；空态使用 `layout.overlay.empty`，可放在内容区但不得覆盖按钮。旧组队窗口的操作须核对原小队引用和 revision，接受邀请前核对原 `PendingInvite`；身份或邀请改变时只刷新，后续点击才作用于新对象。

日常成员行共用 `DailyPartyPresentation` 与 `buttons.daily-party-member`，完整 lore 用单独一行 `%members%` 决定动态名单位置；没有标记时不追加名单。成绩与成员模板由 `GuiItemLore` 统一先识别行标记、再单次展开参数。v50 为已有两处小队 lore 增量追加标记，以保留原有末尾成员列表。地图名预览数量和分隔符属于 `game-selection-screen.presentation.map-preview-limit` 与 `items.map-separator`，不得在菜单中另写固定展示数量和分隔字符。

场地旁观选择器的场地、比赛分区和匹配赛建位置页面共用 `MenuLayout` 与窗口归属检查。每个窗口冻结尺寸、内容、边框、控件和空态覆盖槽；自动刷新与分页更新同一个背包，点击只使用实际渲染的目标表。旧连接、旧管理器、停止后的排队事件和已被替换的窗口不得操作当前菜单；旧管理器关闭时只关闭自己的窗口。场地与传送资格仍由 `GameManager` / `SpectatorManager` 决定，菜单不保存平行的旁观状态，已移除的场地或位置不得凭旧条目进入。

场地菜单的游戏图标/颜色和阶段颜色由 `spectator.styles.games.<命令标识>` / `spectator.styles.stages` 读取，键使用 `GameTypeEnum.commandName()` 与 `Locale.ROOT`，覆盖全部游戏。展示分隔符由 `spectator.presentation.separator` 提供。真实材料、队伍颜色等数据生成缺省图标时，显式物品配置仍应优先；不得声明一个永远被代码覆盖的 `material` 或 `glint`。队伍说明通过完整 lore 中的 `%teams%` 行展开，明确的空列表保持为空，选中状态不得强制追加固定行。跨菜单家族的动态行展开共用 `GuiItemLore`，先识别模板行标记，再单次插值普通参数，不能把玩家、队伍或场地名称中的同名占位符当成模板再次处理。

固定按钮槽位使用该菜单的 `items.<用途>.slot`，已有 `layout.footer.<用途>` 的菜单继续以其为优先入口；渲染和点击不能分别读取。不同状态确实互斥的按钮可以复用槽位，例如成员页第一页的返回按钮与后续页的上一页按钮。`layout.border: []` 表示不填边框；分页内容区必须至少保留一个非控件槽位。声明 `size` 的菜单以内置尺寸为下限，校验拒绝更小的覆盖值，避免渲染悄悄回退。生产加载通过 Paper 的物品注册表校验材质；结构单测与原生材质/背包验证分别报告，不以无服务器的枚举测试替代平台验证。同屏同时存在的两个控件不能占用同一槽位；互斥例外在 `MenuId` 明确声明，不能靠点击分支顺序决定哪个动作获胜。

同一编辑器有多个窗口时，使用独立的 `screens.<用途>.size/layout`；存量编辑器已有按用途分窗的节点时沿用原路径，可通过 `MenuId` 声明复用父菜单的按钮定义。控件位置优先读取显式窗口覆盖，再读取原有父菜单 `items.<用途>.slot`，最后继承内置默认；校验也必须按这个顺序解析，并检查实际窗口尺寸。运行配置新增子窗口时不写入内置 footer 的副本，以免屏蔽用户已有的父菜单槽位。列表条目的材质、标题及完整提示由配置读取，动态详情保留其拼接边界；材料图标缺省时由数据决定，不能用一个静态默认材质覆盖全部真实材料。

Riptide 保留已有 `category`、`entry`、`choice`、`preview` 等十三个窗口路径，由 `RiptideEditorLayout` 统一解析布局。按钮渲染时登记稳定动作 ID，点击只接受本窗口实际登记的动作；条目、属性选项和预览关卡分别保存显示目标，不根据名称、图标、旧槽号或另一份实时列表反推。按类别互斥的配额/参数、创建按钮和施工说明槽位在 `MenuId` 明确声明；不是通用允许按钮重叠。选择器沿用数量布局表，默认26项与返回按钮放在27格窗口内；只有需要多页时才从内容区扣除上一页/下一页，不能为了新增翻页而截断默认选项。空列表或编排失败的展示位置用 `layout.overlay.<用途>`，可与内容区重合但不能遮盖固定控件。

编辑窗口保存不可变配置值快照和当时的施工草稿引用，不另建可写的草稿或身份表。配置或草稿改变后，旧选择、删除、复制和铁砧提交只提示刷新；铁砧验证与最终提交都再次核对权限、原会话和快照。预览打开具体关卡时传递原有不可变计划，避免按新计划中的相同序号选择另一关。新建/复制后返回页按目标类别的配置容量计算。踩色阶段时间、轮数和材料数量由 `RiptideDifficulty` 的实际规则读取，分隔符由 GUI 资源提供，不能维护第二份固定显示数值。

编辑列表只操作当前窗口实际显示的对象。材料区按快照 UUID 查找当前条目，条目已删除时只刷新；通用点位编辑在列表变化后拒绝旧索引，重新展示列表。延迟输入提交再次核对原编辑会话及列表快照，编辑后返回原列表页，条目减少时回到仍存在的页码。

地图入口列表、装备选择与开赛消块菜单共用 `PrepareMenu` 的查看者、原会话、不可变布局和按钮渲染。地图入口使用可配置的54格分页窗口，按名称排序并保存实际地图配置引用；同名定义已替换或已删除时只刷新，不能让旧删除确认作用于新定义。删除后保留原页并夹取合法页码，确认提示必须包含完整游戏/地图标识，时间由实际确认窗口传入。固定装备/消块选项按实际槽位保存稳定枚举，不使用旧槽号或可配置材质/译名反推；点击再次核对管理员权限及原会话。

枚举和静态选项表保存语言资源键，不保存初始化时的译文。`AceRaceEquipment.displayName`、消块 `Mode.displayName` 和 `TGTTOSAreaTypeStep.Option.description` 每次读取当前配置，重载后更新显示，同时保持持久化的装备值、模式值和旧模式别名。已有步骤对象也必须读取当前选项说明；删除覆盖恢复内置默认，显式空值保持为空。装备菜单使用独立的 `buttons.ace-race-equipment`、`selected/idle` 状态和完整 lore；不能借用其他菜单的提示首行作为本菜单说明。

铁砧输入统一由 Core 的 `AnvilInputMenu` 管理原生窗口、占位物和回调，地图编辑只在 `AnvilInputGui` 保留领域校验；队伍菜单不得另存输入窗口表或依赖地图编辑标签。每次打开绑定原领域管理器、原 `Player`、原背包及原会话/队伍对象。验证前、验证后和关闭窗口后都检查当前权限、原连接与领域上下文；关闭事件中重新打开窗口、切换会话或撤销权限后不得执行提交。程序关闭或替换窗口须先取消回调并清空占位物；Esc 返回统一延迟到实体调度器下一 tick，并检查期间没有打开其他窗口。退出、同 UUID 重连、领域释放、调度拒绝/retired 和插件卸载使旧回调失效；旧所有者的清理不能关闭新所有者的输入。已退出登记的原生窗口仍保持只读，但弱引用标记不得持有回调或玩家。

铁砧输入格0与结果格2是容器协议约束，不作为可移动的胸箱控件；标题、提示、材质、glint 和完整 lore 由各自资源管理，地图名称/数字及队伍名称/玩家名输入共用 `buttons.editor-input`。`%prompt%`、`%value%`、`%hint%` 只插值一次；空 lore 明确保持为空。输入物品名称始终保留真实预填值，配置的输入标题提供提示，不能覆盖待编辑数据。`items.hint` 是文本节点，不声明无作用的材质。输入物品必须在第一次发送原生窗口前完整构建，并在关闭前移除，费用保持为零。

队伍管理每个窗口绑定实际创建它的菜单实例和原连接，操作只接受当前顶层背包。现有队伍及队伍选择列表保存实际对象快照，删除后同名重建不能接收旧点击；创建成功的返回也绑定本次服务完成时的对象。异步查询/修改的反馈使用独立界面请求票据，回到原玩家实体调度器后核对权限、连接、插件、原窗口、最新请求及目标对象；窗口曾经关闭或打开后，即使回到同一个默认背包也不能复用旧反馈。确认和创建按钮在接受后禁止重复提交，但返回/取消仍可操作。数据库和已接受写入继续由 `TeamManager` 现有生命周期及并发保护拥有；界面失效只取消旧反馈，不能另建业务操作表或回滚已接受写入。原生命令权限仍由命令分发器执行，菜单入口和延迟回调独立复查。

队伍窗口通过 `TeamMenuItems` 渲染完整物品模板，动态羊毛、玩家头像和队色只提供身份数据，不另写标题、颜色、lore 或 RGB 表。导航依次继承全局按钮、`teams.buttons` 和本地字段，显式空 lore 保留。七类列表均使用 `MenuLayout.pageSlots`，支持稀疏及居中排列；确认窗口没有列表容量，单独保存返回页。只为实际渲染的控件登记点击路由，成员首页的返回与后续页的上一页使用各自配置槽位，隐藏按钮不得响应。空态位置由 `layout.overlay.empty` 配置且不能覆盖常驻控件。颜色可用性说明以独立一行 `%availability%` 插入完整行列表，不能截取首行或自动追加；已有 `light-blue`、`yellow-green`、`light-gray` 配置标识保持兼容。默认及部署配置需分别进行原生物品与标题比较，并覆盖自定义材质、glint、完整/空 lore、全部分页和各颜色状态。

成员行和在线/历史玩家行保存选中 UUID，名称仅供展示。删除使用原队伍对象及 UUID，DAO 同时限定队伍 ID 和 UUID；按名称的命令入口只能解析唯一成员。已选玩家的加队调用把 UUID 交给 `PlayerManager`，在线身份或配置目录返回不一致时，必须在身份缓存和数据库同步前拒绝；手动输入姓名继续使用既有身份模式。在线转队在受理时复查 UUID、姓名及原目标队伍，事务只能删除该 UUID，其他 UUID 的同名行必须拒绝，不能以转队实现隐式身份迁移。成功后按该 UUID 的已保存名称清理原计分板。测试覆盖改名、同名替换、目标队伍重建、迟到查询和事务失败；实际数据库验证使用隔离实例。

玩家身份、TAB 前缀与聊天行的样式统一由 `message.yml` 的 `presentation.player` 配置：`prefix`、`game-label`、`inactive-name-color`、`identity`、`chat`、`daily-chat`。`%player%` 带现有游戏资格决定的队色，`%player_name%` 是不带颜色的姓名；`%label%`、`%prefix%`、`%identity%` 和 `%message%` 按各模板的用途组合。普通参数只替换一次，消息的翻译组件、显式样式、点击及悬停事件不能经字符串转换丢失。名称颜色配置不能改变参赛资格、队伍身份或旁观显隐规则。Core 以不可变 `PlayerTextFormat` 一次替换整组配置；显式空值保留，删除覆盖恢复当前内置默认，类型错误拒绝本次格式更新。

Core 将完整玩家样式冻结为 manifest 的 `presentation.player.*` 字段，Worker 在 PREPARE 校验并沿用快照，包括 DAILY 旁观者的 `presentation.daily-game` 标签。新对局不得使用 Worker 独立样式；旧快照完全没有该前缀时，才读取 common 的历史兼容资源，不补写旧 manifest 或改变哈希。部分字段或未知版本须报错。Worker 尚未接收任何对局时只可使用兼容默认；收到对局后沿用最近的 Core 展示配置。跨服聊天由发送端冻结整行 Adventure JSON 为可选 `renderedJson`，接收端保留该行；旧元数据和正文仍随消息传输，旧消息没有整行字段时按原样式渲染，未知/损坏 JSON 不静默换成另一种内容。旧接收端忽略新增字段；自定义样式须两服均更新后验收。

Bingo 的语言文件首先继承同语种内置资源，再进行语言回退。Core 冻结到 manifest 的共享展示由 Worker 使用，不能在 Worker 重新维护另一份文案。GUI 与 Bingo 普通参数通过 common 的 `MessageTemplate` 单次替换，保留颜色以及参数内的占位符、美元符和反斜杠。Worker 的 `%prefix%` 是既有的显式前缀装饰步骤，允许用于嵌套消息；不得把这种特例扩大为任意参数的递归求值。

Bingo 任务名称的颜色、括号、星号、统计分隔符、集合前缀布局及成员说明行由 `bingo/lang/<locale>.yml` 的 `task.presentation.*` 拥有；成就聊天说明另用 `advancement-description`。`MessageService.rich` 通过平台 `ComponentTemplates` 单次插入富文本，保留原版物品/实体/药水/成就/统计翻译节点及参数自身样式。模板可以重排参数或显式为空；未知参数和参数内容中的 `%...%` 不再展开。任务定义中的材料、事件、统计标识与计数属于玩法数据；未知自定义标识的可读回退不是另一份固定语言文案。

Core 使用平台 `FrozenBingoTaskText` 一次生成完整名称及 lore，再发布到任务 attributes；新快照带 `display.text-version=1`，继续保留既有 `display.name` / `display.lore-size` / `display.lore.N` 字段，旧接收端可读取。禁止为通过无服务器测试而吞掉注册表、语言或序列化异常；无原生注册表的测试只验证不依赖它的任务，材料/成就及完整池在隔离 Paper/Folia 验证。Worker 在 PREPARE 校验所有带标记任务，未知版本、缺失字段、损坏 JSON 或非法行数应拒绝，不能显示部分文本或回退成英文标识。重排/差分卡片只能复用这些已验证任务，不能重建文本。Core 语言重载只影响之后生成的快照。

历史任务完全没有 `display.text-version` 时，保留原有可选名称/说明的读取与跳过规则；Worker 的独立旧名称格式仅存于不可变 `presentation/legacy-task-names.properties`，不能成为新对局配置入口。读取不得修改旧 manifest 或哈希。兼容说明按已有行索引排序，避免巨大但稀疏的旧行数造成无界循环。验证需覆盖实际完整任务池、七类任务、中英文、翻译节点和有效样式、自定义/空模板、完整二进制传输与哈希，以及损坏新快照的 PREPARE 拒绝。语言 schema 迁移先暂存，构建和原生验证成功后与 Core/Worker JAR 一起发布；Worker 没有独立运行语言文件，两服均需重启。

Bingo 卡片的逻辑尺寸只由 `CardSize` 保存；背包布局由 `games.bingo.menus.card.screens.x3/x4/x5` 分别配置尺寸和全部任务槽位，不再使用卡片根节点下旧的未消费 `layout.content`。每个列表必须正好覆盖 9/16/25 个任务，槽位唯一、不越界，不能占用信息卡。布局顺序只改变呈现位置，不改变任务编号、连线、完成状态或变体的逻辑顺序。信息卡默认继承父级 `items.info.slot`，单个窗口可用 `layout.footer.info` 覆盖；校验与渲染采用相同优先级。

Core 与 Worker 卡片共用平台 `BingoCardMenu` / `InventoryItems`，不各自维护一套固定网格或信息物品。Core 通过 `BingoCardMenuConfig` 读取有效 GUI，并将完整 `BingoCardPresentation` 作为 `gui.card.*` 字段冻结到 manifest 的 presentation；Worker 只使用这份快照，Core 后续重载不会改变进行中的远端对局。模板快照通过 `GuiConfig.itemTemplate` 复用本地/共享按钮与状态继承，不依赖原生物品注册表；真正的材质有效性在 Core 配置加载和 Worker PREPARE 的接收端注册表上核对。未知版本、部分字段、缺格、无效材质等必须拒绝，不能静默恢复成默认样式。

卡片窗口 `%title%` 使用当前队伍或观众的语言标题，信息卡可用 `%card-title%` 和 `%win-hint%` 引用 Bingo 语言；无参数的现有 GUI 标题保持原值。完整多行 lore 与显式空列表均保留，参数只插值一次。共享只读持有者取消两端顶部、底部、Shift 点击和拖拽；已离线或被重连替换的 Player 不得重新打开旧卡片。卡片内容与完成情况仍归各端游戏模型，不由展示快照决定。

Bingo 任务物品由 common `BingoTaskPresentation` 判定正常、隐藏、锁定、本队完成、被其他队伍占领五种显示状态，并由平台 `BingoTaskItems` 渲染；Core 和 Worker 只适配任务与已接受的完成事实，不重复实现状态装饰。未有任何队伍完成时，隐藏/锁定任务的名称、说明、物品数量及地图图标都不得泄露任务内容。完成后按本队、其他队伍和中立视角处理；Domination 的其他队伍显示占领状态，中立汇总仍显示原任务。地图沿用原有任务图像、队色边框和连线机制，仅复用隐藏判断与遮罩材质，不使用 GUI 的完成障碍物覆盖地图任务。

`games.bingo.menus.card.items.task` 定义正常物品，`states.hidden/locked/completed/occupied` 定义状态的完整材质、标题、lore 与 glint；`TASK` 仅在非遮罩状态表示原任务图标。正常状态 glint 是原任务高亮的开关，已有任一完成时不再额外高亮；完成/占领状态按配置独立高亮。`task-presentation` 保存备用材质、完成名单分隔符、玩家身份模板、完成汇总完整行模板与未知/短/长时钟模板。`%description%` 和 `%completion-summary%` 是显式完整行占位，未配置的行不得自动追加；后者仅在有完成记录时展开 `task-presentation.completion-summary` 的完整行（默认空行与完成者名单）。显式空 lore 保持为空。隐藏/锁定的标题和 lore 不允许任务数据占位，且不得使用 `TASK` 材质；候选 GUI 在激活前和 Worker PREPARE 原生注册表均须校验。v58 只增量补充这些配置，不覆盖既有卡片标题、信息卡、网格或运行自定义值。

任务样式冻结为独立 `gui.card-task.*` schema；只有整个前缀缺失时读取 common 的历史样式资源，部分字段或未知版本须拒绝。旧 manifest 的哈希不修改；隐藏泄露、占领状态和完成信息属于共享行为修复，不以兼容为由保留错误显示。原版可翻译任务、描述、原生成就图标组件及药水颜色必须保留，不能把富文本先转换成 legacy 字符串再拼回去。`ComponentTemplates` 只替换一次，普通参数保留自身队色；任务状态标题可明确覆盖整个名称树的颜色和删除线。完成记录在接受时保存实际完成者 UUID、当时姓名/队伍和游戏时间；合作模式给其他队伍计入同一完成者，不能改成被计分队伍的身份。Worker 的地图名单从同一份记录派生，不另建一份可漂移的展示完成状态；玩家退出或名单变化不应丢失历史完成信息。完成时钟通过 `DurationText.clock` 统一算法并固定数字区域设置，时间样式来自配置。共享变更需对两端、两种语言、三种卡片尺寸、正常/占领/合作/中立状态以及原生物品数据和地图遮罩分别验证，明确隔离验证的边界。

仅当旧 manifest 完全没有 `gui.card.*` 字段时，读取 common 的 `presentation/legacy-card-menu.properties` 恢复其原 Worker 网格、语言标题和信息卡；不能把历史兼容资源当成新对局的配置来源。只读回退不补写旧快照，不改变其 hash；新字段继续通过既有二进制 codec 与 hash 规则传输，不能为了展示修改历史对局数据。共享卡片修改需同时构建、部署 Core 与 Worker，并在各自支持的平台验证原生背包，区分隔离物品/背包验证与真实玩家或生产验收。

BossBar 时间统一使用 common 的 `DurationText` 算法，保留大于 60 秒才切换分钟及秒余数的规则。Core 通过 `CoreMessages.bossBarTime` 读取 `message.yml` 的 `code-text.duration.seconds` / `minutes-seconds`；普通游戏、正式赛与大厅沿用该入口。Bingo 投票保留所选语言的 `vote.bossbar-time` 模板。新远端对局将 Core 的两个模板冻结到 presentation，Worker 不读取本地可变语言配置。旧持久化 manifest 同时缺少两键时，仅使用 common 的 `presentation/legacy-bossbar.properties` 历史兼容模板，不补写原快照、不改变其 hash；仅缺一键属于不完整的新快照，应报错。该历史资源不能当作新玩法的默认文案入口。

AuthBridge 与 AuthProxy 的准入、在线踢出封禁文案复用 common 的 `BanPresentation`，由各自 `config.yml` 的完整消息模板及 `ban-presentation` 配置提供默认原因、未知到期时间、日期格式和时区。`expiry-format: raw` 保留 Bridge 原有时间值，Proxy 默认沿用既有日期样式；`expiry-zone: system` 表示服务器时区，也可明确指定 ZoneId。玩家原因和时间只插值一次。旧配置缺键继承插件内置资源，显式空文案保留；升级只增量补键，不能覆盖自定义消息或凭据。Bridge 旧 `access.uuid-maintenance-message` 仅在用户未显式配置新键时继续生效。

中文界面使用短标题，并清楚说明当前状态、按键和操作结果。玩家界面沿用“观众/观战”“比赛分区/场地”“小队”“游戏”，不暴露 `PrepareSpot`、`copy0`、revision 或后台术语；只有管理员确实需要操作和排障时才显示内部标识及文件路径。新增或改名配置键要明确迁移，不删除管理员自定义颜色、占位符和拼接边界，不整文件覆盖运行配置。

GUI 修改须验证默认资源的引用与语义键、中文术语、完整多行 lore、状态继承及实际菜单动作；语言修改须验证键与参数集合、空值、缺键和重载。共享展示同时检查 Core/Worker 的默认资源、manifest、运行配置和测试。只扫描 Core 中的中文不足以证明无硬编码，还要检查其他模块、英文文案、固定显示值及未读取的配置字段。

## 清理与兼容性

配置审查区分可运营内容与固定契约。语言模板、GUI 布局和文字实体样式由各自资源拥有；Minecraft 调色板编号、原生字体语法、材质/注册表标识、坐标计算、权限节点、身份枚举和协议版本保持代码或权威数据定义。Bingo 地图数量的阴影/正文绘制只由平台 `TaskCellLayout.drawAmount` 实现，Core/Worker 不再复制像素偏移和 `§<palette>;` 协议。聊天 `&f` 是已有授权颜色解析的初始状态；普通玩家原组件不经此路径，不能为文案清理放宽权限。动态队伍颜色读取队伍数据，默认调色板与无法解析时的原生白色属于身份映射。日志、异常及压测状态字段是运维诊断；压测命令的固定反馈归其 config.yml messages，部署仍保留 disabled 后缀和 auto-start=false。

Bingo Worker 的计时条冻结 Core 已有完整 PvP/保护模板，分别使用 `%time%` 和 `%grace%`；新模板必须成对存在。旧 manifest 只在两键都缺失时使用只读 legacy-worker-text.properties 的历史分隔符，侧栏历史排名模板也归此兼容资源，不能改写持久化快照或哈希。Core 同伴传送的既有本地反馈与 Worker 冻结的 Bingo 语言入口分别保留自定义值；Core 无队友样式由 code-text.bingo-compass-listener.no-teammates 包装原消息组件，显式颜色及字面 token 不重新展开。纯展示整理不改变传送资格、线程、会话或目标验证。

删除兼容分支前检查调用方、默认资源和实际运行配置。需要迁移的配置应明确更新版本与部署文件，保留自定义值和发布状态；不要保留无人使用的转发类、旧方法别名或旧配置猜测。数据库 schema 的历史迁移仍负责升级已有数据，不能因为当前安装已迁移就删除。

抽取方法时让算法和 IO 各有入口；只开放跨包调用需要的类型和成员，其他细节保持 private 或包内可见。移动源码后同步 package、import、反射类名、源码检查路径、工具和文档。不要为了测试把运行时内部状态全部公开。

## 格式、测试和交付

Java 使用 UTF-8、LF、四空格缩进，以 Google Java Format 1.33.0 的 AOSP 样式为准。仓库根目录执行：

```bash
scripts/format-java.sh
scripts/format-java.sh --check
python3 scripts/check-source-layout.py
mvn -B -ntp -pl championships-core,championships-bingo-worker -am clean package
```

Java 格式脚本只处理 Maven 模块的生产和测试源码；首次执行会下载固定版本的格式工具到系统临时目录。维护脚本及用法见 [脚本指南](../scripts/README.md)。Python 脚本使用 Python 3.11 或更高版本和标准库；格式检查使用 Ruff 0.11.13，执行 `ruff format --check scripts`，配置由根目录 `pyproject.toml` 管理。生产源码放 `src/main/java`，JUnit 和测试夹具放 `src/test/java`。测试与被测类同包；多个包共用的夹具放测试源码的 support 包。

测试按独立行为和失败路径组织，公共算法由所属模块验证，调用模块覆盖接入、角色与生命周期。用例设计、昂贵规划的复用和隔离规则见 [测试指南](testing.md)。

按改动范围选择 reactor 模块并带上 `-am`。共享行为须同时检查 Core 与 Worker 的实现、资源、运行配置和测试。包移动需要 clean build，以免旧包的 class 残留在交付 JAR。涉及原生发包、反射或 Folia 线程的行为另按对应契约做原生验证。

成功打包后直接替换受影响的运行 JAR，不创建 JAR 备份。重启对应服务器才能加载新类；仅替换文件不会切换当前已加载代码。仅修改文档不打包、部署或重启。

配置结构版本与对应 JAR 必须同批交付。先在 `/tmp` 准备增量迁移并完成构建和验证，再发布运行配置与 JAR，不能在长时间构建期间提前提高运行文件的版本号。启动失败先核对日志中的已加载版本和启动时间，不能靠降低版本号绕过校验。配置初始化失败会同时阻止菜单、占位符及依赖插件注册；隔离验证须使用实际部署包读取运行配置副本，明确区分配置/占位符验证与完整生产启动验收。

`scripts/` 只保存持续维护的开发检查和只读审查脚本，`docs/` 保存玩法与验证指南。一次性导入、旧资源迁移、地图预置制作、离线模拟及其报告、预览和缓存放在 `/tmp`；任务完成后清理，不重新建立历史工具目录。插件运行只依赖各模块的生产代码和资源，不依赖这些开发脚本。

## 文档维护

- 使用指南说明当前行为、适用范围、操作步骤和配置含义；开发契约说明职责与不变量；验证指南说明输入、方法、期望结果和证据范围。
- 直接描述最终状态。具体问题、前后对比、提交记录和单次验收结果写入 PR 或临时报告；长期文档不保留更新流水账。
- 同一规则选择一处完整说明，其他文档通过链接引用。版本、命令与数值核对实现和默认资源，并明确区分默认值、可配置值与固定约束。
- 仍受支持的兼容格式和迁移步骤属于当前行为，保留其触发条件、边界与操作要求；删除说明前确认代码及实际配置已不依赖它。
- 使用仓库相对路径或服务端标准目录。示例服务名、数据库凭据、地图和玩家名称明确为示例；不提交个人工作目录或私有服务地址。
- 移动、合并或删除文档时同步索引、模块 README 和章节链接，检查本地目标与标题锚点。文档检查无需运行 Maven 或替换 JAR。
