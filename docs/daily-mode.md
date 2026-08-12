# 自由游玩模式

自由游玩是正式赛事系统之外的一层大厅编排。它复用原有游戏实例和地图配置，但队列、同行小队、临时队伍、比赛结果与纪录均使用独立生命周期；不会写入正式赛事的积分、轮次或赛程表。代码和配置中的稳定标识仍使用 `DAILY`/`daily`。

## 模式与命令

- `/cc switch daily`：将大厅切换到自由游玩；已有对局不会被中断。
- `/cc switch championship`：停止接收自由匹配并清空等待队列；已开始的对局继续结算。
- `/cc event ...` 与 `/cc game start ...`：两个服务器模式下都保留，运行模式仍分别是 `EVENT` 与 `GAME`。
- `/cc play`：打开游戏选择菜单。目前适配 Bingo、AceRace 和龙蛋狂欢。
- `/cc play leave`：退出等待或正在进行的游戏；同行小队会作为整体退出，场内无人后立即结束实例。远程 Bingo 由 Worker 接收相同命令并通知 Core 完成全队退出。
- `/cc play leaderboard`：打开榜单分类与详细排名菜单。
- `/cc daily leave`、`/cc daily stats [游戏]`：兼容入口，用于退出游玩、查看独立统计。
- `/cc party invite|accept|leave|disband|info`：管理内存同行小队。创建者默认是队长；所有成员都能通过菜单改变全队游戏选择。

同行小队不写入数据库。任何成员改变游戏时，整队会原子迁移队列；成员离线会暂停全队排队，所有成员离线时自动解散。进行对局期间不允许改变队伍结构。

游戏选择与排行榜均使用 54 格信息菜单，视觉层次与 `/cc vote`、`/cc spectate` 一致：上下边框、顶部概览、居中选择卡与底部操作栏。游戏卡实时展示队列进度、开场倒计时、人数缺口、分队容量、地图和个人成绩；榜单卡展示榜首、前三名、个人名次及按地图划分的计时纪录，并支持分类和详细榜单分页。

## 配置与扩展点

`config.yml` 的 `daily.enabled-games` 是正式比赛游戏列表的日常子集。每个适配器从 `daily.games.<game>` 读取最小人数、最大人数、队伍容量、队伍数量和倒计时；AceRace 还读取 `concurrent-instances`。龙蛋狂欢固定分为两队，每张已发布且空闲的末地地图提供一个运行实例。新增游戏应实现 `DailyGameAdapter`，使用 `DailyRules` 描述队列容量，并由 `DailyManager` 注册。

分配器把同行小队视为不可拆分单元：较大的小队优先落位，个人玩家随机补足未满队伍；全部为个人玩家时同样随机分队。分配完成后才创建 `TeamManager` 的临时颜色队伍（红队、绿队、蓝队等），因此不会污染正式队伍缓存和数据库。

AceRace 的所有地图位于同一个 `acerace` 世界并以赛道边界隔离；同一地图的并发实例共享地图几何。`PlayerIsolationService` 以日常 match ID 隔离玩家可见性，观战者会附着到所观战的实例；离开实例后恢复大厅可见性。

## 数据与 PAPI

独立数据表结构统一维护在 `resources/database/schema.sql`，业务由 `DailyStatsManager` 管理，所有 JDBC 访问通过 `DailyStatsDao`/`DailyStatsDaoImpl` 完成。数据表为：

- `daily_player_stats`：按玩家和游戏聚合场次、胜场；Bingo 额外保存连线数、完成任务总数和单场最多完成数，不保存任何累计积分。
- `daily_match_results`：每场每位玩家的独立战绩，包含该场积分及 Bingo 进度，`matchId + uuid` 保证幂等。
- `daily_player_records`：按游戏、地图、地图版本、规则版本和纪录类型存储最佳耗时。

Bingo 记录胜场、连线数、完成任务总数和单场最多完成数；AceRace 按地图记录最快单圈和最快完整三圈；龙蛋狂欢按率先完成两项末地进度的队伍记录胜负。单局积分只随该场战绩写入 `daily_match_results`，不会累加、继承或进入大厅展示；自由游玩也不会调用正式积分/轮次持久化。

自由游玩占位符统一使用 `%cc_daily_*%`，包括 `mode`、`party_leader`、`party_size`、`selected_game`、`queue_state`、`queue_players`、`countdown`、`active_game`、`active_map`、`match_id`、`games`、`wins`。旧的 `points`、`best` 为兼容保留但固定返回 `0`。这些占位符只读取快照或缓存，离线/空上下文返回稳定默认值，不在 PAPI 回调线程访问数据库。

DAILY 下正式队伍占位符 `player_team_name*`、`player_team_color*` 返回空文本，`player_team_points`/`player_team_rank` 返回 `0`。TAB 应使用 `%cc_tab_prefix%`、`%cc_tab_name_color%` 与 `%cc_tab_footer_status%`：大厅玩家使用白色名字，进入游戏后才使用当局队色；聊天和加入/离开提示复用相同的 `[前缀] 玩家名` 身份格式。匹配产生的临时队伍按颜色命名为“红队、绿队、蓝队”等。

排行榜占位符格式为 `%cc_daily_lb_<榜单>_<名次>%`，并可追加 `_name` 或 `_value` 只取玩家名/数值。例如：`wins`、`bingo_wins`、`bingo_lines`、`bingo_completed_tasks`、`bingo_max_completed`、`acerace_fastest_lap_<地图>`、`acerace_fastest_three_laps_<地图>`。不存在累计积分榜。地图名会转为小写下划线标识。FancyHolograms 的现场配置已预置三块榜单，位置可在重启前后手动调整。

大厅侧边栏使用 `scoreboards.yml` 的 `daily-lobby`。旧运行目录尚无该节点时会使用代码内置的安全模板，避免必须覆盖现场配置。
