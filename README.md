# ChampionshipsCore

ChampionshipsCore 是用于 Minecraft 团队锦标赛的 Paper 插件，统一管理队伍、地图、赛程、计分和观战。它支持管理员手动开局、正式多轮赛事与自助自由游玩，并可将 Bingo 交给独立 Folia Worker 执行。

## 目录

- [主要功能](#主要功能)
- [运行要求](#运行要求)
- [快速开始](#快速开始)
- [构建与安装](#构建与安装)
- [配置](#配置)
- [文档](#文档)
- [参与开发与许可证](#参与开发与许可证)

## 主要功能

- 17 个游戏项目，包含 Bingo、BuildMart、跑酷、团队对抗和最终决赛；完整列表见 [游戏指南](docs/games.md)。
- 统一的地图编辑、校验和发布流程，按玩法支持场地副本或单实例分区。
- 队伍管理、投票、多轮赛程、数据库积分和排行榜。
- 统一观战保护、玩家实体显隐、完整 Tab 列表和可配置侧栏。
- `DAILY` 自由游玩：匹配队列、同行小队和独立战绩。
- 可选的 Redis 跨服同步、远程 Bingo 与身份认证连接器。

## 运行要求

| 组件 | 要求 |
| --- | --- |
| Java | JDK 25（构建）；Java 25（运行） |
| Core 服务端 | Paper 26.2，项目编译 API 为 `26.2.build.112-stable` |
| 数据库 | MariaDB 或 MySQL；需预先创建数据库和可读写、建表的账号 |
| 必需插件 | PacketEvents 2.13.0、ProtocolLib 5.4.0、PlaceholderAPI 2.12.2、FastAsyncWorldEdit 2.15.0 |
| 构建工具 | Maven；依赖版本与仓库地址由根目录和各模块的 `pom.xml` 定义 |

以上插件版本来自当前构建依赖。Core 的插件描述未声明 Folia 支持；远程 Bingo 使用单独的 [Folia Worker](championships-bingo-worker/README.md)。单服 `LOCAL` 模式无需 Redis、代理或身份平台。

## 快速开始

以下步骤从单服 Core 开始。仓库提供配置模板，需要自行准备游戏地图并通过编辑器发布。

1. 准备符合上述要求的 Paper 服务端和数据库。以数据库管理员身份执行以下 SQL，将示例密码替换为实际密码；示例适用于数据库与 Core 在同一主机的部署：

   ```sql
   CREATE DATABASE championships CHARACTER SET utf8mb4;
   CREATE USER 'championships'@'localhost' IDENTIFIED BY 'CHANGE_ME';
   GRANT ALL PRIVILEGES ON championships.* TO 'championships'@'localhost';
   ```

2. 从仓库根目录构建 Core 及依赖：

   ```bash
   mvn -B -ntp -pl championships-core -am clean package
   ```

3. 将 `target/ChampionshipsCore-1.3-SNAPSHOT.jar` 与四个必需插件放入服务端 `plugins/`，启动一次生成配置，再停止服务端。
4. 编辑 `plugins/ChampionshipsCore/config.yml`，填写数据库凭据、身份模式和大厅位置。下方配置是需要合并到生成文件中的示例，保留其他生成项：

   ```yaml
   database:
     type: MARIADB
     address: 127.0.0.1
     port: 3306
     name: championships
     username: championships
     password: CHANGE_ME
   identity:
     mode: OFFLINE
   bingo:
     execution-mode: LOCAL
   lobby:
     location:
       world: world
       x: 0.5
       y: 80.0
       z: 0.5
       yaw: 0.0
       pitch: 0.0
   ```

   大厅世界必须已加载，坐标应是实际安全落点。`OFFLINE` 仅用于离线登录链路；正版服应使用 `PROFILE_UUID` 并设置 `identity.profile-api-base-url: https://api.mojang.com`。代理或自建档案服务须遵守 [UUID 契约](docs/player-uuid-contract.md)。

5. 再次启动，确认没有依赖或数据库连接错误。数据库连接成功后插件自动创建表。拥有 `cc.admin` 权限的玩家执行 `/cc team`，能打开队伍管理菜单即可检查基础加载；普通玩家需授予 `cc.player`。
6. 创建队伍并添加已上线的玩家，执行 `/cc map edit <游戏>`，按 [地图编辑指南](docs/map-editing.md) 绑定世界、设置必需步骤、校验并发布。
7. 退出编辑后，用 `/cc game start <游戏> <地图> <队伍...>` 测试一局，通过 `/cc rank playerboard` 和 `/cc rank teamboard` 检查结果。选择语义见 [比赛启动指南](docs/game-start.md)。

## 构建与安装

完整构建与测试从仓库根目录执行，与 CI 使用相同的 Maven 生命周期：

```bash
mvn -B clean package
```

主要可部署产物如下，版本后缀以当前 POM 为准：

| 插件 | 产物 | 安装位置 |
| --- | --- | --- |
| Core | `target/ChampionshipsCore-1.3-SNAPSHOT.jar` | Paper 的 `plugins/` |
| Bingo Worker（可选） | `championships-bingo-worker/target/championships-bingo-worker-1.3-SNAPSHOT.jar` | 独立 Folia 的 `plugins/` |
| AuthBridge（可选） | `championships-auth-bridge/target/championships-auth-bridge-1.3-SNAPSHOT.jar` | 带 Core 和 AuthMe 的 Paper `plugins/` |
| AuthProxy（可选） | `championships-auth-proxy/target/championships-auth-proxy-1.3-SNAPSHOT.jar` | BungeeCord 的 `plugins/` |

内部 common、engine、platform 与 Redis 模块会打包进对应插件，无需单独安装。LoadTest 仅用于可丢弃的测试环境，见 [压测插件说明](championships-bingo-loadtest/README.md)。

升级时停止相关服务器并替换对应 JAR，再重启以加载新类。共享协议或玩家可见行为改变时，Core 与 Worker 应使用配套构建。配置升级按资源模板核对，保留已有自定义值。

## 配置

Core 的配置位于 `plugins/ChampionshipsCore/`：

| 文件或键 | 用途 |
| --- | --- |
| `config.yml` | 数据库、身份、游戏开关、队伍、大厅、赛事和跨服选项 |
| `enabled-games` | 使用 [游戏指南](docs/games.md)中的配置名称；空列表关闭所有游戏 |
| `formal-events.<游戏>.maps` | 正式赛地图注册名与顺序；空列表从已注册地图选择 |
| `mode` / `daily.*` | `CHAMPIONSHIP` 或 `DAILY` 大厅及自由匹配设置 |
| `weighted-score.*` | 游戏归一化权重和按轮次配置的倍率 |
| `message.yml` / `schedule-message.yml` / `gui.yml` | 消息、赛程文案和菜单 |
| `scoreboards.yml` | 大厅、游戏、地图编辑与状态侧栏 |
| 各游戏目录 | 地图定义、蓝图、任务及其他游戏专属资源 |

配置变更可在空闲时执行 `/cc admin reload --confirm`；数据库连接等需要重启的变更由命令提示。JAR 更新必须重启。游戏世界、选区和地图资源配置见 [地图编辑指南](docs/map-editing.md)。

远程 Bingo 还需 Redis、代理路由、独立 Folia 世界与外部重置管理流程。先阅读 [跨服架构](docs/bingo-remote-architecture.md) 和 [Worker 安装说明](championships-bingo-worker/README.md)，再切换 `bingo.execution-mode`。

## 文档

[文档索引](docs/README.md) 按部署、游戏管理和开发契约组织完整说明。常用入口：

- [游戏与玩法](docs/games.md)、[命令参考](docs/commands.md)、[比赛启动](docs/game-start.md)
- [地图编辑与发布](docs/map-editing.md)、[自由游玩](docs/daily-mode.md)
- [积分、观战与展示](docs/presentation.md)
- [Bingo 跨服架构](docs/bingo-remote-architecture.md)、[容量验证](docs/bingo-64-player-performance-report.md)
- [开发指南](docs/development.md)、[旁观契约](docs/spectator-visibility-contract.md)

## 参与开发与许可证

贡献前阅读 [开发指南](docs/development.md)。提交时说明问题、影响模块和验证结果；共享功能需要同时检查 Core 与 Worker。

项目使用 [MIT License](LICENSE)。Bingo 任务机制与图像资源参考上游 [MineBingo](https://gitee.com/chancelethay/minebingo)；任务图像的来源记录见 [SOURCES.md](championships-core/src/main/resources/bingo/taskimages/SOURCES.md)，Worker 内嵌代码的许可见 [第三方声明](championships-bingo-worker/THIRD-PARTY-NOTICES.md)。
