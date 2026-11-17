# 宽整数加密 / 加速指令集（ModArith）

> 位置：`com.scalarwide.main.integer.ModArith`。作用对象是小端 `long[]` 肢向量，宽度 = 数组长度。
> **等长契约**（与 `Limbs` 相同）：所有操作数、输出、模数必须同长度；越界长度会算错或越界。
> 上一轮目标：**把指令铺全、把语义钉死**；本轮（E3）目标：**乘法去除法**——
> Montgomery CIOS 进 `mulMod` / `powMod` / `powModCt`。数字见 `docs/bench.md` §E3。

## 0. 三个用途，三套要求（别只按 RSA 设计）

| 用途 | 位宽 | 需要的指令 | 现状 |
|---|---|---|---|
| RSA | 1024–4096 | 模幂、Montgomery、严格 CT 模幂 | mulMod/powMod 走 Montgomery CIOS（奇数模数）；严格 CT 模幂仍缺 |
| ECC | 256/384/521 素数域 | addMod/subMod/mulMod/modInverse、**CT 标量乘**（ctSelect/ctSwap/ctAddMod） | 有基础模运算 + Montgomery（宽度无关，9 肢 P-521 已测）；CT 域运算缺 |
| SHA | 512 位状态 = 8×64（或 8×32） | **逐 lane 旋转/右移**、按 word 的大端存取、模 2^N 加 | rotlEach/rotrEach/rotrEach32/shrEach32 已补；BE 存取用核心 fromByteArray |

**ECC 的 521 位是 9 肢**（不是 2 的幂），这也说明引擎必须宽度无关——ModArith 正是按
「数组长度 = 宽度」写的，521 位直接可用；Montgomery 的循环也按 `m.length` 走，9 肢没有特例。

SHA 的旋转和整宽度旋转是**两个操作**：SHA 的状态是独立字向量，旋转不能跨肢进位。
rotl/rotr 是整宽度；rotlEach/rotrEach/rotrEach32 是逐 lane。

## 1. 已有的（核心 `Wide*` 类型，不必再用 ModArith）

`and or xor not andNot`、`shl ushr shr`、`toByteArray(LE/fixed)`、`fromByteArray(LE)`、
`bitLength bitCount`、`add sub mul mulHigh mulFull div rem pow`、`compareUnsigned compareSigned`。

## 2. 本轮新增

| 组 | 方法 | 说明 |
|---|---|---|
| 位访问 | `testBit setBit clearBit flipBit getLowestSetBit reverseBitsInLimbs` | 位索引按整个宽度算，越界是 no-op（`testBit` 返回 false） |
| 旋转 | `rotl rotr` | `k` 取模宽度，支持负数 |
| 模加/减 | `addMod subMod` | 按 `(carry \|\| sum ≥ m)` 选减、按 `borrow` 选加 |
| 模乘 | `mulMod` | **奇数模数：Montgomery CIOS**（一次 `toMont` 转换 + `mulMont`）；偶数模数：全积 + `Limbs.divmod` 回退。单次乘法两种路径实测打平（见 `docs/bench.md` §E3），收益在模幂 |
| 模幂 | `powMod` | 平方-乘，跳过前导零位（**非常数时间**）；奇数模数全程在 Montgomery 域 |
| 模幂 CT | `powModCt` | 固定调度：每位都平方、都乘，再用 `ctSelect` 选；奇数模数在 Montgomery 域 |
| Montgomery | `n0inv mulMont toMont fromMont` | `n0 = -m^-1 mod 2^64`（Newton 迭代 5 步）；CIOS 支持任意肢数；`toMont` 是唯一还要除法的环节（每次模幂只付一次） |
| 数论 | `gcd`（二进制）、`modInverse`（奇模数） | `gcd` 无分配；`modInverse` 目前走 `BigInteger` |
| 常数时间 | `ctCompare ctSelect ctSwap` | 只用 `Long.compareUnsigned` 与掩码，无数据相关跳转/索引 |

## 3. 常数时间声明（必须逐条遵守）

| 方法 | 常数时间？ | 原因 |
|---|---|---|
| `ctCompare` `ctSelect` `ctSwap` | ✅ | 掩码/固定循环 |
| `mulMont` | ⚠️ 接近 | 乘法/归约的肢数固定、无数据相关跳转；但收尾的"条件减模数"是数据相关分支（压测中 50% 命中/未命中），严格 CT 需改掩码版 |
| `powModCt` | ⚠️ 调度固定 | 调度固定（每位平方+乘），且乘法已不再走除法；但 `toMont` 用 `Limbs.divmod`、`mulMod` 的收尾有分支，**整体仍不是严格 CT** |
| `addMod` `subMod` | ⚠️ 有数据相关分支 | 用 `if (carry…)` 选减；密钥路径请用掩码版（下一轮） |
| `mulMod` `powMod` `gcd` `modInverse` | ❌ | 回退路径含除法；`powMod` 前导零扫描；`modInverse` 走 `BigInteger` |

**结论：现在的 API 可以用于公开参数（公钥、模数、哈希输出），不能直接用于私钥/标量。**
严格常数时间的全套（掩码归约、CT 模幂、CT 逆元）仍是优化轮的第一优先级。

## 4. 语义与校验

- 模数 `m` 为 0 → `ArithmeticException`；`modInverse` 还要求 `m` 为奇数。
- `n0inv` / `toMont` / `fromMont` / `mulMont` 要求 `m` 为奇数，否则 `ArithmeticException`；
  `mulMod` / `powMod` / `powModCt` 对偶数模数自动走除法回退，不抛异常。
- `addMod` / `subMod` / `mulMod` / `mulMont` 要求 `a, b < m`（不满足时结果未定义，不做额外校验以免拖慢热路径）。
- `fromMont` 的输入按"Montgomery 余数"解释，要求 `< m`；`toMont` 对任意肢向量都成立（它就是 `a·R mod m`）。
- `powMod` 的指数按数组宽度解释（`exp.length*64` 位），`powModCt` 亦然。
- `gcd(0,0)` → `ArithmeticException`。
- `modInverse` 对不可逆输入返回 `false`，`out` 内容未定义（调用方必须先看返回值）。

## 5. 门禁

`ModArithTest`（16 例，种子 12345，宽度 2/4/6/8/9 肢 = 128/256/384/512/576 位）：

- `addMod` / `subMod` / `mulMod`：各 2000 组随机，对 `BigInteger`；
- `powMod`：500 组对 `BigInteger.modPow`；`powModCt` 与 `powMod` 逐位一致；
- `modInverse`：对 `BigInteger.modInverse`，含不可逆与偶模数路径；
- `gcd`：对 `BigInteger.gcd`；
- 位访问/旋转：对 `BigInteger.testBit/setBit/clearBit/flipBit` 与自写的旋转闭式；
- `ctCompare` / `ctSelect` / `ctSwap` 对普通形式；
- **Montgomery 专项**：`n0inv`（`m[0]·n0 ≡ -1 mod 2^64`）+ `toMont→fromMont` 往返 +
  `mulMont` 对 `a·b·R^-1 mod m` + 完整 Montgomery 管线对 `a·b mod m`（5 个宽度各 1000 组）；
  模数邻域（0/1/2/m/2/m-2/m-1 的成对组合）、全宽 `2^N-1`、`2^(N-1)+1`、`3`；
  模数 = 1（含 `powMod`）；偶数模数回退（`mulMod`/`powMod`/`powModCt` 对拍）且
  `n0inv`/`toMont`/`fromMont`/`mulMont` 抛 `ArithmeticException`；
  P-521 定点：`n0inv(p)`、`toMont(p-1)`、`(p-1)^2 mod p = 1`、`R mod p = 2^55`。

## 6. 下一轮（集体优化）

1. ~~**Montgomery**：`n0inv`、CIOS `mulMont`、`toMont/fromMont`，让 `mulMod` 不再除法~~
   —— **已完成（E3）**：`mulMod` 奇数模数走 `toMont + mulMont`，`powMod`/`powModCt` 在域内循环，
   分配降到 1/500；实测 `powMod` 3.70–5.0×。**但单次 `mulMod` 只拿到 1.31×（646.9 vs 846.3 ns）**，
   与偶数模数的纯除法回退打平（655.6 ns）：单次乘法必须付一次 ×R 变换（`toMont` 9 肢 403 ns），
   静态 API 下这一步就是一次除法。`mulMont`（输入已在域内）9 肢 281 ns / 4 肢 64 ns。
   数字与口径见 `docs/bench.md` §E3。
2. **无分配（部分完成）**：`mulMod` 从"5 个 `2L` 数组"降到 **488/664/928 B/op**（`toMont` 的
   被除数/除数/商 + Montgomery 的 `n+2` scratch），`mulMont` **64/80/104 B/op**；
   `powMod` 从 208,913/420,187/919,538 B/op 降到 **984/1337/1867 B/op**。
   下一步：加 scratch 载体 API（`...Into(a, b, m, scratch, out)`），把热路径降到零分配。
3. **严格 CT**：掩码版 `addModCa`、`mulMont` 收尾的掩码条件减、CT `toMont`（现在用 `Limbs.divmod`）、CT 模幂、CT 逆元；
4. **`powMod` 窗口化**：固定 4 位窗口 + CT 预计算表；
5. 基准：`ModArithBench` 已覆盖 4/6/9 肢的 `mulMod/mulModEven/mulMont/toMont/fromMont/n0inv/powMod/powModCt`
   （§E3）；仍缺 1024/2048/4096 位档与 `modInverse` 基准。
6. **两条能把单次乘法打到 ~100–280 ns 的形状改动（未做，需拍板）**：
   ① 按宽度展开的专用 Montgomery 核（4/6/8/9 肢各一份生成代码，`GenMul` 路线）；
   ② Montgomery 上下文载体（缓存 `n0` 与 `R² mod m`，`toMont` 退化成一次 `mulMont`，
      `mulMod` 落到 ~280 ns 量级）——这次实测证明：**通用循环 + 每调用一次除法是单次乘法的天花板**。
