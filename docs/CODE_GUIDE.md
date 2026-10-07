# conservation_table 代码导读

> 这份文档假设你：**懂一点点编程，但没写过 Minecraft mod，看 Java 代码会懵。**
> 目标是把当前项目里每一行代码"为什么这么写"讲清楚。
>
> 建议阅读顺序：第 1 章 → 第 2 章 → 第 3 章 → 第 4 章。
> 如果你只想搞懂代码，直接跳第 4 章，遇到不懂的名词回第 2/3 章查。

---

## 1. 心智模型：mod 到底是什么

### 1.1 普通程序 vs mod

普通 Java 程序有 `main()`，代码从第一行跑到最后一行。

**Minecraft mod 没有 main()。** 你写的是"**插在别人流程里的钩子**"：

```
普通程序：   你  →  [你的代码从头跑到尾]

MC mod：     Minecraft/Fabric  →  在特定时机  →  调用你的方法  →  回到游戏
```

一句话：

> **mod = 在游戏启动时把自己的东西"注册"进去 + 在游戏运行中"挂上回调"。**

### 1.2 一次完整的时间线

```
1. Java 进程启动
2. Fabric Loader 读 src/main/resources/fabric.mod.json
   → 找到你的入口类 dev.hhl19.conservationtable.Conservation_table
3. 游戏构造各种"注册表"（方块表、物品表、附魔表…）
4. 调用你的 onInitialize()            ← 你的代码在这里插队（注册 attachment、命令）
5. 调用你的 onInitializeClient()      ← 只启动客户端时（注册渲染、按键）
6. 世界加载 → 玩家加入
   → 玩家数据从存档读取时，触发你的 Codec（反序列化）
7. 玩家敲 /conserve deposit
   → Brigadier 找到你注册的命令 → 执行你的方法
8. 存档保存时，MC 把玩家数据写回磁盘 → 再次触发你的 Codec（序列化）
```

**理解这条时间线，就理解了 80% 的 mod 结构。**

---

## 2. 项目文件地图

```
conservation_table-template-26.3/
├── gradle.properties                     ← 改 Minecraft / Fabric 版本的地方
├── build.gradle                          ← 构建脚本（Loom 插件、Java 版本）
├── src/
│   ├── main/                             ← 【双端共用】服务端和客户端都会加载
│   │   ├── java/dev/hhl19/conservationtable/
│   │   │   ├── Conservation_table.java          ← 主入口 ★
│   │   │   ├── store/
│   │   │   │   ├── ConservationStore.java       ← 数据模型 ★
│   │   │   │   └── ModAttachments.java          ← 把数据挂到玩家身上 ★
│   │   │   ├── command/
│   │   │   │   └── ConservationCommand.java     ← 调试命令 ★
│   │   │   └── mixin/ExampleMixin.java          ← 模板自带示例，用不到可删
│   │   └── resources/
│   │       ├── fabric.mod.json                  ← mod 的"身份证"
│   │       ├── assets/conservation_table/       ← 客户端资源（贴图/模型/语言）
│   │       │   ├── blockstates/xxx.json            方块状态
│   │       │   ├── models/block/xxx.json           方块 3D 模型
│   │       │   ├── models/item/xxx.json            物品模型（旧位置）
│   │       │   ├── items/xxx.json              ★ 物品模型定义（1.21.4+ 新增，必须写）
│   │       │   ├── textures/block/xxx.png          16x16 贴图
│   │       │   └── lang/{en_us,zh_cn}.json         名字
│   │       └── data/conservation_table/         ← 服务端数据（掉落/配方）
│   │           ├── loot_table/blocks/xxx.json      挖掉掉落什么（注意单数）
│   │           └── recipe/xxx.json                 合成配方（注意单数）
│   └── client/                           ← 【仅客户端】专用服务器不会加载
│       └── java/.../client/
│           ├── Conservation_tableClient.java    ← 客户端入口
│           └── Conservation_tableDataGenerator.java ← 数据生成入口
└── docs/CODE_GUIDE.md                    ← 你正在看的这份
```

**为什么要分 `main` 和 `client`？**
因为专用服务器（dedicated server）没有图形界面。渲染、按键、屏幕这些类在服务端根本不存在，
写进 `main` 会导致服务器崩溃。所以 Fabric 把源码集拆成两个，服务端只加载 `main`。

---

## 3. Java 语法速通（本项目实际用到的部分）

### 3.1 `package` 和 `import`

```java
package dev.hhl19.conservationtable.store;   // 我这个文件属于哪个"包"
import net.minecraft.world.item.ItemStack;    // 我要用到别人的东西
```

- `package` 必须和**文件夹路径**对应：`dev/hhl19/conservationtable/store/`。
- Java 里的类全名是 `包名.类名`，比如 `net.minecraft.world.item.ItemStack`。
  全名太长，用 `import` 简写成 `ItemStack`。

> 你看到 `net.minecraft.*` 都是官方游戏代码，`net.fabricmc.*` 是 Fabric 加载器/API，
> `dev.hhl19.*` 是你自己的。

### 3.2 类（class）、接口（interface）、实现（implements）

```java
public class Conservation_table implements ModInitializer {
    @Override
    public void onInitialize() { ... }
}
```

- `class` = 一个"东西"，可以装字段和方法。
- `interface` = 一份"契约"：只规定**有哪些方法**，不写实现。
  `ModInitializer` 就是 Fabric 给的契约，它只要求你实现 `onInitialize()`。
- `implements` = "我签了这份契约"。
- `@Override` 是注解，意思是"我这是在实现接口要求的方法"。
  **写错了方法名编译会报错**，是个安全网。

> 这就是 mod 的"插队"机制：Fabric 不认识你的类，但它认识 `ModInitializer` 接口。
> 它只要在 `fabric.mod.json` 里看到你声明了这个入口，就会去调用你的 `onInitialize()`。

### 3.3 访问修饰符、`static`、`final`

```java
public    static    final    String MOD_ID = "conservation_table";
 ↑          ↑         ↑
 谁可见    属于类    不可改
```

| 修饰符 | 含义 |
|---|---|
| `public` | 任何地方都能访问 |
| `private` | 只有**本类内部**能访问 |
| （什么都不写） | 只有同包能访问 |
| `static` | 属于**类本身**，不属于某个对象实例。用 `类名.成员` 访问 |
| `final` | 不能再改（变量=常量，方法=不可被覆写，类=不可被继承） |

例：
```java
public static final long DEFAULT_WITHDRAW = 64L;   // 常量，全大写是约定

public static ConservationStore storeOf(Player player) { ... }
//      ↑ 调用方式：ModAttachments.storeOf(player)
//        不需要先 new ModAttachments()，因为是 static
```

> **为什么 `ModAttachments` 的构造函数是 `private`？**
> ```java
> private ModAttachments() { }
> ```
> 这是个工具类，只需要它的 `static` 成员，不该被 new。这样写等于告诉编译器和其他人：
> "别实例化我"。

### 3.4 泛型 `<T>`：给容器标注"装什么"

```java
List<Entry> entries            // 一个列表，里面装的是 Entry
Codec<Entry>                   // 一个编解码器，处理 Entry
AttachmentType<ConservationStore>   // 一个 attachment，携带 ConservationStore
```

`<...>` 里的东西叫**类型参数**。`List` 本身只是个"列表"，
`List<Entry>` 才明确"这是装 `Entry` 的列表"。

好处：编译器帮你检查。你往 `List<Entry>` 里塞 `String` 会直接编译报错，而不是运行到一半才崩。

有时需要显式指定：
```java
AttachmentRegistry.<ConservationStore>create(...)
//               ↑ 告诉编译器：这里的 T 是 ConservationStore
```
因为后面的 lambda 让编译器推不出 `T` 是什么，所以手动点明。

### 3.5 Lambda 表达式 `->` 和方法引用 `::`

这两个是"**把函数当作参数传递**"的简写，**是本项目里出现最多、也最容易看懵的语法**。

**（a）Lambda：`参数 -> 函数体`**

```java
// 完整写法（匿名内部类）
new Consumer<Builder<ConservationStore>>() {
    public void accept(Builder<ConservationStore> builder) {
        builder.initializer(...).persistent(...);
    }
}

// Lambda 简写
builder -> builder.initializer(...).persistent(...)
```

读作："**给我一个 builder，我就对它做这些事**"。

**（b）方法引用 `::`：`类::方法名`**

```java
ConservationStore::new          // 等价于 () -> new ConservationStore()
Entry::template                 // 等价于 entry -> entry.template()
ConservationCommand::list       // 等价于 ctx -> list(ctx)
Conservation_table::id          // 等价于 path -> Conservation_table.id(path)
```

**（c）为什么 API 要这么设计？**

看这一行：
```java
.initializer(ConservationStore::new)
```
`initializer` 要的是一个"**需要时才生成默认值**的函数"，而不是"立刻生成好的值"。
这样如果玩家数据里已经有仓库了，就压根不需要 new 一个。

---

### 3.6 `record`：专门装数据的类

```java
public record Entry(ItemStack template, long count) { ... }
```

一行就等价于传统写法的一大坨：

```java
// record 自动帮你生成了这些：
public final class Entry {
    private final ItemStack template;
    private final long count;

    public Entry(ItemStack template, long count) {   // 构造
        this.template = template;
        this.count = count;
    }
    public ItemStack template() { return template; } // 读取（注意：方法名就是字段名！）
    public long count() { return count; }
    public boolean equals(Object o) { ... }          // 值相等
    public int hashCode() { ... }
    public String toString() { ... }
}
```

**要点：**
- record 是**不可变**的（字段都是 `final`）。
- 读字段用的是**方法**：`entry.template()`，**不是** `entry.template`。这个新手最容易写错。
- 本项目里"改数量"是**造一个新的 Entry**：
  ```java
  entries.set(i, new Entry(entry.template(), remaining));   // 换掉旧的
  ```

**紧凑构造器**（紧凑形式，用来做校验/归一化）：
```java
public record Entry(ItemStack template, long count) {
    public Entry {                      // 没有参数列表！
        ItemStack single = template.copy();
        single.setCount(1);
        template = single;              // 赋值给参数 = 赋值给字段
    }
}
```
这段的作用：**保证 template 永远是"1 个"**，实际数量只记在 `count` 里。
不管从哪条路径创建 Entry（代码里 new、还是从存档反序列化），都会经过这里，规则统一。

---

### 3.7 `@Override`

```java
@Override
public void onInitialize() { ... }
```
纯粹是给编译器的断言："我确信我在覆盖父类/接口的方法"。
名字打错（比如写成 `onInit`）会立刻报错，而不是默默失效。

### 3.8 基本类型 `int` / `long`

| 类型 | 位数 | 范围 |
|---|---|---|
| `int` | 32 | 约 ±21 亿 |
| `long` | 64 | 约 ±922 亿亿 |

**本项目数量全部用 `long`**，因为你想做"无限格数仓库"，21 亿可能不够。
写常量时加 `L` 后缀：`64L`。

代价：`ItemStack.setCount()` 只接受 `int`，所以代码里有这种转换：
```java
int batch = (int) Math.min(remaining, maxStack);   // (int) 是强制类型转换
```

---

## 4. Fabric / Minecraft 关键概念

### 4.1 入口点（Entrypoint）

`fabric.mod.json` 里声明：

```json
"entrypoints": {
    "main":       ["dev.hhl19.conservationtable.Conservation_table"],
    "client":     ["dev.hhl19.conservationtable.client.Conservation_tableClient"],
    "fabric-datagen": ["...Conservation_tableDataGenerator"]
}
```

| 入口 | 接口 | 什么时候跑 | 放什么 |
|---|---|---|---|
| `main` | `ModInitializer` | 双端（客户端+服务端）都跑 | 注册方块/物品/命令/attachment |
| `client` | `ClientModInitializer` | 只有客户端跑 | 渲染、按键、屏幕 |
| `fabric-datagen` | `DataGeneratorEntrypoint` | 只有执行 `runDatagen` 时跑 | 自动生成配方/模型/语言文件 |

**判断标准**：如果这段代码引用了 `net.minecraft.client.*` 里的东西 → 必须放 `client`。

---

### 4.2 事件（Event）：Fabric 的"钩子"系统

```java
CommandRegistrationCallback.EVENT.register(ConservationCommand::register);
```

拆开看：
- `CommandRegistrationCallback` 是 Fabric 定义的一个"事件类型"。
- `.EVENT` 是这个事件的**全局实例**。
- `.register(回调)` = "游戏事件发生时，请调用我这个函数"。

游戏在准备命令表的时机会遍历所有注册的回调，依次调用。

> **关键理解**：`register` 只是"登记"，不是"立刻执行"。
> `ConservationCommand::register` 这个方法会在游戏搭好命令表**之后**才被调用。

**注册时机很重要**：`attachment` 类型必须在"读玩家存档"**之前**注册完，
所以 `onInitialize()` 里第一件事就是 `ModAttachments.init()` —— 见 4.6。

---

### 4.3 `Identifier`：所有东西的"身份证号"

```java
public static Identifier id(String path) {
    return Identifier.fromNamespaceAndPath(MOD_ID, path);
}
```

格式是 `命名空间:路径`，例如：
- `minecraft:diamond_sword`（原版物品）
- `conservation_table:store`（你的 attachment）

**为什么需要它？** 全世界有无数 mod，大家都要注册"东西"。
用 `命名空间:名字` 就能保证不冲突——你的 `conservation_table:table` 和别人的 `othermod:table` 是不同的东西。

`id("store")` 的结果就是 `conservation_table:store`。

> 本项目的 `MOD_ID = "conservation_table"`，必须和 `fabric.mod.json` 里的 `"id"` 一致。

---

### 4.4 注册表（Registry）

游戏里所有"种类"的东西都存在注册表里：

```java
BuiltInRegistries.ITEM        // 物品表
BuiltInRegistries.BLOCK       // 方块表
Registries.ENCHANTMENT        // 附魔表；注意这是"键"，不是表本身
```

**注意这个坑（我实际踩过）**：

```java
Registries.ITEM.byNameCodec()            // ❌ 编译错误
BuiltInRegistries.ITEM.byNameCodec()     // ✅
```

- `Registries.ITEM` 是 `ResourceKey<Registry<Item>>` —— 只是**钥匙**（标识"哪张表"）
- `BuiltInRegistries.ITEM` 才是**表本身**（`DefaultedRegistry<Item>`）

常用操作：
```java
BuiltInRegistries.ITEM.getKey(item)   // 物品 → 它的 Identifier
BuiltInRegistries.ITEM.byNameCodec()  // 得到一个 Codec，用于序列化成 "minecraft:diamond_sword"
```

---

### 4.5 数据组件（Data Component）—— 26.3 最核心的概念

**现代 MC 里，物品 = 物品类型 + 一堆数据组件。**

```
ItemStack = Item (diamond_sword)
          + DataComponentMap {          ← 这一坨就是"附魔、名字、耐久…"
                minecraft:enchantments = {minecraft:sharpness: 5}
                minecraft:custom_name  = "我的剑"
                minecraft:damage       = 120
            }
```

所以：
- 一把**附魔**钻石剑和一把**普通**钻石剑，`Item` 相同，**组件不同** → 是两个不同的东西。
- 这正是你的需求「附魔不能和普通混在一起」的实现基础。

**相关 API：**

| 方法 | 作用 |
|---|---|
| `ItemStack.isSameItemSameComponents(a, b)` | 判断是否"同种"（忽略数量） |
| `stack.getComponentsPatch()` | 取出**与默认值的差集**（普通剑返回空） |
| `DataComponentPatch.isEmpty()` | 有没有非默认组件 |

> ⚠️ **一个非常反直觉的点**：`ItemStack` **没有**重写 `equals` / `hashCode`（MC 故意的）。
> 这意味着你**不能**把它当 `HashMap` 的键——`map.get(stack)` 永远查不到。
> 这就是为什么 `ConservationStore` 里用的是 **`List` + 手写循环比对**，而不是 `Map`。

---

### 4.6 Codec：MC 的序列化框架

**Codec 是这份代码里最"外星"的东西，但它其实是最好用的工具。**

#### 它解决什么问题？

存档要保存你的数据。传统做法是手写"读"和"写"两套代码，很容易不一致。

`Codec<T>` 把**读和写合并成一个对象**：

```
Codec<ConservationStore>
    ├─ encode(对象) → NBT      （存档时）
    └─ parse(NBT)   → 对象      （读档时）
```

**定义一次，读写都走同一份规则，不可能对不上。**

而且同一个 Codec 还能输出成 JSON、网络包等其它格式——这就是 MC 全用它的原因。

#### 怎么读这些代码？

**（a）内置的 Codec**

```java
Codec.LONG     // 处理一个 long
Codec.STRING   // 处理一个字符串
ItemStack.CODEC // 处理一个 ItemStack（MC 已经写好了）
```

**（b）从一个 Codec 组装出更复杂的 Codec**

```java
Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ItemStack.CODEC.fieldOf("item").forGetter(Entry::template),   // 字段 "item"  ← Entry.template()
        Codec.LONG.fieldOf("count").forGetter(Entry::count)           // 字段 "count" ← Entry.count()
).apply(instance, Entry::new));                                       // 用两个字段 new Entry(...)
```

逐块翻译：
- `.fieldOf("item")` → "这个值存在名叫 `item` 的字段里"
- `.forGetter(Entry::template)` → "**写**的时候，用 `Entry::template` 取值"
- `.apply(instance, Entry::new)` → "**读**的时候，用 `Entry::new(a, b)` 造对象"
- `RecordCodecBuilder` 名字里的 "Record" 就是说"专门为 record 用的"

**（c）`.listOf()` —— 变成列表**

```java
Entry.CODEC.listOf()     // Codec<List<Entry>>
```

**（d）`.xmap(正向, 反向)` —— 类型转换**

```java
public static final Codec<ConservationStore> CODEC =
        Entry.CODEC.listOf().xmap(ConservationStore::new, ConservationStore::snapshot);
```
- 左（正向，读）：`List<Entry>` → `ConservationStore`
- 右（反向，写）：`ConservationStore` → `List<Entry>`

**（e）实际存出来长什么样？**（我在服务端实测抓的）

```
[{count:10L, item:{id:"minecraft:diamond_sword", count:1}},
 {count:3L,  item:{id:"minecraft:diamond_sword", count:1,
                   components:{"minecraft:enchantments":{"minecraft:sharpness":5}}}}]
```

看，附魔被完整保存下来了。

#### 为什么会有 "Ops"（`NbtOps` / `RegistryOps`）？

Codec 本身不关心"输出成什么格式"，这个由 `DynamicOps` 决定：

| Ops | 输出 |
|---|---|
| `NbtOps.INSTANCE` | NBT（存档格式） |
| `JsonOps` | JSON |

而 **`RegistryOps` 是"带注册表访问能力的 Ops"**：

```java
RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, source.registryAccess());
```

**为什么需要它？** 因为**附魔是"数据包注册表"里的东西**，
编码附魔时得去查表把 `Holder` 转换成名字（`minecraft:sharpness`）。
没有 `RegistryOps` 就查不到表，附魔就存不下来。

> 我一开始担心这个：如果 Fabric 保存 attachment 时用的 Ops 不带注册表，
> 附魔就会存失败。后来得到的结论是**安全的**——因为
> **原版玩家背包里的附魔物品走的是同一条序列化路径**，它必须能工作。

---

### 4.7 Attachment：把数据"挂"到已有对象上

**问题**：你想给玩家加一个"仓库"，但 `Player` 类不是你的，不能改。

**三种解法**：
1. 继承 `Player` → 不可能，游戏自己 new 的
2. Mixin 改字节码 → 太重，容易和别的 mod 打架
3. **Attachment** → Fabric 给的官方方案 ✅

```java
public static final AttachmentType<ConservationStore> STORE =
        AttachmentRegistry.<ConservationStore>create(Conservation_table.id("store"), builder -> builder
                .initializer(ConservationStore::new)      // 没有时，默认建一个空的
                .persistent(ConservationStore.CODEC)     // 存进存档（关键！不写就不会保存）
                .copyOnDeath()                           // 玩家死亡重生后，数据带过去
        );
```

用起来就像对象本来就有的字段：

```java
ConservationStore store = player.getAttached(STORE);       // 读
player.setAttached(STORE, store);                          // 写
```

**四个要点：**

| 要点 | 说明 |
|---|---|
| `.persistent(codec)` | 不调用它 → 数据只存在内存，重启就没 |
| `.initializer(...)` | 玩家第一次访问时自动创建 |
| `.copyOnDeath()` | 死亡不掉落（对"仓库"这种需求必须开） |
| **注册时机** | 必须在**读取玩家存档之前**完成注册，否则存档里的数据找不到对应类型，会被丢弃 |

最后一条就是 `ModAttachments.init()` 存在的原因：

```java
@Override
public void onInitialize() {
    // 附着类型必须在世界/玩家数据加载前注册完毕
    ModAttachments.init();
    ...
}
```

这个方法**方法体是空的**——它的唯一作用就是**强制 Java 加载 `ModAttachments` 这个类**，
从而触发 `static` 字段（`STORE`）的初始化。这是一种常见技巧，靠的是：

> Java 规定：**类只在第一次被使用时才加载**，而加载时会执行所有 `static` 字段的初始化代码。

---

### 4.8 Brigadier：命令的树形结构

**Brigadier** 是 Mojang 的命令解析库，命令是一棵**树**：

```
conserve
├── (直接执行) → usage      ← 敲 /conserve 不跟参数
├── list    → list          ← 叶子
├── deposit → deposit       ← 叶子
└── withdraw
    ├── <item>  → withdraw(64)              ← 有 item 参数就能执行
    └── <count> → withdraw(count)           ← 再多一个 count 参数
```

对应的代码：

```java
dispatcher.register(Commands.literal("conserve")
        .executes(ConservationCommand::usage)
        .then(Commands.literal("list").executes(ConservationCommand::list))
        .then(Commands.literal("deposit").executes(ConservationCommand::deposit))
        .then(Commands.literal("withdraw")
                .then(Commands.argument("item", ItemArgument.item(buildContext))
                        .executes(ctx -> withdraw(ctx, DEFAULT_WITHDRAW))
                        .then(Commands.argument("count", LongArgumentType.longArg(1L))
                                .executes(ctx -> withdraw(ctx, LongArgumentType.getLong(ctx, "count")))))));
```

| API | 含义 |
|---|---|
| `Commands.literal("list")` | **固定词**，玩家必须原样输入 `list` |
| `Commands.argument("item", ...)` | **参数**，名字叫 `item` |
| `.then(...)` | 挂一个**子节点** |
| `.executes(...)` | "走到这个节点时，执行这个函数" |
| `ItemArgument.item(buildContext)` | 内置参数类型：会解析物品 id，还带 tab 补全 |
| `LongArgumentType.longArg(1L)` | 内置参数类型：整数，最小 1 |

**执行函数的返回值是 `int`**：（约定）0/负数=失败，正数=成功（常表示"影响了几个"）。

**`CommandSourceStack` = "谁在敲这条命令"**：

```java
ServerPlayer player = ctx.getSource().getPlayer();   // 玩家敲的 → 玩家对象
                                                     // 控制台敲的 → null
if (player == null) { return 0; }                    // 所以必须判空
```

`sendSuccess(消息, 是否广播给其他人)` / `sendFailure(消息)` 用来回话。

---

## 5. 逐文件精读

### 5.1 `Conservation_table.java` —— 主入口

```java
public class Conservation_table implements ModInitializer {
    public static final String MOD_ID = "conservation_table";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModAttachments.init();                                         // ← ①
        CommandRegistrationCallback.EVENT.register(ConservationCommand::register);  // ← ②
        LOGGER.info("conservation_table initialized");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
```

| 行 | 做什么 | 为什么 |
|---|---|---|
| ① | 触发 attachment 注册 | 必须早于读玩家存档（见 4.7） |
| ② | 登记"命令注册回调" | 游戏搭命令表时会调你的 `register` |

`LOGGER` 是你的日志出口，会出现在 `run/logs/latest.log`。
搜 `conservation_table` 就能过滤出你自己的日志——**调试就靠它**。

---

### 5.2 `ModAttachments.java` —— 数据挂载

```java
public final class ModAttachments {
    public static final AttachmentType<ConservationStore> STORE =
            AttachmentRegistry.<ConservationStore>create(Conservation_table.id("store"), builder -> builder
                    .initializer(ConservationStore::new)
                    .persistent(ConservationStore.CODEC)
                    .copyOnDeath());

    private ModAttachments() { }        // 工具类，禁止 new

    public static void init() { }       // 空方法：唯一目的是触发类加载

    public static ConservationStore storeOf(Player player) {
        ConservationStore store = player.getAttached(STORE);
        if (store == null) {
            store = new ConservationStore();
            player.setAttached(STORE, store);
        }
        return store;
    }
}
```

**`storeOf` 为什么手写判空，不用 Fabric 现成的 `getAttachedOrCreate`？**

Fabric 有 `player.getAttachedOrCreate(STORE)`，但它的语义是"**或创建**"——
不同版本里究竟是"只创建"还是"创建并写入"有歧义。
手写这 5 行虽然啰嗦，但**行为百分之百确定**：一定把新对象 `setAttached` 进去。

> 这是个通用的工程判断：**当 API 语义不确定时，宁可自己写清楚。**

---

### 5.3 `ConservationStore.java` —— 数据模型（核心）

**数据结构选择过程（这段最值得看）：**

| 想用的方案 | 结论 |
|---|---|
| `Map<Item, Long>` | ❌ 不能区分附魔（`Item` 不含组件） |
| `Map<ItemStack, Long>` | ❌ **`ItemStack` 没有 `equals`/`hashCode`**，当键会查不到 |
| **`List<Entry>` + 手写比对** | ✅ 用 `isSameItemSameComponents` 显式判断 |

```java
private final List<Entry> entries = new ArrayList<>();
```

**核心方法：**

```java
/** 与 probe 完全同种（物品 + 组件）的数量 */
public long get(ItemStack probe) {
    for (Entry entry : entries) {
        if (ItemStack.isSameItemSameComponents(entry.template(), probe)) {
            return entry.count();
        }
    }
    return 0L;
}
```

```java
public void deposit(ItemStack stack, long amount) {
    if (!stack.isEmpty() && amount > 0) {
        merge(stack, amount);
    }
}

private void merge(ItemStack stack, long amount) {
    for (int i = 0; i < entries.size(); i++) {
        Entry entry = entries.get(i);
        if (ItemStack.isSameItemSameComponents(entry.template(), stack)) {
            entries.set(i, new Entry(entry.template(), entry.count() + amount));  // 加数量
            return;
        }
    }
    entries.add(new Entry(stack, amount));   // 新品种，新增一条
}
```

```java
public long withdraw(ItemStack probe, long amount) {
    if (amount <= 0) return 0L;
    for (int i = 0; i < entries.size(); i++) {
        Entry entry = entries.get(i);
        if (!ItemStack.isSameItemSameComponents(entry.template(), probe)) continue;

        long taken = Math.min(entry.count(), amount);     // 不许多取
        long remaining = entry.count() - taken;
        if (remaining == 0) {
            entries.remove(i);                            // ★ 归零 → 条目消失
        } else {
            entries.set(i, new Entry(entry.template(), remaining));
        }
        return taken;
    }
    return 0L;
}
```

**几个值得注意的写法：**

| 写法 | 含义 |
|---|---|
| `Math.min(entry.count(), amount)` | 要 999 个但只有 42 个 → 给 42 |
| `entries.remove(i)` | **你要的"数量归零图标消失"就在这里** |
| `entry.template()` 而不是 `entry.template` | record 读字段用**方法** |
| `amount <= 0` 提前返回 | 防御式编程，避免负数把数量算乱 |

**两个构造器：**

```java
public ConservationStore() { }                         // 空的，新玩家用
private ConservationStore(List<Entry> entries) { ... } // 从存档读出来时用
```

第二个是 `private`：因为**只有 Codec 会调用它**（见 `xmap` 的第一参数）。
它里面会把重复的条目合并（万一存档里数据异常）。

---

### 5.4 `ConservationCommand.java` —— 调试命令

#### `deposit`：把主手物品存进去

```java
ItemStack held = player.getMainHandItem();
if (held.isEmpty()) { ... return 0; }

int amount = held.getCount();
ModAttachments.storeOf(player).deposit(held, amount);   // ← 先存（此时 held 还有效）
player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);  // ← 再清空手
```

**注意顺序**：先 `deposit` 再清空。
因为 `deposit` 内部会 `entry.template()` 做 `copy()`（在 record 的紧凑构造器里），
所以之后清空主手不会影响已存的数据。

#### `withdraw`：取出来

```java
ItemStack probe = ItemArgument.getItem(ctx, "item").createItemStack(1);  // ① 造探针
long taken = ModAttachments.storeOf(player).withdraw(probe, amount);     // ② 从仓库扣
// ③ 分批发给玩家
int maxStack = probe.getMaxStackSize();      // 通常 64，但剑是 1、药水是 16…
long remaining = taken;
while (remaining > 0) {
    int batch = (int) Math.min(remaining, maxStack);
    ItemStack stack = probe.copy();
    stack.setCount(batch);
    if (!player.getInventory().add(stack)) {
        player.drop(stack, false, Prediction.SERVER_ONLY);   // 背包满了 → 掉在地上
    }
    remaining -= batch;
}
```

**为什么要"探针（probe）"？**
因为仓库区分组件，所以"取什么"必须用**完整的物品**（含附魔）来表示，
而不是只给一个 `Item`。`probe` 就是"用户想取的那种物品的样本"。

`createItemStack(1)` 会把命令里写的组件（如 `[minecraft:enchantments={...}]`）**解析进探针**，
所以 `probe` 和仓库里的条目能被 `isSameItemSameComponents` 匹配上。

#### 三个显示辅助方法

```java
/** vanilla 物品省掉 "minecraft:" 前缀，让整行能塞进聊天栏 */
private static String shortId(ItemStack template) {
    Identifier id = BuiltInRegistries.ITEM.getKey(template.getItem());
    return id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? id.getPath() : id.toString();
}
```
`? :` 是三元运算符：`条件 ? 真时的值 : 假时的值`。
结果是 vanilla 显示 `diamond_sword`（短），mod 物品显示 `othermod:thing`（完整，避免歧义）。

```java
/** 组件的命令写法，可直接粘到 withdraw 的物品 id 后面 */
private static String patchArgument(CommandSourceStack source, DataComponentPatch patch) {
    if (patch.isEmpty()) return null;                     // 普通物品：没有这行

    RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, source.registryAccess());
    if (!(DataComponentPatch.CODEC.encodeStart(ops, patch).result().orElse(null)
            instanceof CompoundTag compound)) {
        return null;
    }
    StringBuilder text = new StringBuilder("[");
    for (String key : compound.keySet()) {
        if (text.length() > 1) text.append(',');
        text.append(key).append('=').append(compound.get(key));
    }
    return text.append(']').toString();
}
```

这段做了一件"翻译"：
```
Codec 编出来的 NBT map：  {"minecraft:enchantments":{"minecraft:sharpness":1}}
                            ↓ 转成命令语法
命令参数形式：              [minecraft:enchantments={"minecraft:sharpness":1}]
```

- `instanceof CompoundTag compound` 是"**模式匹配**"：先判断类型，是的话顺手赋给 `compound`
- `.result().orElse(null)`：把 `DataResult`（可能成功可能失败）取出结果，失败就给 null
- `StringBuilder` 是拼接字符串的高效写法（比反复 `+` 好）

**为什么值得做这个转换？** 因为 NBT map 形式和命令语法"像但不能直接粘贴"
（`{...}` vs `[...]`，`:` vs `=`）。转一下，列表里那行就能直接用——**少一个坑**。

---

## 6. 设计决策记录（为什么这样写）

| 决策 | 理由 |
|---|---|
| 数据挂**玩家**而不是方块 | 你要的是"个人随身仓库"；挂方块的话别人也能开你的仓库 |
| 数量用 `long` | 解决"格子不够"是核心诉求，21 亿可能不够 |
| 按**物品+组件**区分 | 附魔/命名不同的剑如果合并，取出来会**丢附魔**——那是数据丢失 |
| 用 `List` 而不是 `Map` | `ItemStack` 没有 `equals`/`hashCode`，当键会失效 |
| `.copyOnDeath()` | 仓库因死亡丢失太惩罚性 |
| 服务端权威 | 数据只在服务端改，客户端只发请求——否则就是作弊漏洞 |
| 手写 `storeOf` 判空 | 不用语义有歧义的 `getAttachedOrCreate` |

---

## 7. MC 26.3 的坑（和网上老教程的差异）

**这个版本很新，网上 1.20/1.21 的教程直接照抄会编译不过。**

| 你可能在老教程看到 | 26.3 的实际情况 |
|---|---|
| `ResourceLocation` | 改名成 **`Identifier`** |
| `Registries.ITEM.xxx` 当注册表用 | 它只是**键**；注册表本体是 **`BuiltInRegistries.ITEM`** |
| `CommandManager.literal` | 改名成 **`Commands.literal`** |
| `ServerPlayerEntity` | 改名成 **`ServerPlayer`**，且 `Player extends Avatar` |
| `NbtCompound` / `CompoundTag` | 这里是 **`CompoundTag`** |
| 直接操作 NBT 存档 | 现在是 **`Codec` + `ValueOutput`/`ValueInput`** 框架 |
| `player.drop(stack, false)` | 多了参数：**`drop(stack, false, Prediction.SERVER_ONLY)`** |
| 所有 mod 数据都用 Mixin 挂 | 优先用 **Attachment**，更安全 |
| `GuiGraphics` + `renderBg` + `renderItem` | 整个 GUI 渲染重写：**`GuiGraphicsExtractor`**，`extractBackground` / `extractContents` / `item` |
| `mouseClicked(double, double, int)` | 改成 **`mouseClicked(MouseButtonEvent, boolean)`**；`hasShiftDown()` 从 `Screen` 挪到事件对象上 |
| `new Block(...)` 直接用 | **必须 `.setId(ResourceKey)`**（方块和物品都是），否则注册时抛异常 |
| `api.itemgroup.v1.ItemGroupEvents` | 改名成 **`api.creativetab.v1.CreativeModeTabEvents`**，方法变成 `modifyOutputEvent(...).register(out -> out.accept(stack))` |
| 数据包目录 `loot_tables/` `recipes/` | **都改成单数**：`loot_table/`、`recipe/`（1.21.5 起） |
| `models/item/xxx.json` 就够物品用了 | ⚠️ **不够**。1.21.4 起物品模型改由 `assets/<ns>/items/<id>.json` 定义，只写 `models/item/` 的话**掉在地上的物品会显示紫黑缺失贴图**（放成方块却正常） |
| `CustomPacketPayload.createType("ns:path")` | ⚠️ 内部用 `withDefaultNamespace`，会把 `ns:path` 整个当路径。**要用 `new CustomPacketPayload.Type<>(id)`** |
| 给箱子贴图 `renderBg` 里手画 | 一行 `blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, w, h, 256, 256)` |
| 类似 `getTooltipFromItem` 手搓提示 | `extractor.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY)` 一行 |

**遇到不确定的 API，可靠的做法是直接查 jar**（我也是这么做的）：

```powershell
# 列出某个类的所有方法签名
javap -cp "<jar路径>" net.minecraft.world.item.ItemStack

# 看整个 jar 里有哪些类
# （用 PowerShell 的 System.IO.Compression.ZipFile 遍历 .class 条目）
```

这比猜和搜教程都准——**因为你查的是你正在用的那个版本**。

---

## 8. 渲染是怎么做到的（GUI 篇）

> 这一章讲 `StoreScreen` 是怎么把那个翻页列表画出来的。
> 代码在 [StoreScreen.java](../src/client/java/dev/hhl19/conservationtable/client/screen/StoreScreen.java)。

### 8.1 先搞清"画一帧"的调用链

MC 每帧对打开的界面调一次 `extractRenderState(...)`。`AbstractContainerScreen` 把它拆成四步：

```
extractRenderState                 ← MC 调用（我们没重写）
 ├─ extractBackground(...)         ← ① 背景：面板 + 槽位底图      【我们重写】
 ├─ extractContents(...)           ← ② 内容：物品图标、文字        【我们重写】
 ├─ extractCarriedItem(...)        ← ③ 鼠标上"拖"着的那个物品（父类处理）
 └─ extractTooltip(...)            ← ④ 悬停提示                    【我们重写】
```

**关键概念**：26.3 的 `extract` 不是"立刻画"，而是"把要画的东西**填进一份渲染状态**"，
之后统一提交。所以方法名是 `extract*` 而不是老版本的 `render*`。

对我们的写法没影响，但看方法名别懵。

### 8.2 坐标系：`leftPos` / `topPos`

- `leftPos` / `topPos` = 面板左上角在屏幕上的位置，**MC 自动算好的**（居中）
- `imageWidth` / `imageHeight` = 我们传给父类构造器的 `176` / `222`

所以我们**所有绘制都写成 `leftPos + 相对X`**，从不写死屏幕坐标。

好处：窗口大小变了、面板尺寸改了，代码一行都不用动。

### 8.3 ① 背景：一行贴图

```java
extractor.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0f, 0f,
        imageWidth, imageHeight, 256, 256);
```

| 参数 | 值 | 含义 |
|---|---|---|
| pipeline | `GUI_TEXTURED` | 用哪套渲染管线（GUI 贴图专用，负责透明与混合） |
| 贴图 | `minecraft:textures/gui/container/generic_54.png` | 画哪张图 |
| x, y | `leftPos, topPos` | 画到屏幕哪里 |
| u, v | `0f, 0f` | 从贴图的哪个像素位置开始取 |
| w, h | `176, 222` | 取多大 |
| 最后两个 | `256, 256` | 贴图尺寸参数 |

> **最后两个参数为什么是 256，而贴图只有 176×222？**
> 我查过原版 `ContainerScreen` 的字节码——**它给这张 176×222 的贴图也传 `256, 256`**。
> 所以照抄原版就行。（我追到 `blit` 的重载链，但没继续深挖内部归一化逻辑；
> 「照抄原版」在这里是最安全的做法。）

**为什么这一步是"一行搞定"**：原版箱子贴图里**已经画好了面板、边框、网格槽位、玩家背包槽位**。
我们不画任何格子，只是把它贴上来。

然后我们盖掉贴图的第 6 行槽位（让给翻页控件）：

```java
extractor.fill(leftPos + 1, topPos + CONTROLS_Y, leftPos + imageWidth - 1,
        topPos + CONTROLS_Y + CONTROLS_H, PANEL);
```

`fill(x1, y1, x2, y2, 颜色)` 画实心矩形，颜色是 `0xAARRGGBB`（AA=不透明度，FF=不透明）。

> **这也是为什么"换成贴图"比"自己画"简单**：`fill` 方案要三十多行描边画格子，
> `blit` 方案只要一行。**同样的效果，代码少了 30 行。**

### 8.4 ② 内容：物品网格

```java
int start = page * PER_PAGE;                          // 本页第一个物品在总列表里的下标
for (int i = 0; i < PER_PAGE; i++) {
    int index = start + i;
    if (index >= list.size()) break;                  // 这一页没装满，剩下的格子空着
    int cx = leftPos + GRID_X + (i % COLS) * CELL;    // 第几列
    int cy = topPos + GRID_Y + (i / COLS) * CELL;     // 第几行
    extractor.item(stack, cx + 1, cy + 1);
}
```

**（a）`i % COLS` 和 `i / COLS` 把一维下标变成二维坐标**

- `i % 9` = 列号（0..8）——因为模 9 每 9 个循环一次
- `i / 9` = 行号（0..4）——整数除法（Java 向下取整）

**整个"网格排版"的数学就这一条。**

**（b）`extractor.item(stack, x, y)` 画的是真正的物品模型**

物品图标**不是图片**——MC 拿你给的 `ItemStack`，用它的**物品模型**渲染出来。
所以附魔剑显示的就是附魔剑的样子，我们**不需要准备任何图标资源**。

**（c）为什么数量不在这里画？**

因为我们选了"只在悬停时显示"。这里顺便记一个坑：
`extractor.itemDecorations(font, stack, x, y)` 会画数量，**但它只在数量 > 1 时才画**。
我们的模板物品数量恒为 1（真实数量单独存着），所以它会判定"1 不用画"，什么都不画。
——这也是为什么当时格子里的数字完全不显示。

### 8.5 文字

```java
extractor.text(font, "文字", x, y, 颜色);              // 左上角对齐
extractor.centeredText(font, "文字", 中心x, y, 颜色);   // 水平居中
font.width("文字")                                     // 量文字宽度
```

右对齐就是减法：`x = 右边界 - font.width(文字)`。

```java
String total = "共 " + list.size() + " 种";
extractor.text(font, total, leftPos + imageWidth - 8 - font.width(total), topPos + 7, TEXT_DIM);
```

### 8.6 ④ 悬停提示

```java
List<Component> lines = new ArrayList<>(
        Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
lines.add(Math.min(1, lines.size()),
        Component.literal("存量: " + entry.count()).withStyle(ChatFormatting.AQUA));
extractor.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
```

- `getTooltipFromItem` 是原版工具：给一个 `ItemStack`，它返回**完整提示**（名字 + 附魔 + 说明）。
  **附魔信息是免费的，不用自己拼。**
- `lines.add(index, 元素)` 是**插到第 index 位**，不是追加。
  插在 1 是为了让"存量"紧跟在物品名下面，不被附魔列表挤到最底下。
- `Math.min(1, lines.size())` 防越界：万一是空列表就插到 0。
- `ChatFormatting.AQUA` 是青色——因为格子里看不到数量，得让它显眼。

### 8.7 点击是怎么变成"取出物品"的

**渲染和点击是两套方向相反的计算**：

```
渲染：  下标 i   ──(i%COLS, i/COLS) 转坐标──>  画到屏幕
点击：  屏幕坐标 ──(减 GRID_X，除以 CELL)──>  算出下标
```

```java
private int hitIndex(double mouseX, double mouseY) {
    int relX = (int) mouseX - leftPos - GRID_X;    // 换算成"在网格里的相对位置"
    int relY = (int) mouseY - topPos - GRID_Y;
    if (relX < 0 || relY < 0) return -1;           // 在网格左边/上边 → 没点中
    int col = relX / CELL;
    int row = relY / CELL;
    if (col >= COLS || row >= ROWS) return -1;     // 在网格右边/下边 → 没点中
    int index = page * PER_PAGE + row * COLS + col;   // 行×列数 + 列 + 页偏移
    return index < entries().size() ? index : -1;  // 超出实际物品数 → 空格子
}
```

**这就是"命中判定"的全部逻辑**：鼠标坐标 → 格子行列 → 列表下标。

拿到下标后：

```java
ClientPlayNetworking.send(new StoreActionPayload(entries().get(index).stack(), amount));
```

⚠️ **注意：客户端一行数据都没改。** 它只发"我想取这个东西"。
服务端收到后校验、扣减、再回发新快照。**这是防作弊的底线。**

### 8.8 翻页

翻页本身简单到离谱——就是一个 `int`：

```java
private int page;
private int pageCount() {
    return Math.max(1, (entries().size() + PER_PAGE - 1) / PER_PAGE);
}
```

- 渲染时 `start = page * PER_PAGE`，只看这一页
- 点 `>>` 就 `page++`
- `(n + PER_PAGE - 1) / PER_PAGE` 是**向上取整除法**的惯用写法
  （Java 整数除法向下取整，所以先加 `PER_PAGE - 1` 再除）

`clampPage()` 处理一个边界情况：取走物品后总页数会变小，当前页可能越界停在空白页，得收回来。

**而且因为每帧都从 `ClientStoreCache` 重读数据**，服务端一发新快照，界面下一帧就自动更新了——
**整个界面没有任何"刷新"逻辑**。这是"每帧重读"带来的免费好处。

### 8.9 想改的时候改哪里

| 想改什么 | 改哪里 |
|---|---|
| 每页几个 | `COLS` / `ROWS` |
| 面板尺寸 | 构造器里的 `176, 222` |
| 按钮位置 | `CONTROLS_Y`、`BTN_MARGIN`、`BTN_W` / `BTN_H` |
| 按钮长相 | `drawButton(...)` |
| 颜色 | `PANEL` / `PANEL_DARK` / `TEXT` / `TEXT_DIM` / `BTN_OFF` |
| 背景贴图 | `BACKGROUND` |
| 悬停显示什么 | `extractTooltip(...)` |
| 点击行为 | `mouseClicked(...)` |

⚠️ **一个必须知道的联动**：`StoreMenu.PLAYER_INV_Y` / `HOTBAR_Y` 决定的是**真实槽位**的位置，
而贴图里的槽位是**画上去的**。**两者必须对齐**——否则你点背包里的物品会点到空处。
改布局时这两边要一起改。

---

## 9. 下一步怎么继续

当前进度：

- ✅ 数据层（存/取/归零消失/持久化/附魔区分）—— **已在游戏里实测通过**
- ✅ 命令 `/conserve`（open / list / deposit / withdraw）
- ✅ GUI：翻页列表 + 悬停看存量（服务端权威，自定义网络包同步）
- ✅ 方块「保存台」：右键开界面 + 附魔粒子 + 配方 + 挖掉落
- ⬜ 把 `deposit` / `withdraw` 做得更顺手（拖拽、整摞存入等）
- ⬜ 搜索 / 排序（物品上千种时会很需要）
- ⬜ 拖拽存取、整摞存入等更顺手的交互

**给自学的建议**：想搞懂某个 API，最快的路径是——
1. 用 `javap` 看签名（不知道有哪些方法时）
2. 看原版里谁用了它（IDE 里 `Find Usages`）
3. 改一行、编译、看报错

编译器的报错信息很准，**它是你最好的老师**。
