# 通用宽浮点格式（Binary256 / Binary640 / Binary1280）

> 适用范围：`com.scalarwide.fp` 下的**通用浮点类型**。
> 另有一个同名的 **1280 位坐标容器**，它不在本仓库主线，而在 `mc` 分支工作树
> （`C:\Project-ScalarWide-2560`），是给远域地形用的可变 scratch 类型——两者同名但不同物，
> 见 §6。

## 1. 三种格式

| 类型 | 精度 p | 指数域 | 肢数 | 总位数 | 对应生态 |
|---|---:|---|---:|---:|---|
| `Binary256` | 237 | `[-262142, 262143]` | 4 | 256 | Long2 `Binary256`、MinecraftTools `FloatRefactor256` |
| `Binary640` | 616 | `[-8388606, 8388607]` | 10 | 640 | Long2 `Binary640` |
| `Binary1280` | 1252 | `[-134217726, 134217727]` | 20 | 1280 | Long2 `Binary1280`（指数位宽按 2^(e-1)-1 外推） |

值模型：`(-1)^sign · m · 2^(exp - (p-1))`，`m` 为 p 位整数，**内部恒归一化**（最高位在 p-1），
次正规化只发生在舍入时。语义遵循 IEEE 754 的值语义：最近偶数舍入、带符号零、无穷、NaN、
渐进下溢。

## 2. 快速上手

```java
import com.scalarwide.fp.Binary1280;

Binary1280 a = Binary1280.of(1.5);            // 精确：每个有限 double 都能表示
Binary1280 b = Binary1280.of("1e-300");       // 正确舍入的十进制解析
Binary1280 c = a.multiply(b).add(Binary1280.ONE);

System.out.println(c.toString(40));           // 40 位有效数字
c.toDouble();                                 // 正确舍入到 binary64
a.compareTo(b);                               // 全序（-0 < +0，NaN 最大且彼此相等）
```

`Binary640` 的 API 与 `Binary1280` 完全一致，只有格式常量不同。

## 3. API 一览

| 组 | 方法 |
|---|---|
| 构造 | `of(long)` `of(double)` `of(String)` |
| 四则 | `add` `subtract` `multiply` `divide` |
| 融合/根 | `fma(b, c)`（单次舍入）、`sqrt()` |
| 符号 | `negate` `abs` `signBit` |
| 判定 | `isNaN` `isInfinite` `isFinite` `isZero` `isNormal` `isSubnormal` |
| 比较 | `compareTo`（全序）、`equals`、`hashCode` |
| 转换 | `toDouble`、`toString(int digits)`、`toString()` |
| 常量 | `ZERO` `NEGATIVE_ZERO` `ONE` `NEGATIVE_ONE` `POSITIVE_INFINITY` `NEGATIVE_INFINITY` `NaN` `MAX_VALUE` `MIN_NORMAL` `MIN_VALUE` |
| 元数据 | `PRECISION` `EXP_MIN` `EXP_MAX` `FORMAT` `format()` |

## 4. 语义与坑

1. **`fma` 是单次舍入**：`a.fma(b, c)` 与 `a.multiply(b).add(c)` 会不同，前者才是 IEEE 的 fma。
2. **`toString()` 的位数**：无参版本打印 `ceil(p·log10 2)+2` 位有效数字（1280 位是 380 位），
   足以十进制往返；需要定长输出用 `toString(digits)`。
3. **内部指数无界**：`scaleExponent()` 返回最高有效位的无偏指数，次正规值可能小于 `EXP_MIN`。
4. **`equals` 与 `compareTo` 不同**：`equals` 区分 ±0、所有 NaN 相等；`compareTo` 是 `Double.compareTo`
   式全序（-0 < +0，NaN 最大）。
5. **`sqrt` 走 `BigInteger`**：正确但会分配；不要放在热循环里。
6. **`of(String)`**：不支持十六进制浮点字面量，只接受十进制（含 `e` 指数）。

## 5. 门禁与已知缺陷

差分参考有两路：

- `parse` / `sqrt` / `multiply` / `divide` / `fma` → `BigFloat`（同格式的数组版，独立实现）；
- `add` / `subtract` → **精确 BigInteger 有理数**（`WideRef`）。

之所以加减不用 `BigFloat`：它在**极端指数差**下会丢 1 ulp（已验证用例
`5.2e104 − 1.7e-476`，正确结果就是 `5.2e104`）。这是数组版参考实现的缺陷，记录在此，
将来修 `BigFloat` 时以本条为回归用例。

门禁规模：每个宽度 500 组随机满精度操作数（1–320 位十进制数字 + 指数 ±1000），
覆盖 `parse` / 四则 / `sqrt` / `fma` / 特殊值 / 十进制往返。

## 6. 与 mc 线「容器」的区别

| | 通用浮点（本文件） | 容器（`mc` 分支） |
|---|---|---|
| 类型 | 不可变值对象 | **可变** scratch，就地改写 |
| 用途 | 一般浮点运算 | 远域地形每 octave 的坐标变换 |
| 关键操作 | 四则/根/fma | `setFrom` `copyFrom` `addSmall` `mulSmall` `scalePow2` `floorLow64Frac` |
| 分配 | 每次运算分配结果 | **每次调用零分配** |
| 四则 | 全 | 只 `mulSmall`（20×1），禁止 1280×1280 加减 |

两者同属 `com.scalarwide.fp` 且同名 `Binary1280`，靠**分支隔离**：主线只有通用浮点，
`mc` 线只有容器。不要把两者放进同一个 classpath。

## 7. 性能现状

新类型尚未做 JMH 基准（`Binary256` 的数字见 `docs/BENCH.md`）。就结构而言：
`add`/`subtract` 在 `2L+1` 肢帧上对齐后一次舍入，`multiply` 是 `L×L` schoolbook + 一次舍入，
`divide` 用 `Limbs.divmod`。基准与优化留到下一轮，届时按本仓库纪律记进 `docs/BENCH.md`。
