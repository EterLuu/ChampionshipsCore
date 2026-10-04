#!/usr/bin/env python3
"""Summarize the actual CSV output without treating paired seeds as independent people."""

import csv
import math
import sys
from pathlib import Path

rows = list(csv.DictReader(Path(sys.argv[1]).open()))
names = {
    "ZOMBIE": "僵尸",
    "HUSK": "尸壳",
    "SKELETON": "骷髅",
    "SPIDER": "蜘蛛",
    "CREEPER": "苦力怕",
}
phases = [
    "初始（0–20%）",
    "第一次加速（20–40%）",
    "第二次加速（40–60%）",
    "第三次加速（60–80%）",
    "第四次加速（80–100%）",
]


def group(phase, controller="walking"):
    result = [
        r
        for r in rows
        if r["stage"] == str(phase)
        and r["controller"] == controller
        and (r["width"], r["length"]) == ("7", "9")
    ]
    assert len(result) == 5, (phase, controller, "incomplete result")
    return result


def mean(rs, field):
    return sum(float(r[field]) for r in rs) / len(rs)


def percent(p):
    return f"{p * 100:.1f}%"


def wilson(p, n):
    z = 1.96
    denominator = 1 + z * z / n
    center = (p + z * z / (2 * n)) / denominator
    margin = z * math.sqrt((p * (1 - p) + z * z / (4 * n)) / n) / denominator
    return f"{percent(center - margin)}–{percent(center + margin)}"


seed_count = int(rows[0]["seeds"])
first_seed = int(rows[0]["first_seed"])
last_seed = first_seed + seed_count - 1
total_scenarios = seed_count * 25
total_attempts = total_scenarios * 5
lines = [
    "# Riptide 躲避关：最终独立种子验证",
    "",
    f"校准平台为默认7×9木筏。每种怪物、每个航程阶段各{seed_count:,}个新种子（{first_seed}–{last_seed}），共{total_scenarios:,}套场景；每套场景分别测试五种控制器，合计{total_attempts:,}次尝试。五类怪物等权平均。",
    "",
    "通过定义为9秒内没有发生碰撞，前1秒不生成怪物。标准模型步速4.3格/秒、反应延迟0.3秒、每0.15秒决策一次，甲板每0.6秒观察一次、上方每1.6秒观察一次；只能预测已经观察到的生物，不知道未来刷新。末段计入现有速度I（玩家移动速度×1.2）。半数从甲板中央开始，半数从种子确定的随机内部位置开始。",
    "",
    "这些是明确假设下的模型通过率，不能当作真人通过率。模型用20Hz连续脚印碰撞和九种动作（八向归一化移动或停留），不模拟完整第一人称相机、网络延迟、移动惯性、跳跃/潜行或已有护盾。游戏与模拟直接共用随机调度、速度、轨迹和寿命模型；Bukkit使用生物原生垂直碰撞盒。",
    "",
    "## 标准移动模型",
    "",
    "| 阶段 | 平均通过率 | 各怪物范围 | 平均刷新数量 | 平均在场数量 | 平均移动距离 |",
    "|---|---:|---:|---:|---:|---:|",
]
for phase in range(5):
    rs = group(phase)
    rates = [float(r["pass_rate"]) for r in rs]
    lines.append(
        f"| {phases[phase]} | {percent(mean(rs, 'pass_rate'))} | {percent(min(rates))}–{percent(max(rates))} | "
        f"{mean(rs, 'spawn_mean'):.1f} | {mean(rs, 'active_mean'):.1f} | {mean(rs, 'distance_mean'):.1f}格 |"
    )
lines += [
    "",
    "数量、距离均为全部尝试的均值，失败尝试在首次碰撞处停止记录移动距离。在场数量包括甲板外正在进入或离开的生物。",
    "",
    "## 每个变体的通过率",
    "",
    "| 变体 | 初始 | 第一次 | 第二次 | 第三次 | 第四次 |",
    "|---|---:|---:|---:|---:|---:|",
]
for mob, name in names.items():
    rates = [
        next(float(r["pass_rate"]) for r in group(phase) if r["mob"] == mob) for phase in range(5)
    ]
    lines.append(f"| {name} | " + " | ".join(percent(rate) for rate in rates) + " |")
lines += [
    "",
    "## 控制器敏感性",
    "",
    "| 阶段 | 站立 | 慢速反应 | 标准移动 | 快速反应 | 全场即时观察 |",
    "|---|---:|---:|---:|---:|---:|",
]
for phase in range(5):
    rates = [
        mean(group(phase, c), "pass_rate")
        for c in ["stationary", "cautious", "walking", "responsive", "omnivision"]
    ]
    lines.append(f"| {phases[phase]} | " + " | ".join(percent(rate) for rate in rates) + " |")
lines += [
    "",
    "慢速模型：3.2格/秒，反应0.4秒，甲板/上方观察周期0.9/2秒；快速模型：5.6格/秒，反应0.2秒，观察周期0.3/0.8秒。全场即时观察模型与标准模型移动速度、反应时间、决策间隔相同，仅免去观察周期。该对照体现观察假设对结果的影响，不能将标准模型的所有失败归因为物理上无法躲避。",
    "",
    "## 实际使用的参数",
    "",
    "刷新间隔单位为tick（20tick/秒）。满员时跳过当次刷新，所以实际间隔可以更长；每次只生成一只。速度单位为格/秒。阶段按停船时的实际木筏航程计算。",
    "",
    "| 阶段 | 变体 | 随机间隔 | 在场上限 | 冲刺速度 | 通过率95%区间 |",
    "|---|---|---:|---:|---:|---:|",
]
for phase in range(5):
    for r in group(phase):
        lines.append(
            f"| {phases[phase]} | {names[r['mob']]} | {r['min_interval']}–{r['max_interval']} | {r['cap']} | "
            f"{float(r['speed']) * 20:.2f} | {wilson(float(r['pass_rate']), int(r['seeds']))} |"
        )
lines += [
    "",
    "上述区间是单个变体的二项抽样区间，仅反映种子抽样波动。它不覆盖模型假设与真实玩家行为的差异。不同控制器/阶段共用种子，不应把全部尝试当作独立真人样本。",
    "",
    "## 范围与回放",
    "",
    "校准目标针对默认7×9木筏。小木筏按面积降低在场上限；较大或不同比例的甲板通过率会变化，修改尺寸后应重新跑模拟。",
    "",
    "[可播放轨迹](replay.html)同时保存初始、中段、末段的成功与失败样例；[原始数据](validation.csv)含所有控制器和参数。",
]
Path(sys.argv[2]).write_text("\n".join(lines) + "\n")
