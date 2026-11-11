# ScalarWide-FP 规范（P0）

状态：**P0 交付物**。此文件冻结前，P1 不得开始写代码。
本文件的所有表都是**可执行规范**：每一条后面都必须能在测试里找到对应断言。

符号：`p` = 精度（有效数位数，下轮定值）；`p64 = ceil(p/64)` = 肢数。

---

## 1. 格式表

### 1.1 值模型

一个有限值表示 `(−1)^sign × m × 2^(exp − (p − 1))`，其中：

| 字段 | 类型 | 含义 |
|---|---|---|
| `kind` | 枚举 | `FINITE` / `INFINITE` / `NAN` |
| `sign` | 0 或 1 | 0 = 正，1 = 负。**零也带符号**（±0 必须可区分） |
| `exp` | `int` | **最高有效位的无偏指数**（不是 LSB 的） |
| `m` | p 位整数（`p64` 个 `long`） | 有效数。**内部恒为归一化**：`m` 的最高位 = 1 |

**关键设计决定（避免 P4 返工）：内部永不表示次正规数。**
所有有限非零值内部都是归一化的、指数无界的；**次正规化只发生在 `pack`**。
这样 add/mul/div 的实现不需要任何次正规分支，P4 只是让 `pack` 走 `effectivePrecision < p` 的路径。

### 1.2 指数域

| 名字 | 定义 |
|---|---|
| `eMax` | 最大 `exp`（= MAX_VALUE 的指数） |
| `eMin` | 最小 **正规** `exp`（= MIN_NORMAL 的指数） |
| 次正规范围 | `exp ∈ [eMin − (p−1), eMin − 1]`，有效位不足 p |
| 下溢 | `exp < eMin − (p−1)` |
| 上溢 | `exp > eMax` |

导出常量：

| 常量 | 定义 |
|---|---|
| `MAX_VALUE` | `m = 2^p − 1`, `exp = eMax` |
| `MIN_NORMAL` | `m = 2^(p−1)`, `exp = eMin` |
| `MIN_VALUE` | `m = 1`, `exp = eMin − (p − 1)` |
| `ulp(x)` | `2^(exp − (p−1))`（次正规时随有效位减少） |

**待定（下轮）**：`p` 与 `(eMin, eMax)` 的取值。规范对它们保持符号化，四张表按符号写。

### 1.3 特殊值编码

| 值 | `kind` | `sign` | `exp` | `m` |
|---|---|---|---|---|
| `+0` | FINITE | 0 | 任意（建议 `eMin`） | 全 0 |
| `−0` | FINITE | 1 | 任意（建议 `eMin`） | 全 0 |
| `+∞` / `−∞` | INFINITE | 0 / 1 | 无意义 | 无意义 |
| `NaN` | NAN | 无意义 | 无意义 | 无意义（**唯一规范形式**，payload 不做） |

**不变量**（每次构造后必须成立，P1 用 1M 随机构造验证）：

1. `kind == FINITE && m == 0` ⇒ 唯一表示（指数归一化为 `eMin`）；
2. `kind == FINITE && m != 0` ⇒ `m` 最高位为 1；
3. `kind != FINITE` ⇒ `m == 0`；
4. **归一化后 `exp` 必须在 `[eMin − (p−1), eMax]`**（超出即上溢/下溢，`pack` 负责）；
5. 未使用的位（`p64 × 64 − p` 个高位）恒为 0。

---

## 2. 精确中间宽度表

每个运算先算**精确值**（无舍入），再交给 `pack`。下表是硬约束：宽度不够就必须换容器。

| 运算 | 精确中间量 | 需要宽度（位） | `pack` 取的舍入点 | 需要的核心能力 |
|---|---|---|---|---|
| `add` / `sub` | 对齐后的整数和 | `p + 3` + sticky | 归一化后第 `p+1` 位起 | 移位、进位、`bitLength` |
| `mul` | 乘积 | `2p` | 第 `2p−1` 位起 | `mulFull` |
| `div` | 商 + 余数非零标志 | `p + 2` + sticky | 第 `p+1` 位起 | `div` / `rem`（需商余一次返回） |
| `fma` | 2p 积 ⊕ 对齐的 p 位被加数 | `3p` | 第 `3p−1` 位起 | `mulFull` + 3p 容器 |
| `sqrt` | `N = m << (p+2)`，求 `⌊√N⌋` | `2p + 2` | 余数比较 | **整数 sqrt（缺）** |

实例化（`p64` 为肢数；容器取"够用的最小 `WideN`"）：

| p | `add` | `mul` | `div` | `fma` | `sqrt` |
|---|---|---|---|---|---|
| 128 | 131 → `Wide256` | 256 → `Wide256` | 130+ → `Wide256` | 384 → `Wide384` | 258 → `Wide384` |
| 192 | 195 → `Wide256` | 384 → `Wide384` | 194+ → `Wide256` | 576 → `Wide768` | 386 → `Wide512` |
| **256** | 259 → `Wide384` | 512 → `Wide512` | 258+ → `Wide384` | 768 → `Wide768` | 514 → `Wide768` |
| 384 | 387 → `Wide512` | 768 → `Wide768` | 386+ → `Wide512` | 1152 → **缺** | 770 → `Wide1024` |
| 512 | 515 → `Wide768` | 1024 → `Wide1024` | 514+ → `Wide768` | 1536 → **缺** | 1026 → **缺** |

**⚠ 两条由本表导出的硬结论**：

1. **p = 384 或 512 时，`fma` 需要 1152 / 1536 位容器——我们没有。**
   若选这两档，`fma` 必须改用"分段进位"实现（额外的 P5 工作），或放弃 `fma`。
2. **p = 512 时 `sqrt` 需要 1026 位——也没有。** 需额外容器或分段算法。

⇒ **推荐 `p = 256`**：五个运算的容器全部现成，零新增宽度。
（这条就是"下轮决定 p"时最有分量的输入。）

---

## 3. 舍入规则表

### 3.1 唯一的舍入器

```
pack(sign, exp, guard, round, sticky, effectivePrecision) → Binary
```

- `effectivePrecision`：正规数为 `p`；**次正规时小于 `p`**（预留参数，P4 才启用）。
  这一条是避免 P4 返工的关键。
- 舍入模式：**仅 nearest-even**（P0 定，定向舍入不在范围内）。

### 3.2 nearest-even 判定

```
inc = guard && (round || sticky || (结果最低位 == 1))
```

- 第 `p+1` 位 = `guard`，第 `p+2` 位 = `round`，其后非零 = `sticky`。
- **sticky 必须由"被丢弃的所有低位"归约而来**，不能只取一位——这是 `Float256` 出错的同类根因。

### 3.3 边界行为表

| 情形 | 判定 | 结果 |
|---|---|---|
| 上溢 | `exp > eMax`（含舍入后进位导致） | `±∞` |
| 下溢到零 | `exp < eMin − (p−1)` | `±0`（保留符号） |
| 次正规 | `eMin − (p−1) ≤ exp < eMin` | 右移补足，`effectivePrecision` 相应减小 |
| 精确为零 | `m == 0` | `±0`（符号按 IEEE 规则：`(+0)+(−0)=+0`，同号相加保留符号） |
| 相消 | 和为零 | `±0`，nearest-even 下为 `+0` |

### 3.4 特殊值传播

| 运算 | 输入含 NaN | 输入含 ∞ |
|---|---|---|
| `add/sub` | → NaN | `∞ + ∞` 同号 → 同号 ∞；异号 → NaN |
| `mul` | → NaN | `∞ × 0` → NaN；否则 → 符号积的 ∞ |
| `div` | → NaN | `∞/∞`、`0/0` → NaN；`x/0` → ±∞；`∞/x` → ±∞ |
| `sqrt` | → NaN | `+∞` → `+∞`；`−∞` → NaN |
| `fma` | → NaN | 按 `a×b + c` 的 IEEE 规则 |

**NaN 是规范化的**：任何产生 NaN 的路径返回唯一的 `NaN` 常量（不保留 payload、不区分 quiet/signaling）。

---

## 4. 验收矩阵

每个运算必须同时通过"随机"与"定向"两类门禁。

| 运算 | 参考实现 | 随机门禁 | 定向门禁 |
|---|---|---|---|
| 全部 | **`Ref`：BigInteger 精确有理数**（P1 交付） | 1M 组，种子 12345 | 见下行 |
| `add` / `sub` | `Ref` | 1M | 相消到零、指数差 = 0/1/p、对齐进位、半数 |
| `mul` | `Ref` | 1M | 半数、指数边界、符号零 |
| `div` | `Ref` | 1M | 余数恰为 0、循环小数、`x/x = 1`、`1/3` 的 ulp 邻域 |
| `fma` | `Ref`（**一次舍入**） | 1M | 与"先乘后加"结果不同的用例必须存在且方向正确 |
| `sqrt` | 逐位 oracle + 精确平方比较 | 1M | 完全平方、`√(MIN_VALUE)`、`√(MAX_VALUE)`、半数 |
| `pack` | 手写真值表 | — | 上溢/下溢/次正规/进位到 ∞ 全扫 |
| `toString` / `of(String)` | `BigDecimal` 精确展开 | 1M | 半数、次正规、极值、往返一致 |
| 判定（`isNaN` 等） | 手写真值表 | — | 三类特殊值 × 全部判定 |

**门禁的判据是"零错误"，不是"抽样看起来对"。**
`report.md` 里 `Float256` 的 1661 处错误，正是没有精确参考的后果。

---

## 5. API 骨架（P1 实现，P8 冻结）

最小面：`add subtract multiply divide sqrt fma negate abs`、
`compareTo compareNumerically equals hashCode`、
`of(long/double/String) toDouble toInt toLong toString(int)`、
`isNaN isInfinite isFinite isZero isNormal isSubnormal signBit`、
`scalb ulp nextUp nextDown`、`MAX_VALUE MIN_NORMAL MIN_VALUE ZERO NEGATIVE_ZERO NaN POSITIVE_INFINITY NEGATIVE_INFINITY`。

**不在范围内**（P0 明确划掉）：超越函数、十进制格式、IEEE 位级互操作、任意精度、SIMD。

---

## 6. P0 完成判据

- [x] 格式表（含内部"永不表示次正规"的决定）
- [x] 精确中间宽度表（含 p 的五个实例化，并导出两条硬结论）
- [x] 舍入规则表（含 `effectivePrecision` 预留）
- [x] 验收矩阵（每个运算都有参考实现与门禁）
- [ ] **待你确认 `p` 与 `(eMin, eMax)`** ← 唯一未决项

`p` 一旦确定，本文件把该列实例化即冻结，P1 开始。
