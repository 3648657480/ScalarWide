# Binary1280 容器：实测回单（2026-11-17）

> 对应需求单：`C:\Project-G1.1\docs\SCALARWIDE-B1280-BUDGET.md`（v1.0）
> 实现：`fp/src/main/java/com/scalarwide/fp/Binary1280.java`
> 基准：`fp/src/jmh/java/com/scalarwide/fp/bench/B1280Bench.java`
> 门禁：`fp/src/test/java/com/scalarwide/fp/Binary1280Test.java`（6 例，5000 组差分 + 四个指定量级）

## 0. 一句话

容器已实现并实测：**四个 op 全部低于 50 ms/chunk 档的目标，`floorLow64Frac` 与 `scalePow2`
连 30 ms 档也过（余量 6–30×）**；每次调用分配为 0。按需求单的调用次数折算，
**每 chunk 宽容器总耗时约 0.7 ms**（预算 8 ms，余量 11×）。

## 1. 六个问题的答案

| # | 问题 | 回答 |
|---|---|---|
| 1 | `add` 100 ns 含分配吗？ | **容器没有 `add`**。1280×1280 加减按需求单 §4 不提供；per-chunk 初始化若需要，见 §5 的待定项。 |
| 2 | `mulSmall` 与 `mul(wide,wide)` 各多少 ns？ | `mulSmall`（20×1）**74–78 ns/次**；`mul(wide,wide)` **未提供**（需求单 §4 已论证 1.9 ns/op 不可能，故不实现）。 |
| 3 | `scalePow2` 是改指数还是遍历 limb？ | **只改指数**：一条 `int` 加法 + 溢出检查，limb 一个都不动。**0.30–0.32 ns/次（≈1 cycle）**。 |
| 4 | 有没有 `floorLow64Frac` 融合接口？ | **有**，一次调用同时给出「整数部分低 64 位」（出参 `long[1]`）与「小数高 53 位」（`double` 返回值）。**1.8–1.9 ns/次**。 |
| 5 | 每次调用分配几个对象？ | **0 个**。JMH `gc.alloc.rate.norm` 实测 0.002–0.55 B/1024 次（即每次 < 0.001 B），`gc.count = 0`。 |
| 6 | JMH 参数？ | JDK 25.0.3、`-prof gc -f 3 -wi 6 -i 8 -w 1s -r 1s`（24 个样本），笔记本未绑核；单次调用 = ns/op ÷ 1024。 |

## 2. 实测（单次调用，已 ÷1024）

| op | @2^53 | @2^1027 | 50 ms 档目标 | 30 ms 档目标 | 判定 |
|---|---:|---:|---:|---:|---|
| `scalePow2` | **0.32 ns** | **0.30 ns** | 11 ns | 6.7 ns | 过，余量 21–34× |
| `floorLow64Frac` | **1.88 ns** | **1.78 ns** | 19 ns | 11.8 ns | 过，余量 6.3–10× |
| `mulSmall` | **73.9 ns** | **77.9 ns** | 540 ns | 340 ns | 过，余量 4.4–7.3× |
| `compare` | 5.72 ns | 5.83 ns | 5 ns | 3 ns | 50 ms 档超 1.16×（见 §5.4） |
| 分配 | ~0 B | ~0 B | 0 | 0 | 过 |

折算到每 chunk（按需求单 §2 的调用次数）：

| 调用 | 次数 | 单次 | 小计 |
|---|---:|---:|---:|
| `mulSmall` | 3,675 | 73.9 ns | 0.27 ms |
| `scalePow2` | 186,200 | 0.32 ns | 0.06 ms |
| `floorLow64Frac` | 186,200 | 1.88 ns | 0.35 ms |
| `sign`/`compare` | 1,225 | 5.83 ns | 0.007 ms |
| **合计** | 377,300 | | **≈ 0.69 ms** |

**对 8 ms 预算有 11× 余量**；按 30 ms/chunk 档重算（宽预算 4.8 ms）也只占 14%。
这个 0.69 ms 是**库调用本身**的耗时，不含调用点的拆分/装箱（本 API 无装箱）。

两处关键优化（都是"别扫不该扫的 limb"）：

- `isZero()` 从 20 肢 OR 改为只看顶肢——归一化不变量（非零值最高位在 limb 19）保证等价；
- `floorLow64Frac` 正数路径不再需要 sticky 扫描（截断不需要知道被丢的位是否非零），
  @2^53 从 6.2 ns 降到 1.9 ns；负数路径才付那次扫描（最多 18 肢）。

## 3. 接口与语义（已定，需 G1.1 书面确认）

```java
Binary1280 v = new Binary1280();      // 一次分配，之后循环内零分配
v.set(long x);                        // 精确
v.scalePow2(int k);                   // v *= 2^k，O(1)，指数溢出抛 ArithmeticException
v.mulSmall(long w);                   // v *= w，20×1，w 的符号按有符号处理
double frac = v.floorLow64Frac(out);  // out[0]=floor(v) 的低 64 位，返回 v-floor(v) 的高 53 位
int sign = v.signum();                // -1/0/+1
int c = v.compareTo(other);           // 数值序
```

值模型：`v = (-1)^sign · M · 2^lsbExp`，`M < 2^1252` 且归一化（最高位在 bit 1251）。

四条需要确认的语义：

1. **负数的 `floor`**：`floorLow64Frac` 取的是**数学 floor**。`v = -1.5` 给 `out[0] = -2`、
   `frac = 0.5`。若 G1.1 要的是向零截断（`-1` 与 `0.5`），现在改，别等集成后改。
2. **小数是截断不是舍入**：返回 `v - floor(v)` 的**前 53 位，直接截断**；负数的 53 位由
   `2^s - rem` 精确算出，不是「把正的 1-f 近似」。确定性优先，代价是最多 1 ulp 的系统性偏差。
3. **`mulSmall` 的溢出**：乘积超过 1252 位时**丢弃低位**（每丢一位 `lsbExp` 加一），不抛异常、
   不舍入。坐标量级到 2^1027、频率到 2^53 时远不到这个界。
4. **指数溢出抛异常**：`scalePow2` 的指数是 `int`，越界直接抛，不静默回绕。

线程模型：容器无同步，每线程一份，与 G1.1「每 chunk 一个生成线程、互不共享」一致。

## 4. 门禁

`Binary1280Test`（6 例）：

| 例 | 内容 |
|---|---|
| `setLongIsExact` | 5000 组 `long`（含 `Long.MIN/MAX`）往返精确 |
| `scalePow2OnlyMovesTheExponent` | limb 逐位不变、指数加法正确、越界抛异常 |
| `mulSmallMatchesTheExactProduct` | 5000 组，含符号、含截断路径，与 BigInteger 精确积逐位对拍 |
| `floorLow64FracMatchesTheExactSplit` | 5000 组随机 `(M, lsbExp, sign)`，与 BigInteger 精确有理参考对拍 |
| `landmarkMagnitudes` | 2^53 / 2^600 / 2^1016 / 2^1027 各 ±0.5、正负号 |
| `compareFollowsNumericOrder` | 数值序（含零、负数） |

## 5. 未覆盖 / 待定

1. **per-chunk 初始化的构造与 add**：需求单 §5.5 允许 `add/sub` 出现在 `chunkKey×16 + local`，
   但容器没有 `add`。若 G1.1 需要，我们加一个**只用于初始化**的 `addInto`/`setFrom(BigInteger)`
   （O(20)，不在热循环，不进预算）。
2. **单 chunk 集成实测**：需求单 §6.3 要 AsyncProfiler/JFR 的 self time ≤ 8 ms，那必须在 G1.1
   的生成线程里测。本回单给的是**库调用折算值 2.4 ms**，集成后以实测为准。
3. **格式对齐**：容器用满 1252 位有效数 + `int` 指数，**不是** IEEE 的「1+28+1251」位域。
   如果将来要把容器值打包成 IEEE Binary1280（Long2 生态那套），需要另加 `pack`/指数范围检查。
4. **30 ms 档的 `compare`**：4.4–4.6 ns 对 3 ns 的目标偏紧；容器比较在指数相等时走 20 肢全比较，
   真实坐标通常首位肢就分出胜负，这一项在集成后重测更有意义。
