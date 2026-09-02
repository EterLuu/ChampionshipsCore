# ChampionshipsCore

ChampionshipsCore 是 Summer/Winter Collab Championship 使用的赛事核心插件。它把一套 Minecraft 服务端组织成完整的团队锦标赛：从队伍、场地和赛程，到实时计分、观战、聊天展示和最终排名，都由同一套插件统一管理。赛事团队可以用它手动开一场测试局，也可以在正式比赛中驱动多轮赛程、记录成绩并生成最终排名。

插件内置 13 个比赛项目，覆盖宾果探索、建造还原、竞速跑酷、团队对抗和决赛场景。每个项目都支持独立地图、规则介绍、复制场地、积分规则和观战边界。

| 显示名称 | 命令标识 | 配置名称 |
| --- | --- | --- |
| 宾果 | `bingo` | `Bingo` |
| 匹配赛建 | `buildmart` | `BuildMart` |
| 斗战方框 | `battlebox` | `BattleBox` |
| 跑酷追击 | `parkourtag` | `ParkourTag` |
| 跑路战士 | `parkourwarrior` | `ParkourWarrior` |
| 空岛乱斗 | `skywars` | `SkyWars` |
| 去到另一边 | `tgttos` | `TGTTOS` |
| TNT飞跃 | `tntrun` | `TNTRun` |
| 雪球大战 | `snowball` | `SnowballShowdown` |
| 龙蛋狂欢 | `dragoneggcarnival` | `DragonEggCarnival` |
| 烫手鳕鱼 | `hotycodydusky` | `HotyCodyDusky` |
| 躲避箭 | `dodgebolt` | `Dodgebolt` |
| 王牌竞速 | `acerace` | `AceRace` |

## 游戏介绍

### 宾果

所有队伍进入同一套主世界、下界和末地，共同完成 5×5 任务卡。任务会要求获得物品或药水、达成统计目标或完成进度；同一格可由多支队伍分别完成。玩家右键副手地图，即可在地图与菜单两种卡片样式间切换。

完成按先后顺序计分，横、竖或对角连线带来团队奖励。开局发放探索装备与物资，并提供队友传送；常驻效果、PvP 时机、时长和分值由 Bingo 配置决定。`LOCAL` 与 `REMOTE` 共用同一套任务判定、世界规则、玩家状态、计时格式、队伍颜色和展示策略；远程 Worker 执行 Core 冻结后的比赛 manifest，保证两端呈现一致的玩法。

### 匹配赛建

各队从公共资源大厅收集方块，在自己的建造位复刻参考建筑。普通建造位随机分配蓝图，完成后自动补充；金色建造位按固定周期轮换高价值蓝图。

提交时逐方块核对蓝图，完全一致的建筑按星级和当前计分阶段结算，未完成建筑按完成比例结算。比赛结束后，总星数、完成建筑数和平均星级还会决定排名奖励。

### 斗战方框

两队争夺中央 3×3 区域。场地可配置装备套组和瞬间伤害药水点；先用本队方块填满全部 9 格的队伍立即获胜。倒计时结束时仍未填满，则比较双方占据格数。击杀和回合结果都会计入积分。

### 跑酷追击

每场对局由两支队伍参加，并在左右两个镜像区域同时进行：每队选择一名追逐者，追捕对方的逃生者。追逐者碰触逃生者即完成抓捕；逃生者按存活时长得分，追逐者按抓捕和剩余时间得分，存活总时长较长的一方赢得对局奖励。

逃生者可用场地道具短暂干扰追逐者，追逐者也有专属的机动道具。追逐者选择次数、道具和回合时长由场地配置决定。

### 跑路战士

玩家依次挑战主检查点、子检查点和终点路线。系统记录每名玩家的重生点与检查点进度，按完成路线的星级计分；连续完成高星级路线时，单次收益递增。完成最终路线会为全队施加倍率奖励，路线、检查点、时长和倍率均由场地配置决定。

### 空岛乱斗

各队从玻璃笼和队伍出生岛进入空岛生存战。比赛按场地规则依次开放乐魂、收缩安全区域并调整生命恢复；击杀、存活和淘汰其他队伍都会计分。玩家死亡后，携带物品保存在死亡位置的箱子中。

### 去到另一边

玩家穿越障碍并击打终点鸡完成地图。地图可分别配置船、道路工具、空手或鞘翅模式，以及流浪者生成点和随机出生区域。个人越早到达得分越高，团队完成触发额外奖励；正式调度依次使用已加载场地，每张地图进行一轮。

### TNT飞跃

玩家在多层方块场地生存，脚下方块会延迟消失，指定阶段还会落下 TNT 雨。玩家可用鞘翅挽救坠落；每次淘汰奖励仍存活的玩家，轮末按生存名次结算。正式调度使用预先生成的赛道副本，并按赛程配置进行多轮比赛。

### 雪球大战

玩家以队伍颜色装备进入竞技场，使用雪球和铁剑混战。被淘汰后会在随机点复活；击杀会补充雪球，并计入个人与队伍分数。达到场地设定的击杀目标可提前结束，轮末按队伍击杀数排名结算。

### 烫手鳕鱼

每轮随机选出一名鳕鱼持有者；持有者持续受伤，攻击其他玩家可转移鳕鱼，转移后进入短暂保护期。淘汰奖励仍存活的玩家，最终按生存名次结算。正式调度将各队成员分散到多个场地，并按赛程配置进行多轮比赛。

### 龙蛋狂欢

这是两队同时进行的一场完整末影龙战。双方从镜像平台出发，使用固定装备、队伍方块和生存物资推进。队伍率先完成【解放末地】【下一世代】【远程折跃】中的任意两项即可获胜；摧毁末影水晶为全队补充物资并提供随机强化，持续伤害末影龙会给对手施加短暂负面效果。

### 躲避箭

总积分前两名进行五局三胜决赛。箭命中玩家即淘汰，淘汰对方全队即赢下一局；第一局由高顺位队伍获得两箭，后续小局双方各获得一箭。

- 玩家保持在中央分界两侧；箭在每次射击结束后消失并刷新到对方半场。
- 淘汰与累计射箭推动平台逐层收缩；管理员使用 `/cc finale dodgebolt` 下的裁判命令处理暂停、重开、淘汰和强制胜利。
- 躲避箭是决赛项目，使用专属赛程管理。

### 王牌竞速

所有选手在限时内按顺序通过进度点并完成配置圈数。正向穿过终点线结算一圈；跌落到赛段高度以下时返回最近的重生点。每个进度点可切换鞘翅、激流三叉戟或无装备，并分别配置赛段跌落高度。场地可使用速度、跳跃和定向弹射机关；完赛名次和分段名次均会计分。

## 运行要求

- Java 25
- Paper 26.2.x 对应的服务端
- MariaDB 或 MySQL
- ProtocolLib 5.4.0
- PlaceholderAPI 2.12.2
- FastAsyncWorldEdit 2.15.0

ProtocolLib、PlaceholderAPI 或 FastAsyncWorldEdit 缺失时，ChampionshipsCore 会在启动阶段自行禁用。

## 构建与安装

```bash
mvn clean package
```

构建完成后，将 `target/ChampionshipsCore-1.3-SNAPSHOT.jar` 与三个必需依赖放入 Paper 服务端的 `plugins/` 目录，然后启动一次服务端生成配置。

仓库同时包含共享协议、Bingo 计分引擎、Paper/Folia 平台层、Redis transport、独立 Bingo Worker、压测插件、Core 插件和可选认证组件。根目录构建会生成 Core 与 Worker 两个主要制品。共享契约分布在 `championships-common`、`championships-bingo-engine`、`championships-platform-bukkit` 和 `championships-redis`；这些模块或跨端玩家可见行为发生变化时，Core 与 Worker 需成对构建、部署和重启。

`championships-auth-bridge` 部署在 Paper 侧，负责账号绑定资料同步和登录 UUID 核对；`championships-auth-proxy` 部署在 BungeeCord 侧，在登录阶段执行账户准入、封禁检查和有效 UUID 注入。Proxy 持久化身份同步快照；同步源短暂不可达时，已同步玩家仍可进入服务器并由本地 AuthMe 验证密码。UUID 的跨组件边界见 [player-uuid-contract.md](docs/player-uuid-contract.md)。

远程 Bingo 可通过 `bingo.execution-mode: REMOTE` 把世界与玩法执行交给独立 Folia Worker；`LOCAL` 模式保留单服执行方案。部署前阅读 [Worker README](championships-bingo-worker/README.md)、[跨服架构与上线流程](docs/bingo-remote-architecture.md) 和 [64 人性能指南](docs/bingo-64-player-performance-report.md)。性能指南中的逻辑玩家压测覆盖区块与实体负载；真实客户端的端到端验收是生产上线的最后一层确认。压测插件只临时安装在可丢弃的测试世界。

Bingo 任务机制与图集来自上游 [MineBingo](https://gitee.com/chancelethay/minebingo)，并按 ChampionshipsCore 的服务端版本、资源获取条件与计分体系持续适配；默认卡片与难度表由资源契约测试保护。其三页任务投票接入 ChampionshipsCore 的 DAILY 自由游玩，共用 `daily-vote.seconds` 的单一倒计时和 CC 原有大厅、Party、匹配与临时队伍。正式赛与管理员手动局继续使用场地原有固定积分规则。

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
| `whitelist` | 满员时优先放行的玩家名列表；仅影响 Core 的人数上限，账户准入、封禁和 UUID 分配由身份桥接组件管理 |
| `weighted-score.enabled` | 是否启用游戏归一化权重和比赛轮次系数 |
| `weighted-score.round-multipliers` | 按正式比赛轮次从第 1 轮开始配置系数；超出列表的轮次按 0 计算 |
| `strict-spectator-rule` | 正式赛事模式下是否阻止参赛队员自由观战；DAILY 模式不执行此限制 |
| `enabled-games` | 本次赛事启用的游戏；未启用的游戏不会加载，也不会出现在相关命令中 |
| `database.type` | `MARIADB` 使用 MariaDB 驱动，其他值使用 MySQL 驱动 |
| `database.address` / `port` | 数据库地址和端口 |
| `database.name` | 数据库名称 |
| `database.username` / `password` | 数据库凭据 |
| `team.max-members` | 每支队伍最多成员数，默认 4 |
| `lobby.location` | 大厅出生点，也是比赛结束和地图保存时的返回点 |
| `parkourtag.max-chaser-times` | 单名队员最多担任追逐者的次数 |
| `redis.enabled` | 启用数据库缓存同步、跨服公共聊天和 REMOTE Bingo 所需的统一 Redis 生命周期 |
| `redis.instance-id` | Core 实例的稳定唯一标识；`auto` 会在数据目录持久化生成，克隆实例后必须显式区分 |
| `redis.uri` / `namespace` | Redis 连接地址和逻辑命名空间；Core 与 Worker 必须一致 |
| `redis.consumer-group-prefix` | Core 数据同步及聊天 consumer group 的前缀 |
| `bingo.execution-mode` | `LOCAL` 在 Core 内执行；`REMOTE` 把玩法执行交给 Folia Worker |
| `bingo.worker-id` / `worker-server` | REMOTE 模式的 Worker 标识与代理服务名 |

修改配置后执行：

```text
/cc admin reload
```

`enabled-games` 使用文首“配置名称”一列中的名称，不区分大小写；设为 `[]` 会关闭所有游戏。

场地配置和游戏专属配置分别位于对应游戏目录。宾果还使用 `bingo/config.yml`、`cards/`、`tags/`、`tierlists/` 和语言文件；匹配赛建使用 `buildmart/blueprints/` 中的蓝图。

Bingo 的场地文件还可通过 `permanent-effects` 调整常驻效果，格式为 `效果:等级`，例如 `night_vision:1`、`jump_boost:8`。

空岛乱斗场地通过 `variant` 选择规则方案。保留默认值 `inline` 时，计时、边界、计分和介绍文本均读取场地 YAML；填写其他名称时，插件会读取 `skywars/variants/<名称>.yml`。首次启动会生成可复制修改的 `default.yml`。

## 权限

插件只在 `/cc` 后的第一级命令上检查权限。管理员权限会自动继承玩家命令。

| 权限 | 可用功能 |
| --- | --- |
| `cc.player` | `/cc spawn`、`vote`、`spectate`、`rank` 等玩家功能 |
| `cc.admin` | 队伍、单局、正式赛事、地图、世界和裁判管理；同时可用玩家功能 |
| `cc.refuge` | 严格观战规则开启时，允许参赛队员以裁判/替补身份观战 |

建议使用权限插件分组：普通选手授予 `cc.player`，赛事管理员授予 `cc.admin`；裁判或替补按需增加 `cc.refuge`。

## 命令约定

- `<参数>` 表示必填参数，`[参数]` 表示可选参数。
- `<队伍>` 使用创建队伍时的内部名称。
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

每名玩家只能属于一支队伍，人数受 `team.max-members` 限制。在线成员直接使用 Bukkit/代理提供的 UUID；离线成员的 UUID 由 `identity.mode` 决定：`OFFLINE` 按 Minecraft 的 `OfflinePlayer:<玩家名>` 规则计算，`PROFILE_UUID` 调用 `identity.profile-api-base-url` 的标准档案接口，并要求返回值与登录链路一致。档案缺失、服务异常、响应格式错误或本地身份冲突会终止该次操作并提示管理员。完整边界见 [player-uuid-contract.md](docs/player-uuid-contract.md)。

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

`/cc game start` 用于手动测试局：它只处理命令指定的队伍（或 `all` 所代表的全部队伍），把成员加入指定实例，并保持其他玩家状态不变。常规正式赛的规则介绍、自动旁观调度和跨轮次观众承接由 `/cc event start` 管理；冠军决赛统一由 `/cc finale` 管理。

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

主大厅世界和 Bingo 三维度不能删除或重命名。删除必须显式附加 `confirm`，且不会破坏仍被 ChampionshipsCore 地图配置引用的世界。重命名未加载世界时需给出其原环境；世界名只允许字母、数字、下划线和连字符；`create` 默认使用 `normal` 环境。普通小游戏世界使用虚空生成器，Bingo 的 `bingo`、`bingo_nether` 和 `bingo_the_end` 三个世界则使用原版地形。

`world delete` 的世界名 Tab 补全会展示所有已加载或已存储世界，包含受保护世界；实际执行删除时仍会拒绝主大厅、Bingo 三维度以及被地图配置引用的世界。

## 地图编辑与发布

地图编辑统一从游戏内向导进入：

```text
/cc map edit <游戏>
/cc map rename <游戏> <旧场地名> <新场地名>
```

`map edit` 会打开该游戏的地图列表。“新建地图”只建立尚未绑定世界的草稿，不会创建世界。之后可用 `/cc admin world create` 创建或加载世界，站在目标世界中通过向导的“绑定当前世界”步骤进行绑定；重复执行该步骤可更换绑定。已有地图可左键编辑，右键两次删除地图配置；删除地图仅移除配置关联，物理世界删除由 `/cc admin world delete` 独立处理。

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

1. 准备一份比赛就绪的赛事配置，包含固定羊毛色队伍、注册玩家和项目列表，并获取 Core 导入链接。
2. 执行 `/cc event teams import <赛事配置链接> --confirm`，原子结束上一届积分并整体替换正式队伍。
3. `/cc admin vote start` 开放下一项目投票；玩家使用 `/cc vote` 投票，随后 `/cc admin vote end` 公布结果。
4. 管理员执行 `/cc event start <游戏>` 启动常规正式赛程；只能启动当前导入赛事游戏列表中的项目。冠军决赛仍使用 `/cc finale <游戏> start <场地>`。
5. 调度器广播项目介绍和积分规则，进行 10 秒倒计时；游戏结束事件触发下一小轮，小轮之间默认等待 30 秒。
6. 全部小轮结束后，调度器广播本项目积分和总榜，并将观众移出场地。
7. 全部常规项目结算且无正式赛程运行后，执行 `/cc event export`。JSON 会写入 `plugins/ChampionshipsCore/exports/<赛事标识>-results.json`，供成绩发布流程读取。

## 正式赛事命令

| 命令 | 行为 |
| --- | --- |
| `/cc event teams import <赛事配置链接> --confirm` | 校验并导入比赛就绪的赛事、固定羊毛色队伍和注册玩家；旧有效积分失效、旧轮次和队伍在同一数据库事务内清除 |
| `/cc event start <游戏>` | 启动普通正式赛程；同一项目运行中再次执行会紧急停止 |
| `/cc event export` | 无正式赛程运行时，把当前赛事的团队/个人总分和逐游戏积分导出到 `plugins/ChampionshipsCore/exports/<赛事标识>-results.json` |
| `/cc finale dragoneggcarnival start <场地> [队伍1 队伍2]` | 在指定场地启动龙蛋狂欢决赛；未指定队伍时按总榜选择前二 |
| `/cc finale dodgebolt start <场地> [队伍1 队伍2] [--force]` | 在指定场地启动躲避箭决赛；未指定队伍时按总榜选择前二，`--force` 允许使用在线子阵容 |
| `/cc finale <游戏> cancel` | 取消决赛准备，或强制结束正在进行的正式决赛 |
| `/cc event stop <游戏>` | 显式停止该项目的赛程任务和运行实例 |
| `/cc event reset --confirm` | 重置正式比赛轮次和游戏顺序 |
| `/cc event undo --confirm` | 停止并撤销最近一轮正式比赛及其成绩 |

正式赛程支持除匹配赛建外的全部游戏。匹配赛建使用 `/cc game start buildmart all <场地>` 启动单局。斗战方框与跑酷追击需要偶数支队伍和足够的复制实例；去到另一边会依次使用已加载地图；其他游戏按各自赛程管理器选择地图和轮数。

## 投票、积分与观战

投票持续 120 秒，只允许已加入队伍的选手投票。已经记录为完成的游戏不能再次被投票；结束时插件按票数广播排行。

每个场地在比赛中累计玩家积分，结束时写入数据库。`weighted-score.enabled` 开启后，总榜会应用游戏归一化权重；存在已导入的活动赛事时，再叠加该赛事的单游戏积分权重和轮次倍率。没有活动赛事时，轮次倍率回退到 `weighted-score.round-multipliers`。`/cc rank info` 可查看动态归一化权重。

在正式赛事模式中开启 `strict-spectator-rule` 后，正常赛事轮次中的参赛队员不能随意观战；拥有 `cc.refuge` 的裁判或替补不受此限制。DAILY 模式始终跳过该判定。

## 聊天与跨服展示

普通聊天、加入/退出消息、TAB、Sidebar 和原生计分板队伍统一使用 `PlayerPresentation` 的身份格式。Core 与 Worker 都显示队伍颜色和活动选手状态；Worker 会从 manifest 投影本局原生队伍，并在平台不支持队伍变更时降级为插件侧 `/teammsg`、`/tm` 处理。

启用 Redis 后，Core 和 Bingo Worker 的普通聊天会写入同一命名空间的聊天流，再由每个实例各自的 consumer group 投递给本服玩家和控制台。聊天依赖稳定且唯一的 Core `redis.instance-id` 与 Worker `worker-id`；重复 ID 会共享消费进度并造成实例漏收。Redis 不可用时，本服聊天由 Paper/Folia 正常显示，跨服转发暂停。`/teammsg` 始终只面向当前比赛队伍。

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

### 宾果

| Placeholder | 含义 |
| --- | --- |
| `%bingo_current_time%` | 当前玩家所在 Bingo 场地的剩余时间 |
| `%bingo_current_time_[场地]%` | 指定场地的剩余时间 |
| `%bingo_current_tasks_team%` | 当前玩家所属队伍已完成的任务数 |
| `%bingo_current_tasks_team_[场地]%` | 当前玩家所属队伍在指定场地完成的任务数 |
| `%bingo_area_rank_1_[场地]%` 至 `%bingo_area_rank_4_[场地]%` | 指定场地第 1 至 4 名队伍及其分数 |

Bingo 同样支持通用的 `%bingo_area_status_[场地]%` 和 `%bingo_area_timer_[场地]%`。

## 侧栏记分板

ChampionshipsCore 内置基于 FastBoard 的统一侧栏，取代 SternalBoard。显示优先级为参赛/旁观游戏板、地图 prepare 编辑板、管理员地图状态板和赛事大厅板；游戏板按玩家实际归属选择。remote Bingo 的模板由 Core 随比赛 manifest 下发，Worker 复用同一份配置。

所有标题、行文本、颜色、游戏模板和地图覆盖均位于 `scoreboards.yml`。配置沿用 `&`、
`#RRGGBB` 和 `&#RRGGBB` 颜色写法，最多渲染 15 行；`/cc admin reload --confirm` 会原子重载，
配置无效时继续使用上一份有效快照。默认模板保留原 SternalBoard 的赛事和游戏信息，并为
队伍、对手、排行榜及管理员警告增加原生彩色显示。

## 相关文档

- [自由游玩模式](docs/daily-mode.md)
- [Bingo 跨服拆分架构](docs/bingo-remote-architecture.md)
- [Bingo 64 人性能指南](docs/bingo-64-player-performance-report.md)
- [玩家 UUID 边界与演进方案](docs/player-uuid-contract.md)
- [AuthBridge 与 AuthProxy 同步协议](docs/auth-bridge-protocol.md)
- [地图重命名契约](docs/map-rename-contract.md)

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

正式改图前备份整个插件数据目录和数据库；保存地图、重载插件和场地 YAML 编辑安排在空闲窗口执行。

## 许可证

本项目使用 [MIT License](LICENSE)。
