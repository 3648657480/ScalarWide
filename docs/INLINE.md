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
