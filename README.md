# 净土协议 · EDEN PROTOCOL

**一款 Minecraft 搜打撤（Extraction Raid）合作模组 —— 带上卡牌，潜入浊潮，把世界一寸寸夺回来。**

> 末世浊潮蔓延，幸存者退居轨道方舟。组队深潜回被污染的地表，在侵蚀时钟走满之前搜刮、战斗、撤离；
> 带回的物资与样本将推进全服共享的「尘星」净化战役——直到污染度归零，净土之门在方舟开启。

*Minecraft `26.1.2` · NeoForge · 当前版本 `0.2.0-alpha`（α 开发阶段）*

---

## 核心玩法循环

1. **方舟枢纽（`eden:ark`）** —— 安全的家。商店采购、天赋配置、个人储物柜、编年史墙都在这里。
2. **发射远征** —— 在发射台选择威胁等级与目的地（污染主世界 / 下界 / 末地），踏入一个**每次重开都全新随机生成**的污染世界。
3. **侵蚀时钟** —— 30 分钟硬时限。侵蚀槽不断累积，等级越高掉血越快；污染词缀、浊息脉冲与环境本身都在催你快走。
4. **搜打撤** —— 采集浊化矿物与样本、开箱、摧毁**污染核心**（全服污染度 -2%）、在**希望绿洲**喘息。
5. **撤离** —— 找到随机生成的公共撤离点，或自己部署定位器。登记、注能、扛住**浊潮蜂拥**与净化护罩的充能消耗，满池发射。
6. **结算** —— 物资折算为全服物资点；完美撤离（零承伤）/ 速通 / 单人远征均有额外奖励。

## 特色系统

| 系统 | 一句话 |
|---|---|
| 🃏 **卡牌构筑** | 3 个 Curios 卡槽，23 张纯净/诅咒卡；诅咒卡带 Tab 键主动技能（短按爆发 / 长按焚卡）与侵蚀代价 |
| 🌳 **职业与天赋** | 5 职业 × 61 节点的代码绘制天赋地图（J 键），属性实时生效；每职业一个主动技能（R 键） |
| 🧪 **尘星战役** | 全服共享的世界污染度：摧毁核心、屠龙推进阶段，逐级解锁难度档与下界/末地远征；污染归零开启净土维度与全服胜利 |
| 🎲 **每局词缀** | 每局随机 1–3 个污染词缀，**进局只揭示第一个**——用扫描器、时间或洞察卡逐步破译其余 |
| 🛒 **市场波动** | 商店价格逐日 ±30% 浮动、每日紧缺物资回收 +50%；有人远征时市场冻结 |
| 🎒 **撤离高潮** | 充能期浊潮波次、可被击穿的净化护罩、一次性的生命维持场——防守本身会拖慢发射 |
| 📊 **信息博弈** | 扫描器读污染浓度/威胁/撤离点方位；寻径卡 HUD 常显撤离指针 |

## 快速上手（单机 / 小型服务器）

1. 安装 **Minecraft 26.1.2 + NeoForge**，把 `eden-<版本>.jar` 与前置放入 `mods/`：
   - **必需**：[Curios](https://modrinth.com/mod/curios)（卡槽）、entity_modifier（难度档案）
   - **可选**：Sophisticated Backpacks（背包内容回收）、Just Enough Characters（商店拼音搜索）
2. 进入世界后自动降落方舟；管理员执行 `/eden setup_ark` 布置发射台、商店、储物柜与编年史墙。
3. 右键发射台选择难度与目的地，出发。
4. 默认按键：**R** 职业主动 · **Tab** 卡牌主动 · **J** 天赋地图（均可在控制设置中改键）。

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
发射台规划界面、储物柜、编年史战役面板、按人数缩放、屏幕叠加与音效反馈均已实装。

**接下来（v1 收尾 → v2）**：美术贴图全面替换占位图、方舟建筑美化（`.nbt` 结构）、更多污染词缀与
Boss、卡牌协同套装、赛季化与编年史高光。欢迎在 Issues 里提想法。

## 授权

代码以 [LGPL-3.0-or-later](LICENSE) 授权。

---

### EDEN PROTOCOL (English)

A cooperative extraction-raid mod for Minecraft 26.1.2 (NeoForge): launch from an orbital ark into a
freshly-generated polluted world every run, loot and fight under a 30-minute erosion clock, extract via
charge-up return pods, and spend the salvage pushing a server-wide purification campaign — destroy
pollution cores, fell the tainted dragon, and finally unlock the paradise dimension. Features a Curios
card build system (23 cards with curse-card actives), 5 classes × 61 talent nodes, per-run hidden
affixes with progressive intel, a fluctuating market, and crew-scaled threats.

*Status: **alpha** — the core loop and most v1 systems are playable; art is placeholder in places and
content is still expanding.*

License: LGPL-3.0-or-later.
