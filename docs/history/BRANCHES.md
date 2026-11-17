# 分支策略：单分支 `main`

> 2026-11-18（2.0.0）起，`mc` 分支已删除，内容并入 `main` 的 `com.scalarwide.mc.*` 包。
> 本文替代旧的「双分支」策略。

## 定位

| | 现在 |
|---|---|
| 分支 | 只有 `main` |
| 结构 | 单模块、四个包：`main.integer` / `main.fp` / `mc.integer` / `mc.fp` |
| 发布 | `v2.0.0` 起，标签不带前缀 |

## 两组的边界

- **同仓库、同模块，但两组代码各自独立**（`main.*` 与 `mc.*` 的同名类不是同一个类）：
  允许各自为各自的场景优化。
- 边界细节（加密只进 `main.integer`、正确性缺陷双组同修、容器只暴露少数操作）见
  `docs/design/TWO-LINES.md` 与 `AGENTS.md`。
- **不再有 merge**：跨组的工作在同一分支上按包进行，不存在「回流」流程。

## 历史

- `v0.11.x`–`1.0.x`：双分支（`main` + `mc`），`mc` 用 `mc-v*` 标签，通过 merge 同步。
- 2026-11-17/18（2.0.0）：合并为单分支四包；`mc` 分支本地与远端均已删除，
  旧 tip 保留为 tag `mc-final`（不可变的考古入口）。

## CI 与发布纪律（2026-10-11 起）

- push / PR 触发 GitHub Actions（`.github/workflows/ci.yml`）：`test` 矩阵 JDK 21/25，
  外加 `bench-smoke`（小规模 JMH）与 `guard`（`-Dscalarwide.guard=true` 的共享误用检测）。
- 发布：`VERSION` + `CHANGELOG.md` 同步更新，打 `vX.Y.Z` 标签；破坏性变更必须附迁移表。
