# 净土协议 · EDEN PROTOCOL

**一款 Minecraft 合作制净化战役模组 —— 搜打撤只是手段，把被浊潮吞噬的世界一寸寸夺回来，才是目的。**

> 末世浊潮吞没了地表，幸存者退居轨道方舟「净土号」。在这里，每一场三十分钟的远征搜刮都是全服净化
> 战役的一步：带回物资与样本、摧毁污染核心、击落浊潮巨龙——直到全服污染度归零，天堂之门在方舟落下，
> 净土维度向所有人开启。

*Minecraft `26.1.2` · NeoForge · 当前版本 `0.3.4-alpha`（α 开发阶段）*

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
| 🧪 **尘星战役** | 全服污染度计量：核心、屠龙、生态 Boss、阶段跃迁逐级解锁内容；污染归零开启净土维度与全服胜利 |
| 🎲 **每局词缀** | 每局随机 1–3 个污染词缀，**进局只揭示第一个**——用扫描器、时间或洞察卡逐步破译其余 |
| 🛒 **市场波动** | 商店价格逐日 ±30% 浮动、每日紧缺物资回收 +50%；有人远征时市场冻结 |
| 📦 **战利品注入** | 全部战利品箱表（原版 + 任意模组：地牢/矿井/下界遗迹/村民/试炼密室…）按规则自动注入本模组物资；修改器 GUI 内可精细编辑命中规则与每档难度的奖池，保存即生效 |
| 🎒 **撤离高潮** | 充能期浊潮波次、可被击穿的净化护罩、一次性的生命维持场——防守本身会拖慢发射 |
| 🛰️ **方舟太空站** | 虚空中的代码生成轨道站台：中央反应堆桁架、青色能源环、信标灯阵；也支持放入自己的 `.nbt` 建筑整体替换 |
| 📊 **信息博弈** | 扫描器读污染浓度/威胁/撤离点方位；寻径卡 HUD 常显撤离指针 |
| 🌑 **浊潮生态** | 蠕动蔓延的污染积液、三座生态巢穴 Boss（孢子母树/浊鳞巨物/深渊掘凿者）、高风险难度限定的浊雨事件——每个 Boss 都掉落祭坛升星材料 |
| ⚒️ **裂隙祭坛** | 卡牌锻造：三张同品质卡融合升阶；Boss 材料 + 物资点为爱卡升星（上限 5★） |
| 💬 **引导情报** | 聊天栏低频「你知道吗？」小知识：随机配色播报，关键机制以流转的虹彩字体提醒 |

## 快速上手（单机 / 小型服务器）

1. 安装 **Minecraft 26.1.2 + NeoForge**，把 `eden-<版本>.jar` 与前置放入 `mods/`：
   - **必需**：[Curios](https://modrinth.com/mod/curios)（卡槽）、entity_modifier（难度档案）
   - **可选**：Sophisticated Backpacks（背包内容回收）、Just Enough Characters（商店拼音搜索）、[Patchouli](https://modrinth.com/mod/patchouli)（游戏内《远征手册》）
2. 进入世界后自动降落方舟轨道站——发射台、商店、裂隙祭坛、储物柜与编年史墙已内置在站台结构中，无需任何命令（`/eden setup_ark` 保留为手动重置入口）。
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

**0.3.4（测试反馈修复批次）**：①战利品编辑器全面重构——「修改器」战利品页现在打开**全部战利品表清单**：
滚动+搜索列表展示每张箱子表（原版+模组）及其使用结构（本地化提示），选中进入**逐表详细编辑**（跟随全局 /
强制启用 / 强制禁用三态 + 该表独立的分难度奖池），保存即生效；②裂隙祭坛改为**原版容器菜单**（标准槽位
交互：点击/拖拽/Shift 快速移动/快速合成全部可用，结果预览即所得，关闭自动归还材料）；③天赋星图连线改为
节点边缘裁剪的直连线（不再被中途星节点遮挡），未解锁节点 tooltip 明确列出前置名与完成状态。

**0.3.3（S1 矿洞季·批C 剧情层）**：《三位起草人》——肃、融、铭——的 18 页残片散落深处：肃之页藏在
废弃矿井矿车箱，融之页沉睡于试炼密室战利品，铭之页依附繁茂洞穴的发光浆果与深板岩层（第 18 页为
焦黑残页，唯有贯穿全季的三角符文仍在发光）。右键解读即录入全服共享档案；编年史墙新增**「协议余量」
暗星页**——三根绝不解释自己的能量条；污染度降至 50% 以下后，伴随 AI 的「你知道吗？」措辞开始悄悄
向当前领先的一方偏移。行为的记账是隐形的：净化核心/净化剂/生态猎杀/浊雨守序、诅咒卡撤离/放过生态/
不采浊化方块/探访绿洲、解读残页/扫描/查阅编年史……玩家只能自行发现规律。

**0.3.2（S1 矿洞季·批B 修饰包）**：远征世界叠加矿洞季修饰层——地底刷怪压力 +30% 且洞穴生物更多
（蜘蛛→洞穴蜘蛛、僵尸→尸壳概率替换）；随机**局部塌方**事件（10 秒预警、30 秒落石，头顶有掩体即免疫）；
词缀池新增三条洞穴词缀：**深处低语**（地下假脚步声）、**幽暗菌毯**（孢子漂浮 + 地下怪物感知 +40%）、
**矿脉共鸣**（矿石隔墙微光、但挖掘惊动 20 格内所有怪）；浊雨概率下调让位塌方；商店上架火把补给包 /
荧石信标 / 铁镐，浊晶回收价 +1；《远征手册》同步更新。

**0.3.1（S1 矿洞季·批A收尾）**：发射台新增赛季委托行——出发前携带最多 1 个委托（★本周聚焦奖励 ×1.5，
✓ 标记已完成项，再次点击取消携带），所选委托目标常驻发射台提示；编年史墙新增赛季状态行与
「推进赛季」按钮——≥3/5 委托达成后亮起，点击发起 30 秒全服投票，过半在线玩家同意即推进赛季
（战役重置、元进度保留、季终演出）。

**0.3.0（战利品注入 + 修复批次）**：新增基于 `LootTableLoadEvent` 的全量表战利品注入系统——
命中规则（前缀 / 排除 / 命名空间范围）与每档难度的注入奖池（物品 / 权重 / 数量 / 概率 / 抽取次数）
均可在「修改器」的战利品编辑页中调整，保存后自动重载数据包即时生效，覆盖原版与其他模组的全部
箱子表；修复方舟功能方块在每次返回时向上堆叠的问题；修复帕秋莉《远征手册》无法打开（无效的书）
的问题——`book.json` 移至 `data/` 并声明 `use_resource_pack`，手册 4 章 17 条目双语正常加载。

**接下来（v2 余项）**：卡牌协同套装、世界复净演出、赛季化与编年史高光扩展。浊潮生态首批（污染积液 /
生态巢穴 Boss ×3 / 浊雨 / 裂隙祭坛锻造）已在本版实装。欢迎在 Issues 里提想法。

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
actives, crew-scaled threats, low-frequency "did you know" guidance tips, an idempotent code-built
orbital station (or bring your own `.nbt` ark), and the first v2 tidal-ecology wave: creeping tainted
sludge, three ecology-lair bosses whose materials fuel the rift altar's card forging, and high-risk-only
tainted rain events.

*Status: **alpha** — the core loop and most v1 systems are playable; art is placeholder in places and
content is still expanding.*

License: LGPL-3.0-or-later.
