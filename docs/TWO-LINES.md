# 两组四包：main 与 mc

> 2026-11-17 重构后，两条线合并为**一个仓库、一个模块、四个包**。
> 本文替代旧的「两个仓库/分支」边界说明。

## 1. 结构

```
src/main/java/com/scalarwide/
├─ main/integer/    核心整数：Wide128…1024、MutableWide*、Limbs、Carry、ModArith（加密）
├─ main/fp/         通用浮点：Binary256/640/1280（值语义）、BigFloat、WideOps、Decimal
├─ mc/integer/      MC 组整数：核心副本（含 MulHi/DivMagic）+ 宽键、平表族
└─ mc/fp/           MC 组浮点：容器 Binary256/640/1280（可变 scratch，零分配）
```

`int` 是 Java 保留字，故包名用 `integer`。

## 2. 为什么还是两组

加密计算（`ModArith`）只服务 main 组，MC 组不需要；而 MC 组的容器与宽键要求
「每次调用零分配、只暴露少数操作」，与通用浮点的值语义相反。两组的取舍不同，
所以源码各自独立：

| 类型 | main 组 | mc 组 |
|---|---|---|
| `Wide*` 整数 | 为加密让路（可加类、可加分支） | 为键/哈希让路（分配、缓存行、吞吐） |
| 浮点 | `Binary256/640/1280` 值语义 | `Binary256/640/1280` 容器（scratch、零分配） |
| 加密 | 有 `ModArith` | **没有** |

## 3. 规则

1. **两组的同名类不是同一个类**：`com.scalarwide.main.integer.Wide512` 与
   `com.scalarwide.mc.integer.Wide512` 是两份代码，行为可以不同（性能取舍不同）。
2. **加密代码只进 `main/integer/ModArith`**，不得为它改 `Wide*`/`Limbs` 热路径。
3. **正确性缺陷必须双组同修**：任一组发现 `Wide*`/`Limbs`/容器语义缺陷，修复要在
   同一轮同步到另一组（性能改动不必同步）。行为不一致比速度不一致危险。
4. MC 组的 `addSmall` 有远场前提 `|v| >= 2^53`；门禁按该前提构造用例。

## 4. 历史

重构前是「同一仓库的两个分支/工作树」：`main` 分支 + `mc` 分支
（`C:\Project-ScalarWide-2560`）。两者靠分支隔离，同名类同名不同物。
2026-11-17 合并为单仓库四包，mc 分支随后删除，内容全部迁入 `mc/*` 包并通过门禁。
