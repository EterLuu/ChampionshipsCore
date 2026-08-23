# ChampionshipsCore

ChampionshipsCore 是 Summer/Winter Collab Championship 使用的 Minecraft 综合赛事核心插件。它负责队伍与选手管理、比赛场地、游戏生命周期、积分排行、自动赛程、投票、观战、聊天分组和 PlaceholderAPI 变量。

| 正式名称 | 命令标识 | 配置名称 |
| --- | --- | --- |
| 火热宾果（Bingo But Hot） | `bingo` | `Bingo` |
| 匹配赛建（Build Match） | `buildmart` | `BuildMart` |
| 斗战方框（Battle Box） | `battlebox` | `BattleBox` |
| 跑酷追击（Parkour Tag） | `parkourtag` | `ParkourTag` |
| 跑路战士（Runaway Warrior） | `parkourwarrior` | `ParkourWarrior` |
| 空岛乱斗（Sky Brawl） | `skywars` | `SkyWars` |
| 去到另一边（Try Get To The Other Side） | `tgttos` | `TGTTOS` |
| TNT飞跃（TNT Spleef） | `tntrun` | `TNTRun` |
| 雪球乱斗（Snowball Showdown） | `snowball` | `SnowballShowdown` |
| 龙蛋狂欢（Dragon Egg Carnival） | `dragoneggcarnival` | `DragonEggCarnival` |
| 烫手鳕鱼（Hoty Cody Dusky） | `hotycodydusky` | `HotyCodyDusky` |
| 躲避箭（Dodgebolt） | `dodgebolt` | `Dodgebolt` |
| 王牌竞速（Ace Race） | `acerace` | `AceRace` |

## 游戏介绍

### 火热宾果（Bingo But Hot）

所有队伍会被分散到同一套主世界、下界和末地，在默认 10 分钟内完成 5×5 任务卡。任务包括获得物品或药水、达成统计目标和完成进度。玩家可右键副手地图，在地图和菜单两种卡片样式间切换。

- 同一格可由不同队伍分别完成，不会因首支队伍完成而锁定。
- 单格按完成先后默认依次获得 60、50、40、30、20、10 分；第六名以后仍获得 10 分。单格分数归完成任务的玩家。
- 连成横、竖或对角线时，前 4 条线为全队每人 50 分，之后每条线为全队每人 25 分。
- 按总分排名；同分时，较早达到该分数的队伍优先。
- 开局会发放队伍色护甲、鞘翅、食物、工具、武器和指南针；左键指南针可以传送到在线队友身边。
- 默认拥有夜视、跳跃提升，初始皮靴附有摔落缓冲 IV，死亡不掉落物品。前三分钟关闭 PvP，此后仍禁止攻击队友。

### 匹配赛建（Build Match）

各队从公共资源大厅收集方块，在自己的三个普通建造位和一个金色建造位复刻参考建筑。默认比赛 12 分钟、准备 10 秒，金色蓝图每 120 秒刷新。

- 三个普通建造位开局自动获得不同的随机蓝图；完成后清空，并在 5 秒后自动补充新蓝图。
- 点击建造位旁的提交按钮后逐方块检查，只有与蓝图完全一致才算完成；得分为蓝图星级乘以当前阶段的每星分值，比赛前、中、后三段默认分别为 10、15、20 分。最后 10 秒停止接受提交。
- 金色蓝图需要在 5 秒内连续点击两次确认提交；未完成时提交或蓝图轮换都会清空当前金色建造，材料不会返还。
- 时间结束时，未完成建筑按完成比例结算。
- 总星数、完成建筑数和平均星级三个奖项分别给前三名队伍的每名成员加 25、15、5 分。

### 斗战方框（Battle Box）

两队在默认 60 秒内争夺中央 3×3 区域。场地可配置装备套组和瞬间伤害药水点；任一队先用本队方块填满全部 9 格便立即获胜，否则倒计时结束后比较双方占据格数。

- 击杀一名对手获得 15 分。
- 获胜队每名成员获得 40 分；平局时双方每名成员获得 15 分。

### 跑酷追击（Parkour Tag）

每场对局由两支队伍参加，并在左右两个镜像区域同时进行：每队选择一名追逐者，追捕对方的逃生者。默认每小局 60 秒，追逐者碰触逃生者即完成抓捕。

- 未被全员抓获时，每名存活逃生者获得 20 分。
- 每名逃生者按存活时长，每满 10 秒获得 2 分。
- 追逐者每抓到一名逃生者获得 6 分。
- 追逐者抓完全部对手后按剩余时间获得递减分数。
- 存活总时长较长的一方获胜，每名成员另得 30 分。
- 逃生者可用末影之眼令对方追逐者发光 3 秒，并可全队共享一次风弹，使追逐者浮空 1.5 秒；追逐者可用羽毛获得 6 秒速度 II。

### 跑路战士（Runaway Warrior）

玩家在默认 10 分钟内依次挑战主检查点、子检查点和终点路线，最后 30 秒进入突然死亡阶段。系统记录每名玩家的重生点与检查点进度，并按完成的 2 至 5 星关卡数量逐级计分；连续完成较高星级关卡时，单次收益会递增。

完成最终路线还会给全队施加倍率奖励。内置简单、普通、困难路线倍率分别为 0.15、0.35、0.8，具体路线与检查点由场地配置决定。

### 空岛乱斗（Sky Brawl）

各队从玻璃笼和队伍出生岛进入空岛生存战。默认时长 16 分钟；开局 2 分钟后，每支队伍的出生岛会出现可骑乘的乐魂，开局 5 分钟后安全区域开始收缩，最后阶段关闭生命恢复。

- 击杀一名对手获得 40 分。
- 每有一名玩家被淘汰，所有仍存活玩家各得 10 分；整支队伍被淘汰时，所有存活玩家再各得 2 分。
- 比赛结束时仍存活的玩家各得 50 分。
- 玩家死亡后，携带物品会保存在死亡位置的箱子中。

### 去到另一边（Try Get To The Other Side）

玩家在默认 90 秒内穿越障碍并击打终点鸡完成地图。地图的 `area-type` 可配置为 `BOAT`（橡木船）、`ROAD`（不可破坏钻石镐和队伍色混凝土）、`NONE`（无物品）或 `ELYTRA`（胸甲槽装备不可破坏鞘翅）；地图可选配置流浪者生成点，并分别用一格高的 WorldEdit 平面设置鸡和玩家的随机生成区域，玩家区域同时记录统一朝向。

个人到达越早得分越高，前 10 名另有到达奖励；全队完成也会触发团队奖励。正式调度会依次比赛全部已加载场地，每张地图进行一轮。

### TNT飞跃（TNT Spleef）

玩家在多层方块场地生存，脚下方块会在 8 tick 后消失。默认每轮 180 秒；剩余 120、60、20 秒时分别触发约 10 秒的 TNT 雨，玩家可使用鞘翅挽救坠落。

- 每淘汰一名玩家，所有仍存活玩家各得 2 分。
- 轮末按淘汰顺序结算生存名次，前三档分别为 100、70、30 分。
- 正式调度默认进行 3 轮，并将选手分配到预先生成的赛道副本。

### 雪球乱斗（Snowball Showdown）

玩家以队伍颜色装备进入竞技场，使用雪球和铁剑混战。被淘汰后会在随机点复活；击杀者获得 4 分并补充 6 个雪球。默认一轮 300 秒，队伍率先达到 100 次击杀时可提前结束。

轮末按队伍击杀数排名，队内每名成员获得对应名次奖励：第一名从 60 分开始，之后每个不同名次递减 5 分，最低为 10 分。正式调度默认进行 3 轮。

### 烫手鳕鱼（Hoty Cody Dusky）

每轮随机选出一名鳕鱼持有者；持有者每 3 秒受到 2 点伤害，攻击其他玩家可转移鳕鱼，转移后有 1.5 秒保护期。默认一轮 240 秒，初始持有者获得 10 分。

玩家淘汰后，仍存活者会按此前淘汰人数各得 15 分；最终生存名次另有 25、20、15 分奖励。正式调度会把各队成员打散到 4 个场地，共进行 3 轮。

### 龙蛋狂欢（Dragon Egg Carnival）

这是两队同时进行的一场完整末影龙战。双方分别从 `(100, 49, 0)` 与其镜像平台出发，使用固定钻石装备、无限队伍混凝土和生存物资推进。

队伍率先完成【解放末地】【下一世代】【远程折跃】任意两项即可获胜。摧毁末影水晶会为全队补充末影珍珠并随机提升锋利、保护或力量；每累计造成 20% 末影龙生命值的伤害，会为对手施加 8 秒负面效果。

### 躲避箭（Dodgebolt）

总积分前两名进行五局三胜决赛。箭命中玩家即淘汰，将对方全队淘汰即可赢下一局；第一局由高顺位队伍获得两箭，后续小局双方各获得一箭。

- 玩家不能越过中央分界，箭在每次射击结束后会消失并刷新到对方半场。
- 淘汰与累计射箭会推动平台逐层收缩；管理员可使用 `/cc finale dodgebolt` 下的裁判命令处理暂停、重开、淘汰和强制胜利。
- 躲避箭是非积分决赛，不参与普通游戏投票。

### 王牌竞速（Ace Race）

所有选手在限时内按顺序通过保存点并完成配置圈数。只有正向穿过终点线才会结算一圈；跌落到赛段高度以下时会返回最近保存点。

- 与 TGTTOS 相同，所有 Ace Race 地图可放在同一个 `acerace` 世界中；每张地图通过独立赛道边界隔离。
- 保存点可以切换鞘翅、激流三叉戟或无装备，并分别配置赛段跌落高度。
- 黄色/黄绿色带釉陶瓦提供速度或跳跃效果，红色/橙色羊毛提供不同强度的定向弹射。
- 完赛基础分从 500 分起，每后一名减少 10 分且最低为 80 分；前 19 名另有分段名次奖励，未完赛不得分。

## 运行要求

- Java 25
- Paper/Spigot 26.2 API 对应的服务端
- MariaDB 或 MySQL
- ProtocolLib 5.4.0
- PlaceholderAPI 2.12.2
- FastAsyncWorldEdit 2.15.0

ProtocolLib、PlaceholderAPI 或 FastAsyncWorldEdit 缺失时，ChampionshipsCore 会在启动阶段自行禁用。

## 构建与安装

```bash
mvn clean package
```

构建产物位于 `target/`。将插件本体和三个必需依赖放入服务端的 `plugins/` 目录，然后启动一次服务端生成配置。

仓库已使用 Maven Reactor 管理共享协议、纯 Java Bingo 计分引擎、Paper/Folia 平台层、Redis transport、独立 Bingo Worker、一次性压测插件和 `championships-core` 核心插件。根目录执行同一条构建命令会生成历史兼容路径 `target/ChampionshipsCore-1.3-SNAPSHOT.jar`、`championships-bingo-worker/target/championships-bingo-worker-1.3-SNAPSHOT.jar` 和 `championships-bingo-loadtest/target/championships-bingo-loadtest-1.3-SNAPSHOT.jar`。

远程 Bingo 可通过 `bingo.execution-mode: REMOTE` 把世界与玩法执行迁移到独立 Folia Worker；`LOCAL` 模式仍保留为单服执行方案。部署前请阅读 [Worker README](championships-bingo-worker/README.md)、[跨服架构与上线流程](docs/bingo-remote-architecture.md) 和 [64 人性能指南](docs/bingo-64-player-performance-report.md)。性能指南中的逻辑玩家压测只覆盖区块与实体负载，不代替真实客户端的端到端验收。压测插件只能临时安装在可丢弃的测试世界，不得留在生产服务器。

Bingo 任务机制与图集当前同步至 [MineBingo](https://gitee.com/chancelethay/minebingo) 提交 `dd84456fdf7784deca11e37618cb5af8708d21e9`（2026-08-19）。模式/难度投票与奇遇仅接入 ChampionshipsCore 的 DAILY 自由游玩，继续使用 CC 原有大厅、Party、匹配和临时队伍；正式赛与管理员手动局仍使用场地原有固定积分规则。

推荐的首次部署顺序：

1. 创建 MariaDB/MySQL 数据库和专用账号。
2. 首次启动服务端，让插件生成 `plugins/ChampionshipsCore/`。
3. 修改 `config.yml` 中的数据库连接、队伍人数、出生点等选项。
4. 重启服务端，确认控制台没有数据库或依赖加载错误。
5. 创建队伍并添加队员。
6. 创建并配置游戏场地。
7. 先用手动命令进行测试赛，再启用正式调度器。

数据库表会在连接成功后由插件自动创建。

## 基础配置

主要配置位于 `plugins/ChampionshipsCore/config.yml`：

| 配置项 | 说明 |
| --- | --- |
| `mode` | 插件运行模式，默认 `CHAMPIONSHIP` |
| `max-players` | 赛事允许的最大玩家数 |
| `whitelist` | 赛事白名单 |
| `weighted-score` | 是否启用加权积分 |
| `strict-spectator-rule` | 是否阻止参赛队员自由观战 |
| `enabled-games` | 本次赛事启用的游戏；未启用的游戏不会加载，也不会出现在相关命令中 |
| `database.type` | `MARIADB` 使用 MariaDB 驱动，其他值使用 MySQL 驱动 |
| `database.address` / `port` | 数据库地址和端口 |
| `database.name` | 数据库名称 |
| `database.username` / `password` | 数据库凭据 |
| `team.max-members` | 每支队伍最多成员数，默认 4 |
| `lobby.location` | 大厅出生点，也是比赛结束和地图保存时的返回点 |
| `parkourtag.max-chaser-times` | 单名队员最多担任追逐者的次数 |

修改配置后执行：

```text
/cc admin reload
```

`enabled-games` 使用文首“配置名称”一列中的名称，不区分大小写；设为 `[]` 会关闭所有游戏。

场地配置和游戏专属配置分别位于对应游戏目录。火热宾果还使用 `bingo/config.yml`、`cards/`、`tags/`、`tierlists/` 和语言文件；匹配赛建使用 `buildmart/blueprints/` 中的蓝图。

Bingo 的场地文件还可通过 `permanent-effects` 调整常驻效果，格式为 `效果:等级`，例如 `night_vision:1`、`jump_boost:8`。

空岛乱斗场地通过 `variant` 选择规则方案。保留默认值 `inline` 时，计时、边界、计分和介绍文本均读取场地 YAML；填写其他名称时，插件会读取 `skywars/variants/<名称>.yml`。首次启动会生成可复制修改的 `default.yml`。

## 权限

插件只在 `/cc` 后的第一级命令上检查权限，子命令不会继续拼接权限节点。管理员权限会自动继承玩家命令。

| 权限 | 可用功能 |
| --- | --- |
| `cc.player` | `/cc spawn`、`vote`、`spectate`、`rank` 等玩家功能 |
| `cc.admin` | 队伍、单局、正式赛事、地图、世界和裁判管理；同时可用玩家功能 |
| `cc.refuge` | 严格观战规则开启时，允许参赛队员以裁判/替补身份观战 |

建议使用权限插件分组：普通选手授予 `cc.player`，赛事管理员授予 `cc.admin`；裁判或替补按需增加 `cc.refuge`。

## 命令约定

- `<参数>` 表示必填参数，`[参数]` 表示可选参数。
- `<队伍>` 使用创建队伍时的内部名称，不是彩色显示名。
- 场地设置、WorldEdit 选区和当前位置相关命令必须由游戏内玩家执行。
- 多数名称匹配区分大小写，推荐队伍名、场地名和蓝图名统一使用小写英文、数字和下划线。
- 输入到中间命令节点时，插件会显示当前节点下的帮助列表，例如 `/cc game start` 或 `/cc event`。

## 玩家与通用命令

| 命令 | 说明 |
| --- | --- |
| `/cc spawn` | 传送回 `lobby.location` |
| `/cc vote [配置名称]` | 打开投票菜单，或在投票开放期间直接投票 |
| `/cc spectate <游戏> <场地> [实例]` | 观战指定游戏；一级补全包含所有已启用游戏（含 Bingo），TNTRun/雪球大战可在菜单继续选择具体子场地 |
| `/cc spectate leave` | 退出观战并返回大厅 |
| `/cc rank playerboard` | 查看个人积分榜 |
| `/cc rank teamboard` | 查看队伍积分榜 |
| `/cc rank info` | 查看各游戏的积分权重 |
| `/cc rank recap` | 重看最近一次游戏结算和总榜 |

`/cc vote <配置名称>` 使用文首“配置名称”一列中的值，匹配不区分大小写；`DragonEggCarnival` 与 `Dodgebolt` 不参与投票。

观战命令使用启用游戏的配置名称，Tab 补全会自动隐藏 `enabled-games` 中未启用的游戏。

## 队伍与成员管理

游戏内拥有 `cc.admin` 权限的管理员可直接输入 `/cc team` 打开原生队伍管理界面。界面支持队伍总览、创建与删除、成员在线状态、在线或历史离线成员添加、成员移除，以及将单支/全部队伍传送到管理员当前位置；总览的“快速调队”可先选在线玩家再选目标队伍，已有队伍时经二次确认并以数据库事务原子迁移。原有子指令仍完整保留，供控制台和自动化使用。

### 创建队伍

命令格式：

```text
/cc team add <内部队伍名> <颜色名> <聊天颜色代码>
```

例如：

```text
/cc team add red_rabbits red &c
/cc team add aqua_axolotls light_blue &b
```

第二个参数必须是以下 Minecraft 颜色之一：

```text
white orange magenta light_blue yellow lime pink gray
light_gray cyan purple blue brown green red black
```

第三个参数用于生成队伍彩色名称和消息，可以使用 `&` 颜色代码。队伍内部名称和颜色名都应保持唯一，否则可能与主计分板队伍冲突。

数据库数字 ID 会自动生成，不需要手工输入。

### 添加队员

```text
/cc team member add <队伍名> <玩家名>
/cc team member delete <队伍名> <玩家名>
```

示例：

```text
/cc team member add red_rabbits Steve
/cc team member add red_rabbits Alex
```

每名玩家只能属于一支队伍，人数不能超过 `team.max-members`。添加成员时会使用该玩家的离线 UUID，因此应保证服务端正版/离线模式在整个赛事周期内保持一致。

### 查询、传送和删除

| 命令 | 说明 |
| --- | --- |
| `/cc team info <队伍名>` | 查看队伍成员 |
| `/cc team tphere <队伍名>` | 将指定队伍传送到命令执行者的位置 |
| `/cc team tphere all` | 将全部队伍传送到命令执行者的位置 |
| `/cc team delete <队伍名>` | 删除队伍及成员关系 |

正在参加游戏的队伍不能删除。正式建队后建议依次执行 `team info`，并让所有成员上线确认队伍颜色、聊天频道和计分板状态。

## 完整管理命令

### 比赛启动

| 类型 | 命令 |
| --- | --- |
| 两队对战 | `/cc game start battlebox <场地> <队伍1> <队伍2>` |
| 两队对战 | `/cc game start parkourtag <场地> <队伍1> <队伍2>` |
| 决赛直接开场 | `/cc finale dragoneggcarnival start-direct <场地> <队伍1> <队伍2>` |
| 决赛直接开场 | `/cc finale dodgebolt start-direct <场地> <队伍1> <队伍2> [--force]` |
| 所有队伍 | `/cc game start bingo all <场地>` |
| 所有队伍 | `/cc game start buildmart all <场地>` |
| 所有队伍 | `/cc game start skywars all <场地>` |
| 所有队伍 | `/cc game start tgttos all <场地>` |
| 所有队伍 | `/cc game start tntrun all <场地>` |
| 所有队伍 | `/cc game start snowball all <场地>` |
| 所有队伍 | `/cc game start parkourwarrior all <场地>` |
| 所有队伍 | `/cc game start acerace all <场地>` |
| 指定多队 | `/cc game start hotycodydusky <场地> <队伍...>` |

`/cc game start` 只启动一次测试局，不创建正式赛事轮次，并且只处理命令明确指定的队伍
（或 `all` 所代表的全部队伍）；它不会播报赛事规则、自动吸纳无队伍玩家旁观，也不会改动
其他玩家的状态。常规正式赛的规则介绍、自动旁观调度和跨轮次观众承接由 `/cc event start` 管理；冠军决赛统一由 `/cc finale` 管理。

### 管理员命令

| 命令 | 说明 |
| --- | --- |
| `/cc admin reload` | 重载插件配置和管理器 |
| `/cc admin set-max-player <数量>` | 修改最大玩家数 |
| `/cc admin sudo <队伍名> <命令...>` | 让一支队伍的在线成员执行命令 |
| `/cc admin sudo all <命令...>` | 让所有在线参赛者执行命令 |
| `/cc admin teleport gameplayers` | 将正在比赛的玩家传送到管理员位置 |
| `/cc admin teleport spectators` | 将观众传送到管理员位置 |
| `/cc admin vote start` | 开始 120 秒投票 |
| `/cc admin vote end` | 提前结束投票并公布结果 |
| `/cc admin world list` | 查看已加载及磁盘上尚未加载的世界 |
| `/cc admin world create <世界> [normal\|nether\|the_end]` | 创建世界，或加载已有世界 |
| `/cc admin world rename <旧世界> <新世界> [normal\|nether\|the_end]` | 重命名世界；地图世界会同步更新配置与模板 |
| `/cc admin world delete <世界> confirm` | 永久删除未被地图配置引用的世界 |
| `/cc admin world teleport <世界>` | 传送到世界出生点并开启飞行 |
| `/cc admin world unload <世界>` | 保存并卸载世界，不删除世界文件 |

主大厅世界和 Bingo 三维度不能删除或重命名。删除必须显式附加 `confirm`，且不会破坏仍被 ChampionshipsCore 地图配置引用的世界。重命名未加载世界时需给出其原环境；世界名只允许字母、数字、下划线和连字符；`create` 未指定环境时使用 `normal`。普通小游戏世界使用虚空生成器，Bingo 的 `bingo`、`bingo_nether` 和 `bingo_the_end` 三个世界则使用原版地形。

`world delete` 的世界名 Tab 补全会展示所有已加载或已存储世界，包含受保护世界；实际执行删除时仍会拒绝主大厅、Bingo 三维度以及被地图配置引用的世界。

## 地图编辑与发布

地图编辑统一从游戏内向导进入：

```text
/cc map edit <游戏>
/cc map rename <游戏> <旧场地名> <新场地名>
```

`map edit` 会打开该游戏的地图列表。“新建地图”只建立尚未绑定世界的草稿，不会创建世界。之后可用 `/cc admin world create` 创建或加载世界，站在目标世界中通过向导的“绑定当前世界”步骤进行绑定；重复执行该步骤可更换绑定。已有地图可左键编辑，右键两次删除地图配置；删除地图不会删除物理世界，世界删除仍只由 `/cc admin world delete` 负责。

`map rename` 只接受空闲且未被 prepare 锁定的地图。它会卸载对应运行实例，同时修改配置文件名、配置内 `name` 和运行时登记名，并迁移正式积分及日常赛数据库记录，然后用新名称重新加载；任一步失败都会回滚数据库、配置文件和原登记。

进入编辑会话后，插件会暂存玩家物品栏，并通过热键栏和步骤菜单引导完成世界确认、schematic、复制布局、出生点、范围、检查点、物品列表等游戏专属配置。需要范围的步骤使用 WorldEdit 选区；列表步骤在 GUI 中新增、编辑、排序或删除。完成后先执行校验，再发布地图。

发布会保存当前物理世界。未发布、存在未保存修改或正被其他管理员锁定的地图不能开赛。同一张地图同一时间只允许一名管理员编辑；正常退出会恢复原物品栏，意外掉线后的快照会在下次加入时恢复。

推荐流程：

1. 执行 `/cc map edit <游戏>`，选择已有地图或新建未绑定世界的地图草稿。
2. 使用 `/cc admin world create <世界>` 准备并加载世界，传送过去后用“绑定当前世界”步骤绑定或更换地图世界。
3. 按步骤菜单完成所有必需点位、范围、列表和复制布局。
4. 使用“校验”检查缺项，再使用“发布”保存地图。
5. 退出编辑模式，使用 `/cc game start ...` 进行单局测试。
6. 检查观战边界、比赛结束、地图重置、大厅返回和积分写入。

斗战方框、跑酷追击和 TNT飞跃使用 `schematics/arena.schem` 生成复制场地；匹配赛建保留管理员手建的资源大厅，只选择其边界，并使用 `schematics/base.schem` 生成队伍基地。这四类游戏允许多张地图绑定到同一个物理世界，各地图通过各自的选区、锚点和直线复制布局占用互不重叠的区域；TNTRun 与匹配赛建赛后只恢复本地图的 schematic 区域，不会重载共享世界。匹配赛建 0 号基地只作模板，实际队伍从 1 号基地开始分配；大厅与基地各设置一个传送门落点即可双向路由。复制数量、布局和所有锚点都由各自的向导步骤配置。

跑酷追击的每个复制场地是一整个双赛道对局单元：对局位 A、B 各有一个准备点；赛道 1 同时承载 A 队追击者与 B 队逃跑者，赛道 2 同时承载 B 队追击者与 A 队逃跑者。每条赛道分别配置一个完整活动边界、一个追击者出生点和一组逃跑者出生点，不存在彼此独立的“追击区”和“逃跑区”。

每张地图都可配置赛前规则介绍。设置 `introduction-spawn-point` 和 `rules` 后，玩家会先进入规则介绍阶段；将介绍点留空或把 `rules` 设为 `[]` 即可跳过。

### 匹配赛建蓝图

用 WorldEdit 选择成品建筑后执行：

```text
/cc map blueprint create <蓝图名> [覆盖星级]
/cc map blueprint audit <蓝图名|all> [地图] [页码]
/cc map blueprint preview <蓝图名|all> [地图] [页码]
```

插件会忽略空气，把方块保存为相对选区最小角的偏移并立即重载蓝图库。蓝图长、宽、高均不能超过 7 格，文件位于 `buildmart/blueprints/<名称>.yml`；目录为空时会写出三个示例蓝图。

匹配赛建中的铜不会自然氧化，蓝图匹配和材料审查不区分涂蜡状态。保存蓝图时会去除全部铜蜡层；凡是不能由同氧化阶段完整铜块即时合成或切石获得的部件（铜栏杆、铜链、铜箱、铜门、铜傀儡雕像、铜灯笼、铜活板门、避雷针），都会统一成未氧化版本。保存材料区时也会把涂蜡铜快照规范化为同氧化阶段的普通铜。

保存匹配赛建材料区时，插件会扫描当前选区并生成 `buildmart/material-manifests/<地图>.yml`。该文件按材料区和总计记录非空气方块的 `minecraft:<material>` 数量，同时保留精确 `BlockData` 数量，供检查全部建筑的材料需求是否被资源大厅覆盖；它是生成结果，不参与游戏配置读取或运行时逻辑。

可使用 `scripts/buildmart_blueprint_audit.py` 检查单张蓝图或整个蓝图目录。脚本会结合方块数、材料种类、材料岛数量、高度、离散结构、方向和其他 `BlockData` 复杂度生成难度分数，并依据材料清单报告直接覆盖、可合成覆盖和未覆盖材料。例如：

```text
python3 scripts/buildmart_blueprint_audit.py <蓝图文件或目录> \
  --manifest <material-manifests/地图.yml> \
  --area-config <areas/地图.yml> \
  --markdown /tmp/buildmart-audit.md --csv /tmp/buildmart-audit.csv
```

## 比赛流程

### 手动测试赛

手动模式适合验图和单场测试：

1. 确保参与队伍的成员已经加入数据库并上线。
2. 确认目标场地处于 `WAITING`，没有其他比赛占用队伍或玩家。
3. 让观众执行 `/cc spectate <游戏> <场地> [实例]`，或直接打开 `/cc spectate` 菜单选择。
4. 使用对应 `/cc game start ...` 命令开赛。
5. 插件负责准备倒计时、传送、物品和效果初始化、计时、胜负判定与积分记录。
6. 单场或正式赛最终轮结束后玩家返回大厅并写入积分；正式多轮赛的中间轮留在场地安全点并直接进入下一轮。需要模板复原的地图会在整场结束后重新加载。
7. 用 `/cc rank playerboard`、`/cc rank teamboard` 检查结果。

同一支队伍或玩家不能同时进入多个场地。如果开始命令没有生效，应优先检查场地状态、队伍名称、队伍是否已在其他游戏中，以及地图必需点位是否完整。

### 正式赛事建议流程

1. `/cc admin vote start` 开放下一项目投票。
2. 玩家使用 `/cc vote` 打开菜单，或使用 `/cc vote <配置名称>` 直接投票。
3. `/cc admin vote end` 公布结果。
4. 管理员执行 `/cc event start <游戏>` 启动常规正式赛程，或用 `/cc finale <游戏> start <场地>` 启动冠军决赛。
5. 调度器广播项目介绍和积分规则，进行 10 秒倒计时。
6. 游戏结束事件触发下一小轮；小轮之间默认等待 30 秒。
7. 全部小轮结束后，调度器广播本项目积分和总榜，并将观众移出场地。

## 正式赛事命令

| 命令 | 行为 |
| --- | --- |
| `/cc event start <游戏>` | 启动普通正式赛程；同一项目运行中再次执行会紧急停止 |
| `/cc finale dragoneggcarnival start <场地> [队伍1 队伍2]` | 在指定场地启动龙蛋狂欢决赛；未指定队伍时按总榜选择前二 |
| `/cc finale dodgebolt start <场地> [队伍1 队伍2] [--force]` | 在指定场地启动躲避箭决赛；未指定队伍时按总榜选择前二，`--force` 允许使用在线子阵容 |
| `/cc finale <游戏> cancel` | 取消决赛准备，或强制结束正在进行的正式决赛 |
| `/cc event stop <游戏>` | 显式停止该项目的赛程任务和运行实例 |
| `/cc event reset --confirm` | 重置正式比赛轮次和游戏顺序 |
| `/cc event undo --confirm` | 停止并撤销最近一轮正式比赛及其成绩 |

正式赛程支持除匹配赛建外的全部游戏。匹配赛建使用 `/cc game start buildmart all <场地>` 启动单局。Battle Box 与 Parkour Tag 需要偶数支队伍和足够的复制实例；TGTTOS 会依次使用已加载地图；其他游戏按各自赛程管理器选择地图和轮数。

## 投票、积分与观战

投票持续 120 秒，只允许已加入队伍的选手投票。已经记录为完成的游戏不能再次被投票；结束时插件按票数广播排行。

每个场地在比赛中累计玩家积分，结束时写入数据库。`weighted-score` 开启后，总榜会按当前赛事轮次设置的权重计算。`/cc rank info` 可查看各项目权重。

开启 `strict-spectator-rule` 后，正常赛事轮次中参赛队员不能随意观战；拥有 `cc.refuge` 的裁判或替补不受此限制。

## PlaceholderAPI

以下变量可用于计分板、Tab 列表和全息文字。`[场地]`、`[游戏]`、`[名次]` 替换为实际值。

### 选手与排行榜

| Placeholder | 含义 |
| --- | --- |
| `%cc_player_points%` | 当前玩家积分 |
| `%cc_player_rank%` | 当前玩家名次 |
| `%cc_player_team_name%` | 彩色队名 |
| `%cc_player_team_name_no_color%` | 无颜色队名 |
| `%cc_player_team_color%` | 队伍颜色名 |
| `%cc_player_team_color_code%` | 队伍颜色代码 |
| `%cc_player_team_points%` | 当前玩家所属队伍积分 |
| `%cc_player_team_rank%` | 当前玩家所属队伍名次 |
| `%leaderboard_player_[名次]%` | 指定名次的玩家 |
| `%leaderboard_team_[名次]%` | 指定名次的队伍 |

### 赛程与投票

| Placeholder | 含义 |
| --- | --- |
| `%schedule_round_total%` | 当前赛事总轮次 |
| `%schedule_round_points%` | 下一轮积分倍率 |
| `%schedule_round_[游戏]%` | 指定游戏的小轮次 |
| `%vote_can_vote_[配置名称]%` | 游戏当前是否可投 |
| `%vote_vote_nums_[配置名称]%` | 当前票数 |
| `%vote_player_vote%` | 当前玩家的选择 |

### 游戏通用变量

多数游戏实现了：

```text
%<游戏前缀>_area_status_[场地]%
%<游戏前缀>_area_timer_[场地]%
```

游戏前缀包括 `battlebox`、`parkourtag`、`tntrun`、`skywars`、`tgttos`、`snowball`、`decarnival`、`parkourwarrior` 和 `hotycodydusky`。各游戏还提供存活人数、队伍、对手、角色、击杀和检查点等专属变量。

### 火热宾果

| Placeholder | 含义 |
| --- | --- |
| `%bingo_current_time%` | 当前玩家所在 Bingo 场地的剩余时间 |
| `%bingo_current_time_[场地]%` | 指定场地的剩余时间 |
| `%bingo_current_tasks_team%` | 当前玩家所属队伍已完成的任务数 |
| `%bingo_current_tasks_team_[场地]%` | 当前玩家所属队伍在指定场地完成的任务数 |
| `%bingo_area_rank_1_[场地]%` 至 `%bingo_area_rank_4_[场地]%` | 指定场地第 1 至 4 名队伍及其分数 |

Bingo 同样支持通用的 `%bingo_area_status_[场地]%` 和 `%bingo_area_timer_[场地]%`。

## 侧栏记分板

ChampionshipsCore 内置基于 FastBoard 的统一侧栏，不再需要 SternalBoard。显示优先级为：
参赛/旁观游戏板、地图 prepare 编辑板、管理员地图状态板、赛事大厅板。游戏板按玩家实际
归属选择，不依赖所在世界；remote Bingo 的模板由 Core 随比赛 manifest 下发，Worker 不需要
额外配置。

所有标题、行文本、颜色、游戏模板和地图覆盖均位于 `scoreboards.yml`。配置沿用 `&`、
`#RRGGBB` 和 `&#RRGGBB` 颜色写法，最多渲染 15 行；`/cc admin reload --confirm` 会原子重载，
配置无效时继续使用上一份有效快照。默认模板保留原 SternalBoard 的赛事和游戏信息，并为
队伍、对手、排行榜及管理员警告增加原生彩色显示。

## 数据目录

```text
plugins/ChampionshipsCore/
├── config.yml
├── message.yml
├── schedule-message.yml
├── scoreboards.yml             # 统一大厅、游戏、地图状态和地图编辑侧栏
├── maps/                       # 比赛重置使用的静态世界模板
├── battlebox/
│   └── schematics/arena.schem
├── parkourtag/
│   └── schematics/arena.schem
├── tntrun/
│   └── schematics/arena.schem
├── buildmart/
│   ├── areas/
│   ├── blueprints/
│   ├── material-manifests/    # 自动生成的材料区方块清单（只读）
│   └── schematics/
│       └── base.schem
├── skywars/
│   └── variants/
│       └── default.yml
└── bingo/
    ├── areas/
    ├── cards/
    ├── lang/
    ├── tags/
    └── tierlists/
```

正式改图前请备份整个插件数据目录和数据库。不要在比赛进行中执行保存地图、重载插件或直接编辑场地 YAML。

## 许可证

本项目使用 [MIT License](LICENSE)。
