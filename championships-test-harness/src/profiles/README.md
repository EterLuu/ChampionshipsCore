# Action profiles

Profile 只描述协议客户端发送的动作，不复制 Core 或 Worker 的游戏规则。它可以读取聊天、窗口、实体和位置包，再通过 Harness 接口发送移动、视角、交互、攻击或重连动作。

当前 profile：

- `idle`：完成连接、AuthMe 对话和保持在线，适合 resident 模式；
- `look`：周期发送视角包，检查动作包和实体线程，不改变位置；
- `bingo-walk`：旧脚本兼容 profile，在受限路径发送带 `onGround` 标志的移动包。

某个游戏需要动作验证时，在这里增加对应 profile，并在 [无人测试游戏矩阵](../../../docs/game-test-matrix.md) 登记地图、动作和结果。Profile 只产生客户端输入，比赛规则、胜负和计分仍由 Core 或 Worker 执行。
