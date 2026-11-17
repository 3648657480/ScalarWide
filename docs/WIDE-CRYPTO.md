# 宽整数加密 / 加速指令集（ModArith）

> 位置：`com.scalarwide.ModArith`。作用对象是小端 `long[]` 肢向量，宽度 = 数组长度。
> **等长契约**（与 `Limbs` 相同）：所有操作数、输出、模数必须同长度；越界长度会算错或越界。
> 本轮目标：**把指令铺全、把语义钉死**；性能优化（Montgomery、无分配、常数时间）在下一轮。

## 1. 已有的（核心 `Wide*` 类型，不必再用 ModArith）

`and or xor not andNot`、`shl ushr shr`、`toByteArray(LE/fixed)`、`fromByteArray(LE)`、
`bitLength bitCount`、`add sub mul mulHigh mulFull div rem pow`、`compareUnsigned compareSigned`。

## 2. 本轮新增

| 组 | 方法 | 说明 |
|---|---|---|
| 位访问 | `testBit setBit clearBit flipBit getLowestSetBit reverseBitsInLimbs` | 位索引按整个宽度算，越界是 no-op（`testBit` 返回 false） |
| 旋转 | `rotl rotr` | `k` 取模宽度，支持负数 |
| 模加/减 | `addMod subMod` | 按 `(carry \|\| sum ≥ m)` 选减、按 `borrow` 选加 |
| 模乘 | `mulMod` | 全积 + `Limbs.divmod`（正确优先） |
| 模幂 | `powMod` | 平方-乘，跳过前导零位（**非常数时间**） |
| 模幂 CT | `powModCt` | 固定调度：每位都平方、都乘，再用 `ctSelect` 选 |
| 数论 | `gcd`（二进制）、`modInverse`（奇模数） | `gcd` 无分配；`modInverse` 目前走 `BigInteger` |
| 常数时间 | `ctCompare ctSelect ctSwap` | 只用 `Long.compareUnsigned` 与掩码，无数据相关跳转/索引 |

## 3. 常数时间声明（必须逐条遵守）

| 方法 | 常数时间？ | 原因 |
|---|---|---|
| `ctCompare` `ctSelect` `ctSwap` | ✅ | 掩码/固定循环 |
| `powModCt` | ✅ 调度固定 | 但它调用的 `mulMod` 走除法，**整体仍不是严格 CT**；严格 CT 需等 Montgomery + 无分支归约 |
| `addMod` `subMod` | ⚠️ 有数据相关分支 | 用 `if (carry…)` 选减；密钥路径请用掩码版（下一轮） |
| `mulMod` `powMod` `gcd` `modInverse` | ❌ | 除法、前导零扫描、`BigInteger` |

**结论：现在的 API 可以用于公开参数（公钥、模数、哈希输出），不能直接用于私钥/标量。**
严格常数时间的全套（CT Montgomery、CT 模幂、CT 逆元）是优化轮的第一优先级。

## 4. 语义与校验

- 模数 `m` 为 0 → `ArithmeticException`；`modInverse` 还要求 `m` 为奇数。
- `addMod` / `subMod` / `mulMod` 要求 `a, b < m`（不满足时结果未定义，不做额外校验以免拖慢热路径）。
- `powMod` 的指数按数组宽度解释（`exp.length*64` 位），`powModCt` 亦然。
- `gcd(0,0)` → `ArithmeticException`。
- `modInverse` 对不可逆输入返回 `false`，`out` 内容未定义（调用方必须先看返回值）。

## 5. 门禁

`ModArithTest`（9 例，种子 12345，宽度 2/4/8 肢）：

- `addMod` / `subMod` / `mulMod`：各 2000 组随机，对 `BigInteger`；
- `powMod`：500 组对 `BigInteger.modPow`；`powModCt` 与 `powMod` 逐位一致；
- `modInverse`：对 `BigInteger.modInverse`，含不可逆与偶模数路径；
- `gcd`：对 `BigInteger.gcd`；
- 位访问/旋转：对 `BigInteger.testBit/setBit/clearBit/flipBit` 与自写的旋转闭式；
- `ctCompare` / `ctSelect` / `ctSwap` 对普通形式。

## 6. 下一轮（集体优化）

1. **Montgomery**：`n0inv`、CIOS `mulMont`、`toMont/fromMont`，让 `mulMod` 不再除法；
2. **无分配**：`mulMod` 现在每次建 5 个 2L 数组；
3. **严格 CT**：掩码版 `addModCa`、CT Montgomery、CT 模幂、CT 逆元；
4. **`powMod` 窗口化**：固定 4 位窗口 + CT 预计算表；
5. 基准：`ModArithBench`（1024/2048/4096 位、模幂、逆元），数字进 `docs/BENCH.md`。
