# 可选认证组件部署

AuthBridge 在 Paper 上同步 AuthMe 账号、BCrypt 密码和身份控制任务；AuthProxy 在 BungeeCord 上决定准入并注入登录 UUID。两者需要实现 [同步协议](auth-bridge-protocol.md) 的外部身份平台，单服 Core 不依赖它们。

## 要求与构建

- JDK / Java 25。
- AuthBridge：Paper 26.2、ChampionshipsCore 与 AuthMe；项目编译依赖 AuthMe 5.7.0。
- AuthProxy：BungeeCord，项目编译 API 为 `26.1-R0.1-SNAPSHOT`。本模块不是 Velocity 插件。
- 可访问的兼容身份 API、独立 keyId 和至少 32 字节的 HMAC 密钥。

从仓库根目录构建：

```bash
mvn -B -ntp -pl championships-auth-bridge,championships-auth-proxy -am clean package
```

把各模块 `target/` 中的插件 JAR 分别安装到 Paper 与 BungeeCord 的 `plugins/`，启动生成配置后停止服务端，再填写配置。

## 配置

两端生成配置都包含以下字段。示例域名和密钥需要替换；此片段合并到生成文件中，保留各插件其他字段：

```yaml
api:
  base-url: https://identity.example.com
  key-id: example-client
  hmac-secret: REPLACE_WITH_A_RANDOM_SECRET_OF_AT_LEAST_32_BYTES
  allow-insecure-private-http: false
  poll-seconds: 10
  connect-timeout-seconds: 5
  request-timeout-seconds: 10
```

Bridge 使用 writer key，Proxy 使用独立 reader key。HTTPS 可用于远程接口；非回环 HTTP 只有明确启用 `allow-insecure-private-http` 才被接受。替换生成的 `messages.*` 中账号绑定地址与提示文案，避免沿用模板中的示例或部署地址。

AuthBridge 的 `access.admission-owner`：

| 值 | 适用场景 |
| --- | --- |
| `PROXY` | AuthProxy 在代理前端决定准入；Bridge 负责 AuthMe 同步、维护锁和实际 UUID 校验 |
| `BRIDGE` | 无代理准入时由 Bridge 拒绝未绑定、撤销或封禁玩家 |

AuthProxy 使用 `offline-cache.enabled` 和 `offline-cache.max-stale-hours` 控制已同步档案的故障回退。未知玩家仍被拒绝；鉴权错误和非法响应不会触发离线回退。

## 身份一致性检查

1. 根据登录链路设置 Core `identity.mode`：独立离线服为 `OFFLINE`，档案登录链路为 `PROFILE_UUID`。
2. 在使用代理 UUID 转发或 authlib-injector 的部署中，确保所有环节返回相同 UUID；具体转发设置按所用代理与服务端配置。
3. 核对一个测试账户的档案 API UUID、代理登录 UUID、Bukkit `Player#getUniqueId()`、Core 队员记录和 AuthMe 资料。
4. 测试未绑定、撤销、封禁、维护、网络中断、密钥错误及重连行为。
5. 已有身份发生 UUID 变化时执行 [UUID 迁移流程](player-uuid-contract.md#5-uuid-迁移规范)，只改配置不会迁移数据库。

Proxy 持久状态位于其数据目录的 `state.properties`，Bridge 位于 `state.yml`。保留这些文件以支持重启后的游标、准入和封禁恢复。
