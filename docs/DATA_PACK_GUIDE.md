# 数据包文件导读

> 配套文档：[CODE_GUIDE.md](CODE_GUIDE.md) 讲的是**代码侧**（方块、GUI、网络、存档）。
> 这份讲**数据包侧**——那些放在 `data/` 和 `assets/` 里的 JSON 文件。
>
> **阅读约定**：每段代码/JSON 上面都标注了它所在的文件路径。
> 标了「✅ 实测」的结论是我在本项目里真跑过验证的，不是抄教程。

---

## 1. 两条路线：代码 vs 数据包

一个 mod 的内容分成两半：

| | 代码侧（Java） | 数据包侧（JSON） |
|---|---|---|
| 放哪 | `src/main/java/` | `src/main/resources/data/` |
| 例子 | 方块行为、界面、网络包 | 配方、掉落、标签、进度 |
| 改了要 | **重新编译**（`gradlew build`） | 重新加载数据包（`/reload` 或重开） |
| 适合 | 逻辑 | 数据 |

**为什么要把数据单独拿出来？**

因为「这个方块合成需要什么」是**数据**，不是**逻辑**。
分开之后，改配方、加个时运掉落，**完全不用碰代码、不用重新编译**——这是 MC 数据包体系的核心价值。

---

## 2. 唯一要记的规则：id → 文件路径

**每种数据都有一个「目录名」，由它的注册表键决定。这个目录名在 26.3 里全是单数。**

我读了 `Registries` 类的常量池确认（下面这些字符串都是它的字面量）：

| 数据种类 | 目录名 | 完整路径 |
|---|---|---|
| 配方 | `recipe` | `data/<命名空间>/recipe/<名字>.json` |
| 战利品表 | `loot_table` | `data/<命名空间>/loot_table/<名字>.json` |
| 进度 | `advancement` | `data/<命名空间>/advancement/<名字>.json` |
| 标签 | `tags` | `data/<命名空间>/tags/<注册表名>/<名字>.json` |
| 附魔 | `enchantment` | `data/<命名空间>/enchantment/<名字>.json` |
| 物品修饰器 | `item_modifier` | `data/<命名空间>/item_modifier/<名字>.json` |
| 谓词 | `predicate` | `data/<命名空间>/predicate/<名字>.json` |
| 伤害类型 | `damage_type` | `data/<命名空间>/damage_type/<名字>.json` |

### ⚠️ 老教程会害你

| 老教程写的 | 26.3 实际 |
|---|---|
| `data/<ns>/recipes/` | **`recipe/`**（单数） |
| `data/<ns>/loot_tables/` | **`loot_table/`**（单数） |
| `data/<ns>/advancements/` | **`advancement/`**（单数） |

**本项目已经踩过这个坑两次**，所以这里标注得很明确。

### 特别说明：方块掉落表多一层 `blocks/`

方块掉落表**不是** `loot_table/<方块名>.json`，而是：

```
data/conservation_table/loot_table/blocks/conservation_table.json
                                 ^^^^^^^ 这一层从哪来的？
```

答案在 `BlockBehaviour$Properties` 的字节码里，我挖出来了：

```java
// 来源：net.minecraft.world.level.block.state.BlockBehaviour$Properties
//      （MC 内部方法，这里是等价重写，方便阅读）
private static Optional<ResourceKey<LootTable>> defaultLootTable(ResourceKey<Block> blockKey) {
    return Optional.of(ResourceKey.create(
        Registries.LOOT_TABLE,
        blockKey.identifier().withPrefix("blocks/")   // ← 给方块 id 加前缀
    ));
}
```

**推导过程**（代入本项目的方块）：

```
方块 id                conservation_table:conservation_table
withPrefix("blocks/")  → conservation_table:blocks/conservation_table
                              ↓ 这就是战利品表的 key
对应文件               data/conservation_table/loot_table/blocks/conservation_table.json
```

> 💡 `withPrefix` 加的是**路径部分**，命名空间不变，所以还是 `conservation_table:` 开头。

顺带解开一个谜团——同一段字节码里有这一行：

```
Objects.requireNonNull(id, "Block id not set")
```

**这就是 26.3 强制要求 `Block.Properties.setId(...)` 的另一个原因**：
不设 `id`，连掉落表的位置都算不出来，注册阶段就抛异常。

---

## 3. 配方（Recipe）

### 3.1 文件位置

📄 **`src/main/resources/data/conservation_table/recipe/conservation_table.json`**

这个文件名（`conservation_table`）**随便起**，只是给人看的。惯例是用产物名。

### 3.2 完整文件（本项目实际内容）

📄 **`src/main/resources/data/conservation_table/recipe/conservation_table.json`**

```json
{
	"type": "minecraft:crafting_shaped",
	"category": "misc",
	"pattern": [
		"PPP",
		"PCP",
		"PPP"
	],
	"key": {
		"P": "#minecraft:planks",
		"C": "minecraft:chest"
	},
	"result": {
		"id": "conservation_table:conservation_table",
		"count": 1
	}
}
```

### 3.3 逐字段讲

#### `type` — 用哪套规则解析

`minecraft:crafting_shaped` = 工作台**有序**合成，对应代码类 `ShapedRecipe`。

可用的**大类**（来自 `RecipeType` 注册表，我列过方法签名）：

```
CRAFTING  SMELTING  BLASTING  SMOKING
CAMPFIRE_COOKING  STONECUTTING  SMITHING  BREWING
```

⚠️ 但 JSON 里写的**不是**上面这些大类名，而是更细的 **serializer 名**：

| JSON 里写 | 说明 |
|---|---|
| `minecraft:crafting_shaped` | 工作台，**有序** |
| `minecraft:crafting_shapeless` | 工作台，**无序** |
| `minecraft:smelting` | 熔炉 |
| `minecraft:blasting` | 高炉 |
| `minecraft:smoking` | 烟熏炉 |
| `minecraft:campfire_cooking` | 营火 |
| `minecraft:stonecutting` | 切石机 |
| `minecraft:smithing_transform` | 锻造台（升级） |

#### `pattern` — 形状，一个字符一格

```
"PPP"    ← 第 1 行
"PCP"    ← 第 2 行
"PPP"    ← 第 3 行
```

**就是 3×3 工作台的布局，照抄下来即可。**

- **空格 = 那格必须空着**（这是表达"空心"的唯一方式）
- 最多 3 行，每行最多 3 字符
- 不需要写满：比如"木棍"只写 `["P", "P"]`

#### `key` — 字符 → 材料 ⚠️ 最容易踩坑

```json
"P": "#minecraft:planks",
"C": "minecraft:chest"
```

**26.3 直接写字符串。** 老教程写的 `{"item": "minecraft:oak_planks"}` 在 26.3 上**会解析失败**。

为什么变了？我查了 `Ingredient` 的 `CODEC`——它用的是 **`holderSet`** codec。`HolderSet` 天然支持"单个 id"和"标签"两种形态，所以能写三种：

| 写法 | 含义 |
|---|---|
| `"minecraft:oak_planks"` | 指定一种物品 |
| `"#minecraft:planks"` | **标签**：该标签下所有物品都行 |
| `["minecraft:oak_planks", "minecraft:birch_planks"]` | 列表：几种都接受 |

> 💡 **推荐用标签**。本项目的配方原本写死 `minecraft:oak_planks`（只认橡木），
> 改成 `#minecraft:planks` 后任意木板都能用——这是实际改进，已验证。

#### `result` — 产物

```json
"id": "conservation_table:conservation_table",
"count": 1
```

26.3 用 **`id`**（老版本用 `item`）。`count` 可省略，默认 1。

这个结构来自 `ItemStackTemplate` 类（`Item` + 数量 + 可选组件），所以理论上还能带附魔等组件。

#### `category` — 只影响配方书分类标签

`building` / `redstone` / `equipment` / `misc`。可有可无。

### 3.4 其他配方类型的写法

**无序合成**（不看摆哪，只看有没有）：

📄 示意（本项目未使用）：`data/<命名空间>/recipe/<名字>.json`

```json
{
	"type": "minecraft:crafting_shapeless",
	"ingredients": ["minecraft:chest", "#minecraft:planks"],
	"result": { "id": "conservation_table:conservation_table", "count": 1 }
}
```

注意这里是 **`ingredients`**（数组），没有 `pattern` 和 `key`。

**烧炼类**（熔炉 / 高炉 / 烟熏 / 营火共用一套字段）：

📄 示意（本项目未使用）：`data/<命名空间>/recipe/<名字>.json`

```json
{
	"type": "minecraft:smelting",
	"ingredient": "minecraft:raw_iron",
	"result": { "id": "minecraft:iron_ingot" },
	"experience": 0.7,
	"cookingtime": 200
}
```

`cookingtime` 单位是**游戏刻**（20 刻 = 1 秒，所以 200 = 10 秒）。

---

## 4. 战利品表（Loot Table）

### 4.1 文件位置

📄 **`src/main/resources/data/conservation_table/loot_table/blocks/conservation_table.json`**

注意那个 **`blocks/` 子目录**——见第 2 章的推导。

### 4.2 它是怎么工作的

**我们没有写任何掉落代码。** `ConservationTableBlock extends Block` 且**没有覆写** `getDrops`，所以继承的是：

```java
// 来源：net.minecraft.world.level.block.state.BlockBehaviour
protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params)
```

但这个方法**本身不产出任何物品**——它只是拿"钥匙"去查表：

```
玩家挖掉方块
   ↓
Block.getDrops(...)                       ← 静态方法
   ↓
block.getLootTable() → conservation_table:blocks/conservation_table
   ↓
从 LOOT_TABLE 注册表查出这张表
   ↓
组 LootParams（带上工具、幸运值等上下文）
   ↓
LootTable 掷骰 → 产出 List<ItemStack>
   ↓
生成掉落物实体
```

**方块的字段里存的是「钥匙」，不是物品**：

```java
// 来源：net.minecraft.world.level.block.state.BlockBehaviour
protected final Optional<ResourceKey<LootTable>> drops;
```

⚠️ **如果那个 JSON 不存在**：查表失败 → 回退到 `LootTable.EMPTY` → **方块挖掉什么都不掉，而且不报错**。

这是个很难查的坑：**"我方块做得挺好，挖了怎么没了？"** —— 大概率就是路径写错了（比如漏了 `blocks/`）。

### 4.3 完整文件（本项目实际内容）

📄 **`src/main/resources/data/conservation_table/loot_table/blocks/conservation_table.json`**

```json
{
	"type": "minecraft:block",
	"pools": [
		{
			"rolls": 1,
			"entries": [
				{
					"type": "minecraft:item",
					"name": "conservation_table:conservation_table"
				}
			],
			"conditions": [
				{
					"condition": "minecraft:survives_explosion"
				}
			]
		}
	]
}
```

### 4.4 逐字段讲

| 字段 | 含义 |
|---|---|
| `type: minecraft:block` | **上下文类型**。这张表是给"方块被破坏"用的，决定了能用哪些条件。实体的表用 `minecraft:entity`，礼物用 `minecraft:gift` |
| `pools` | **抽奖池**列表。每个池**独立掷骰** |
| `rolls: 1` | 这个池掷 **1 次**。写 3 就是"重复抽 3 次" |
| `entries` | 池子里有什么可掉 |
| `type: minecraft:item` + `name` | 条目类型是"掉落某物品"，`name` 是物品 id |
| `conditions` | **什么时候这个池生效** |
| `survives_explosion` | 原版标准条件：方块被**爆炸**摧毁时**不**掉落（这就是苦力怕能炸掉箱子的原因） |

### 4.5 想改的话

**精准采集掉落本体**（经典用法：石头 → 有精准采集掉石头，否则掉圆石）：

📄 示意（本项目未使用）：`data/<命名空间>/loot_table/blocks/<方块名>.json`

```json
{
	"type": "minecraft:block",
	"pools": [
		{
			"rolls": 1,
			"entries": [
				{
					"type": "minecraft:alternatives",
					"children": [
						{
							"type": "minecraft:item",
							"name": "minecraft:stone",
							"conditions": [
								{
									"condition": "minecraft:match_tool",
									"predicate": {
										"predicates": {
											"minecraft:enchantments": [
												{
													"enchantments": "minecraft:silk_touch",
													"levels": { "min": 1 }
												}
											]
										}
									}
								}
							]
						},
						{ "type": "minecraft:item", "name": "minecraft:cobblestone" }
					]
				}
			]
		}
	]
}
```

`minecraft:alternatives` 的语义是：**从上往下找第一个条件成立的**，命中就停。

**时运加成**（在条目里加一条 condition）：

📄 示意（本项目未使用）：`data/<命名空间>/loot_table/blocks/<方块名>.json`

```json
{
	"condition": "minecraft:apply_bonus",
	"enchantment": "minecraft:fortune",
	"formula": "minecraft:ore_drops"
}
```

**多池**（同时掉几组不同的东西）：`pools` 数组里再加一个对象，各池独立掷骰。

### 4.6 也可以在代码里改

📄 来源：`BlockBehaviour$Properties`（本项目暂未使用这些开关）

| 方法 | 作用 |
|---|---|
| `noLootTable()` | **永远不掉落**。适合装饰方块、纯结构方块 |
| `overrideLootTable(Optional.of(key))` | **指向别的战利品表**。适合多个方块共用一张表 |

> ⚠️ 老教程里常见的 **`dropsLike(otherBlock)` 在 26.3 里已经不存在**（我查过 `Properties` 的完整方法列表），改用 `overrideLootTable`。

---

## 5. 标签（Tags）

标签是"一组物品/方块"的集合。上面配方里的 `#minecraft:planks` 就是个标签。

### 5.1 文件位置 ✅ 实测

📄 **`src/main/resources/data/conservation_table/tags/item/<标签名>.json`**

> 这个路径我**实测验证过**：建了一个测试标签跑服务端，日志打出 `tagItemCount=14`，
> 说明 `tags/item/` 是正确的目录名（我没猜，是跑出来的）。

**`tags/` 后面那一层是"注册表名"**：

| 注册表名 | 用途 | 例子 |
|---|---|---|
| `item` | 物品标签 | `tags/item/planks.json` |
| `block` | 方块标签 | `tags/block/mineable/pickaxe.json` |
| `entity_type` | 实体标签 | `tags/entity_type/bosses.json` |
| `enchantment` | 附魔标签 | `tags/enchantment/treasure.json` |

标签名**可以带斜杠**做分组，比如 `minecraft:mineable/pickaxe` 对应文件 `tags/block/mineable/pickaxe.json`。

### 5.2 文件格式

📄 示意（本项目当前未使用；下面这个格式我实测加载成功过）

```json
{
	"values": [
		"minecraft:oak_planks",
		"minecraft:chest",
		"#minecraft:planks"
	]
}
```

- 普通字符串 = 一个具体物品
- `#` 开头 = **引用另一个标签**（可以嵌套，很有用）
- 可选字段 `"replace": true` = 覆盖同名标签而不是合并

### 5.3 怎么用

**在配方里**（本项目已用）：

📄 **`src/main/resources/data/conservation_table/recipe/conservation_table.json`**

```json
"key": {
	"P": "#minecraft:planks",
	"C": "minecraft:chest"
}
```

**在战利品表里**：条目的 `name` 也能写 `#标签`，表示"掉这个标签里的随机一个"。

**在代码里**：

📄 来源：`net.minecraft.core.Registry` / `net.minecraft.tags.TagKey`

```java
TagKey<Item> key = TagKey.create(Registries.ITEM, Conservation_table.id("my_tag"));
for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(key)) {
    // 遍历标签内容
}
```

> 💡 **为什么推荐用标签**：写 `#minecraft:planks` 之后，
> 任何**别的 mod 加的木头**都会自动被接受。写死 `minecraft:oak_planks` 就只认橡木。
> 这是低成本高回报的兼容性改进。

---

## 6. 怎么调试数据包文件

### 6.1 快速重载

改完数据包文件（配方/掉落/标签），不用重启游戏：

```
/reload
```

> ⚠️ 但 `assets/` 下的（贴图、模型、语言）`/reload` **不一定**能更新，
> 那些属于"资源包"，通常要重开客户端。

### 6.2 看日志

路径写错时，游戏**不一定报错**，但日志里常有线索。搜关键词：

| 关键词 | 说明 |
|---|---|
| `Couldn't parse` | JSON 语法或字段名错了 |
| `Failed to load` | 数据包加载失败 |
| `Unknown recipe` | 配方里的物品 id 写错了 |
| `missing following references` | 标签引用了不存在的东西 |

日志位置：`run/logs/latest.log`

### 6.3 程序化验证（推荐）

改完文件后，可以像我验证时那样，在服务端启动钩子里打日志确认：

📄 来源：`src/main/java/dev/hhl19/conservationtable/Conservation_table.java`（临时验证用，验证完要删）

```java
net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
    // 配方
    var recipeKey = ResourceKey.create(Registries.RECIPE, id("conservation_table"));
    boolean recipeLoaded = server.getRecipeManager().byKey(recipeKey).isPresent();

    // 战利品表
    var lootKey = ResourceKey.create(Registries.LOOT_TABLE, id("blocks/conservation_table"));
    boolean lootLoaded = server.reloadableRegistries().lookup()
            .lookup(Registries.LOOT_TABLE)
            .flatMap(reg -> reg.get(lootKey))
            .isPresent();

    Conservation_table.LOGGER.info("recipeLoaded={} lootTableLoaded={}", recipeLoaded, lootLoaded);
});
```

**本项目这两项都实测过 `true`。**

### 6.4 在游戏里看

- **配方**：开工作台 → 左边那本书（配方书）里翻
- **掉落**：挖一个看掉不掉
- **标签**：`/tag <标签名> list` 之类（部分标签有指令支持）

---

## 7. 常见坑速查

| 坑 | 正确做法 |
|---|---|
| 目录写成 `recipes/` `loot_tables/` | 26.3 是**单数** `recipe/` `loot_table/` |
| 方块掉落表漏了 `blocks/` | `loot_table/blocks/<方块名>.json` |
| `key` 写 `{"item": ...}` | 26.3 直接写字符串 |
| `result` 写 `item` | 26.3 用 `id` |
| `pattern` 各行长度不一致 | 会解析失败 |
| `pattern` 用了字符但 `key` 没定义 | **报错** |
| 掉落表路径错了 | 方块**静默不掉落**，不报错 |
| 忘了 `setId(...)` | 注册阶段就抛异常，掉落表也定位不到 |

---

## 8. 相关文件索引

| 文件 | 内容 |
|---|---|
| [CODE_GUIDE.md](CODE_GUIDE.md) | 代码侧：Java 语法、Fabric 概念、方块/GUI/网络/存档、渲染原理 |
| `src/main/resources/data/conservation_table/recipe/conservation_table.json` | 合成配方 |
| `src/main/resources/data/conservation_table/loot_table/blocks/conservation_table.json` | 挖掘掉落 |
| `src/main/resources/assets/conservation_table/` | 资源侧：blockstates / models / items / textures / lang |
| `src/main/resources/fabric.mod.json` | mod 元数据（身份证） |
