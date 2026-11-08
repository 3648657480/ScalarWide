# 交接：宽类型性能升级（供 Project-G2 首屏使用）（2026-11-05）

> 起点文档。目标是把宽运算在 2^53 场景下的**分配与 BigInteger 中转**消掉，让下游 mc 线
> 和 Project-G2 的首屏受益。
> 规则沿用本库：一次一个变量、同机同命令同版本、只记现象，不下夸大的结论；
> 性能对照按 `BENCH.md` 的《比较体系 v2》（主对照 = 同场景库 BC Nat + 版本前后，
> BigInteger 只作正确性 oracle）。

## 1. 三层拓扑（先认清谁是谁）

| 层 | 仓库 | 版本 | 内容 |
|---|---|---|---|
| 核心库 | `C:\Project-ScalarWide`（本仓库） | 1.0.0 | `Wide128/256/384/512/768/1024`、`Mutable*`、`Limbs` 等 limb 原语，无 `mc` 包 |
| mc 线 | `C:\Project-ScalarWide-2560` | 1.0.4 | 在核心之上提供 `com.scalarwide.mc.*`：`WideChunkCoord1024`、`WideSectionKey2560`、`WideSectionCoord512`、各种宽 map |
| 使用方 | `C:\Project-G2` | 0.1.0 | `mod/build.gradle` 里 `implementation` + `include "com.scalarwide:scalarwide:1.0.4"`；fork 补丁层也直接引用 mc 线的类 |

**关键**：G2 实际热路径上的类是 mc 线的包装类（`WideChunkCoord1024` / `WideSectionKey2560`），
它们自己持有 `long[] limbs` 并**各自实现**了 `toByteArray` / `toBigInteger` / `shiftRight` /
`equals`。所以只改核心库不会自动改善 G2 —— 需要在核心库落优化后的 limb 原语，再由 mc 线
改成委托（保持 ABI），最后由 mc 线发新版本给 G2。

## 2. 现象（带条件，来自 G2 的采样）

G2 客户端 JFR（`-Dfarlands.jfr=<file>`，70 s，1247 个 `jdk.ExecutionSample`），测量窗口是
"进入 2^53 全新区域后首屏的 20 秒"（2026-11-05 17:17 左右，本地开发机）：

| 方法 | 采样占比 |
|---|---|
| `WideChunkCoord1024.toByteArray()` | 6.89% |
| `java.math.BigInteger.getInt(int)` | 6.61% |
| `jdk.internal.util.ArraysSupport.mismatch(int[],int[],int)` | 6.28% |
| `BigInteger.makePositive(...)` | 3.68% |
| `BigInteger.compareTo(BigInteger)` | 3.38% |
| `BigInteger.shiftRightImplWorker(...)` | 2.91% |
| `BigInteger.stripLeadingZeroBytes(...)` | 2.79% |
| `WideChunkCoord1024.of(BigInteger)` | 1.59% |

调用栈（G2 侧）：

```
ChunkAccess.getSection(int)
  → ChunkAccessWindowMixin.farlands$key(WideSectionCoord512)
    → WideSectionCoord512.toBigInteger() → toByteArray()
```

**必须知道的前提**：这条栈是 **G2 自己的调用点**（每方块访问一次，把
`WideSectionKey2560` 当 map 键）。G2 已在自己的工作树里用 long 键的 section 读缓存消掉了它，
首屏 3×3 从 20 s 降到 12 s，服务端指纹不变。

因此：**上表不能直接当作"核心库固有成本"**。本仓库的第一步不是照单优化，而是：

1. 用本库 BENCH 复现 `toByteArray` / `toBigInteger` / `shiftRight` / 宽键 `hash/equals`
   在 1024/2560 位下的绝对成本；
2. 再看 mc 线包装类里有多少是能委托给核心库的重复实现。

## 3. 已知的实现事实（核心库层面）

- `WideChunkCoord1024.hashCode()` / `WideSectionKey2560.hashCode()` 已返回缓存的 `hash`；
  `equals` 是 `Arrays.equals(limbs)`（不会提前退出）。
- 真正产生分配的是：`toByteArray()`（每次 new byte[]）、`toBigInteger()`
  （`new BigInteger(bytes)`）、`shiftRight(bits)`（`of(toBigInteger().shiftRight(bits))`）、
  `toString()`。
- 现有 BENCH 的历史结论：≤512 位 add/sub/mul 与 BC Nat 持平或领先；768/1024 位的 add 落后
  15–25%，`mulHigh` 同列为待办（见 `docs/BENCH.md` 与相应 csv）。这些都是"真待办"，不要
  在交接里当成已完成。

## 4. 建议的优化方向（按预期收益）

1. **低 limb 快速路径**：G2 的实际键是 chunk 2^49 / block 2^53，高位 limb 全零。比较、哈希、
   序列化都可以先判高位为零，退化成 `long` 路径。
2. **`toByteArray` / `toBigInteger` 不留在热路径**：需要时缓存；`shiftRight` 用 limb 移位，
   不要 BigInteger 中转；`toString` 只在诊断用。
3. **`equals` 的宽度**：2560 位 = 320 字节；可先比 hash 或从低位 limb 提前退出。
4. **`of(...)` 的分配**：常用/小值可复用，或补 `long` 构造重载（旧签名必须保留）。
5. **mc 线去重**：把 `toByteArray` / `shiftRight` / 比较等改成调用核心库的原语，避免两份实现
   各自漂移。

## 5. 下游 ABI 约束（不能改）

G2 的 mod 与 fork 补丁层直接引用以下符号；改名/改签名会让 weave 在加载期失败：

- mod：`com.scalarwide.Wide`、`com.scalarwide.mc.WideChunkPos`（`of`、`neighbor`）、
  `com.scalarwide.mc.WideSectionCoord`、`com.scalarwide.mc.WideSectionKey`
- fork 补丁层：`com.scalarwide.mc.WideChunkCoord`、`WideChunkPosByteMap`、
  `WideChunkPosFlatMap`、`WideChunkPosOrderedSet`、`WideSectionCoord`、`WideSectionKey`、
  `WideSectionKey2560`、`WideSectionCoord512`、`WideChunkCoord1024`

版本号变更可以；包名、类名、公开方法签名不要动。

## 6. 验收（必须回 G2 侧跑）

1. mc 线发新版本（例如 1.0.5）。
2. G2 侧：`mod/build.gradle` 的 `implementation` / `include` 版本升上去 → **重打
   identity-patched fork jar** → 构建。
3. 硬指标（全部满足才算通过）：
   - 指纹不变：`norm` 的 `surf=b9216bad2029 floor=8936c275d063 biome=4bf4e2ea5526`；
     `dist53` 的 `surf=879024146b33 floor=9319dd6a448a`；
   - 五门 5/5：`powershell -ExecutionPolicy Bypass -File .\tools\verify.ps1 -Mode all`
     （norm / seam / coll / save / dist53）；
   - 首屏 3×3 出现时间 < 12 s（当前基线），且 JFR `hot-methods` 前 10 里没有
     `BigInteger` 系 / `toByteArray`。

## 7. 复现与测量（G2 侧，供回验）

- 首屏：`runClient` + `-Dfarlands.autotest.passive=true` + `-Dfarlands.autotest.directTp=true`
  传送到**从未生成过**的远域（如 z=-210000），`-Dfarlands.diag.follow=true`；测
  `[FARPUSH] ring recenter=... jump` → `[FARPUSH] ring promote radius=2` 的时间差。
- 采样：`-Dfarlands.jfr=<file.jfr>`，运行中 `jcmd <pid> JFR.dump filename=<file>` 抓取，
  `jfr view hot-methods` 看热点。
- 坑：测试前确认没有残留的 `runClient` 进程，否则世界被锁、日志出现
  `Failed to read level test7 data`，数据无效。

## 8. 约束

- G2 仓库的规则不允许把本库代码/名称搬进去（第三方代码只在行为层面观察）；交接只在本库仓库内。
- 本库当前 jar 内没有 `LICENSE` / `NOTICE`；mc 线会被 G2 `include` 进 mod jar 分发，发版前
  建议补上许可证文件。
- 改完先在本库 BENCH 上给前后对比（同机同命令），再交给 G2 做端到端验收；不要用体感当结论。
