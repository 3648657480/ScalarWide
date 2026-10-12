# ScalarWide

> [English](README.en.md) | 中文

> 纯标量定点大数库（**128 → 1024 位，六档**），零 SIMD、零平台探测。
> 为"宽域坐标 / 大数 key"场景准备：**正确性以 BigInteger 对拍为准，性能以 JMH 实测为准。**

版本：见仓库根 `VERSION`（唯一权威）。**已冻结于 `1.0.0`（项目结项，维护模式）**——
公开 API 冻结语义见 `docs/COMPATIBILITY.md` §6，未竟优化见 `docs/FUTURE-WORK.md`；
实现矩阵与全部实测（含负结果）见 `CHANGELOG.md`、`docs/DESIGN.md`、`docs/BENCH.md`。

## 这是什么

- `Wide128` / `Wide256` / `Wide384` / `Wide512` / `Wide768` / `Wide1024`：定宽二补码大整数（little-endian limbs）；
  `MutableWide128/256` 为零分配 scratch 载体。
- 运算：`add` `sub` `neg` `mul`（低 N 位）`mulHigh`（高 N 位）`div` `rem` `pow` `and` `or` `xor` `not` `andNot` `shl` `ushr` `shr` `compareUnsigned` `compareSigned` `bitLength` `bitCount` `min` `max` `abs`，以及 `fromBigInteger` / `toBigInteger` / `toHexString` / `toByteArray`（BE/LE/定宽）/ `fromByteArray`。
- 默认语义：算术按 2^N 回绕；`compareTo` = 无符号序（与 `equals` 一致，适合做 key）；移位量必须在 `[0, N)`，否则抛 `IllegalArgumentException`。
- 组合层：384/512/768/1024 的乘法由 128/256 原子块积组合而成（512/384 档另有直算 add/比较）；
  设计与边界见 `docs/DESIGN.md` / `docs/INLINE.md`；`Limbs`（包私有）为常驻内部 oracle，参与三路对拍。

## 构建 / 测试 / 跑分

```powershell
.\gradlew.bat build
.\gradlew.bat test
.\gradlew.bat jmh --args="com.scalarwide.bench.Wide128Bench -wi 3 -i 5 -f 1"
.\gradlew.bat jmhJar
```
- 构建需 **JDK 21+**（字节码 release 21；本机默认 java 过旧时，在用户级
  `~/.gradle/gradle.properties` 里固定 `org.gradle.java.home=<JDK 路径>`）；Gradle 9.5（wrapper 走腾讯镜像）。
- 测试 = BigInteger 差分对拍：全极端值两两组合 + 固定种子（12345）随机 5000 对 × 每个运算，另含移位边界与构造器边界。
- 运行时要求 **Java 21+**（class major 65）。全库唯一 Java 18+ API 是 `Math.unsignedMultiplyHigh`
  （原子乘法，77 处）；如需兼容 Java 17，可用 `Math.multiplyHigh` + 符号修正替换（每条多 ~3-4 指令，代价未实测）。

## 作为依赖使用（库的用法）

- **JitPack（推荐，零安装）**：

  ```groovy
  repositories { maven { url 'https://jitpack.io' } }
  dependencies { implementation 'com.github.3648657480:ScalarWide:v1.0.0' }
  ```

- **本地安装**：`.\gradlew.bat publishToMavenLocal` → 坐标 `com.scalarwide:scalarwide:1.0.0`
  （之后本机任何 Gradle/Maven 工程可直接引用）；
- **手动**：GitHub Releases 下载 `ScalarWide-<版本>.jar`（+ `-sources.jar`）放入 classpath；
- API 入口：`Wide128/256/384/512/768/1024`（不可变值类型）、`MutableWide128/256`（零分配 scratch）、
  语义见各类 Javadoc 与 `docs/DESIGN.md`。
- 红线：`MutableWide*` 非线程安全、永不可作 map 键；加 `-Dscalarwide.guard=true` 可开启共享误用
  检测（跨线程调用抛 `IllegalStateException`，best-effort，关闭时零成本）。

### 版本与兼容策略

- **库独立演进、框架零改动吃性能**：版本号分离，消费者只钉依赖坐标；`1.0.x` 补丁线内
  仅性能/修复/纯增量（签名与语义冻结，见 `docs/COMPATIBILITY.md` §6），升级 = 只改一行依赖版本号 + 重构建。
- 破坏性变更只能进新次版本线并附迁移说明；公开 API 面与消费者回归契约见
  `docs/COMPATIBILITY.md`（测试：`ConsumerApiTest`）。
- 仓库双分支：`main` = 完整库（全宽度/全工具）；`mc` = 改版 MC 精简极致版
  （宽度 ≤512、零已知退化，标签 `mc-v*`）。策略见 `docs/BRANCHES.md`。

## 快速使用

```java
Wide256 k0 = Wide256.of(0x0123456789ABCDEFL, 0xFEDCBA9876543210L, 0, 0);
Wide256 k1 = Wide256.ofSigned(-1);
Wide256 mixed = k0.xor(k1);
BigInteger hi = k0.mulHigh(k1).toBigInteger();
```

## 宽度与坐标范围（必读）

| 宽度 | 定位 | 说明 |
|---|---|---|
| 128 | 原子 · 存储 key / Long128 的替代 | 实测 key 场景只需 59+59 位；128 是收益/代价甜点 |
| 256 | 原子 · 通用档（key、哈希、混合运算） | 主原子 |
| 384 | 3×128 · 中继 | 组合+直算；全算子已实现 |
| 512 | 2×256 · 数据域单缓存行 | 组合+直算；全算子已实现 |
| 768 | 3×256 · 超大坐标 | 直算；`mulHigh` 为已记录架构短板（见 `docs/BENCH.md`） |
| 1024 | 4×256 · 逼近 double 上限 | 直算；`mulHigh` 为已记录架构短板（见 `docs/BENCH.md`） |

基准数字（BigInteger 同场）与全部负结果见 `docs/BENCH.md`。

**不覆盖**：1e306 量级的原始坐标（≈1020 bit）。原始坐标应保持"大数坐标 + 局部窗口"的分层设计，本库负责其中的 key / 哈希 / 短大数运算。

## 目录

```
src/main/java/com/scalarwide/     Limbs（参考引擎）、Wide128/256/384/512/768/1024、MutableWide128/256
src/test/java/com/scalarwide/     BigInteger 对拍测试（含消费者契约门禁）
src/jmh/java/com/scalarwide/bench JMH 基准与诊断探针（BigInteger 同场对照）
docs/                             DESIGN / BENCH / BRANCHES / COMPATIBILITY / FUTURE-WORK
```

## 路线

- **已完成**：0.1.x 基线 → 附魔轮（寄存器化/展开）→ 直算化引擎 + Knuth-D 除法 +
  商模式除法 + 字节序列化；（`mc` 线另有硬接线层与 Java 17+ 多版本覆层）。
- **已冻结（`1.0.0`）**：项目转入维护模式——未竟优化（batch/SoA、大表分片、
  `rem` q-skip、768/1024 `mulHigh`）见 `docs/FUTURE-WORK.md`（带触发条件）。
- **久期**：SIMD 冻结为实验项（标量是产品，SIMD 是 oracle 对照下的实验）。

## 许可

MIT（见 `LICENSE.txt`）。
