# 净土协议 · EDEN PROTOCOL

**一款 Minecraft 合作制净化战役模组 —— 搜打撤只是手段，把被浊潮吞噬的世界一寸寸夺回来，才是目的。**

> 末世浊潮吞没了地表，幸存者退居轨道方舟「净土号」。在这里，每一场三十分钟的远征搜刮都是全服净化
> 战役的一步：带回物资与样本、摧毁污染核心、击落浊潮巨龙——直到全服污染度归零，天堂之门在方舟落下，
> 净土维度向所有人开启。

*Minecraft `26.1.2` · NeoForge · 当前版本 `0.2.1-alpha`（α 开发阶段）*

---

## 这是一款什么样的模组？

《净土协议》不是一张打完就散的搜打撤地图，而是一场**持续整个存档生命周期的合作战役**。它由四根支柱构成：

1. **方舟枢纽（`eden:ark`）** —— 轨道太空站，安全的家。玩家出生在一座悬浮于虚空的八角形站台：发射台、
   补给终端、个人储物柜与编年史墙环列四周，商店市场每日波动，聊天里的「你知道吗？」会持续传授生存智慧。
2. **远征循环** —— 在发射台选择威胁等级与目的地（污染主世界 / 下界 / 末地），踏入**每次重开都全新随机
   生成**的污染世界。侵蚀时钟、隐藏污染词缀、随机撤离点与返回舱协作充能，构成一局 30 分钟的搜打撤。
3. **尘星战役** —— 全服共享的世界污染度与战役阶段：摧毁污染核心 -2%、击落浊潮末影龙 -5%，逐级解锁
   更高的难度档与更深的远征维度。你在局内的每一次抉择，都在为整个服务器的战役记账。
4. **净土终局** —— 污染度归零的那一刻，全服迎来胜利广播，天堂之门落成，净化定型后的净土维度开放。
   战役会结束，而你们共同夺回的世界不会。

**单局是心跳，战役是人生。** 一个人的远征是生存挑战，一群人的战役是一部编年史。

## 核心玩法循环

1. **准备** —— 方舟商店采购（价格逐日 ±30% 浮动，每日有紧缺物资）、天赋星图配点、职业快切、买损耗保单。
2. **发射** —— 发射台规划界面：难度 5 档 × 目的地 3 选 × 职业快切（战役阶段解锁门控）。
3. **深入** —— 侵蚀时钟 30 分钟硬时限；扫描器逐条破译隐藏污染词缀；在希望绿洲喘息，或直捣污染核心。
4. **撤离** —— 找到随机生成的公共返回舱或部署定位器：登记、注能、扛住浊潮蜂拥——防守本身会拖慢发射。
5. **结算** —— 物资折算为全服物资点；完美撤离 / 速通 / 单人远征均有加成，高光写入编年史。
6. **推进** —— 物资点兑换天赋点、驱动商店经济；污染度下降推进战役阶段，解锁新难度与新维度，直至净土。

## 特色系统

| 系统 | 一句话 |
|---|---|
| 🃏 **卡牌构筑** | 3 个 Curios 卡槽，23 张纯净/诅咒卡；诅咒卡带 Tab 键主动技能（短按爆发 / 长按焚卡）与侵蚀代价 |
| 🌟 **天赋星图** | 5 职业 × 92 节点的放射状星图（J 键）：星型节点从中心向外点亮，属性实时生效；每职业一个主动技能（R 键） |
| 🧪 **尘星战役** | 全服污染度计量：核心、屠龙、阶段跃迁逐级解锁内容；污染归零开启净土维度与全服胜利 |
| 🎲 **每局词缀** | 每局随机 1–3 个污染词缀，**进局只揭示第一个**——用扫描器、时间或洞察卡逐步破译其余 |
| 🛒 **市场波动** | 商店价格逐日 ±30% 浮动、每日紧缺物资回收 +50%；有人远征时市场冻结 |
| 🎒 **撤离高潮** | 充能期浊潮波次、可被击穿的净化护罩、一次性的生命维持场——防守本身会拖慢发射 |
| 🛰️ **方舟太空站** | 虚空中的代码生成轨道站台：中央反应堆桁架、青色能源环、信标灯阵；也支持放入自己的 `.nbt` 建筑整体替换 |
| 📊 **信息博弈** | 扫描器读污染浓度/威胁/撤离点方位；寻径卡 HUD 常显撤离指针 |
| 💬 **引导情报** | 聊天栏低频「你知道吗？」小知识：随机配色播报，关键机制以流转的虹彩字体提醒 |

## 快速上手（单机 / 小型服务器）

1. 安装 **Minecraft 26.1.2 + NeoForge**，把 `eden-<版本>.jar` 与前置放入 `mods/`：
   - **必需**：[Curios](https://modrinth.com/mod/curios)（卡槽）、entity_modifier（难度档案）
   - **可选**：Sophisticated Backpacks（背包内容回收）、Just Enough Characters（商店拼音搜索）
2. 进入世界后自动降落方舟轨道站；管理员执行 `/eden setup_ark` 布置发射台、商店、储物柜与编年史墙。
3. 右键发射台选择难度与目的地，出发。目标是把全服污染度打到 0%。
4. 默认按键：**R** 职业主动 · **Tab** 卡牌主动 · **J** 天赋星图（均可在控制设置中改键）。

## 从源码构建

```bash
# 需要 JDK 25
./gradlew build      # 产物在 build/libs/eden-<版本>.jar
./gradlew runClient  # 开发客户端
```

`libs/` 内的第三方依赖 jar 仅为构建便利随仓库分发，版权归各自作者（Curios: LGPL-2.1+ ·
Sophisticated Core/Backpacks: LGPL-3.0 · Just Enough Characters: MIT · entity_modifier: 作者自带前置），
与本模组的授权无关。

## 当前状态与路线图

**α（当前）**：核心闭环 + v1 主体内容已可玩——三远征维度、净化战役、卡池 23 张、词缀揭示、
发射台规划界面、储物柜、编年史战役面板、按人数缩放、屏幕叠加与音效反馈均已实装；本版起方舟为
代码生成的轨道太空站、地形污染采用「原版贴图 + 浅紫罩色」、天赋星图改为星型节点放射布局，
并加入聊天引导小知识。

**接下来（v2）**：浊潮生态（污染液 / 酸雨 / 生态 Boss）、卡牌协同套装、世界复净演出、赛季化与
编年史高光扩展。欢迎在 Issues 里提想法。

## 授权

代码以 [LGPL-3.0-or-later](LICENSE) 授权。

---

### EDEN PROTOCOL (English)

A cooperative **world-reclamation campaign** mod for Minecraft 26.1.2 (NeoForge). Extraction raids are the
heartbeat, but the campaign is the point: live on an orbital ark station, launch into a freshly-generated
polluted world every run, loot and fight under a 30-minute erosion clock, extract via charge-up return pods —
and every haul pushes a server-wide purification campaign one step closer to zero pollution, at which point
the Paradise Gate descends and a purified dimension opens for everyone.

Four pillars: the **Ark hub** (market with daily fluctuation, 92-node radial talent starmap, private lockers,
chronicle wall), the **expedition loop** (five difficulty tiers, three destinations, hidden per-run affixes,
random extraction points with swarm-defense climaxes), the **Duststar campaign** (server pollution meter,
stage-gated unlocks, core destruction and dragon bounties), and the **Paradise endgame** (server-wide victory).

Also features a Curios card-build system (23 cards with curse-card actives on Tab), five classes with R-key
actives, crew-scaled threats, low-frequency "did you know" guidance tips, and an idempotent code-built
orbital station (or bring your own `.nbt` ark).

*Status: **alpha** — the core loop and most v1 systems are playable; art is placeholder in places and
content is still expanding.*

License: LGPL-3.0-or-later.
