# 启动单局与正式比赛

`/cc game start` 手动启动一局，`/cc event start` 启动游戏原有的正式赛程。两者使用同一套游戏、地图、队伍和子场地选择规则；正式赛会保留选择，后续轮次继续使用相同参赛队伍和指定地图。

```text
/cc game start <游戏> <地图|auto> [all|队伍...] [--arena all|编号]
/cc event start <游戏> [地图|auto] [all|队伍...] [--arena all|编号]
```

地图名称是地图编辑器中的注册名。`auto` 自动选择可用地图；正式赛不指定地图时沿用 `formal-events.<游戏>.maps` 的安排。队伍可以使用名称或 `#ID`，省略队伍或填写 `all` 选择注册队伍；包含空格的地图和队伍名称用引号包住。队伍不能重复，`all` 不能与明确队伍混用，其他正在比赛的队伍不会被自动抢占。

子场地编号从 **1** 开始，1 为主场地，后续编号为副本。`--arena all` 自动分配，`--arena 2` 指定第二个子场地；逗号可以选择多个子场地，如 `--arena 1,3`。编号指向实际地图位置，不随随机分队改变。

以下命令中的 `example_map`、`red`、`blue` 等为示例名称，须先创建对应地图与队伍。

## 双队副本比赛

BattleBox、ParkourTag 和 LaserBox 每个运行副本接收两支队伍。四队会组成两场，六队会组成三场；明确列出的队伍按相邻顺序配对。队伍数量必须为双数，不能丢弃最后一队。未指定编号时选择空闲副本，指定编号时只使用那些副本；数量不足或指定副本正在比赛时整次请求失败。

```text
/cc game start battlebox example_map red blue green yellow
/cc game start parkourtag example_map all
/cc game start laserbox example_map red blue --arena 3
```

各场先共同完成落点预热，再进入准备流程。启动失败时释放本次请求取得的占用，保留无关比赛。

## 一个实例中的多个子场地

TNT Run、烫手鳕鱼、霜冻决斗和雪球大战由一个比赛实例管理多个物理分区。选择编号只改变本局的出生与玩法分配，不建立另一套计时、结算或旁观状态。同一个实例正在比赛时，不能再用另一子场地编号重复开局。

```text
/cc game start tntrun example_map all --arena 1
/cc game start frostbite example_map red blue --arena all
/cc game start snowball example_map all --arena 2,4
/cc event start hotycodydusky example_map red blue --arena all
```

TNT 的方块消失、TNT 雨和边界只使用选中的分区；雪球的出生与重生使用选中的分区；霜冻的队伍击杀和排名继续按整个实例结算。烫手鳕鱼地图包含一个主场地及配置的副本，各区独立持鱼和传鱼，计时、淘汰记录、团队奖励和最后排名由同一个实例负责，原分值与并列规则保留。

烫手鳕鱼每次随机选择持鱼者奖励 10 分，普通攻击传鱼沿用原有不额外加分规则。每次淘汰奖励全实例存活者 15 分，全队淘汰再奖励一次；最终前三档奖励 25、20、15 分，同一淘汰时间并列且后续排名不跳位。分区数量由地图配置决定，编辑器保存模板后生成副本；地图注册名与物理世界名分别管理。

AceRace、ParkourWarrior 等同图并发实例则每次选一个运行副本；指定编号可以选择它，省略编号使用空闲实例。其他没有副本的地图仅有编号 1。

## 最终对决

Dodgebolt、龙蛋狂欢和硫方足球都可以通过统一的 `game start` 指定地图和两支队伍直接开局。最终对决恰好需要两队；多队联赛配对不用于冠军决赛。

```text
/cc game start dodgebolt example_map red blue
/cc game start dragoneggcarnival example_map red blue
/cc game start sulfursoccer example_map red blue
/cc event start sulfursoccer example_map red blue
```

正式决赛省略队伍时沿用总榜前二选择。手动局保持其他玩家的状态，正式赛负责规则介绍、观众调度和跨轮次承接。躲避箭的暂停、重开、强制胜负等裁判操作仍使用 `/cc finale dodgebolt ...`。

再次执行正在运行游戏的 `/cc event start` 保持原有紧急停止行为；明确停止也可用 `/cc event stop <游戏>`。地图必须已发布、配置完整且未被编辑会话锁定。
