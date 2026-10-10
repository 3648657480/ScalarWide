# ScalarWide

> English | [中文](README.md)

> A pure scalar fixed-width big-integer library (128 -> 1024 bits, six widths).
> Zero SIMD, zero platform detection.
> Correctness is gated by BigInteger differential testing; performance is
> measured with JMH.

Version: see repo-root `VERSION` (single authority). Current `0.11.1` (mulHigh
tuning + guardrails); implementation matrix and all measurements (including
negative results) in `CHANGELOG.md`, `docs/DESIGN.md`, `docs/BENCH.md`.

## What it is

- `Wide128` / `Wide256` / `Wide384` / `Wide512` / `Wide768` / `Wide1024`:
  fixed-width two's-complement big ints (little-endian limbs);
  `MutableWide128/256` are allocation-free scratch carriers.
- Ops: `add` `sub` `neg` `mul` (low N bits) `mulHigh` (high N bits) `and` `or`
  `xor` `not` `andNot` `shl` `ushr` `shr` `compareUnsigned` `compareSigned`
  `bitLength`, plus `fromBigInteger` / `toBigInteger` / `toHexString`.
- Semantics: arithmetic wraps mod 2^N; `compareTo` is unsigned (consistent with
  `equals`, suitable for keys); shift amounts must be in `[0, N)` or an
  `IllegalArgumentException` is thrown.
- Composition layer: 384/512/768/1024 multiplication composes 128/256 atom
  block products (512/384 also have direct add/compare); design and boundaries
  in `docs/DESIGN.md` / `docs/INLINE.md`; the package-private `Limbs` engine
  is the permanent internal oracle in the three-way differential testing.

## Build / test / bench

```powershell
.\gradlew.bat build
.\gradlew.bat test
.\gradlew.bat jmh --args="com.scalarwide.bench.Wide128Bench -wi 3 -i 5 -f 1"
.\gradlew.bat jmhJar
```

- Build requires **JDK 21+** (bytecode release 21). Gradle 9.5 wrapper.
- Tests: differential vs BigInteger - all extreme values in pairs, plus 5000
  seeded (12345) random pairs per operation, plus shift/constructor boundaries.

## Using as a dependency

- **JitPack (recommended, zero install)**:

  ```groovy
  repositories { maven { url 'https://jitpack.io' } }
  dependencies { implementation 'com.github.3648657480:ScalarWide:v0.11.1' }
  ```

- **Local install**: `.\gradlew.bat publishToMavenLocal` publishes
  `com.scalarwide:scalarwide:0.11.1` for any local Gradle/Maven project.
- **Manual**: download `ScalarWide-<version>.jar` (+ `-sources.jar`) from
  GitHub Releases and put it on the classpath.
- API entry points: `Wide128/256/384/512/768/1024` (immutable value types),
  `MutableWide128/256` (allocation-free scratch); semantics in the Javadoc and
  `docs/DESIGN.md`.
- Red lines: `MutableWide*` are not thread-safe and never valid as map keys;
  `-Dscalarwide.guard=true` enables best-effort cross-thread misuse detection
  (`IllegalStateException`; zero cost when off).

### Versioning & compatibility

- **The library evolves independently; consumers get performance upgrades with
  zero code changes**: version numbers are decoupled, a consumer only pins the
  dependency coordinate. Within the `0.11.x` line, releases are performance /
  bugfix / additive only (signatures and semantics frozen) - upgrading means
  changing one dependency version and rebuilding.
- Breaking changes only enter a new minor line with a migration note; the
  protected public API surface and the consumer regression contract are in
  `docs/COMPATIBILITY.md` (test: `ConsumerApiTest`).

## Quick use

```java
Wide256 k0 = Wide256.of(0x0123456789ABCDEFL, 0xFEDCBA9876543210L, 0, 0);
Wide256 k1 = Wide256.ofSigned(-1);
Wide256 mixed = k0.xor(k1);
BigInteger hi = k0.mulHigh(k1).toBigInteger();
```

## Widths and coordinate range

| Width | Role | Notes |
|---|---|---|
| 128 | atom - storage keys / Long128 replacement | real keys need ~59+59 bits; best cost/benefit |
| 256 | atom - general purpose (keys, hashing, mixing) | main atom |
| 384 | 3x128 - relay | composed: add/compare/mul (mul 10x, 2.55x over BigInteger); mulHigh pending |
| 512 | 2x256 - single-cache-line data domain | composed: add/compare/mul (mul 1.5x over BigInteger); sub/shifts pending |
| 768 | 3x256 - extra-large coordinates | engine-backed; composition pending |
| 1024 | 4x256 - double's exponent ceiling | engine-backed; composition pending |

**Not covered**: raw coordinates near 1e306 (~1020 bits). Raw coordinates stay
on the "big coordinate + local window" layered design; this library covers the
key / hash / short big-integer math inside it.

## Layout

```
src/main/java/com/scalarwide/     Limbs (reference engine), Wide128/256/384
src/test/java/com/scalarwide/     BigInteger differential tests
src/jmh/java/com/scalarwide/bench JMH benches (BigInteger side by side)
docs/DESIGN.md                    decisions / enchantment roadmap / acceptance
```

## Roadmap

1. **0.1.x baseline**: correctness loop + JMH baseline numbers (`docs/BENCH.md`).
2. **Enchantment 1 (done in 0.2.0)**: register/unrolled hot paths for 128/256
   (one variable per round, CPU-pinned JMH before/after).
3. **Enchantment 2**: zero-allocation API (output buffers / pooling).
4. **Enchantment 3**: inlining verification (`-XX:+PrintInlining`), `VarHandle`
   evaluation.
5. SIMD stays frozen as an experiment (scalar is the product, SIMD is the
   experiment against the scalar oracle).

## License

MIT (see `LICENSE.txt`).
