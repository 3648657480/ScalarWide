# 分支策略：`main` 与 `mc`

## 定位

| | `main` | `mc` |
|---|---|---|
| 定位 | 通用加速库（完整工具、全宽度 128→1024） | 改版 MC 专用（精简 + 极致） |
| 宽度 | 128/256/384/512/768/1024 | **≤ 512**（768/1024 已剪） |
| 工具 | 全套 | 仅 MC 所需（共享代码同源） |
| 性能 | 全面，但接受 768/1024 的架构上限与个别薄 margin | **≤512 零已知退化**（对 BigInteger 全数领先） |
| 发布 | `v0.11.x` 标签 | `mc-v0.11.x` 标签 |
| 消费者 | 任何通用场景 | 改版 MC / V2 框架 |

## 同步规则

- `main` 是共享代码的唯一真源；性能/修复补丁发布后 **merge `main` → `mc`**（被剪的部分自然不回流）。
- `mc` 上任何**通用纯收益**（如 384/512 `shr` 直算、块积实参直读字段）→ 必须**回流 `main`**，以补丁级发布。
- `mc` 专属取舍（进一步剪枝、MC 特化路径）留在 `mc`，集中在少量 commit，便于重复 rebase/merge。

## 版本与坐标

- `mc` 的 `VERSION` 与 `main` 同号（随合并演进）；标签以 `mc-` 前缀区分。
- **两线已冻结于 `1.0.0`**（`main`→`v1.0.0`、`mc`→`mc-v1.0.0`）；维护模式与
  按需唤醒清单见 `docs/FUTURE-WORK.md`。
- JitPack：`com.github.3648657480:ScalarWide:mc-v1.0.0`；框架钉 `mc-` 标签。
- 兼容契约（`docs/COMPATIBILITY.md`）对 `mc` 同样有效（≤512 子集）。

## CI 与发布纪律（2026-10-11 起）

- push / PR 触发 GitHub Actions（`.github/workflows/ci.yml`）：`test` 矩阵 JDK 21/25
  （两线通用）、`test (JDK 17, mc floor)`（仅 mc 线 push / PR 到 mc）、
  `package`（JDK 21 组装 jar + sources 并上传产物）。
- **打 tag 前置条件：目标 commit 的 CI 必须全绿。** JitPack 只看 tag、不等 CI——
  绿灯纪律由人执行：先等 Actions 结果，再 `git tag` + push。
- tag 命名（沿用）：`main` 线 `v0.11.x`；`mc` 线 `mc-v0.11.x`。

## `mc` 现状

### mc-v0.11.1
- 剪枝：`Wide768`/`Wide1024` 及对应测试/bench 移除；`LayoutReport`/`ConsumerApiTest` 适配 ≤512。
- 极致项（首批）：384/512 `shr` 直算——384: 6.74→2.17 ns；512: 13.31→4.94 ns（原 0.92× 落后 → 2.5× 领先）。
- ≤512 全部算子对 BigInteger 领先；`mulHigh`/512 亦无退化；oracle + 消费者契约测试全绿。

### mc-v0.11.2（针对性冲击第一波，MC 专属工具包）
- `com.scalarwide.mc.Wide256FlatMap`：交错槽开放寻址键映射（零分配、免装箱）——1M 键查找较
  `HashMap<Wide256>` 快 ~1.24×、较 BigInteger 映射 ~1.7×。
- `com.scalarwide.mc.McWarmup`：初始化预热——256 位首百次调用 5144→134 ns（38×）。
- `FlatMapProbe` 新探测（flat/hashmap/big 三模式同 run 对照）。
