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
- `mc` 上任何**通用纯收益**（无数据假设、全输入均匀正确；如 384/512 `shr` 直算、Knuth-D）→ 必须**回流 `main`**，以补丁级发布。
- **硬接线层（`main` 永不收）**：带输入分布/平台假设的优化只留 `mc`，且必须附：
  1. 分布证据（如 P4：41.6% 高位空）；2. 退化兜底（超界输入走通用路径）；3. 双重门禁测试（假设域 + 全域差分）。
  例如：64 位打包键映射（epoch-local 假设）、稀疏快速路径等。
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

### mc-v0.11.5（硬接线第一锤：64 位打包键）
- `LongLongFlatMap`（原语 ctrl+key+value 数组，`pack(x, z)` 局部坐标进单 long；向后移位删除）。
- 1M 键查找 **25.3–26.6 ns**：较 `HashMap<Long>` 1.9×、较 `Wide256FlatMap` 2.3×、
  较 `HashMap<Wide256>` 3.0×（对 BigInteger 映射 ~4.3×）；insert 15.7–28.6。
- 假设书：键 = epoch 窗口内局部坐标（x/z 各 ≤32 位有符号）；超界键回退 `Wide256FlatMap`；
  差分/边界/簇测试全绿（`LongLongFlatMapTest`）。

### mc-v0.11.6（Java 17+：服务全 MC 版本的兼容层）
- 基版 `--release 17`（major 61）；唯一 18+ API 收敛为 `MulHi.high`（17 版 = `multiplyHigh` + 修正）。
- **多版本覆层**：`META-INF/versions/21/.../MulHi.class` 在 21+ 自动恢复 intrinsic。
- 实测（工作站 JDK21）：覆层路径回归 ≤1.1%（噪声级）；纯 shim 路径（17）mul 家族慢 9–48%。
- 测试矩阵 17/21/25 全绿；基准出处头（`Provenance`）落地。

### mc-v0.11.7（B4：稀疏宽断派发——首组 mc 独占 per-op 收益）
- 保留：`Wide256.mulFull`/`mulHigh` 的 ≤128 位操作数短路——稀疏域 **1.85× / 9.7×**，稠密域零退化。
- 撤除：`mul`/`div`/`rem` 候选（mul 无收益且混合 +17%；div/rem 稠密 +7~9% 破坏零退化承诺）——
  五选二、逐元素留撤已复测归位。
- 意义：**mc 与 main 的同算子性能差首次出现**（main 保持均匀无假设；本类分支 main 永不收）。
