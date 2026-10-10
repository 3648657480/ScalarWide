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
- JitPack：`com.github.3648657480:ScalarWide:mc-v0.11.1`；框架钉 `mc-` 标签。
- 兼容契约（`docs/COMPATIBILITY.md`）对 `mc` 同样有效（≤512 子集）。

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

### mc-v0.11.4（同步 main 0.11.3/0.11.4 + FlatMap v2）
- **merge main**：数学算子（div/rem/pow/bitCount/min/max/abs）与 Knuth-D 除法进入 ≤512 面
  （div 全宽反超 big 1.6–2.6×）；`VERSION` 对齐 0.11.4。
- `Wide256FlatMap.remove`：**回退移位删除**（无墓碑、探测簇保持紧凑）——20 万随机操作差分
  与簇/边界用例全绿。
- 主仓 `DivisionInternalTest` 的 Limbs 级用例在 mc 保留（引擎通用，不受宽度剪枝影响）。
