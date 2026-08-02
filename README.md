# ChampionshipsCore

ChampionshipsCore 是 Summer/Winter Collab Championship 使用的 Minecraft 综合赛事核心插件。它负责队伍与选手管理、比赛场地、游戏生命周期、积分排行、自动赛程、投票、观战、聊天分组和 PlaceholderAPI 变量。

| 正式名称 | 命令标识 | 源码枚举 |
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

## 游戏介绍

### 火热宾果（Bingo But Hot）

所有队伍被分散传送到同一套主世界、下界和末地，在默认 20 分钟内完成 5×5 任务卡。任务可以是获得物品或药水、达成统计目标或完成进度；副手地图和菜单用于查看卡片。

- 同一格可由不同队伍分别完成，不会因首支队伍完成而锁定。
- 单格按完成先后默认依次获得 50、40、30、20、10、5 分；超出列表后不再获得该项名次分。
- 连成横、竖或对角线会获得连线奖励，默认前 4 条各 200 分，其余各 100 分。
- 按总分排名；同分时，较早达到该分数的队伍优先。

### 匹配赛建（Build Match）

各队从公共资源大厅收集方块，在自己的三个普通建造位和一个金色建造位复刻参考建筑。默认比赛 12 分钟、准备 10 秒；普通蓝图库每 90 秒刷新，金色蓝图每 120 秒刷新，资源区每 60 秒重置。

- 普通建造位通过菜单选图，每个建造位可主动换图一次。
- 提交时逐方块检查，只有与蓝图完全一致才算完成；得分为蓝图星级乘以当前阶段的每星分值，比赛前、中、后三段默认分别为 10、15、20 分。
- 金色蓝图轮换时，尚未完成的金色建造会被清空。
- 时间结束时，未完成建筑按完成比例结算。
- 总星数、完成建筑数和平均星级三个奖项分别给前三名队伍的每名成员加 100、50、25 分。

### 斗战方框（Battle Box）

两队在默认 60 秒内争夺中央 3×3 区域。场地可配置装备套组和瞬间伤害药水点；任一队先用本队方块填满全部 9 格便立即获胜，否则倒计时结束后比较双方占据格数。

- 击杀一名对手获得 15 分。
- 获胜队每名成员获得 40 分；平局时双方每名成员获得 15 分。

### 跑酷追击（Parkour Tag）

每场对局由两支队伍参加，并在左右两个镜像区域同时进行：每队选择一名追逐者，追捕对方的逃生者。默认每小局 60 秒，追逐者碰触逃生者即完成抓捕；逃生者可使用时钟令对方追逐者发光 3 秒，冷却 10 秒。

- 未被全员抓获时，每名存活逃生者获得 20 分。
- 每名逃生者按存活时长，每满 10 秒获得 2 分。
- 追逐者抓完全部对手后按剩余时间获得递减分数。
- 存活总时长较长的一方获胜，每名成员另得 30 分。

### 跑路战士（Runaway Warrior）

玩家在默认 10 分钟内依次挑战主检查点、子检查点和终点路线，最后 30 秒进入突然死亡阶段。系统记录每名玩家的重生点与检查点进度，并按完成的 2 至 5 星关卡数量逐级计分；连续完成较高星级关卡时，单次收益会递增。

完成最终路线还会给全队施加倍率奖励。内置简单、普通、困难路线倍率分别为 0.15、0.35、0.8，具体路线与检查点由场地配置决定。

### 空岛乱斗（Sky Brawl）

各队从玻璃笼和队伍出生岛进入空岛生存战。默认时长 240 秒，场地支持边界收缩，并会在后期关闭生命恢复。

- 击杀一名对手获得 40 分。
- 每有一名玩家被淘汰，所有仍存活玩家各得 10 分；整支队伍被淘汰时，所有存活玩家再各得 2 分。
- 比赛结束时仍存活的玩家各得 50 分。

### 去到另一边（Try Get To The Other Side）

玩家在默认 90 秒内穿越障碍并击打终点鸡完成地图。不同场地类型可以发放船，或提供钻石镐和队伍色混凝土用于铺路；地图还可配置流浪者和终点鸡生成点。

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

这是两队参加的五局三胜制决赛。每小局开始时场中央生成龙蛋，并每 10 秒随机发放一次场地配置的装备；任一队夺得龙蛋即可赢下该局。

若比赛进行到 100 秒仍无人夺蛋，龙蛋消失并生成 60 点生命值的末影龙，同时发放决战物品；击杀末影龙的一方赢下该局。每局结束后地图会重载，率先取得 3 个小局胜场的队伍成为最终胜者。

## 运行要求

- Java 25
- Paper 26.1.2 或 Folia 26.1.2 服务端
- MariaDB 或 MySQL
- ProtocolLib 5.4.0
- PlaceholderAPI 2.12.2
- FastAsyncWorldEdit 2.15.0
- PhantomWorlds（可选软依赖）

ProtocolLib、PlaceholderAPI 或 FastAsyncWorldEdit 缺失时，ChampionshipsCore 会在启动阶段自行禁用。
Folia 部署时，这三个硬依赖也必须使用同时支持 Folia 和 26.1.2 的构建。

## 构建与安装

```bash
mvn clean package
```

构建产物位于 `target/`。将插件本体和三个必需依赖放入服务端的 `plugins/` 目录，然后启动一次服务端生成配置。

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

场地配置和游戏专属配置分别位于对应游戏目录。火热宾果还使用 `bingo/config.yml`、`cards/`、`tags/`、`tierlists/` 和语言文件；匹配赛建使用 `buildmart/blueprints/` 中的蓝图。

## 权限

插件只在 `/cc` 后的第一级命令上检查权限，子命令不会继续拼接权限节点。例如，拥有 `cc.game` 就可以访问 `/cc game start ...` 和 `/cc game area ...`。

| 权限 | 可用功能 |
| --- | --- |
| `cc.spawn` | 返回赛事大厅 |
| `cc.vote` | 为下一场游戏投票 |
| `cc.spectate` | 加入或退出观战 |
| `cc.rank` | 查看个人、队伍排行和游戏权重 |
| `cc.team` | 创建、删除、查询和传送队伍 |
| `cc.member` | 添加或移除队伍成员 |
| `cc.game` | 创建场地、设置点位、保存地图、手动开始比赛 |
| `cc.admin` | 重载、投票、赛程、强制执行和传送等管理功能 |
| `cc.refuge` | 严格观战规则开启时，允许参赛队员以裁判/替补身份观战 |

建议使用权限插件分组：普通选手只授予 `cc.spawn`、`cc.vote`、`cc.rank`；观众增加 `cc.spectate`；赛事管理员授予所有 `cc.*`。

## 命令约定

- `<参数>` 表示必填参数，`[参数]` 表示可选参数。
- `<队伍>` 使用创建队伍时的内部名称，不是彩色显示名。
- 场地设置、WorldEdit 选区和当前位置相关命令必须由游戏内玩家执行。
- 多数名称匹配区分大小写，推荐队伍名、场地名和蓝图名统一使用小写英文、数字和下划线。
- 输入到中间命令节点时，插件会显示当前节点下的帮助列表，例如 `/cc game area`。

## 玩家与通用命令

| 命令 | 说明 |
| --- | --- |
| `/cc spawn` | 传送回 `lobby.location` |
| `/cc vote <游戏枚举>` | 在投票开放期间投票 |
| `/cc spectate <游戏> <场地>` | 观战指定场地 |
| `/cc spectate leave` | 退出观战并返回大厅 |
| `/cc rank playerboard` | 查看个人积分榜 |
| `/cc rank teamboard` | 查看队伍积分榜 |
| `/cc rank info` | 查看各游戏的积分权重 |

`/cc vote` 接收 Java 枚举名而不是小写命令标识，常用值包括 `Bingo`、`BuildMart`、`BattleBox`、`ParkourTag`、`ParkourWarrior`、`SkyWars`、`TGTTOS`、`TNTRun`、`SnowballShowdown` 和 `HotyCodyDusky`。枚举名区分大小写；`DragonEggCarnival` 会被拒绝。

观战游戏标识为：`bingo`、`buildmart`、`battlebox`、`parkourtag`、`parkourwarrior`、`skywars`、`tgttos`、`tntrun`、`snowball`、`dragoneggcarnival`、`hotycodydusky`。

## 队伍与成员管理

### 创建队伍

实际命令格式为：

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

> 命令内置帮助目前把三个参数显示为“队伍ID、名称、颜色”，但源码实际按“内部队伍名、颜色名、颜色代码”处理；请以上述格式为准。数据库数字 ID 会自动生成，不需要手工输入。

### 添加队员

```text
/cc member add <队伍名> <玩家名>
/cc member delete <队伍名> <玩家名>
```

示例：

```text
/cc member add red_rabbits Steve
/cc member add red_rabbits Alex
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
| 两队对战 | `/cc game start dragoncarnival <场地> <队伍1> <队伍2>` |
| 所有队伍 | `/cc game start bingo all <场地>` |
| 所有队伍 | `/cc game start buildmart all <场地>` |
| 所有队伍 | `/cc game start skywars all <场地>` |
| 所有队伍 | `/cc game start tgttos all <场地>` |
| 所有队伍 | `/cc game start tntrun all <场地>` |
| 所有队伍 | `/cc game start snowball all <场地>` |
| 所有队伍 | `/cc game start parkourwarrior all <场地>` |
| 指定多队 | `/cc game start hotycodydusky <场地> <队伍...>` |

注意龙蛋狂欢的手动启动标识是 `dragoncarnival`，而场地和观战标识是 `dragoneggcarnival`。

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

## 创建场地：通用流程

通常一个场地由两部分组成：游戏专属 YAML 配置和地图模板。比赛开始时，插件从 `plugins/ChampionshipsCore/maps/` 复制静态模板到实际游戏世界；比赛结束后重新加载干净模板。Bingo 是例外，它会维护持久化的主世界、下界和末地，并通过 `bingo/` 下的卡池与规则配置生成任务。

推荐流程：

1. 准备地图或 WorldEdit schematic。
2. 执行 `/cc game area <游戏> add <场地名>` 创建配置。
3. 进入该游戏世界，按场地类型生成/粘贴地图。
4. 站到出生点执行 `set`，需要范围时先用 `//pos1`、`//pos2` 建立 WorldEdit 选区。
5. 对支持 `save` 的游戏保存静态地图；模板化游戏的 `prepare` 会自动保存。
6. 使用 `spectate` 检查观众点，使用手动 `game start` 做一场测试赛。
7. 确认比赛结束后地图能重置、玩家能返回大厅、积分能写入数据库。

所有游戏均支持：

```text
/cc game area <游戏> add <场地名>
/cc game area <游戏> set <场地名> <参数> [...]
```

只有空岛乱斗、TNT飞跃和龙蛋狂欢暴露独立保存命令：

```text
/cc game area skywars save <场地名>
/cc game area tntrun save <场地名>
/cc game area dragoneggcarnival save <场地名>
```

保存只能在场地处于 `WAITING` 状态时执行，执行后场内人员会被送回大厅，世界会卸载、复制到静态模板目录并重新加载。

### 场地参数

下表列出命令实际会处理的参数。表中“当前位置”表示执行命令时玩家的坐标和朝向，“WE 选区”表示当前 WorldEdit 选区。

| 游戏 | 参数 | 设置方式 |
| --- | --- | --- |
| 火热宾果 | `spectator-spawn-point` | 当前位置 |
| 斗战方框 | `right-spawn-point`、`left-spawn-point` | 当前位置 |
| 斗战方框 | `right-pre-spawn-point`、`left-pre-spawn-point` | 当前位置 |
| 斗战方框 | `spectator-spawn-point` | 当前位置 |
| 斗战方框 | `wool-pos`、`area-pos` | WE 选区 |
| 斗战方框 | `potion-spawn-points add\|clean` | 添加当前位置或清空 |
| 跑酷追击 | `right-pre-spawn-point`、`left-pre-spawn-point` | 当前位置 |
| 跑酷追击 | `spectator-spawn-point` | 当前位置 |
| 跑酷追击 | `area-pos`、`right-area-area-pos`、`left-area-area-pos` | WE 选区 |
| 跑酷追击 | `right-area-chaser-spawn-point`、`left-area-chaser-spawn-point` | 当前位置 |
| 跑酷追击 | `right-area-escapee-spawn-points add\|clean` | 添加当前位置或清空 |
| 跑酷追击 | `left-area-escapee-spawn-points add\|clean` | 添加当前位置或清空 |
| TNT飞跃 | `spectator-spawn-point`、`copy-spawn` | 当前位置 |
| 空岛乱斗 | `pre-spawn-point`、`spectator-spawn-point` | 当前位置 |
| 空岛乱斗 | `area-pos` | WE 选区 |
| 空岛乱斗 | `team-spawn-points add\|clean` | 添加当前位置或清空 |
| 去到另一边 | `spectator-spawn-point` | 当前位置 |
| 去到另一边 | `area-pos` | WE 选区 |
| 去到另一边 | `monster-spawn-points add\|clean` | 添加当前位置或清空 |
| 去到另一边 | `chicken-spawn-points add\|clean` | 添加当前位置或清空 |
| 去到另一边 | `player-spawn-points add\|clean` | 添加当前位置或清空 |
| 雪球乱斗 | `spectator-spawn-point` | 当前位置 |
| 雪球乱斗 | `area-pos` | WE 选区 |
| 雪球乱斗 | `player-spawn-points <组名> add\|clean` | 修改配置中已有的出生组 |
| 龙蛋狂欢 | `spectator-spawn-point`、`dragon-egg-spawn-point`、`dragon-spawn-point` | 当前位置 |
| 龙蛋狂欢 | `area-pos` | WE 选区 |
| 龙蛋狂欢 | `left-spawn-points add\|clean`、`right-spawn-points add\|clean` | 添加当前位置或清空 |
| 龙蛋狂欢 | `kits add\|clean` | 添加主手物品副本或清空 |
| 烫手鳕鱼 | `spectator-spawn-point`、`player-spawn-point` | 当前位置 |
| 烫手鳕鱼 | `area-pos` | WE 选区 |
| 跑路战士 | `spectator-spawn-point`、`player-spawn-point` | 当前位置 |
| 跑路战士 | `area-pos` | WE 选区 |

部分命令的 Tab 补全还会显示 `name`、`timer`、`area-type`，但当前命令实现不会写入这些值。需要修改时请编辑对应场地 YAML，再执行 `/cc admin reload`。

### 跑路战士检查点

```text
# 创建主检查点；类型为 main、sub 或 fin
/cc game area parkourwarrior set <场地> checkpoints add <检查点名> <类型>

# 站在重生点设置检查点出生位置
/cc game area parkourwarrior set <场地> checkpoints set-spawn <检查点名>

# 用 WE 选区设置检查点入口
/cc game area parkourwarrior set <场地> checkpoints set-enter <检查点名>

# 用 WE 选区追加一个子检查点
/cc game area parkourwarrior set <场地> checkpoints add-sub-checkpoint <检查点名>
```

创建检查点时会把当前位置同时记录为初始重生点。修改后场地会立即重载检查点。

## 模板化场地流程

斗战方框、跑酷追击和 TNT飞跃可以把一个完整场地保存为 schematic，再一次生成多份副本以支持并行比赛。`<份数>` 至少应等于同一轮的并发对局数；两队制比赛通常需要 `队伍数 / 2` 份。

### 斗战方框（Battle Box）

```text
# 1. 用 //pos1 和 //pos2 选择一个完整的双队对战场
/cc game area battlebox schematic

# 2. 创建场地配置并生成 N 份副本
/cc game area battlebox add main
/cc game area battlebox prepare main <份数>

# 3. 在 0 号副本设置左右出生点、预备点、羊毛区、场地范围和药水点
/cc game area battlebox set main <参数>
```

schematic 保存为 `battlebox/schematics/arena.schem`。`prepare` 会粘贴副本并自动固化地图模板。

### 跑酷追击（Parkour Tag）

```text
/cc game area parkourtag schematic
/cc game area parkourtag add main
/cc game area parkourtag prepare main <份数>
/cc game area parkourtag set main <参数>
```

需要配置整个副本范围、左右预备点、左右追逐区范围、追逐者出生点、逃生者出生点和观众点。调度器会把一轮的全部对局放入同一逻辑场地的不同副本中。

### TNT飞跃（TNT Spleef）

```text
/cc game area tntrun schematic
/cc game area tntrun add main
/cc game area tntrun prepare main <份数>
/cc game area tntrun set main spectator-spawn-point
/cc game area tntrun set main copy-spawn
```

`copy-spawn` 是 0 号赛道的模板出生点；其他副本会按布局偏移推导。

## 匹配赛建（Build Match）场地与蓝图

匹配赛建由一个公共资源大厅和多个队伍基地组成。地图在准备阶段一次生成并固化，比赛期间不会临时克隆基地。

### 生成地图

```text
# 1. 分别选择完整大厅和一个完整队伍基地
/cc game area buildmart schematic hub
/cc game area buildmart schematic base

# 2. 创建场地并按队伍数生成基地
/cc game area buildmart add main
/cc game area buildmart prepare main <队伍数>
```

模板保存为 `buildmart/schematics/hub.schem` 和 `base.schem`。准备后，在 0 号基地配置锚点；其他基地会按座位偏移自动推导。

### 配置大厅

以下命令都记录玩家当前位置：

```text
/cc game area buildmart set main spectator-spawn-point
/cc game area buildmart set main hub-spawn-point
/cc game area buildmart set main hub-pos1
/cc game area buildmart set main hub-pos2
/cc game area buildmart set main hub-return-pos1
/cc game area buildmart set main hub-return-pos2
/cc game area buildmart set main golden-display-point
```

- `hub-pos1/2`：资源大厅范围，范围内限制飞行和方块放置。
- `hub-return-pos1/2`：返回基地的触发区域。
- `golden-display-point`：金色蓝图在大厅中的展示锚点。

### 配置 0 号基地

```text
/cc game area buildmart set main base spawn
/cc game area buildmart set main base portal-pos1
/cc game area buildmart set main base portal-pos2
/cc game area buildmart set main base normal-plot-1
/cc game area buildmart set main base normal-plot-2
/cc game area buildmart set main base normal-plot-3
/cc game area buildmart set main base normal-ref-1
/cc game area buildmart set main base normal-ref-2
/cc game area buildmart set main base normal-ref-3
/cc game area buildmart set main base golden-plot
/cc game area buildmart set main base golden-ref
```

`normal-plot-*` 是普通蓝图建造锚点，`normal-ref-*` 是对应参考模型锚点；`golden-plot` 和 `golden-ref` 用于金色蓝图。

### 创建蓝图

用 WorldEdit 选择成品建筑，然后执行：

```text
/cc game area buildmart blueprint create <蓝图名> <星级>
```

插件会忽略空气，把方块保存为相对选区最小角的偏移，并立即重载蓝图库。单个蓝图最多导出 20,000 个非空气方块。蓝图文件位于 `buildmart/blueprints/<名称>.yml`；目录为空时会写出三个示例蓝图。

匹配赛建场地 YAML 还可调整：

| 配置项 | 默认值 | 说明 |
| --- | ---: | --- |
| `timer` | 720 秒 | 比赛时长 |
| `prepare-time` | 10 秒 | 开赛准备倒计时 |
| `library-refresh-seconds` | 90 秒 | 普通蓝图库刷新间隔 |
| `golden-refresh-seconds` | 120 秒 | 金色蓝图刷新间隔 |
| `resource-reset-seconds` | 60 秒 | 大厅资源区重置间隔 |
| `portal-cooldown-millis` | 1000 毫秒 | 传送门防抖冷却 |

## 比赛流程

### 手动测试赛

手动模式适合验图和单场测试：

1. 确保参与队伍的成员已经加入数据库并上线。
2. 确认目标场地处于 `WAITING`，没有其他比赛占用队伍或玩家。
3. 让观众执行 `/cc spectate <游戏> <场地>`。
4. 使用对应 `/cc game start ...` 命令开赛。
5. 插件负责准备倒计时、传送、物品和效果初始化、计时、胜负判定与积分记录。
6. 游戏结束后玩家返回大厅，场地从静态模板重新加载，积分写入数据库。
7. 用 `/cc rank playerboard`、`/cc rank teamboard` 检查结果。

同一支队伍或玩家不能同时进入多个场地。如果开始命令没有生效，应优先检查场地状态、队伍名称、队伍是否已在其他游戏中，以及地图必需点位是否完整。

### 正式赛事建议流程

1. `/cc admin vote start` 开放下一项目投票。
2. 玩家使用 `/cc vote <游戏枚举>` 投票。
3. `/cc admin vote end` 公布结果。
4. 管理员执行 `/cc admin schedule <游戏>` 启动自动赛程。
5. 调度器广播项目介绍和积分规则，进行 10 秒倒计时。
6. 游戏结束事件触发下一小轮；小轮之间默认等待 30 秒。
7. 全部小轮结束后，调度器广播本项目积分和总榜，并将观众移出场地。

## 调度器使用方法

| 命令 | 行为 |
| --- | --- |
| `/cc admin schedule battlebox` | 生成两两配对，进行 9 个小轮，并行使用已生成的场地副本 |
| `/cc admin schedule parkourtag` | 生成两两配对，进行 9 个小轮，并行使用已生成的场地副本 |
| `/cc admin schedule snowball` | 在 `area1` 进行 3 轮雪球乱斗 |
| `/cc admin schedule skywars` | 在 `area2` 进行 1 轮空岛乱斗 |
| `/cc admin schedule tntrun` | 在 `area1` 进行 3 轮 TNT飞跃 |
| `/cc admin schedule tgttos` | 依次使用全部已加载的 TGTTOS 场地，每个场地 1 轮 |
| `/cc admin schedule parkourwarrior` | 在 `area1` 进行 1 轮跑路战士 |
| `/cc admin schedule hotycodydusky` | 将各队成员打散到 4 个场地，进行 3 轮 |
| `/cc admin schedule dragoneggcarnival <队伍1> <队伍2>` | 在 `area1` 对指定两队进行龙蛋狂欢调度 |
| `/cc admin schedule reset` | 清空已记录的总轮次/游戏顺序 |

调度器使用注意事项：

- 同一个调度命令在该项目已启用时再次执行，会结束该项目的调度，而不是重复开始。
- Battle Box 和 Parkour Tag 要求队伍总数为偶数，并要求提前生成足够的并行副本。
- 雪球乱斗、TNT飞跃、跑路战士和龙蛋狂欢必须存在名为 `area1` 的场地；空岛乱斗必须存在 `area2`。
- Battle Box 和 Parkour Tag 使用对应管理器找到的第一个场地；正式服建议各自只保留一个正式赛场。TGTTOS 会依次使用所有已加载场地。
- Hoty Cody Dusky 按最多 4 个场地分配玩家，当前调度器要等 4 个场地都完成才会推进下一轮，因此正式赛应准备 4 个可用场地。
- 单人/全队项目会在一轮结束事件到来后自动进入下一轮。
- `schedule reset` 只重置赛事轮次/游戏顺序，不会删除队伍、积分、地图或场地配置。
- 当前调度命令不包含火热宾果和匹配赛建；这两个游戏请使用手动的 `game start ... all` 命令。
- 龙蛋狂欢命令内置 usage 未显示两个队伍参数，但实际必须提供。

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
| `%vote_can_vote_[游戏枚举]%` | 游戏当前是否可投 |
| `%vote_vote_nums_[游戏枚举]%` | 当前票数 |
| `%vote_player_vote%` | 当前玩家的选择 |

### 游戏通用变量

多数游戏实现了：

```text
%<游戏前缀>_area_status_[场地]%
%<游戏前缀>_area_timer_[场地]%
```

游戏前缀包括 `battlebox`、`parkourtag`、`tntrun`、`skywars`、`tgttos`、`snowball`、`decarnival`、`parkourwarrior` 和 `hotycodydusky`。此外还有存活人数、队伍、对手、角色、击杀、检查点等游戏专属变量，可直接参考 `integration/papi/` 下对应 Placeholder 类。

## 数据目录

```text
plugins/ChampionshipsCore/
├── config.yml
├── message.yml
├── schedule-message.yml
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
│   └── schematics/
│       ├── hub.schem
│       └── base.schem
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
