# 无人测试与现有功能的边界

无人测试通过显式的测试会话和运行模式接入 Core。它为测试身份、临时队伍、比赛实例和远程 Worker 建立独立的所有权；正式玩家、正式队伍和正式赛程仍由原有入口管理。

## 没有测试会话时

Core 的正常入口继续使用 `GAME`、正式 `EVENT` 和 `DAILY`：

- `/cc game start`、`/cc event start` 解析正式队伍和地图配置；
- `PlayerManager` 按当前登录链路处理身份和数据库记录；
- 正式 Rank、队伍、Redis、赛程和赛事导出沿用原有所有权；
- AuthProxy、AuthBridge、AuthMe 和普通玩家的 UUID 检查不经过测试 lease；
- 测试关闭时，`cc.test` 命令只返回配置提示，不改变普通功能。

因此，测试代码的存在不会把普通玩家转换为测试身份，也不会把正式比赛转换为 Test Event。

## 测试会话拥有的资源

会话创建后，`TestSessionManager` 记录 session UUID、generation、运行模式、回合计划、64 个 roster 席位、Bot UUID、开发者替换席位、transient team 和已启动实例。每个延迟回调都带有 generation，停止、重载、插件卸载或下一场会让旧回调失效。

测试身份通过签名 lease 进入 Core。lease 绑定 server ID、session/generation、用户名、期望 UUID、过期时间和 nonce；Core 仍以 Paper 实际观察到的 UUID 为登录权威。AuthMe 登录成功后，身份才会进入测试会话的 ephemeral 状态。历史 reservation 只用于阻止旧名称或旧 UUID 回退为普通账号，不会代替新的 lease。

测试队伍使用 transient team。它可以让现有游戏通过 UUID 找到队伍和队色，但不会写入正式队伍关系。测试结束先恢复游戏实例和玩家，再撤销 lease、移除临时队伍、清理 ephemeral 身份。

## 运行模式边界

`TEST` 和 `TEST_EVENT` 通过 `GameExecutionContext` 区分玩法流程与正式赛程所有权：

- 游戏仍执行真实准备、倒计时、玩法、结算、事件和地图恢复；
- Test Event 由测试会话按 profile 推进轮次，不调用正式 Schedule 的全局轮次和观众释放；
- 测试上下文允许写入结果、Rank、Redis、事件和远程 match，便于验证内容、顺序和幂等性；
- `stop --confirm` 使用统一恢复入口，远程 Bingo 要等待 Worker `RELEASED` 后才释放世界占用；
- 远程协议 v10 的 Core、Worker、AuthBridge 和 AuthProxy 需要配套版本。

## 身份与认证边界

真实 cc-web 流程由 cc-web 管理 Bot 账号、密码、profile UUID 和允许 CIDR。Harness 只取得账号密码；AuthProxy 注入 profile，Paper、AuthBridge 和 Core 读取服务器实际 UUID。离线 UUID lease 只属于隔离夹具，不是生产账号管理方式。

AuthProxy 和 AuthBridge 都执行账号状态、CIDR、封禁/维护状态和 UUID 校验。过期 lease、nonce 重放、session 不匹配、UUID mismatch 或 AuthMe 未确认时，连接直接结束。认证失败不会落入普通玩家路径。

## 配置与部署边界

Core、Worker 的 `test.enabled` 默认都是 `false`。允许生产与调试共用时，可以在现有 Core、Worker、Bungee、AuthMe、MariaDB、Redis、世界和 cc-web 上显式开启；开关只开启测试入口，不替代资源排班、RCON 网络限制、Bot 专用身份或 Worker 的单自然世界 slot。

涉及 common 协议、共享展示、AuthBridge/AuthProxy 或 Worker 释放的版本时，Core、Worker 和认证组件需要配套构建和重启。只修改 Markdown、测试夹具或 Harness 不需要替换生产 JAR。Harness 仍不安装进生产插件目录，但可以从受控内部网络连接开启调试入口的生产 Core；不得公开 RCON 或把普通账号当作测试身份。

## 运维检查

每次原生运行结束后检查：

1. Harness 报告包含完整 64 名 Bot，`activeAtFinish`、退出码和错误统计符合预期；
2. Core `status` 显示会话状态和 16 个临时队伍，结束后回到无活动会话；
3. 测试数据库和 Redis 中的写入与预期结果一致，测试 reservation、release journal 和世界状态按流程清理；
4. 下一场测试可以重新建立，旧 session、旧回调和旧 lease 不会影响新名册；
5. 生产调试模式下确认测试写入和正式数据域是预期行为，并确认正式赛程、正式玩家和 Worker 世界 slot 没有资源冲突。

相关命令见 [命令参考](commands.md)，身份约束见 [UUID 契约](player-uuid-contract.md)，旁观和队伍投影见 [旁观契约](spectator-visibility-contract.md)。
