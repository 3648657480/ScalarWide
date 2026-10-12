# ScalarWide

> 本分支（`mc`）：**改版 MC 专用精简极致版**——宽度 ≤512、工具精简、性能拉满；
> 通用完整库（全宽度全工具）见 `main` 分支。分支策略：`docs/BRANCHES.md`。

> [English](README.en.md) | 中文

> 纯标量定点大数库（mc 版 **128 → 512 位，四档**），零 SIMD、零平台探测。
> 为"宽域坐标 / 大数 key"场景准备：**正确性以 BigInteger 对拍为准，性能以 JMH 实测为准。**

版本：见仓库根 `VERSION`（唯一权威）。**已冻结于 `1.0.0`（项目结项，维护模式）**——
公开 API 冻结语义见 `docs/COMPATIBILITY.md` §6，未竟优化见 `docs/FUTURE-WORK.md`；实现矩阵与全部实测
（含负结果）见 `CHANGELOG.md`、`docs/DESIGN.md`、`docs/BENCH.md`。

## 这是什么

- `Wide128` / `Wide256` / `Wide384` / `Wide512`（mc 版 ≤512）：定宽二补码大整数（little-endian limbs）；
  `MutableWide128/256` 为零分配 scratch 载体。
- 运算：`add` `sub` `neg` `mul`（低 N 位）`mulHigh`（高 N 位）`div` `rem` `pow` `and` `or` `xor` `not` `andNot` `shl` `ushr` `shr` `compareUnsigned` `compareSigned` `bitLength` `bitCount` `min` `max` `abs`，以及 `fromBigInteger` / `toBigInteger` / `toHexString` / `toByteArray`（BE/LE/定宽）/ `fromByteArray`。
- 默认语义：算术按 2^N 回绕；`compareTo` = 无符号序（与 `equals` 一致，适合做 key）；移位量必须在 `[0, N)`，否则抛 `IllegalArgumentException`。
- 组合层：384/512 的乘法由 128/256 原子块积组合而成（另有直算 add/子运算）；
  设计与边界见 `docs/DESIGN.md` / `docs/INLINE.md`；`Limbs`（包私有）为常驻内部 oracle，参与三路对拍。

## 构建 / 测试 / 跑分

```powershell
.\gradlew.bat build
.\gradlew.bat test
.\gradlew.bat jmh --args="com.scalarwide.bench.Wide128Bench -wi 3 -i 5 -f 1"
.\gradlew.bat jmhJar
```
- 构建需 **JDK 21+**（基础字节码 release 17；`META-INF/versions/21` 覆层以 `--release 21` 编译；本机默认 java 过旧时，
  在用户级 `~/.gradle/gradle.properties` 里固定 `org.gradle.java.home=<JDK 路径>`）；Gradle 9.5（wrapper 走腾讯镜像）。
- 测试 = BigInteger 差分对拍：全极端值两两组合 + 固定种子（12345）随机 5000 对 × 每个运算，另含移位边界与构造器边界。
- 运行时要求 **Java 17+**（MC 1.17–1.20.4 的 Java 17 也能用）：基版对 `MulHi.high` 用
  `Math.multiplyHigh` + 符号修正（mul 家族慢 9–48%，已实测）；**Java 21+ 自动加载多版本覆层**里的
  `Math.unsignedMultiplyHigh` intrinsic，全速无差（回归 ≤1.1%，实测）。测试矩阵：17/21/25 全绿。

## 作为依赖使用（库的用法）

- **JitPack（推荐，零安装）**：

  ```groovy
  repositories { maven { url 'https://jitpack.io' } }
  dependencies { implementation 'com.github.3648657480:ScalarWide:mc-v1.0.0' }
  ```

- **本地安装**：`.\gradlew.bat publishToMavenLocal` → 坐标 `com.scalarwide:scalarwide:1.0.0`
  （之后本机任何 Gradle/Maven 工程可直接引用）；
- **手动**：GitHub Releases 下载 `ScalarWide-<版本>.jar`（+ `-sources.jar`）放入 classpath；
- API 入口：`Wide128/256/384/512`（不可变值类型，mc 版 ≤512）、`MutableWide128/256`（零分配 scratch）、
  `DivMagic`（常数除法）、MC 工具包（`com.scalarwide.mc`）；语义见各类 Javadoc 与 `docs/DESIGN.md`。
- 红线：`MutableWide*` 非线程安全、永不可作 map 键；加 `-Dscalarwide.guard=true` 可开启共享误用
  检测（跨线程调用抛 `IllegalStateException`，best-effort，关闭时零成本）。

### 版本与兼容策略

- **库独立演进、框架零改动吃性能**：版本号分离，消费者只钉依赖坐标；`1.0.x` 补丁线内
  仅性能/修复/纯增量（签名与语义冻结，见 `docs/COMPATIBILITY.md` §6），升级 = 只改一行依赖版本号 + 重构建。
- 破坏性变更只能进新次版本线并附迁移说明；公开 API 面与消费者回归契约见
  `docs/COMPATIBILITY.md`（测试：`ConsumerApiTest`）。
- 仓库双分支：`main` = 完整库（全宽度/全工具）；`mc` = 改版 MC 精简极致版
  （宽度 ≤512、零已知退化，标签 `mc-v*`）。策略见 `docs/BRANCHES.md`。

### MC 工具包（`mc` 分支专属）

- `Wide256FlatMap` / `LongLongFlatMap`：开放寻址键映射（零分配、免装箱）——256 位键 1M 查找较
  `HashMap<Wide256>` 快 ~1.24×、较 BigInteger 映射 ~1.7×；64 位打包键 **25–27 ns**（较 `HashMap<Long>` 1.9×）。
- `DivMagic`（`com.scalarwide`）：常数除数魔法除法——div **1.33–1.36×**、rem **1.84–1.91×**、
  2 的幂 **6.05×**。
- `McWarmup`：初始化预热，`run()` 一次后 256 位首百次调用 5144→134 ns（38×）。
- 稀疏宽断派发：`Wide256.mulFull/mulHigh`、`Wide512.mulHigh/div/rem` 在操作数 ≤半宽时走短路
  （稀疏域 1.25×–20×，稠密域 ≤2% 门禁）。

## 快速使用

```java
Wide256 k0 = Wide256.of(0x0123456789ABCDEFL, 0xFEDCBA9876543210L, 0, 0);
Wide256 k1 = Wide256.ofSigned(-1);
Wide256 mixed = k0.xor(k1);
BigInteger hi = k0.mulHigh(k1).toBigInteger();
```

## 宽度与坐标范围（必读）

| 宽度 | 定位 | vs BigInteger（工作站实测）|
|---|---|---|
| 128 | 原子 · 存储 key / Long128 的替代 | add 5.7× · sub 5.7× · mul 8.6× · div 2.7× |
| 256 | 原子 · 通用档（key、哈希、混合运算） | add 4.2× · mul 4.3× · div 1.6× · hash 2.8× |
| 384 | 3×128 · 中继 | add 2.7× · mul 3.2× · mulHigh 1.13× · shr 4.6× |
| 512 | 2×256 · 数据域单缓存行 | add 2.3× · mul 2.2× · mulHigh 1.06→（稀疏派发后更高）· div 1.1× |

**不覆盖**：1e306 量级的原始坐标（≈1020 bit）。原始坐标应保持"大数坐标 + 局部窗口"的分层设计，本库负责其中的 key / 哈希 / 短大数运算（全库对 BigInteger 无已知退化）。

## 目录

```
src/main/java/com/scalarwide/     核心：Limbs（参考引擎）、Wide128/256/384/512、
                                  MutableWide128/256、DivMagic、MulHi
src/main/java/com/scalarwide/mc/  MC 工具包：Wide256FlatMap、LongLongFlatMap、McWarmup
src/main/java21/…/MulHi.java      Java 21+ 多版本覆层（intrinsic 恢复）
src/test/java/com/scalarwide/     BigInteger 对拍（含稀疏/域外/消费者契约门禁）
src/jmh/java/com/scalarwide/bench JMH 基准与诊断探针（稀疏/魔法除法/多版本等）
docs/DESIGN.md · BENCH.md · BRANCHES.md · COMPATIBILITY.md
```

## 路线

- **已完成**：0.11.x 性能线（比较式进位、全宽度直算化、Knuth-D 除法、商模式除法、
  稀疏宽断派发、魔法常数除法、Java 17+ 多版本覆层、MC 工具包与硬接线层）。
- **已冻结（`1.0.0`）**：项目转入维护模式——未竟优化（batch/SoA、大表分片、
  `rem` q-skip、main 线 768/1024 `mulHigh` 等）见 `docs/FUTURE-WORK.md`（带触发条件）。
- **久期**：SIMD 冻结为实验项（标量是产品，SIMD 是 oracle 对照下的实验）。

## 许可

MIT（见 `LICENSE.txt`）。
