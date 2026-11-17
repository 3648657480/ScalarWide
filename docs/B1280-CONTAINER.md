# Binary1280 容器：实测回单（2026-11-17）

> 对应需求单：`C:\Project-G1.1\docs\SCALARWIDE-B1280-BUDGET.md` **v1.1**（含 §9 对账）
> 实现：**mc 线** `C:\Project-ScalarWide-2560\src\main\java\com\scalarwide\mc\Binary1280.java`
> 门禁：mc 线 `src/test/java/com/scalarwide/mc/Binary1280Test.java`（8 例）
> 基准：迁移前在主线 `fp` 的 `B1280Bench` 上测得；mc 线没有 jmh 模块，脚本未随行，
> 数字与命令保留在 `docs/BENCH.md` 的 E1 节。
>
> **两条线已彻底分开**：主线只有通用浮点 `com.scalarwide.fp.Binary1280`（不可变值语义），
> mc 线只有容器 `com.scalarwide.mc.Binary1280`（可变 scratch，零分配）。两者同名不同物，
> 即使同 classpath 也不撞车，但**不能混用**。
> 门禁：`fp/src/test/java/com/scalarwide/fp/Binary1280Test.java`（8 例，5000 组差分 + 四个指定量级）
> 本文件是 ScalarWide 侧记录；发给 G1.1 的回单是 `C:\Project-G1.1\docs\SCALARWIDE-B1280-REPLY.md`。

## 0. 一句话

容器已实现并实测（**v1.1 的 13,475 次 `mulSmall` 与新增的 `addSmall` 都算进去了**）：
每 chunk 宽容器 **≈ 1.4–1.5 ms**（预算 8 ms，余量 **5.3×**）；四个 op 全部低于 50 ms 档目标，
`scalePow2` / `floorLow64Frac` 连 30 ms 档也过；每次调用分配 **0**。

## 1. 六个问题的答案

| # | 问题 | 回答 |
|---|---|---|
| 1 | `add` 100 ns 含分配吗？ | 容器没有 1280×1280 `add`；per-chunk 初始化用 `setFrom(long[])`（冷路径）+ 每 sample 的 `addSmall(long)`。 |
| 2 | `mulSmall` 与 `mul(wide,wide)` 各多少 ns？ | `mulSmall`（20×1）**70–79 ns**；`mul(wide,wide)` **不提供**（需求单 §4 已论证 1.9 ns/op 不可能）。 |
| 3 | `scalePow2` 是改指数还是遍历 limb？ | **只改指数**：一条 `int` 加法 + 溢出检查。**0.31–0.32 ns/次**。 |
| 4 | 有没有 `floorLow64Frac` 融合接口？ | **有**：出参 `long[1]` 收 `floor(v)` 低 64 位，返回 `v-floor(v)` 的高 53 位。**1.5–1.7 ns/次**。 |
| 5 | 每次调用分配几个对象？ | **0 个**（`gc.alloc.rate.norm` ≤ 0.56 B / 1024 次，`gc.count = 0`）。 |
| 6 | JMH 参数？ | JDK 25.0.3、`-prof gc -f 3 -wi 6 -i 8 -w 1s -r 1s`（24 样本）、笔记本未绑核；单次调用 = ns/op ÷ 1024。 |

## 2. 实测（单次调用，已 ÷1024）

| op | @2^53 | @2^1027 | 50 ms 档目标 | 30 ms 档目标 |
|---|---:|---:|---:|---:|
| `scalePow2` | **0.31 ns** | **0.32 ns** | 11 ns | 6.7 ns |
| `floorLow64Frac` | **1.73 ns** | **1.45 ns** | 19 ns | 11.8 ns |
| `mulSmall` | **60.5 ns** | **68.3 ns** | 540 ns | 340 ns |
| `addSmall` | **5.4 ns** | **12.8 ns** | 需求单估 10–20 | — |
| `compare` | 5.2 ns | 7.4 ns | 10 ns（v1.1 放宽） | — |
| 分配 | <0.001 B/次 | <0.001 B/次 | 0 | 0 |

`addSmall` 的两档差别来自 delta 落在哪个肢上（@2^53→ li=18，只动 2 个肢；@2^1027→ li=3，动 17 个）。

每 chunk 折算（v1.1 调用次数：`mulSmall` 13,475、新增 `addSmall` 13,475）：

| 调用 | 次数 | 单次（取两档较大者） | 小计 |
|---|---:|---:|---:|
| `mulSmall` | 13,475 | 68.3 ns | 0.92 ms |
| `addSmall` | 13,475 | 12.8 ns | 0.17 ms |
| `scalePow2` | 186,200 | 0.32 ns | 0.06 ms |
| `floorLow64Frac` | 186,200 | 1.73 ns | 0.32 ms |
| `sign`/`compare` | 1,225 | 7.4 ns | 0.009 ms |
| **合计** | 387,100 | | **≈ 1.48 ms** |

按 @2^1027 那一列算则 ≈ 1.35 ms。**对 8 ms 预算余量 5.3–5.9×**；30 ms/chunk 档（宽预算 4.8 ms）也放得下。
这个数是**库调用本身**的耗时，不含调用点（本 API 无装箱、无临时对象）。

三处关键优化（都是"别扫不该扫的 limb"）：

- `isZero()` 从 20 肢 OR 改为只看顶肢（归一化不变量：非零值最高位在 limb 19）；
- `floorLow64Frac` 正数路径去掉 sticky 扫描（截断不需要知道被丢位是否非零），@2^53 6.2 → 1.9 ns；
- `addSmall` 的加减循环从 `li` 起步而不是从 limb 0（低于 `li` 的肢本来就不动），@2^53 19.9 → 5.4 ns。

## 3. 接口与语义（需求单 v1.1 §9.2 已逐条确认，冻结）

```java
Binary1280 v = new Binary1280();      // 一次分配，循环内零分配
v.setFrom(long[] limbs);              // 1024 位 chunk 坐标 → 容器（冷路径，越界抛异常）
v.copyFrom(Binary1280 o);             // 每 sample 从基准复制
v.addSmall(long delta);               // 每 sample 加局部偏移 0..15（有符号）
v.mulSmall(long w);                   // × 频率的 53 位尾数
v.scalePow2(int k);                   // × 2^(频率指数-52)，O(1)
double frac = v.floorLow64Frac(out);  // out[0]=floor(v) 低 64 位；返回小数高 53 位
v.signum(); v.compareTo(o);
```

值模型：`v = (-1)^sign · M · 2^lsbExp`，`M < 2^1252` 且归一化（最高位在 bit 1251）。

已确认的四条语义：

1. **负数是数学 floor**（`-1.5` → `low64 = -2`、`frac = 0.5`），与 CWG / `Mth.floor` 一致。
2. **小数截断不舍入**：取 `v - floor(v)` 的前 53 位；负数的 53 位由 `2^s - rem` 精确计算。
3. **`mulSmall` 超 1252 位丢低位**、`lsbExp` 前移，不抛异常。
4. **指数越界抛 `ArithmeticException`**（`scalePow2`）。

频率路径确认：`freq = mant53 · 2^(e-52)`（含隐含位）是**精确分解**，
`mulSmall(mant53)` + `scalePow2(e-52)` 给出**精确乘积**；double 侧 `x*f` 会舍入到 53 位，
所以只有在乘积本就 ≤53 位时两者逐位相同，其余情况容器更精确（不回舍）。

## 4. 门禁

`Binary1280Test`（8 例）：`set` / `setFrom` / `mulSmall` / `floorLow64Frac` 各 5000 组
BigInteger 精确对拍；`addSmall` 5000 组 + 交叉零/重归一化定点；
四个指定量级（2^53 / 2^600 / 2^1016 / 2^1027）的 ±0.5 与正负号；`scalePow2` 只动指数；数值序比较。

## 5. 未覆盖 / 待定

1. **单 chunk 集成实测**（需求单 §6.3）：必须在 G1.1 的生成线程里跑 JFR/AsyncProfiler。
   本文件给的是库调用折算值 ≈1.6 ms，集成后以实测为准。
2. **交付形态**：`Binary1280` 在 `fp` 子项目里，G1.1 怎么依赖（fp 独立坐标 / 合进主 jar / 拷源码）未定。
3. **IEEE 位域打包**：容器是「1252 位有效数 + `int` 指数」，不是「1+28+1251」位域；
   需求单 §9.5 已确认本轮不需要。
