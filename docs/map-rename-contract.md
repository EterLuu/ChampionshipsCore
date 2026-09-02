# 地图重命名契约

`/cc map rename <game> <old> <new>` 修改地图注册名。除匹配赛建外，该操作不移动物理 Bukkit 世界。匹配赛建支持在同一个物理世界内放置多个独立地图区域；当某张地图独占默认世界 `buildmart_<注册名>` 时，重命名会同时修改两个标识。例如 `area` 与 `buildmart_area` 会变为 `skyline` 与 `buildmart_skyline`。共享世界或自定义世界中的地图注册名改变时，物理世界保持原位。

匹配赛建草稿可以绑定任意已加载 Bukkit 世界，包括另一张匹配赛建地图正在使用的世界。管理员先通过常规世界管理创建该世界，再在 prepare 会话中绑定。行式布局副本会放置在地图已知基础设施以东 384 格；管理员仍须为不同地图选择互不重叠的源区域和大厅区域。

仅当所选地图没有活动运行实例、没有 prepare 会话，且该游戏没有正在运行的正式赛事时，才允许执行重命名。匹配赛建只在所选地图独占物理世界、且配置名正好为 `buildmart_<旧名>` 时移动世界；该规则避免注册名重命名误移动共享或无关世界。

该操作是一个逻辑事务：

1. 分离运行时注册，并等待队列中的 DAILY 与积分写入完成。
2. 在同一个 SQL 事务中迁移所有第一方数据库地图标识：`player_points.area`、`daily_match_results.map`、`daily_player_records.map`、`daily_map_player_stats.map`，跑路战士还包括 `daily_pkw_records.map`。
3. 移动地图 YAML 并更新 `name` 字段。地图拥有的生成资产随注册名迁移。匹配赛建通常移动 `material-manifests/<地图>.yml`（重写 `map` 字段）和 `schematics/<地图>/`（包括 `base.schem` 与 `material-zones/`）。旧版匹配赛建地图的 YAML `name` 可能与注册名/文件名不同：`base.schem` 跟随注册名，材料清单与材料区快照跟随 YAML 名；迁移会同时移动并规范化这两类旧位置。斗战方框、跑酷追击和 TNT 飞跃移动 `schematics/<地图>/`（包括 `arena.schem`）。
4. 若匹配赛建地图独占 `buildmart_<旧名>`，卸载并移动该世界及全部命名伴随数据：Core 地图模板（`ChampionshipsCore/maps/<世界>`）、WorldGuard 状态（`WorldGuard/worlds/<世界>`）和 FAWE 历史（`FastAsyncWorldEdit/history/<世界>`）。地图 YAML 的 `world-name`、序列化位置中的世界前缀、`world_key` 值以及材料清单的 `world` 会在运行时地图重建前更新为新的专用世界。共享或自定义匹配赛建世界保持原位，其引用不变。目标世界和所有目标伴随路径必须不存在；目标路径必须为空，保证操作不覆盖既有状态。
5. 更新 `formal-events.<游戏>.maps` 中的对应项，按新名称重建运行时地图注册，然后刷新 DAILY 记录、按地图统计、跑路战士纪录、排行榜和打开中的地图列表菜单。

冲突的历史 DAILY 行按常规写入语义合并：计数累加，最大值取较大值，计时纪录取更快值。所有目标资产路径必须不存在；该命令不覆盖先前地图的资产。

后续任何持久化地图注册名的功能都必须加入 `MapRecordRenameMigration`；任何以地图命名的文件或目录都必须加入带回滚状态的 `MapAssetRename`。全局游戏数据、共享匹配赛建蓝图（`buildmart/blueprints`）和 `buildmart-bak` 备份目录不属于地图重命名目标。除专用匹配赛建世界规则外，世界名称只能由独立的世界管理操作修改；共享世界目录保持原位，其他地图定义中保存的坐标不变。
