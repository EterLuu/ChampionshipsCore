# Riptide 游戏外移动测试

用实际冲刺调度器生成攻击，在20Hz甲板平面内模拟玩家移动。该程序不依赖Minecraft服务器或第三方库，需要JDK25与Python3。

生物速度按木筏实际航程的20%/40%/60%/80%节点递增；不是在一关内分波加速。每关固定9秒，前1秒准备，随后在同一来向上逐只随机刷新，满员跳过。2026-09-30根据实际游玩反馈，将刷新间隔增加约50%（向上取整）、在场上限降低约25%（向下取整），水平碰撞宽度从0.8格缩至0.6格。

调整前的校准目标为默认7×9木筏上，标准移动模型的通过率从前段约80%逐步降至末段约50%。历史结果见 [独立种子验证报告](results/report.md) 和 [成功与失败轨迹回放](results/replay.html)；这些结果使用调整前的密度与0.8格碰撞宽度，不能代表当前参数。原始数据在 [validation.csv](results/validation.csv)，其他甲板尺寸在 [dimensions.csv](results/dimensions.csv)。下方命令可验证当前参数。

标准模型以4.3格/秒移动、0.3秒反应、每0.15秒从八个归一化方向或停留中选动作。它只能看见已刷新的怪物；甲板每0.6秒观察一次，上方每1.6秒观察一次，记住已观察到的直线路径，预测未来0.6秒。观察相位由种子确定。末段计入玩家现有速度I加成。站立、较慢反应、快速反应和全场即时观察模型用于对照。默认模型是可审查的假设，不代表实际玩家统计；完整视野、网络延迟、移动惯性、跳跃、潜行和入关时护盾状态没有建模。

随机刷新、五档参数、速度、位置及寿命直接使用 [RiptideDodgeSchedule.java](../../championships-core/src/main/java/ink/ziip/championshipscore/api/game/riptiderush/RiptideDodgeSchedule.java)。正式和试玩通过同一个实体适配器执行这些路径，保证测试参数与游戏使用的参数一致。碰撞模拟用玩家0.6格脚印和怪物0.6格水平宽度，下降苦力怕只在与站立身体高度重叠的时间内造成碰撞。

从仓库根目录复现最终测试：

```bash
bash tools/riptide-dodge-sim/run.sh --self-test
bash tools/riptide-dodge-sim/run.sh 3000 /tmp/riptide-validation.csv validate 60000 default
python3 tools/riptide-dodge-sim/report.py /tmp/riptide-validation.csv /tmp/riptide-report.md
bash tools/riptide-dodge-sim/run.sh 300 /tmp/riptide-dimensions.csv validate 65000
bash tools/riptide-dodge-sim/run.sh 1 /tmp/riptide-replay.json replay 70000
python3 tools/riptide-dodge-sim/render.py /tmp/riptide-replay.json /tmp/riptide-replay.html
```

`calibrate`运行刷新间隔、数量上限和五档速度的网格搜索；`fine`在当前参数附近搜索。调参种子与最终验证种子段分开。`validate`可在最后添加`0:CREEPER`这类过滤器复测单个阶段、变体。早期的`sweep`/`focused`模式保留了固定速度的密度实验和原三波排布对照（CSV中间隔0代表旧排布），不用于最终五阶段报告。

模拟自检包含：站立会被直线攻击命中、移动可躲过同一攻击、空场不需要移动、未知刷新不能被提前躲避、帧间碰撞、刷新间隔和在场上限。游戏侧测试另检查四种赛道方向的坐标转换、模拟与实体路径一致、死亡/出场/取消清理及原生垂直碰撞盒。构建验证使用：

```bash
mvn -B -o -pl championships-core -am package
```
