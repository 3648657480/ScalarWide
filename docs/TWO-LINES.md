# 两条线：Main 组与 MC 组

> 本文定义 `Project-ScalarWide`（Main 组）与 `Project-ScalarWide-2560`（MC 组）的边界。
> 变更这条边界需要改本文 + 两个仓库的 `AGENTS.md`，不是随手 merge 的事。

## 1. 为什么分两组

加密计算会引入一整套**MC 用不到**的东西（模运算、Montgomery、常数时间原语），
而 MC（Minecraft 宽键 / 远域地形）只关心浮点容器与整数键的性能。
所以两边按「INT + FP 成组」切开，各自演进。

## 2. 两组的构成

| | Main 组 | MC 组 |
|---|---|---|
| INT | `com.scalarwide`：`Wide128…1024`、`Limbs`、`MutableWide*`、**`ModArith`（加密）** | `com.scalarwide`：`Wide*`、`Limbs`、`MutableWide*`、宽键类型（`WideChunkPos` 等）；**无 `ModArith`** |
| FP | `com.scalarwide.fp`：`Binary256/640/1280`（不可变值语义） | `com.scalarwide.mc`：`Binary1280` 容器（可变 scratch，零分配） |
| 构建 | release 21 | release 17 |

## 3. 边界规则

1. **两组的 INT 是有意分叉，不是同一份源码的两个视图。**
   分叉之后 10 个同名文件已经不同（`Carry`、`Limbs`、`Wide*`…），
   两边的 `com.scalarwide.Wide512` 是**不同的类**。因此：
   **两个 jar 永不放进同一个 classpath**——同名不同物会让其中一个静默失效。
2. **MC 组冻结在分叉点**：不再 merge Main 的演进，只 cherry-pick 纯性能修复。
   这样 MC 的产物里永远没有 `ModArith`。
3. **加密代码只在 `ModArith` 里**，不得为它改动 `Wide*` / `Limbs` 的热路径。
   加密是 Main 组的新功能，不是给所有人加的开销。
4. **FP 层的包已分开**（`fp` vs `mc`），FP 之间不共享任何类型；
   同名 `Binary1280` 在两组里语义不同（值语义 vs scratch），不可混用。

## 4. 分叉点

MC 组的 INT 来自 Main 的历史提交（`mc-v1.0.5` 合并批次）。分叉后新增的
`ModArith` 只存在于 Main。需要回溯某个 MC 文件的来源时，用
`git log --follow` 在两个工作树里对照，不要假设两边一致。

## 5. 将来的选项（目前不做）

若某天需要 MC 吃到 Main 的新整数优化，正确的做法是：MC 依赖已发布的
`com.scalarwide:scalarwide` jar、删掉本地源码副本，而不是再 merge 一次。
代价是要先合流那 10 个漂移文件，并确认 MC 没有用到包私有 API。
