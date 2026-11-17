# ScalarWide 使用指南

> 面向使用者的入口文档。规格与设计的细节在 `docs/design/`，历史与决策在 `docs/history/`，
> 性能数字在 `docs/bench.md`。

## 1. 你该用哪个包

| 需求 | 包 | 类型 |
|---|---|---|
| 定宽整数、键、哈希、加密原型 | `com.scalarwide.main.integer` | `Wide128…1024`、`MutableWide128/256`、`ModArith`、`Montgomery` |
| 高精度浮点（正确舍入） | `com.scalarwide.main.fp` | `Binary256/640/1280` |
| Minecraft 宽键 / 平表 | `com.scalarwide.mc.integer` | `WideChunkPos`、`WideSectionKey2560`、宽 map 族 |
| 坐标容器（每 octave 零分配） | `com.scalarwide.mc.fp` | `Binary256/640/1280` 容器 |

两组的同名类**不是同一个类**（`main.integer.Wide512` ≠ `mc.integer.Wide512`），
边界与同步纪律见 `docs/design/TWO-LINES.md`。

## 2. 整数（`main.integer`）

```java
import com.scalarwide.main.integer.Wide256;

Wide256 a = Wide256.of(0x0123456789ABCDEFL, 0xFEDCBA9876543210L, 0, 0);
Wide256 b = Wide256.ofSigned(-1);
Wide256 c = a.xor(b).add(a);
BigInteger hi = a.mulHigh(b).toBigInteger();
```

- 语义：算术按 2^N 回绕；`compareTo` = 无符号序（与 `equals` 一致，适合做 key）；
  移位量必须在 `[0, N)`。
- `MutableWide128/256` 是零分配 scratch 载体：非线程安全、不可作 map 键。

## 3. 浮点（`main.fp`）

```java
import com.scalarwide.main.fp.Binary1280;

Binary1280 x = Binary1280.of("1.234567890123456789e-300");
Binary1280 y = x.multiply(x).add(Binary1280.ONE);
double d = y.toDouble();
String s = y.toString(40);      // 40 位有效数字
```

- IEEE 值语义：最近偶数、带符号零、无穷/NaN、渐进下溢。
- `fma` 是**单次舍入**；`sqrt` 走 BigInteger（正确但会分配）。

## 4. 坐标容器（`mc.fp`）

```java
import com.scalarwide.mc.fp.Binary1280;

Binary1280 c = new Binary1280();   // 每线程一份
c.setFrom(chunkLimbs);             // 每 chunk 一次
c.scalePow2(-40);                  // 每 octave，只改指数
c.mulSmall(0x9E3779B97F4A7C15L);   // 20x1 乘法
long[] low = new long[1];
double frac = c.floorLow64Frac(low);
```

- 只提供地形采样需要的操作，每次调用**零分配**。
- `addSmall` 的文档前提是 `|v| >= 2^53`（远场）。

## 5. 加密（`main.integer`）

```java
import com.scalarwide.main.integer.ModArith;
import com.scalarwide.main.integer.Montgomery;

// 一次性模运算
long[] r = new long[n];
ModArith.mulMod(a, b, m, r);

// 批量模运算：上下文缓存 n0inv 与 R^2 mod m，域内乘法免除法
Montgomery ctx = new Montgomery(m);
ctx.toMont(a, aDom);
ctx.mulMont(aDom, bDom, prod);
ctx.fromMont(prod, r);
```

- 常数时间声明逐条写在 `docs/guide/WIDE-CRYPTO.md`：当前只有 `ctCompare/ctSelect/ctSwap`
  与 `powModCt` 的**调度**是常数时间，模乘/模幂整体仍不是；**不要用于私钥/标量**。

## 6. 文档地图

| 分类 | 文件 |
|---|---|
| 基准 | `docs/bench.md`（数字与口径）、`docs/bench/*.csv`（原始跑批） |
| 设计 | `docs/design/DESIGN.md`、`INLINE.md`、`LAYOUT.md`、`HETEROGENEOUS.md`、`TWO-LINES.md` |
| 规格 | `docs/guide/SPEC.md`、`SEMANTICS.md`、`WIDE-FORMATS.md`、`COMPATIBILITY.md` |
| 加密 | `docs/guide/WIDE-CRYPTO.md` |
| 容器 | `docs/guide/B1280-CONTAINER.md` |
| 历史 | `docs/history/BRANCHES.md`、`FUTURE-WORK.md`、`WORKSTATION.md`、`BRIEF-*.md`、`HANDOFF-*.md` |
