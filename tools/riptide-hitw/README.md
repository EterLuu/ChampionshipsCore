# HITW 建筑目录与地图评级工具

这里保存激流勇进使用的离线建筑来源、重建器、碰撞几何和预览。运行时从插件资源读取目录，不访问 Wiki、不自动迁移旧快照，也不覆盖管理员保存的建筑。游戏编排规则见 [关卡指南](../riptide-stage-mechanics.md)。

## 要求

Python 3.11 或更高版本。重建目录仅用标准库；评级已有地图还需 `tools/requirements.txt` 中的 PyYAML。命令从仓库根目录执行，示例参数需替换为实际文件。

## 目录和来源

目录 v4 固定 [MCC Island Wiki 的 Hole in the Wall 页面](https://mccisland.wiki/Hole_in_the_Wall) 修订 32764，包含 13 个子地图的 354 条来源记录。`sources.json` 保存 URL、轮廓和方块分类；`adapted.json` 记录删列、局部洞口处理和通行位置；`preview.html` 对照原图与碰撞侧视图，查看原图时需要联网。

来源 E/D/X 标签保留来源分组，游戏难度由当前建筑的有效通行截面积决定。轮廓从 14 列适配到最多 12 列，优先删除重复或差异较小的列，保留洞口、楼梯和细杆的相对顺序。材料使用云杉木、半砖、楼梯、栅栏和活版门；保存快照保留朝向及连接状态。实体跨度可能小于 12 格，保存空间与实体占用大小分别处理。

`build_catalog.py` 从固定来源重建：

- `championships-core/src/main/resources/riptiderush/hitw-walls.json`；
- 默认 `riptiderush/area.yml` 中标记的 HITW 目录段；
- `adapted.json` 和 `preview.html`。

它仅使用 Python 标准库，保留目录段外的地图模板选项。旧版迁移快照已经删除；生成结果只包含当前建筑、评级和通行元数据。

```bash
python3 tools/riptide-hitw/build_catalog.py
python3 -m unittest discover -s tools/riptide-hitw/tests
```

## 评级已有地图

`apertures.py` 离线读取 Sponge v3，在七格甲板范围、脚面以上三格内计算通行截面积。它识别半砖、直楼梯、栅栏与活版门，要求洞宽至少 0.6 格；平地或半格台阶可使用 1.5 格潜行洞，一格门槛需要至少 1.8 格净高。高窗和窄缝不计入，重叠洞口去重，厚建筑取各非空墙面的最小值。其他方块保守视为完整方块。

| 通行面积 | 难度 |
| --- | --- |
| 至少 6 格² | 1 |
| 至少 3 格²、少于 6 格² | 2 |
| 少于 3 格² | 3 |

这是尺寸评级，连续墙仍由游戏检查换位和动作时间。只有快照完全匹配当前目录的建筑才使用目录通行元数据，改过的建筑不能沿用原图洞口判断。

`update_map.py` 对所选地图的已保存 PASS 建筑重评，并修正一格门槛上方的悬空半格洞：其他通路足够时封实误导洞口，否则移除顶半砖。它保留条目 ID、名称、启停、权重及其他属性，不恢复已删除的建筑，也不替换旧目录快照或更改配置版本。

先在虚拟环境中安装工具依赖，再预览：

```bash
python3 -m venv .venv
.venv/bin/pip install -r tools/requirements.txt
.venv/bin/python tools/riptide-hitw/update_map.py <地图.yml> --report riptide-rating.json
```

默认只读取地图。确认报告后加 `--apply` 应用；有实际建筑或评级修改时会设置 `prepare.dirty: true`，发布修订号和时间保留，需要重新校验并发布。编辑会话和运行比赛结束后再操作，应用后重新加载地图或重启 Core 服务端，防止旧内存回写。

目录、单关试玩和正式赛道的客户端显示还需要实际游玩验收。此工具不操作世界存档，不替代 Paper 上的碰撞与通行验证。
