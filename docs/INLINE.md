# ScalarWide 内联探针记录

> 目的：在写"套娃"（1.5）与 Karatsuba（1.6）之前，先验证"JIT 会把原子内联成扁平"
> 这个地基假设（v2 结论 #4/#5）。
> 结论：**假设在 128 成立、在 256 失败**——瓶颈是字节码尺寸，不是内联深度。

## 2026-10-08 · PrintInlining 探针（0.3.0）

- 命令：

```
java -jar build\libs\ScalarWide-0.3.0-jmh.jar "Wide128Bench\.(add|mul|mulFull)$|Wide256Bench\.(add|mul|mulFull)$" -wi 2 -i 2 -f 1 -w 200ms -r 200ms -jvmArgsAppend "-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining"
```

- 原始日志：`%TEMP%\opencode\inline-probe.log`（未入库；下表为摘要）。

### 方法尺寸与热内联裁决

| 方法 | 字节码 | 热内联 |
|---|---|---|
| Carry.addCarryOut | 18 | ✓（早期编译偶报 "uses too much stack"，热编译成功） |
| Wide128::<init> | 15 | ✓ |
| Wide128::add | 49 | ✓ |
| Wide128::mul | 54 | ✓ |
| Wide128::mulFull | 212 | ✓（< 325 热阈值） |
| Wide256::<init> | 27 | ✓ |
| Wide256::of | 14 | ✓ |
| Wide256::add | 139 | ✓ |
| **Wide256::mul** | **473** | ✗ failed: hot method too big |
| **Wide256::mulFull** | **1000** | ✗ failed: hot method too big |

### 结论（仅限本条件）

1. 内联约束是**尺寸**：热阈值约 325B（`MaxFreqInlineSize`），与深度无关（实际深度远低于 9）。
2. 128 原子全链可热内联 → "封装函数 → JIT 扁平"在 128 档成立。
3. 256 的 `mul`/`mulFull` 是巨石（473/1000B），**热编译也不内联**——调用开销 + 无法跨边界
   折叠/逃逸分析；这解释了 256 全积 34.9ns 的相当一部分。
4. 组合（1.5/1.6）若以巨石叶子为调用单元，将退化为"一串不内联的大方法调用"——
   **必须先拆小叶子，再组合**。

### 对阶梯的修订

- 新增 **1.4 拆小方法**（前置到 1.5/1.6 之前）：把 256 的 `mul`/`mulFull` 拆成 ≤~300B 的
  列/进位链分块（进位入参 → 值 + 进位出参），让热内联把叶子化整为零；
  **探针复验尺寸**后再进 1.5。
- 1.5/1.6 的"套娃内联成扁平"前提改为：叶子已是可内联小块，否则组合无意义。

## 2026-10-08 · 1.4 拆小后复验（0.4.0）

- `Wide256::mul` 473 → **56B**；`Wide256::mulFull` 1000 → **92B**；
  列块 `col0..col7` = 19 / 84 / 175 / 241 / 243 / 177 / 111 / 21B，**全部热内联**。
- `MulAcc` 被逃逸分析抹除（`-prof gc`：两版均 80.001 B/op，仅结果对象分配）。
- 结论：组合层的叶子现在都是可内联小块——1.5/1.6 的"套娃扁平化"前提成立；
  性能是否受益由 1.5 实测（叶子基准显示内联本身无收益，调用开销非瓶颈）。

## 2026-10-08 · 1.5b 512 组合尺寸（0.6.0）

- `Wide512::mul` 编排器 51B、`mulHigh` 162B；`lowHalf`/`highHalf` 20B、`shiftLeft/Right256` 28B、
  `compareUnsigned` 140B——全部可热内联；**`add` = 319B（边界：部分调用点被拒）**。
- 新消息 "already compiled into a big method"：编排器字节码小、但其编译体大（内联了叶子），
  故不再向调用方内联——符合预期（调用开销 ~1ns 量级，已知非瓶颈）。
- 1.5b 实测：512 mul 3.15×、反超 BigInteger 1.5×（详见 BENCH.md）。

## 2026-10-08 · Round A（0.6.1）

- `Wide512::add` 单进位式：**319 → 235B**（进位入 ∈ {0,1} 已证明）。
- `Wide512::mulHigh` = 130B（无分支版），但**其内联树 ~1.5kB 超预算**：
  `-prof gc` 832 B/op vs `mul` 的 80 B/op——EA 全线失败，两轮修复无效（存档）。
- 现象：`DesiredMethodLimit` 式预算 ≈ 1kB 级；`mul` 的内联树 ~0.9kB 恰好放行。
