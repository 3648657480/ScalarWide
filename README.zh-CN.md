# ScalarWide

> 纯标量定点大数库（128 / 256 / 384 位），零 SIMD、零平台探测。
> 为"宽域坐标 / 大数 key"场景准备：**正确性以 BigInteger 对拍为准，性能以 JMH 实测为准。**

版本：见仓库根 `VERSION`（唯一权威）。当前 `0.3.0` = 附魔轮 1（128/256 寄存器化+展开）
+ `mulFull` 全积原语 + `Wide512`；形态与数据见 `CHANGELOG.md` 与 `docs/BENCH.md`。

## 这是什么

- `Wide128` / `Wide256` / `Wide384`：定宽二补码大整数（little-endian limbs）。
- 运算：`add` `sub` `neg` `mul`（低 N 位）`mulHigh`（高 N 位）`and` `or` `xor` `not` `andNot` `shl` `ushr` `shr` `compareUnsigned` `compareSigned` `bitLength`，以及 `fromBigInteger` / `toBigInteger` / `toHexString`。
- 默认语义：算术按 2^N 回绕；`compareTo` = 无符号序（与 `equals` 一致，适合做 key）；移位量必须在 `[0, N)`，否则抛 `IllegalArgumentException`。
- 内部参考引擎 `Limbs`（包私有）：所有宽度共用的正确性基线；128/256 的热路径特化、零分配 API 是后续"附魔轮"内容（见 `docs/DESIGN.md`）。

## 构建 / 测试 / 跑分

```powershell
.\gradlew.bat build
.\gradlew.bat test
.\gradlew.bat jmh --args="com.scalarwide.bench.Wide128Bench -wi 3 -i 5 -f 1"
.\gradlew.bat jmhJar
```
- 工具链：JDK 25（toolchain，字节码 release 21）；Gradle 9.5（wrapper 走腾讯镜像）。
- 测试 = BigInteger 差分对拍：全极端值两两组合 + 固定种子（12345）随机 5000 对 × 每个运算，另含移位边界与构造器边界。

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
| 128 | 存储 key / Long128 的替代 | 实测 key 场景只需 59+59 位；128 是收益/代价甜点 |
| 256 | 通用档（key、哈希、混合运算） | |
| 384 | 兜底（正确性优先，性能 P1） | |

**不覆盖**：1e306 量级的原始坐标（≈1020 bit）。原始坐标应保持"大数坐标 + 局部窗口"的分层设计，本库负责其中的 key / 哈希 / 短大数运算。

## 目录

```
src/main/java/com/scalarwide/     Limbs（参考引擎）、Wide128/256/384
src/test/java/com/scalarwide/     BigInteger 对拍测试
src/jmh/java/com/scalarwide/bench JMH 基准（BigInteger 同场对照）
docs/DESIGN.md                    设计决策 / 附魔路线 / 验收标准
```

## 路线

1. **0.1.x 基线**：正确性闭环 + JMH 基线数字（记录在 `docs/BENCH.md`）。
2. **附魔轮 1（0.2.0 已完成）**：128/256 热路径寄存器化 + 手动展开（一次一变量，JMH 锁核前后对照）。
3. **附魔轮 2**：零分配 API（结果写调用方缓冲 / 对象池）。
4. **附魔轮 3**：内联验证（`-XX:+PrintInlining`）、`VarHandle` 评估。
5. SIMD 冻结为实验项（标量是产品，SIMD 是 oracle 对照下的实验）。

## 许可

MIT（见 `LICENSE.txt`）。
