# Lingua Universalis

**English** | [简体中文](README.md)

> "Unroll the scroll, read the world."

A Minecraft monster-girl raising mod built around the idea of **partners, not pets**.
The code is 100% AI-developed.

![version](https://img.shields.io/badge/version-0.0.1a-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-26.2-green)
![NeoForge](https://img.shields.io/badge/NeoForge-26.2.0.77%2B-orange)

---

## Requirements

| Item | Requirement |
|---|---|
| Minecraft | Java Edition **26.2** |
| Loader | **NeoForge 26.2.0.77+** |
| Dependency | **GeckoLib 5.5.5+** (required) |
| Optional | JEI (recipe lookup) |

## Gameplay at a glance

- **Meet** them: nekomata spawn in forests and plains at night. They are wary — don't swing first.
- **Learn** about them: you start with a "first draft"; right-click a girl to record name, taxonomy, habits, combat data and favourite food.
- **Bond**: feed what she likes, pet her, sleep nearby. Affinity runs 0→200; at 100 she becomes your companion — you can command her, she sleeps in a monster bed, and she uses the companion chest.
- **Command**: sneak + right-click with an empty hand cycles follow / standby / wander; the monster whistle toggles her combat mode.
- **Don't let her die**: a fallen girl drops a "fatum filum" holding her snapshot. Use it on a wither rose to grow a spina florens; under the right conditions she is revived on the spot.

## Contents

| Type | Contents |
|---|---|
| Species | nekomata |
| Blocks | scriptorium, companion chest (54 shared slots), medium monster bed, spina florens, arakhne nest core |
| Items | first draft (codex), speculum scientiae, fatum filum, first aid kit, present case, monster whistle |
| Commands | `/lu help`, `/lu check`, `/lu guide`, `/lu codex export|import` (admin) |

## Building

```bat
gradlew.bat build      :: produces build/libs/lingua_universalis-0.0.1a.jar
gradlew.bat runClient  :: launch the dev client
```

- Requires **JDK 25**;
- Requires access to `maven.neoforged.net` and `api.modrinth.com/maven`;
- GeckoLib is **not** bundled — players must install GeckoLib 5.5.5+ themselves.

## Documentation

Most design notes are written in Chinese:

- [`开发守则与踩坑清单.md`](开发守则与踩坑清单.md) — coding conventions and API pitfalls
- [`设计-实现对照.md`](设计-实现对照.md) — design vs. implementation checklist
- [`指令系统.md`](指令系统.md) — command reference
- [`图鉴-数据设计.md`](图鉴-数据设计.md) / [`图鉴-界面设计.md`](图鉴-界面设计.md) — codex design

## License

Licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE) (not an OSI open-source license):

- ✅ Noncommercial use (personal, research, education, charity, …) is free — keep the license and the `Required Notice`;
- 💬 Videos / streams / articles **about** this mod may be monetised through platform programmes — no permission needed;
- ⚠️ **Any commercial use** (selling the mod, paid modpacks, for-profit servers using it as a selling point, …) requires prior written permission;
- 🔒 The NetEase (China) edition channel is reserved for the author and authorised parties only.

Licensing / contact: **1146963524@qq.com** or [open an issue](https://github.com/MapleBadd/Lingua_Universalis/issues).
This is an unofficial project, not affiliated with Mojang Studios, Microsoft or NetEase.