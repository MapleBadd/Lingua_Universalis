# 万象牧语 · Lingua Universalis

[English](README.en.md) | **简体中文**

> "释卷以读乾坤"

以「伙伴而非宠物」为基调的 Minecraft 魔物娘养成模组。程序部分 100% 由 AI 开发。

![版本](https://img.shields.io/badge/version-0.0.1a-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-26.2-green)
![NeoForge](https://img.shields.io/badge/NeoForge-26.2.0.77%2B-orange)

---

## 环境要求

| 项 | 要求 |
|---|---|
| Minecraft | Java 版 **26.2** |
| 加载器 | **NeoForge 26.2.0.77+** |
| 前置模组 | **GeckoLib 5.5.5+**（必装） |
| 可选 | JEI（装了才有配方查询支持） |

## 玩法概览

- **遇见**：猫又夜里在森林/平原自然生成；她们会警戒，别一上来就打。
- **认识**：开局发「初稿」，对着她右键记录 —— 名字、分类、习性、战斗、喜好逐步解锁。
- **相处**：投喂爱吃的、摸头、陪睡觉，好感 0→200；到 100 成为伙伴，可指挥、睡魔物床、用伙伴宝箱。
- **指挥**：潜行 + 空手右键循环 跟随 / 待机 / 游荡；魔物哨切换战斗模式。
- **不死**：她真死后会掉落「命缕」（带个体快照），对凋灵玫瑰使用可化为绽放之刺，达成条件可原地复活。

## 内容一览

| 类型 | 内容 |
|---|---|
| 物种 | 猫又（nekomata） |
| 方块 | 誊写台、伙伴宝箱（54 格共享）、中型魔物床、绽放之刺、蜘蛛巢心 |
| 物品 | 初稿（图鉴）、百晓镜、命缕、急救箱、礼物盒、魔物哨 |
| 指令 | `/lu help`、`/lu check`、`/lu guide`、`/lu codex export|import`（管理员） |

## 构建

```bat
gradlew.bat build      :: 产出 build/libs/lingua_universalis-0.0.1a.jar
gradlew.bat runClient  :: 启动开发客户端
```

- 需要 **JDK 25**；
- 需要能访问 `maven.neoforged.net` 与 `api.modrinth.com/maven`；
- 不要内嵌 GeckoLib：玩家侧需自行安装 GeckoLib 5.5.5+。

## 文档

- [`开发守则与踩坑清单.md`](开发守则与踩坑清单.md) —— 开发约定、API 踩坑
- [`设计-实现对照.md`](设计-实现对照.md) —— 设计条目与实现进度对照
- [`指令系统.md`](指令系统.md) —— 指令说明
- [`图鉴-数据设计.md`](图鉴-数据设计.md) / [`图鉴-界面设计.md`](图鉴-界面设计.md) —— 图鉴设计

## 许可证

本项目使用 [PolyForm Noncommercial License 1.0.0](LICENSE)（**非** OSI 开源许可）：

- ✅ 非商业用途（个人、研究、教育、慈善等）随便用，随附许可证并保留 `Required Notice`；
- 💬 以本作品为内容做视频 / 直播 / 图文并取得平台收益 —— **不用问**；
- ⚠️ 一切**商业用途**（卖模组、付费整合包、营利服务器拿它当卖点等）需事先取得作者书面授权；
- 🔒 中国网易版渠道仅限作者本人或其授权者。

商务授权 / 问题反馈：**1146963524@qq.com** 或 [提交 issue](https://github.com/MapleBadd/Lingua_Universalis/issues)。
本项目为非官方作品，与 Mojang Studios、Microsoft、网易均无隶属关系。