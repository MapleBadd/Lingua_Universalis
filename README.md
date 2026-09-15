# 万象牧语 · Lingua Universalis

##  ”释卷以读乾坤“

以"伙伴而非单纯的宠物"为基调的 Minecraft Java 魔物娘养成模组。
本模组的程序部分100%由AI开发。
目标环境：**Minecraft Java 26.2 + NeoForge 26.2.0.77**（加载器版本随 NeoForge 发布页更新）。
设计文档见仓库根目录 `万象牧语-设计汇总.md` 等 `*.md / *.txt` 文件；
设计与实现覆盖对照见 `设计-实现对照.md`。

---

## ⚖️ 许可证与授权（请先读这一段）

> **本项目使用 [PolyForm Noncommercial License 1.0.0](LICENSE)**（SPDX 标识符：`PolyForm-Noncommercial-1.0.0`）
> —— 标准许可证正文置于 `LICENSE`，**逐字原文，未作任何改动**。
> ⚠️ 它**不是** OSI 认证的开源许可证：**允许非商业用途，商业用途需另行取得授权**。

| | |
|---|---|
| ✅ **允许（非商业）** | 个人使用、研究实验、私人娱乐、业余项目、爱好用途；以及慈善/教育/公共研究/公共安全与卫生/环保/政府机构使用（不论经费来源） |
| ✅ **允许（非商业）** | 复制、修改、创作衍生作品、再分发（源码或编译产物）——须随附本许可证全文或官网链接，并保留 `LICENSE` 顶部的 `Required Notice:` 行 |
| ✅ **不受限制** | 法律给你的"合理使用（fair use）"权利，本许可证不限制 |
| ⚠️ **需事先书面授权** | **一切商业用途**：销售/付费下载/付费解锁、付费整合包、以营利为目的的服务器运营、把代码或美术资源用于商业产品等 |
| 🔒 **仅作者本人或书面授权者** | **中国网易版渠道**（网易我的世界中国版，含网易官方模组平台及其渠道）的商业发行、运营、改编或分成 |

**作者的额外许可（Additional Permission，不属于 PolyForm 文本）**

> 作为版权持有人，在此额外允许（无需另行商业授权）：
> **以本作品为内容制作的视频、直播与图文，即使通过平台创作激励、广告分成、会员订阅、观众打赏、商业赞助（恰饭）获得收益。**
> 判断标准：**收益来自"内容"⇒ 随意；收益来自"本作品本身"（卖模组、卖整合包、卖资源、付费服务器拿它当卖点）⇒ 需授权。**
> 这条是作者在 PolyForm 非商业许可证之外另行给出的许可，**不修改 `LICENSE` 正文**。

- **简要总结**：非商业随便用（随附许可证、保留 `Required Notice` 即可）；**商业用途（含把本作品或资源本身变现）都得先找作者谈**；网易版渠道只留给作者本人或其授权的人；**拍视频恰饭不用问**。
- 第三方组件（GeckoLib 为 MIT、NeoForge 为 LGPL-2.1，Minecraft 及其资源归 Mojang）仍受各自许可证约束。
- 本项目为**非官方作品**，与 Mojang Studios、Microsoft、网易均无隶属或背书关系；使用本项目还须遵守 Minecraft EULA 与使用指南。

**授权 / 商务联系（Licensing & contact）**

> 📧 邮箱：**1146963524@qq.com**
> 💬 QQ：**1146963524**
>
> 需要**商业授权**（含中国网易版渠道）、或对上述条款有疑问，请通过以上任一方式联系作者。
> 事先洽谈并取得书面授权后，商业使用完全可以；未经沟通直接商用不行。

---

## 🤝 贡献约定（Contributing）

**欢迎提交 PR**（bug 修复、文档修正、美术提案尤其欢迎）。因为本项目在**中国网易版渠道是商业化的**，为了让项目始终能合法地做商业授权，合并前有下面几条硬性约定：

1. **必须签署 CLA（贡献者许可协议）。** 要点：**你保留自己贡献的版权**，但授予项目作者一项**永久、不可撤销、全球范围、免版税、可再许可（含按商业条款再许可）**的权利。没有这一条，作者就无法把含你代码的版本拿去网易版商用。签署流程随 `CLA.md`、`CONTRIBUTING.md` 在开放贡献时一并提供，作者所获盈利也将按协议分享给贡献者。
2. **贡献同时按本项目许可证授权给所有人**：你提交的内容默认按 [`LICENSE`](LICENSE)（PolyForm Noncommercial 1.0.0）向公众授权（非商业免费使用/修改/再分发）。
3. **必须声明 AI 生成内容**：提交 PR 或素材时，请说明哪些部分是 AI 生成的、用了什么模型/提示来源。**纯 AI 生成物在很多法域不受著作权保护**，来源不清会污染整个项目的权利链。
4. **美术资源必须声明来源**：只能提交**你自己原创**，或**已获得允许在本项目（含中国网易版渠道商用）使用的授权**的模型/贴图/动画/音效。不接受来源不明、从其他模组或商业素材库扒来的内容。
5. **不接受复制粘贴**：从其他模组、反编译产物或不明来源粘来的代码一律不收。
6. 作者保留**不合并、回退或重写**任何贡献的权利；重大改动建议先开 issue 讨论。

> 外部代码贡献暂未开放；在此之前，**issue 与美术提案同样欢迎**。



---

## 阶段进度

| Phase | 内容 | 状态 |
|---|---|---|
| 0 | 工程骨架（ModDevGradle 2.0.146、注册表门面、创意标签、占位物品"初稿"及其模型/贴图占位） | ✅ |
| 1 | 核心数据模型：属性/档位常量、分类学阶元、物种档案注册表（阿拉克涅/猫又）、每日上限、心情低落模板 | ✅ |
| 1.5 | 纯规则层：互动/档位/衰减/休眠/心情/战败急救/图鉴解锁/礼物表/命令与游荡范围/低落恢复/快照/巢心守卫/饱食自给/档案完整性/战斗面板/敌意目标选择/威胁以巢心为圆心/守巢敌视一切生物/被惹反击豁免绑玩家/同种族敌意两变量/物种专属能力（蛛网·蛛丝·毒牙·流血·暗影箭·闪避，含弹道种类、伤害结算口径与寿命上限）/巢穴方块物种变量/伪装形态规则/巢穴结构几何/猫又社交习性（偷鱼·绿宝石·赠礼·陪睡·亡灵视野）/**动画规则（main 互斥与过渡、叠加层、random 系列 3%/秒同系列互斥、跳跃→悬空衔接）**/野生投喂认主/无巢狂暴开关/分类学类目注册/纲模板复用/全阶元模板默认特性+键目录/状态与图鉴持久化/**自然生成（刷猫转换概率与结构口径/未声明即不转换/自然消失与"获得过一次好感后转持久"）**/**猫形伪装表现（待机坐姿、休眠/陪睡趴姿、原版过渡步长、原版全黑猫贴图口径）**/**头部跟随视角（最短弧包装/部分 tick 插值/偏航俯仰限幅）**/**动画名与骨骼名对表（动画资源、AllHead 骨骼）**/**双档移速（跟随 >8 格加速、追到跟随最低距离 6 格才降回 walk、攻击即最快、每 tick 重算、倍率热改：物种覆盖 > 全局 > 档案值）**/**快捷栏与背包（槽位切分与命名、背包格数夹取、GUI 行数/居中/面板高度、槽位不重叠且在面板内）**/**物品栏行为（不饿不捡食物/满了不捡、5 秒入包、饿到阈值才吃且咀嚼 32t、武器只要面板伤害更高的且耐久 ≤10% 换下、伙伴背包满才往宝箱存、宝箱搜索 8 格/存取 2 格）** + 冒烟测试 280 项全过 | ✅ |
| 3.0 | 物品/方块占位注册：id 常量表（LUIds）+ 物品/刷怪蛋 + 4 个占位方块（誊写台/伙伴宝箱/绽放之刺/蜘蛛巢心）+ 双语名 + 模型/贴图/blockstate 占位（含生成脚本） | ✅ |
| 2/4-① | 通用魔物娘实体 + 互动全套（摸头/投喂/送礼/礼物盒/急救/百晓镜/单击防重）+ 生活/战败系统 + 命缕链路（掉落强化/充能/快照/绽放之刺仪式复活）+ /lubond /lurevive + 巢心守卫/被惹反击/狂暴/低落/同种族变量敌意 + **物种专属战斗能力（蛛网/蛛丝拉拽/毒牙/流血/暗影箭/闪避，远程均以弹道发射）与巢穴增益** | 🔧 Phase 4a-① 已实现，待验收（清单见 `设计-实现对照.md`） |
| 2 | 通用互动系统接入实体（事件/AI 装配：摸头/投喂/送礼/战败） | ✅ 已验收（命令循环 跟随/待机/游荡 缺陷搁置待修） |
| 3 | 物品实现（初稿 GUI/誊写台、命缕/命帛收纳、百晓镜图鉴写入、誓约协议书、伙伴宝箱容器等） | ⏳ Phase 3 进行中（**魔物娘 GUI：快捷栏 + 背包** 已完成，见下） |
| 3-GUI | **魔物娘快捷栏 + 背包**：手持**初稿**右键**伙伴档**魔物娘打开容器界面 —— 快捷栏（物种档案命名槽：猫又主手/副手、阿拉克涅主手/副手/附肢1/附肢2，**模型可见槽位**）+ 背包（猫又 8 / 阿拉克涅 9 格）+ 玩家背包；槽位随物种档案变化，**贴图暂用原版漏斗**（上片段原尺寸 + 中段 1px 灰底纵向拉伸 + 下片段原尺寸拼任意高度面板；槽位底图取漏斗贴图里现成的 18×18 槽位）；她的物品随存档持久化（按槽位索引存，读档不串位）、真死时撒落原地、倒地不掉 | 🔧 已实现，待验收 |
| 3-初稿 | **初稿：GeckoLib 3D 模型 + 最朴素的展示**：初稿用美术的模型（`初稿.geo.json` / `初稿.png`）而不是 2D 贴图，物品外观声明为 `assets/<ns>/items/<item>.json` 里的 `minecraft:special` + `geckolib:geckolib`（26.2 的 item model definition 体系）。**动画与右键交互都已移除**：`registerControllers` 有意留空 ⇒ 模型永远保持默认（合着）姿态；手持两个手位都用**原版普通物品**（`item/generated`）的数值，物品栏/掉落物/展示框用美术调好的数值（在 `models/item/first_draft.json` 的 `display` 段，改完 `gradlew processResources` + 游戏内 F3+T 即时生效）。美术的开合/翻页动画（`初稿动画.json` 的 display/open/flip）留在资源目录备用，未接入 | 🔧 已实现，待验收 |
| 3-伙伴宝箱 | **伙伴宝箱（54 格共享收纳）+ 魔物娘物品栏行为**：伙伴宝箱 = 自定义方块 + 方块实体（54 格，不能合成大箱子）+ 容器界面（`generic_54` 布局）；**外观暂时借用末影箱**（方块走 `ChestRenderer` 并把材质强制成 `ENDER_CHEST`，物品走 `minecraft:chest` 特殊模型 + `texture: minecraft:ender`）。魔物娘这边接上设计 §6：**拾取**（不饿不捡食物、没空位不捡）→ 先进快捷栏 → **5 秒后放进背包**（快捷栏里任何来源的东西都算，只有"交战中主手"和"正在副手咀嚼"两个例外）；**进食**（饱食 <12 才吃，快捷栏优先，食物一律放副手，咀嚼 32t 后按营养值回饱食度）；**武器**（发现敌人时挑面板伤害高于自身近战的武器拿到主手，耐久 ≤10% 放回背包换别的）；**与宝箱联动**（搜索半径 8 格，但**存取必须走到箱子 2 格内**——不够近就自己跑过去，跑腿期间跟随/游荡让路；饿且身上没吃的就取一份，伙伴档 + 背包满就把东西存进去，多只共用同一个箱子） | 🔧 已实现，待验收 |
| 4 | 生物框架细节（物种专属行为：阿拉克涅结网/爬墙、猫又偷鱼·礼物表·猫形陪睡、自然生成）+ GeckoLib 动画 | 🔧 Phase 4a 进行中（战斗能力 + 猫又伪装 + 巢穴结构程序化生成 + 猫又 GeckoLib 模型/动画 + **村庄刷猫自然生成** 已完成） |

> 当前主线见 `设计-实现对照.md`「待办」：Phase 4a-② 猫又社交习性（伪装/偷鱼/礼物表）与自然生成，或先修复命令循环搁置项。

## 构建说明

> 版本发布说明：`neoforge.mods.toml` 为**静态文件**（位于 `src/main/resources/META-INF/`，
> 不经过模板展开），升级模组版本号时需同步修改 `gradle.properties` 的 `mod_version`
> 与其中的 `version="..."` 两处。

前置要求：
- **JDK 25**（MC 26.2 需要；`build.gradle` 已声明 Java 25 toolchain；无本地 JDK 25 时 Gradle 在有网环境会自动下载）。
- 网络需能访问 `https://maven.neoforged.net/`（NeoForge 构件源）与 `https://api.modrinth.com/maven`（**GeckoLib** 构件源）。
- 运行期依赖 **GeckoLib**（`neoforge.mods.toml` 已声明 `modId="geckolib"` 的必装依赖）；
  **本模组不内嵌（未做 jar-in-jar）GeckoLib**，因此玩家侧需与本模组一起安装 GeckoLib 5.5.5+。

### ⚠️ GeckoLib 依赖的三个坑（前两个是构建期，第三个是渲染期）

1. **Modrinth 上 GeckoLib 同一版本号有 3 个文件**：`geckolib-fabric-*` / `geckolib-forge-*` / `geckolib-neoforge-*`，
   三者 `version_number` 都是 `5.5.5`，**只有 NeoForge 那个是 NeoForge mod**。
   Maven 坐标若用版本号（`maven.modrinth:geckolib:5.5.5`）可能解析到 **Fabric** 那个：
   编译照样通过（class 布局相同），但运行期 FML 不认它是 mod →
   `Mod lingua_universalis requires geckolib 5.5.5 or above. Currently, geckolib is not installed`。
   因此 `build.gradle` 用**版本 id** 精确指定：`maven.modrinth:geckolib:${geckolib_version_id}`
   （`gradle.properties` 里 `geckolib_version_id=fTK5ltWI` = NeoForge 26.2-5.5.5）。
   换版本时用 `https://api.modrinth.com/v2/project/geckolib/version` 查 `loaders=neoforge` 那条的 `id`。
2. **校验方法**：解压 jar 看有没有 `META-INF/neoforge.mods.toml`（NeoForge 版有，Fabric 版只有 `fabric.mod.json`）。
3. **GeckoLib 只认 `geckolib/models/` 与 `geckolib/animations/` 两个目录，而且 Identifier 不是文件路径**
   （5.5.5 实测，已核对 GeckoLib 字节码 `GeckoLibResources` / `BakedModelCache` / `BakedAnimationCache`）：

   - 资源在**这两个目录之外**（例如老的 `assets/<ns>/geo/`、`assets/<ns>/animations/`）→**根本不会被扫描**，
     日志开头就是 `Loaded 0 models and 0 animations from resources`，随后每帧刷 `Unable to find model/animation`，
     实体渲染成**紫黑色占位块**。
   - 缓存键 = 文件路径剥掉 `geckolib/ models/ animations/` 前缀与 `.geo.json` / `.animation(s).json` 后缀后的**裸名字**。

   | 资源 | 文件（唯一正确位置） | 代码里的 Identifier |
   |---|---|---|
   | 模型 | `assets/<ns>/geckolib/models/<name>.geo.json` | `<ns>:<name>` |
   | 动画 | `assets/<ns>/geckolib/animations/<name>.animation.json` | `<ns>:<name>` |
   | 贴图 | `assets/<ns>/textures/entity/<name>.png` | `<ns>:textures/entity/<name>.png`（完整路径含后缀） |

   > 本模组实例：文件 `assets/lingua_universalis/geckolib/models/nekomata.geo.json` +
   > `assets/lingua_universalis/geckolib/animations/nekomata.animation.json`，**模型与动画的 Identifier 都是
   > `lingua_universalis:nekomata`**（两者是不同缓存，不冲突）。
   > 新增物种照抄：文件名换成 `<species>`，键同名——**每个物种一套名字**，别复用。
   >
   > 注意 `Superfluous prefix or suffix found ... Should be '...'` 这条提示是 GeckoLib 在归一化
   > **你传进去的键**（`stripLegacyPath`），**不是**在告诉你文件该放哪——照它改文件名会走错方向。
4. **箱型 UV（Box UV）在 GeckoLib 里会把 cube 尺寸向下取整**，含小数的 cube 整块 UV 错位：

   ```java
   // com.geckolib.loading.definition.geometry.GeometryUvPair#bakeQuad
   final Vec3 uvSize = new Vec3(Mth.floor(cubeSize.x), Mth.floor(cubeSize.y), Mth.floor(cubeSize.z));
   ```

   即 `size:[1, 0.4, 1]` 会按 `[1, 0, 1]` 展开 UV 布局 → 面片采到隔壁像素、细节整体错位
   （模型里小件越多越明显）。**修法：把模型转成逐面 UV**（逐面路径直接用给定值，不取整）。
   仓库自带转换器：`tools/boxuv-to-faceuv/BoxUvToFaceUv.java`（见该文件头部注释的编译/运行命令），
   它会按 GeckoLib 自己的箱型布局、但用**真实未取整**的尺寸算出六个面的 `uv`/`uv_size`。
   > 更省事的替代：在 Blockbench 里关掉 cube 的 "Box UV"（即用逐面 UV 模式）重新导出 geo.json，
   > 由 Blockbench 自己算逐面值——两者结果一致，后者更权威。

   > 本模组当前状态：`nekomata.geo.json` 用的就是**美术侧导出的原文件**（`美术资源/猫又.geo.json`，逐字节相同）；
   > `tools/boxuv-to-faceuv` 保留备用——以后若又收到箱型 UV 的模型，直接跑它转换。
   > （已交叉验证：工具对头部 cube 的转换结果与 Blockbench 自己的逐面转换**完全一致**。）
5. **动画控制器「注册顺序 = 优先级」，后写覆盖先写**：GeckoLib 的 `applyAnimationControllers`
   按注册顺序把每个控制器的骨骼快照写进同一份 `BoneSnapshots`，同骨骼同属性**后写的赢**。
   资源里的 `constant` 恰好也动 `LeftArm`/`RightArm`（±1° 摆动），若把它注册在 `main` **之后**，
   它就会把 `main` 给手臂的旋转覆盖掉 → **表现为"两条手臂完全不随动画摆动"**。
   本模组顺序（底 → 上）：`constant` → `main` → `tail` → `pet` → `random_blink` / `random_idle`。

### 🎨 美术资源改动约定

**`美术资源/` 里的 geo / 动画 / 贴图一律不做任何修改**：只按原样拷贝到 `src/main/resources/`
（拷贝前后可用 `Get-FileHash` 比对，确保逐字节一致）。任何需要改动资源本身的情况
（例如换 identifier、UV 转换、补动画名）**先通知再动手**；代码侧能解决的绝不落到资源上。

### 🧭 头部跟随视角（追加功能）

`AllHead`（整颗头的父骨骼）的**旋转**跟随原版转头：偏航 = 头朝向 − 身体朝向（限幅 ±75°）、
俯仰 = 实体 X 旋转（限幅 ±60°），只动旋转，**位置与缩放保持动画值**。

- 纯规则：`core/anim/HeadLookRules`（取最短弧插值 + 限幅，有断言）；
- 接线：`client/NekomataRenderer#extractRenderState` 把插值后的角度塞进渲染状态，
  `adjustModelBonesForRender` 在动画写完之后叠加到骨骼上；
- 注意该覆写用了**裸类型** `RenderPassInfo`：GeckoLib 的 `RenderPassInfo<R>` 要求
  `R extends GeoRenderState`，而原版渲染状态只在运行时由 GeckoLib 的 mixin 注入该接口，
  编译期写不出泛型签名（写泛型会因边界不满足而编译失败）。
- 若实测转头方向相反：改 `config/lingua_universalis.properties` 里的
  `headLook.yawSign` / `headLook.pitchSign`（±1）即可，无需改代码；
- **轴向约定**（建模方声明）：转头以 `AllHead` 枢轴为中心，正方向为向左转、反向为向右转；
  X 正方向抬头、负方向低头；Z 正方向向右歪头、负方向向左歪头。
  当前实现：偏航落在 Y 轴、俯仰落在 X 轴，Z 轴（歪头）暂未驱动（原版没有对应来源）；
  符号由设置项给出（默认都为 `-1`，即与"头朝向−身体朝向"和实体俯仰取反）。

### ⚠️ 动画名改名会静默失效
动画是**按名字**从 `<species>.animation.json` 取的，资源里改了名而代码没跟着改，
只会打一行 `Unable to find animation: 'x' in animation file 'y'` 然后那条动画不播。
因此冒烟测试里加了一条对表检查：`AnimationRules.REQUIRED_ANIMATIONS` 中每个名字都必须出现在
`geckolib/animations/nekomata.animation.json` 里（`ok: every requested animation exists in ...`）。
新增/改名动画时同步这两处即可。

> 本仓库另外放了一份可直接丢进 `mods/` 的 NeoForge GeckoLib：`libs/geckolib-neoforge-26.2-5.5.5.jar`
> （仅供本地安装/离线编译用；正式构建仍走上面的 Maven 依赖。开发环境 `runClient`/`runServer`
> 由 classpath 自动提供 GeckoLib，无需手动放。）

```bat
gradlew.bat build        :: 构建发布 jar
gradlew.bat runClient    :: 启动开发客户端
gradlew.bat runData      :: 运行数据生成（输出到 src/generated/resources）
```

## 游戏内速查

```mcfunction
summon lingua_universalis:arakne          // 阿拉克涅（亦可用 nekomata / monster_girl）
summon lingua_universalis:nekomata        // 猫又
lubond @e[type=lingua_universalis:arakne,limit=1] info   // 查看状态
lubond @e[type=lingua_universalis:arakne,limit=1] bond   // 绑定为伙伴(好感150)
lubond @e[type=lingua_universalis:arakne,limit=1] aff 77 // 设置属性
lubond selftest                            // 系统自检+图鉴概览
lurevive                                   // 手持已充能命缕按快照复活
give @p lingua_universalis:arakne_spawn_egg    // 阿拉克涅刷怪蛋
give @p lingua_universalis:nekomata_spawn_egg  // 猫又刷怪蛋（用蛋生成 = 野生状态）
give @p lingua_universalis:first_aid_kit
give @p lingua_universalis:speculum_scientiae
give @p lingua_universalis:present_case 2
```

| 操作 | 效果 |
|---|---|
| 空手右键（友善+，**主副手皆空**） | 摸头：好感+1、心情+10（每日一次）；任一手持物则不触发（避免边用物品边摸头） |
| 持食物右键 | 投喂：好感+1/日（吃不吃都给）；**野生个体：喂食者是养成起点，好感累计≥10 自动认主绑定** |
| 潜行+右键（非食物非工具） | 送礼：喜爱 +2/日 |
| 主手礼物盒 + 副手物品 → 右键空气 | 打包礼物盒 |
| 手持已打包礼物盒右键（友善+绑定） | 送礼盒：喜爱 +2/日（盒消耗） |
| 空手 Shift+右键（伙伴） | 命令循环 跟随/待机/游荡（原生防重，单击一次切一次） |
| 手持急救箱右键 | 倒地锁血结束后：回复并站起 |
| 手持百晓镜右键 | 目击+观测录入图鉴，显示个体摘要 |
| 手持已充能命缕右键凋灵玫瑰 | 仪式：化为绽放之刺（原地留祭品命缕） |
| 绽放之刺击杀 ≥20 血生物 | 按祭品命缕快照原地复活魔物娘 |

> 防重复：所有右键交互（摸头/投喂/送礼/急救/礼物盒/百晓镜/Shift+右键命令/调试件）统一用 26.2 原生机制——客户端 mobInteract 对认领的手势返回 CONSUME，一次点按只发一个交互包；服务端另有 0.1s 兜底窗口。单击一次只生效一次，同时高频点击逐次生效。

> 注：若在受限网络（如国内直连）无法访问 `maven.neoforged.net`，可把构建仓库切换到社区镜像
> （如 Lss233 聚合镜像 `https://lss233.littleservice.cn/repositories/minecraft/`），需同步修改
> `settings.gradle` 的插件源。以镜像站最新说明为准。

### 云端构建（GitHub Actions，推荐）

本仓库含 `.github/workflows/build.yml`：推送到 GitHub 后自动用 **JDK 25** 执行 `./gradlew build`，
产物在 Actions 的 Artifacts 中下载。网络受限的机器可直接用此通道完成真实编译验收：把本目录推送到
任意 GitHub 仓库（或先 `git init` 提交后推送），打开 Actions 页查看构建日志并回传报错。

## 规则冒烟验证（无需 Minecraft 依赖，仅需 JDK）

`core` 层与 `tools/rules-smoke` 不依赖 Minecraft API，可在任意 JDK 上验证：

```bat
:: 从项目根目录执行（Windows 下建议用 pwsh / powershell）
pwsh -NoProfile -Command "$files = Get-ChildItem -Recurse -Filter *.java src/main/java/com/linguauniversalis/core | %% FullName; javac -encoding UTF-8 -d build/smoke-classes $files; javac -encoding UTF-8 -cp build/smoke-classes -d build/smoke-classes tools/rules-smoke/SmokeMain.java; java -cp build/smoke-classes SmokeMain"
```

当前断言覆盖：投喂/送礼/摸头的每日上限、10→9 退野生清绑、伙伴黏性解锁、命令权阈值、
誓约锁定好感 200、休眠进入/唤醒、心情活动不足连续判定、战败倒地锁血/急救箱、
图鉴知识解锁（目击/缩放/喜好互动）、猫又礼物表分布、命令权/低落不听指挥/游荡范围、
低落击杀回心情、状态快照、阿拉克涅巢心守卫（领地/威胁/红线/狂暴）、饱食进食与自给
（饥饿伤害/自主觅食/伙伴需管理）、**野生投喂认主（好感≥10 自动绑定喂食者；同天限一次）**、**物种专属能力（蛛网·蛛丝拉拽·毒牙·流血叠加与周期结算·暗影箭·闪避：参数读取+冷却门控）**、**能力弹道（蛛网/蛛丝/暗影箭的弹道种类、伤害结算口径、寿命上限与射空自消失）**、**巢穴方块物种变量（有巢/显式无巢 + hasNest 总开关：无巢物种不认领巢心/不返巢/不狂暴/不得巢穴增益）**、**伪装形态规则（物种变量、三类现形触发、静默恢复、伙伴档失效）**、**巢穴结构几何（树干收分/球壳巢壁/20% 球冠出入口/巢心方位/蛛网递减与通路/螺旋楼梯）**、**巢穴住户锚点（巢心方块实体固定生成一只并认领）**、**猫又社交习性（偷鱼仅游荡+只偷背包 9–35 槽、绿宝石 50%/1–3、睡醒赠礼、伙伴猫形陪睡、亡灵视野每日心情）**、**自然生成（刷猫转换概率物种变量与 `cats_spawn_in` 结构口径、未声明即不转换、自然消失与"获得过一次好感后转持久"）**、**动画规则（main 优先级：跳跃 > 悬空 > 进食 > 休眠 > 移动 > 待机；`random_` 系列名归一与同系列互斥、3%/秒判定；`pet` 触发的一次性动画）**、物种档案完整性、战斗面板数值、**敌意目标选择（纲模板驱动：领地巡游/拟态/中性 + 物种参数覆盖）**、**威胁/领地以巢心方块为圆心（非本体）**、**守巢威胁内敌视一切生物（玩家/亡灵/普通生物一视同仁，仅豁免绑玩家）**、**被惹反击无视巢距（豁免绑玩家）**、**同种族敌意两变量（巢穴威胁内/狂暴是否攻击同族，阿拉克涅=均 true）**、**无巢狂暴开关（刷怪蛋生成豁免）**、**分类学类目注册增删（附属模组扩展）**、**全阶元模板默认特性（TaxonTemplates：界跳跃/自动上阶、科流血/闪避/击退等；TemplateKeys 键目录；物种覆盖优先）**、GirlState 编解码往返、
图鉴（Encyclopedia）编解码往返（256 项）。预期输出 `ALL_PASS`。

## MC 层校验（三档：真实构建 → 真实 API 离线编译 → API 桩）

### ① 真实构建（最权威，推荐）

依赖已在 Gradle 缓存中时**完全离线**即可完成真实编译/打包：

```bat
gradlew.bat build --offline
:: 预期输出：BUILD SUCCESSFUL
:: 产物：build/libs/lingua_universalis-<版本>.jar
```

> 注意 `createMinecraftArtifacts` 任务仍会访问一次 `piston-meta.mojang.com` 校验版本清单；
> 已缓存时会 `REUSE`，只有换 NeoForge 版本/清缓存时才需要重新下载。

> ⚠️ **编译通过 ≠ 启动通过**：注册期语义错误（例如物品 id 没进 `Properties`）编译期看不出来，
> 只在启动时炸。**自定义物品一律用 `ITEMS.registerItem(id, 工厂, p -> p.…)`** —— 这个重载会先给
> `Item.Properties` 写入注册 id；自己 `new Item.Properties()` 交给构造函数会在启动注册阶段抛
> `NullPointerException: Item id not set`（`Item` 构造时要靠 id 生成描述/翻译键，而那时物品还没进注册表）。

### ② 对**真实 NeoForge API** 的全工程离线编译（快速自查，无桩）

把真实构建用的 classpath 导出一次，之后即可用 `javac` 对全部源码做与 Gradle 等价的类型检查：

```bat
:: 导出真实编译 classpath（补丁版 MC + NeoForge + 运行库 + GeckoLib）
gradlew.bat --offline -q --no-configuration-cache --init-script tools/dump-classpath.gradle luDumpCp > build\lu-compile-classpath.txt

:: 对真实 API 编译整个工程（默认自动优先用上面导出的 classpath）
powershell -ExecutionPolicy Bypass -File tools/compile-full-offline.ps1
:: 预期输出：FULL OFFLINE COMPILE OK (real NeoForge API)
```

> 这一档**不使用任何桩**：NeoForge 事件/补丁 API 的签名问题（如 `EntityJoinLevelEvent`、
> `FinalizeSpawnEvent`、`EventHooks.finalizeMobSpawn`、`Cat#getSpawnType`）都会在这里暴露。

### ③ API 桩校验（仅在拿不到真实 jar 时使用）

```bat
powershell -ExecutionPolicy Bypass -File tools/compile-mc-with-stubs.ps1     :: MC-LAYER STUB COMPILE OK
powershell -ExecutionPolicy Bypass -File tools/compile-full-offline.ps1 -Mode stub
```

> 桩签名按 NeoForge 26.2 官方源码对齐（如 `registerSimpleItem(String, UnaryOperator<Item.Properties>)`）；
> 回退模式需要 `LU_MC_JAR`（vanilla 26.2 client.jar）与 `LU_LIBS`（该版本全部非 native 库）。

### ⚙️ 本地可调设置（不改代码就能调）

> **改完存盘即可生效（每秒自动重读一次，不用重启游戏）**。路径：开发环境 `run/config/lingua_universalis.properties`，正式环境 `.minecraft/config/...`。
>
> **两层结构：全局 + 物种覆盖**（便于逐个魔物娘微调）。物种覆盖写法 = `<物种id>.<键>`，优先级
> `<物种id>.<键>` > `<键>` > 代码默认值；默认文件里会为**每个已注册物种**列出一整段注释版覆盖键，
> 去掉行首 `#` 即生效（`body.*` 那三行在该物种段里填的就是它档案里的当前值）。
>
> ```properties
> animation.speed.main_walk = 2          # 全局：所有物种走路倍速
> nekomata.animation.speed.main_walk = 3 # 只有猫又用 3（覆盖上面那条）
> nekomata.body.width = 0.6              # 只有猫又的碰撞箱宽（不写则用物种档案值）
> arakne.headLook.enabled = false
> ```

`config/lingua_universalis.properties`（不存在时启动自动生成带注释的默认文件）：

| 键 | 默认 | 说明 |
|---|---|---|
| `headLook.enabled` | `true` | AllHead 是否跟随原版转头 |
| `headLook.yawAxis` / `headLook.pitchAxis` | `z` / `x` | 该数值作用到骨骼的哪根轴（`x`/`y`/`z`）——左右转头 / 抬头低头 |
| `headLook.yawSign` / `headLook.pitchSign` | `-1` / `-1` | 方向反了就改 `-1` |
| `headLook.followPitch` | `true` | `false` = 只跟随左右转头（单独排查轴向时用） |
| `headLook.lookAtNearbyPlayer` | `true` | 没有绑定玩家时也注视 8 格内玩家（俯仰/偏航的"目视目标"来源） |
| `headLook.maxYawDeg` / `headLook.maxPitchDeg` | `75` / `60` | 转头限幅（度） |
| `animation.mainTransitionTicks` | `8` | 主要动画自动过渡时长（tick）；`0` = 不做过渡，越大越柔和 |
| `animation.jumpTransitionTicks` | `0` | 跳跃动画的过渡时长（跳跃动画只有 10 tick，**默认不做过渡**，否则会被过渡稀释掉） |
| `animation.speed.<动画名>` | 全部 `1.0` | 每个动画的播放倍速（1.0 = 原速，2.0 = 快一倍）。**默认文件里 13 个动画名都会写出来**，直接改数值；旧键 `animation.walkSpeed` 仍兼容但只对 `main_walk` 生效 |
| `animation.freezePawTransition` | `true` | `LeftPaw`/`RightPaw` 不参与自动过渡（过渡期间钉在原姿态，避免绕远路反向转 180°+） |
| `model.renderScale` | `1.0` | 模型渲染缩放（模型实测约 3.2 格高、碰撞箱 1.5 格，想让两者对齐可试 `0.47`；视线高度跟着缩放走） |
| `body.width` / `body.height` / `body.eyeHeight` | 不写 = 用档案值 | 碰撞箱与视线高度（格）；猫又档案值 = `0.6` / `1.5` / `2.1548` |
| `speed.walkScale` / `speed.fastScale` | 不写 = 用档案值 | 移速倍率（**最终速度 = 基础值 `0.25` 格/tick × 倍率**）。猫又档案值 = `1.0` / `1.7`（≈ 玩家疾跑 0.13 的 1.3 倍）；`≤0` 视为不写。跟随玩家 >8 格、或正在攻击敌人 → `fast`；**追到"跟随最低距离"6 格才降回 `walk`**（6 格也是停步距离，共用同一常量，中间 2 格是滞回带） |

> **移速档位为什么每 tick 重算**：`MoveControl#tick` 每 tick 都做 `setSpeed(speedModifier × MOVEMENT_SPEED)`，
> 所以改属性下一 tick 就生效、**不需要重新寻路**；但属性之前只在出生/赋物种时写过一次（那时既无敌人也无绑定玩家）
> ⇒ 永远停在 walk 档。现在 `tickMovementSpeed()` 每 tick 算档位，只在真的变档时写属性（避免每 tick 发属性同步包）。
> 另外「有没有敌人」必须读本模组自己的 `combatTarget`（实体从不调 `setTarget()`，`getTarget()` 恒为 null）。

## 目录结构（代码）

- `src/main/java/com/linguauniversalis/`
  - `LinguaUniversalis.java` —— @Mod 主类（注册门面 + 实体属性事件）
  - `LinguaUniversalisClient.java` —— 客户端入口（占位实体渲染器注册）
  - `registry/` —— `LUConstants`（MODID 常量）、`LUIds`、`LURegistries`（物品/方块/创意标签）、`LUEntities`
  - `item/FirstDraftItem.java` —— 初稿（GeckoLib 模型物品 + 翻开/翻页/合书动画控制器）
  - `entity/MonsterGirlEntity.java` —— 通用魔物娘实体（状态/持久化/档案/基础 AI/GeckoLib 动画控制器）
  - `entity/LuBoltEntity.java` —— 自研弹道实体（蛛网/蛛丝/暗影箭；无重力、不拾取、寿命上限+超界丢弃）
  - `entity/GirlInventory.java` —— 她的快捷栏 + 背包容器（连续容器；按槽位索引存档，真死可整体取出）
  - `menu/GirlInventoryMenu.java` —— 快捷栏 + 背包容器菜单（槽位布局取自物种档案；两端按「实体 id + 物种 id」重建）
  - `event/GirlNaturalSpawnHandler.java` —— 自然生成接线（原版自然刷猫 → 按物种变量转换为野生个体）
  - `client/NekomataGeoModel.java` —— 猫又 GeckoLib 模型（人形；猫形 geo 资源为预留分支）
  - `client/MonsterGirlFormModel.java`、`client/MonsterGirlRenderer.java` —— 阿拉克涅/通用体的占位渲染（原版玩家模型 + 村民贴图）
  - `client/NekomataRenderer.java` —— 猫又渲染器（按形态分派：人形 GeckoLib / 猫形原版猫）
  - `client/CatFormRenderer.java` —— 猫形伪装渲染（**原版猫模型 + 原版全黑猫贴图 + 原版猫动画**）
  - `client/GirlInventoryScreen.java` —— 魔物娘 GUI 屏幕（快捷栏 + 背包；**贴图暂用原版漏斗**，拼面板 + 铺槽位底图）
  - `client/FirstDraftGeoModel.java`、`client/FirstDraftItemRenderer.java` —— 初稿的模型与物品渲染器
  - `client/BookAnimationTracker.java` —— 初稿动画阶段机（客户端，每 tick 推进；阶段注入渲染状态给控制器读）
  - `core/` —— 纯数据/规则层（无 MC 依赖，可单测）
    - `ModConstants.java` —— 全部数值口径（范围/阈值/衰减/每日上限/战败）
    - `taxonomy/` —— 分类学阶元与档位
    - `species/` —— 物种档案（数据驱动模板 + 内置物种）
    - `state/` —— 养成状态数据（好感/心情/饱食/默契/休眠）
    - `interaction/` —— 每日互动奖励上限跟踪
    - `behavior/` —— 心情低落等行为模板注册表 + 快捷栏/背包槽位规则（`InventoryRules`）
    - `gui/` —— 容器界面几何（`GirlInventoryLayout`：面板高度/槽位坐标，与贴图无关）
    - `anim/` —— 动画选择规则（main 优先级/叠加层/random 系列）与猫形伪装表现规则
    - `rule/` —— 互动效果、档位推进/衰减、休眠计时、心情活动判定
- `src/main/resources/assets/lingua_universalis/`
  - `lang/en_us.json`、`lang/zh_cn.json`
  - `models/item/*.json`、`models/block/*.json`、`blockstates/*.json`、`textures/{item,block}/*.png`
    （16×16 程序生成的占位贴图，正式美术同路径覆盖即可）
  - `geo/nekomata.geo.json`、`animations/nekomata.animation.json`、`textures/entity/nekomata.png`
    （猫又正式模型/动画/贴图，已接入）
- `tools/`
  - `rules-smoke/SmokeMain.java` —— 规则层冒烟测试
  - `boxuv-to-faceuv/BoxUvToFaceUv.java` —— 箱型 UV → 逐面 UV 转换器（修 GeckoLib 箱型 UV 取整问题）
  - `compile-full-offline.ps1` —— 全工程编译校验（优先对**真实 NeoForge API**，可回退到桩模式）
  - `dump-classpath.gradle` —— 从 Gradle 导出与真实构建一致的编译 classpath（供上面脚本使用）
  - `compile-mc-with-stubs.ps1` —— MC 层离线语法/类型校验（配 `mc-stubs/` 的 API 桩）
  - `gen-placeholder-item.ps1` —— 物品占位模型/贴图生成器（`powershell -ExecutionPolicy Bypass -File tools/gen-placeholder-item.ps1 -Ids a,b,c`）
- `tools/mc-stubs/src/` —— NeoForge 26.2 最小 API 桩（仅作离线编译校验，非真实实现）
- `src/main/resources/META-INF/neoforge.mods.toml` —— 模组元数据（**静态文件**，不经模板展开）

## 美术资源需求（待用户提供）

- **物品**（Phase 3）：命缕/命帛/百晓镜/礼物盒/急救箱/誓约协议书等正式贴图；先用程序生成的 16×16 占位 PNG 推进。
- **生物**（Phase 4，GeckoLib）：每物种一套模型与动画，清单见《万象牧语-设计汇总.md》§14
  （通用 15 个动画：idle/idle_sit/walk/sneak/swim/jump/atk_melee/atk_ranged/defeat/interact_pet/
  interact_eat/interact_gift/emote_sad/interact/sleep + 个别专属），建议以猫又为基准制作人形可复用动作集。
  - ✅ 已接入：**猫又（人形）**——`geo/nekomata.geo.json` + `animations/nekomata.animation.json` + `textures/entity/nekomata.png`。
  - ✅ **猫又（猫形/伪装）不需要美术**：直接用**原版猫模型 + 原版全黑猫贴图（`cat_all_black`）+ 原版猫动画**。
  - ⏳ 待提供：**阿拉克涅**模型与动画（当前为原版玩家模型占位）。
