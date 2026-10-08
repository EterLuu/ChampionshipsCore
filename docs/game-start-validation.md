# 比赛启动与分区验证

启动命令用法见 [比赛启动指南](game-start.md)，接入职责见 [开发指南](development.md)。本矩阵用于检查统一参数解析、占用、分区和赛程生命周期。

## 检查矩阵

| 范围 | 检查内容 |
| --- | --- |
| 全部游戏 | `GameStartArguments` / `GameStartRules` / `GameStartService` 解析指定地图、队伍、GAME 模式与顺序 |
| 最终对决 | Dodgebolt、DragonEggCarnival、SulfurSoccer 恰好两队；正式决赛默认前二，手动指定保留 |
| 双队副本 | BattleBox、ParkourTag、LaserBox 支持双数队伍批量配对，拒绝重复/奇数/容量不足 |
| 启动失败 | 只释放本次占用，不结束无关比赛；共同预热后进入准备 |
| 单实例分区 | TNT、烫手鳕鱼、霜冻、激光乱斗使用稳定物理编号，一个计时和积分所有者 |
| 跨轮次 | `EventStartSelection` 保留地图/队伍，结束或中止清除 |
| 烫手鳕鱼 | 各物理区独立持鱼/传鱼，全实例结算生存和团队奖励，保持并列规则 |
| 区块票据 | 重叠副本引用计数，最后使用者释放，旧预热失效 |
| 延迟回调 | reset/dispose 后旧恢复/持鱼/补给/重生不能进入新局 |
| 远程 Bingo | 显式 roster 进入 manifest，两端协议和 ownership 保持一致 |

## 原生验证

在支持的 Paper 上创建隔离地图与多支测试队伍，分别启动单区、部分区、全部分区、双队批量和决赛。验证出生位置、实例数量、计时任务、区块票据、传鱼/碰撞边界与结算。

使用真实协议连接检查完整 Tab 和模式呈现；按 [旁观验证](spectator-visibility-validation.md) 安装完整共享呈现服务，避免简化探针遗漏包处理。

配置迁移按源地图分别核对边界、朝向、规则、自定义值和发布状态。分区数量和位置依照该游戏的配置与固定约束校验，不能从其他地图推定。历史积分的地图名称只在明确改名事务中迁移。

## 回归命令

```bash
mvn -B -ntp -pl championships-core,championships-bingo-worker -am clean package
scripts/format-java.sh --check
python3 scripts/check-source-layout.py
```

记录各游戏实际检查结果、版本、配置和失败原因。构建成功、隔离验证通过和正式比赛验收是不同证据，不用旧日志替代当前验证。
