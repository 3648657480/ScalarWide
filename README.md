# ScalarWide

> [English](README.en.md) | 中文

> 纯标量定点大数库（**128 → 1024 位整数 + 256/640/1280 位浮点容器**），零 SIMD、零平台探测。
> 为"宽域坐标 / 大数 key / 加密原型"场景准备：**正确性以 BigInteger 对拍为准，性能以 JMH 实测为准。**

版本：见仓库根 `VERSION`（唯一权威），当前 **2.0.0**。2.0.0 是**破坏性重构**（包名全部改变），
迁移表见 `CHANGELOG.md`。实现矩阵与全部实测（含负结果）见 `docs/DESIGN.md`、`docs/BENCH.md`
（**账本顶部有单页「当前权威基线」**）。

> **性能快照（1.0.3 批次，工作站 7950X / JDK 25 / 锁核）**：`toBigInteger` **19.3×**、
> `toHexString` **16.0×**、`hashCode` **14.1×**、字节编解码 **4.6–5.3×**、`bitLength` **3.01×**、
> `toString` **2.84×**、`mulHigh` **1.65–2.66×**、`mul` **1.24–1.92×**、`add`/`sub` **1.09–1.75×**；
> 与 Long2 0.1.8 全项对拍（1024 档 18 项）：**7 胜 / 8 平 / 3 负**。
> **2.0.0 新增实测**（`docs/BENCH.md` E4–E6）：容器 `mulSmall` 23.2 ns(@256) / 42.3 ns(@640)、
> 零分配；Montgomery 域内乘法 4 肢 **41.7 ns**（循环版 85.1）、6 肢 **82.2 ns**（142.0）；
> 无状态 `powMod` 9 肢 1505 → **406 µs**（3.7×），分配降到 1/500。

## 这是什么

单仓库、单模块、**四个包**，两组各自演进（详见 `docs/TWO-LINES.md`）：

| 包 | 内容 |
|---|---|
| `com.scalarwide.main.integer` | `Wide128…1024`（定宽二补码，little-endian limbs）、`MutableWide128/256`、`Limbs`、`Carry`、**`ModArith`（加密指令集）**、**`Montgomery`（上下文）** |
| `com.scalarwide.main.fp` | `Binary256/640/1280`（IEEE 值语义）、`BigFloat`、`FpFormat`、`WideOps`、`Decimal`、`Exact` |
| `com.scalarwide.mc.integer` | MC 组整数：核心副本（`MulHi`/`DivMagic`）+ 宽键 `WideChunkPos` / `WideSectionKey2560` / `WideChunkCoord1024` + 平表族 |
| `com.scalarwide.mc.fp` | MC 组浮点：`Binary256/640/1280` **坐标容器**（可变 scratch、每调用零分配） |

- 整数运算：`add` `sub` `neg` `mul` `mulHigh` `div` `rem` `pow` `and` `or` `xor` `not` `andNot`
  `shl` `ushr` `shr` `compareUnsigned` `compareSigned` `bitLength` `bitCount` `min` `max` `abs`，
  以及 `fromBigInteger` / `toBigInteger` / `toHexString` / `toByteArray` / `fromByteArray`。
- 默认语义：算术按 2^N 回绕；`compareTo` = 无符号序（与 `equals` 一致，适合做 key）；
  移位量必须在 `[0, N)`，否则抛 `IllegalArgumentException`。
- 加密：`ModArith` 提供位访问、整宽/逐 lane 旋转、模加/减/乘/幂、`gcd`、`modInverse`、
  `ctCompare/ctSelect/ctSwap`、大端 64 位字存取；**常数时间声明逐条写在类注释与 `docs/WIDE-CRYPTO.md`**。
- 浮点：`Binary256/640/1280` 支持 `add/subtract/multiply/divide/sqrt/fma/compare/toString`；
  MC 容器只提供 `set/setFrom/copyFrom/addSmall/mulSmall/scalePow2/floorLow64Frac/compareTo`。

## 构建 / 测试 / 跑分

```powershell
.\gradlew.bat build
.\gradlew.bat test
.\gradlew.bat jmh --args="com.scalarwide.bench.Wide128Bench -wi 3 -i 5 -f 1"
.\gradlew.bat jmhJar
```

- 构建需 **JDK 21+**（字节码 release 21）；Gradle 9.5（wrapper 走腾讯镜像）。
- 测试 = BigInteger 差分对拍：全极端值两两组合 + 固定种子（12345）随机 5000 对 × 每个运算；
  2.0.0 共 **306 例**（main 组 + mc 组各自独立门禁）。
- 运行时要求 **Java 21+**（class major 65）。

## 作为依赖使用（库的用法）

- **JitPack（推荐，零安装）**：

  ```groovy
  repositories { maven { url 'https://jitpack.io' } }
  dependencies { implementation 'com.github.3648657480:ScalarWide:v2.0.0' }
  ```

- **本地安装**：`.\gradlew.bat publishToMavenLocal` → 坐标 `com.scalarwide:scalarwide:2.0.0`；
- **手动**：GitHub Releases 下载 `ScalarWide-2.0.0.jar`（+ `-sources.jar`）放入 classpath；
- 红线：`MutableWide*` 与 MC 容器非线程安全、永不可作 map 键；加 `-Dscalarwide.guard=true`
  可开启共享误用检测（best-effort，关闭时零成本）。

### 版本与兼容策略

- 版本号见 `VERSION`；消费者只钉依赖坐标，升级 = 改一行版本号 + 重构建。
- **2.0.0 是破坏性变更**：包名重构（`com.scalarwide.*` → `com.scalarwide.main.integer.*` 等）、
  `fp` 子项目并回主模块、`mc` 分支内容并入 `mc.*` 包。迁移表见 `CHANGELOG.md` 顶部。
- 公开 API 面与消费者回归契约见 `docs/COMPATIBILITY.md`（测试：`ConsumerApiTest`）。
- **单分支**：仓库只有 `main`。两组的边界（同名类不是同一个类、加密只进 main、正确性缺陷双组同修）
  写在 `docs/TWO-LINES.md` 与 `AGENTS.md`。

## 快速使用

```java
import com.scalarwide.main.integer.Wide256;

Wide256 k0 = Wide256.of(0x0123456789ABCDEFL, 0xFEDCBA9876543210L, 0, 0);
Wide256 k1 = Wide256.ofSigned(-1);
Wide256 mixed = k0.xor(k1);
BigInteger hi = k0.mulHigh(k1).toBigInteger();
```

```java
import com.scalarwide.mc.fp.Binary1280;   // MC 组：每线程一份，循环内零分配

Binary1280 c = new Binary1280();
c.setFrom(chunkLimbs);      // 每 chunk 一次
c.scalePow2(-40);           // 每 octave 一次
c.mulSmall(0x9E3779B97F4A7C15L);
long[] low = new long[1];
double frac = c.floorLow64Frac(low);
```

## 宽度与坐标范围（必读）

| 宽度 | 定位 | 说明 |
|---|---|---|
| 128 | 原子 · 存储 key / Long128 的替代 | 实测 key 场景只需 59+59 位；128 是收益/代价甜点 |
| 256 | 原子 · 通用档（key、哈希、混合运算） | 主原子 |
| 384 | 3×128 · 中继 | 组合+直算；全算子已实现 |
| 512 | 2×256 · 数据域单缓存行 | 组合+直算；全算子已实现 |
| 768 | 3×256 · 超大坐标 | 直算；`mulHigh` 改列累加内核（1.0.1） |
| 1024 | 4×256 · 逼近 double 上限 | 直算；`mulHigh` 改列累加内核（1.0.1） |

浮点：`Binary256`（p=237）、`Binary640`（p=616）、`Binary1280`（p=1252）。MC 容器同精度。
基准数字（BigInteger 同场）与全部负结果见 `docs/BENCH.md`。

**不覆盖**：1e306 量级的原始坐标（≈1020 bit）。原始坐标应保持"大数坐标 + 局部窗口"的分层设计。

## 目录

```
src/main/java/com/scalarwide/main/integer  核心整数 + ModArith + Montgomery
src/main/java/com/scalarwide/main/fp       通用浮点
src/main/java/com/scalarwide/mc/integer    MC 整数（核心副本 + 宽键 + 平表）
src/main/java/com/scalarwide/mc/fp         MC 容器（256/640/1280）
src/test/java/...                          BigInteger 对拍门禁（含消费者契约）
src/jmh/java/...                           JMH 基准与诊断探针
docs/                                      DESIGN / BENCH / TWO-LINES / COMPATIBILITY / WIDE-CRYPTO / WIDE-FORMATS ……
```

## 路线

- **已完成**：0.1.x 基线 → 附魔轮（寄存器化/展开）→ 直算化引擎 + Knuth-D 除法 + 商模式除法 +
  字节序列化 → 1.0.0 冻结 → 2.0.0 两组四包重构 + 容器 256/640 + Montgomery 上下文与展开核。
- **未竟**：Montgomery 9 肢的形状优化（展开实测更慢）、严格常数时间（CT 模幂/逆元）、
  `powMod` 窗口化、容器 `compareTo` 基准重测。见 `docs/FUTURE-WORK.md` 与 `docs/WIDE-CRYPTO.md`。
- **久期**：SIMD 冻结为实验项（标量是产品，SIMD 是 oracle 对照下的实验）。

## 许可

MIT（见 `LICENSE.txt`）。
