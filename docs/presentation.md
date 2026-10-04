# 积分、观战与展示

投票持续 120 秒，只允许已加入队伍且没有管理员权限的选手投票。游戏须启用、有可开赛的已发布地图、尚未完成且不是决赛项目；结束时广播票数排行。

每个场地在比赛中累计玩家积分，结束时写入数据库。`weighted-score.enabled` 开启后，总榜会应用游戏归一化权重；存在已导入的活动赛事时，再叠加该赛事的单游戏积分权重和轮次倍率。没有活动赛事时，轮次倍率回退到 `weighted-score.round-multipliers`。`/cc rank info` 可查看动态归一化权重。

在正式赛事模式中开启 `strict-spectator-rule` 后，正常赛事轮次中的参赛队员不能随意观战；拥有 `cc.refuge` 的裁判或替补不受此限制。DAILY 模式始终跳过该判定。

## 聊天与跨服展示

Core 与 Worker 的比赛聊天使用 `玩家名 <队名> » 消息` 格式，日常聊天保留 `[标签] 玩家名 » 消息` 格式；加入/退出消息和 TAB 使用 `[标签] 玩家名` 格式。队伍颜色、活动选手状态和聊天模式由共享的 `PlayerPresentation` 提供，跨服聊天也保留发送方的模式。拥有 `cc.admin` 或 `cc.refuge` 的玩家可在公共聊天中使用 `&c` 颜色、`&l` 加粗、`&n` 下划线等格式代码，也支持十六进制颜色，跨服转发会保留这些格式。Worker 会从 manifest 投影本局原生队伍，并在平台不支持队伍变更时降级为插件侧 `/teammsg`、`/tm` 处理。

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
配置无效时继续使用上一份有效快照。默认模板展示赛事和游戏信息，并为
队伍、对手、排行榜及管理员警告增加原生彩色显示。
