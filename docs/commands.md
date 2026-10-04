# 命令参考

命令由服务端控制台或具有相应权限的玩家执行。`<参数>` 为必填，`[参数]` 为可选；示例中的队伍、玩家和地图均需先创建。

## 目录

- [权限](#权限)
- [命令约定](#命令约定)
- [玩家命令](#玩家与通用命令)
- [队伍与成员](#队伍与成员管理)
- [管理员操作](#管理员操作)
- [比赛流程](#比赛流程)
- [正式赛事命令](#正式赛事命令)

## 权限

命令按声明的权限检查访问。拥有 `cc.admin` 的管理员也可使用玩家命令。

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

`/cc vote <配置名称>` 使用 [游戏指南](games.md) 的“配置名称”一列中的值，匹配不区分大小写；`DragonEggCarnival`、`Dodgebolt` 与 `SulfurSoccer` 不参与投票。

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

每名玩家只能属于一支队伍，人数受 `team.max-members` 限制。在线成员直接使用 Bukkit/代理提供的 UUID；离线成员的 UUID 由 `identity.mode` 决定：`OFFLINE` 按 Minecraft 的 `OfflinePlayer:<玩家名>` 规则计算，`PROFILE_UUID` 调用 `identity.profile-api-base-url` 的标准档案接口，并要求返回值与登录链路一致。档案缺失、服务异常、响应格式错误或本地身份冲突会终止该次操作并提示管理员。完整边界见 [player-uuid-contract.md](player-uuid-contract.md)。

### 查询、传送和删除

| 命令 | 说明 |
| --- | --- |
| `/cc team info <队伍名>` | 查看队伍成员 |
| `/cc team tphere <队伍名>` | 将指定队伍传送到命令执行者的位置 |
| `/cc team tphere all` | 将全部队伍传送到命令执行者的位置 |
| `/cc team delete <队伍名>` | 删除队伍及成员关系 |

正在参加游戏的队伍不能删除。正式建队后建议依次执行 `team info`，并让所有成员上线确认队伍颜色、聊天频道和计分板状态。

## 管理员操作

### 比赛启动

所有游戏使用统一格式：

```text
/cc game start <游戏> <地图|auto> [all|队伍...] [--arena all|编号]
/cc event start <游戏> [地图|auto] [all|队伍...] [--arena all|编号]
```

手动局可以指定地图和参赛队伍，包括躲避箭、龙蛋狂欢及硫方足球。BattleBox、ParkourTag、LaserBox 自动把双数队伍按相邻顺序配对到空闲副本；副本不足或队伍不可用时整次请求失败。子场地编号从 1 开始，TNT、烫手鳕鱼、霜冻和雪球可选择单一区或全部分区，仍按一个实例计时与结算。

正式赛使用同样的选择参数，选择贯穿后续轮次；省略参数时使用配置的地图安排和注册队伍。手动局不额外把管理员加入名单，也不调整其他玩家；正式赛管理规则、观众和赛程。完整示例与分区语义见 [比赛启动指南](game-start.md)。

### 管理员命令

| 命令 | 说明 |
| --- | --- |
| `/cc game stop <游戏> <场地> <实例> --confirm` | 精确结束运行实例；已开赛则正常结算 |

| 命令 | 说明 |
| --- | --- |
| `/cc admin reload --confirm` | 重载配置；基础设施变更可能需要重启 |
| `/cc admin set-max-player <数量>` | 修改最大玩家数 |
| `/cc admin mute <玩家> [原因...]` | 永久禁言玩家的公共聊天 |
| `/cc admin tempmute <玩家> <时长> [原因...]` | 限时禁言公共聊天，例如 `30m`、`2h`、`1d`、`1h30m` |
| `/cc admin unmute <玩家>` | 解除公共聊天禁言 |
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

禁言命令需要 `cc.admin`，支持控制台和离线玩家。时长支持 `s/m/h/d/w` 及组合写法，到期自动恢复公共聊天；后一次禁言覆盖先前记录。记录按 UUID 原子保存到 `mutes.yml`，重启与配置重载后继续生效，离线期间仍计时。Core 会拦截本服公共聊天以及收到的该玩家跨服公共聊天；`/teammsg` 队伍聊天不受影响。

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
4. 管理员执行 `/cc event start <游戏>` 启动正式赛程；未导入赛事时直接使用服务器现有队伍，已导入赛事时只能启动该赛事游戏列表中的项目，已归档赛事需先导入下一届。Tab 补全会列出全部已启用且支持赛事的游戏，无需先导入赛事。`riptide`、`frostbite` 和 `laserbox` 均支持此入口；`sulfursoccer` 等决赛游戏沿用总榜前二的决赛流程，也可使用 `/cc finale <游戏> start <场地>` 指定场地。
5. 调度器广播项目介绍和积分规则，进行 10 秒倒计时；游戏结束事件触发下一小轮，小轮之间默认等待 30 秒。
6. 全部小轮结束后，调度器广播本项目积分和总榜，并将观众移出场地。
7. 全部项目结算且无正式赛程运行后，执行 `/cc event export [冠军队伍]`。JSON 写入 `plugins/ChampionshipsCore/exports/<赛事标识>-results.json`，包含赛事 ID、固定游戏键、版本、玩家 UUID 和已加权积分，在兼容的外部赛事平台上传发布。未填写冠军时，上传前必须在网页选择最终冠军，不能把积分第一自动当作冠军。

## 正式赛事命令

| 命令 | 行为 |
| --- | --- |
| `/cc event teams import <赛事配置链接> --confirm` | 校验并导入比赛就绪的赛事、固定羊毛色队伍和注册玩家；旧有效积分失效、旧轮次和队伍在同一数据库事务内清除 |
| `/cc event start <游戏> [地图\|auto] [all\|队伍...] [--arena all\|编号]` | 启动正式赛程；同一项目运行中再次执行会紧急停止 |
| `/cc event export [冠军队伍]` | 导出当前赛事的团队/个人积分、UUID 与游戏版本；可指定最终冠军。文件位于 `plugins/ChampionshipsCore/exports/<赛事标识>-results.json` |
| `/cc finale dragoneggcarnival start <场地> [队伍1 队伍2]` | 在指定场地启动龙蛋狂欢决赛；未指定队伍时按总榜选择前二 |
| `/cc finale dodgebolt start <场地> [队伍1 队伍2] [--force]` | 在指定场地启动躲避箭决赛；未指定队伍时按总榜选择前二，`--force` 允许使用在线子阵容 |
| `/cc finale sulfursoccer start <场地> [队伍1 队伍2]` | 启动硫方足球决赛；未指定队伍时按总榜选择前二 |
| `/cc finale <游戏> cancel` | 取消决赛准备，或强制结束正在进行的正式决赛 |
| `/cc event stop <游戏>` | 显式停止该项目的赛程任务和运行实例 |
| `/cc event reset --confirm` | 重置正式比赛轮次和游戏顺序 |
| `/cc event undo --confirm` | 停止并撤销最近一轮正式比赛及其成绩 |

正式赛程支持全部已启用游戏。斗战方框、跑酷追击和激光方盒需要偶数支队伍和足够的场地副本；激光方盒同轮需要 `队伍数 / 2` 个空闲副本，最多九轮。三种 1v1 匹配项目的前半程使用随机后的循环赛抽签，轮数为总轮数向上取整的一半；从下一轮开始，每轮结束后按截至上一轮的累计胜负（胜 1 分、平 0.5 分）重新生成强强、弱弱配对，并通过历史对手记录避免重复交手。去到另一边会依次使用已加载地图；其他游戏按各自赛程管理器选择地图和轮数。
