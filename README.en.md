# ScalarWide

> English | [中文](README.md)

> A pure scalar fixed-width big-integer library (128 -> 1024 bits, six widths).
> Zero SIMD, zero platform detection.
> Correctness is gated by BigInteger differential testing; performance is
> measured with JMH.

Version: see repo-root `VERSION` (single authority). **Frozen at `1.0.0` (project closed,
maintenance mode)** - public-API freeze semantics in `docs/COMPATIBILITY.md` §6, deferred
work in `docs/FUTURE-WORK.md`; implementation matrix and all measurements (including
negative results) in `CHANGELOG.md`, `docs/DESIGN.md`, `docs/BENCH.md`.

## What it is

- `Wide128` / `Wide256` / `Wide384` / `Wide512` / `Wide768` / `Wide1024`:
  fixed-width two's-complement big ints (little-endian limbs);
  `MutableWide128/256` are allocation-free scratch carriers.
- Ops: `add` `sub` `neg` `mul` (low N bits) `mulHigh` (high N bits) `div` `rem`
  `pow` `and` `or` `xor` `not` `andNot` `shl` `ushr` `shr` `compareUnsigned`
  `compareSigned` `bitLength` `bitCount` `min` `max` `abs`, plus `fromBigInteger`
  / `toBigInteger` / `toHexString` / `toByteArray` (BE/LE/fixed) / `fromByteArray`.
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
  dependencies { implementation 'com.github.3648657480:ScalarWide:v1.0.0' }
  ```

- **Local install**: `.\gradlew.bat publishToMavenLocal` publishes
  `com.scalarwide:scalarwide:1.0.0` for any local Gradle/Maven project.
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
  dependency coordinate. Within the `1.0.x` patch line, releases are performance /
  bugfix / additive only (signatures and semantics frozen - see
  `docs/COMPATIBILITY.md` §6) - upgrading means changing one dependency version
  and rebuilding.
- Breaking changes only enter a new minor line with a migration note; the
  protected public API surface and the consumer regression contract are in
  `docs/COMPATIBILITY.md` (test: `ConsumerApiTest`).
- Two branches: `main` = full library (all widths/tools); `mc` = MC-facing fork
  pruned to <=512 with zero known regressions (tags `mc-v*`). Policy:
  `docs/BRANCHES.md`.

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
| 384 | 3x128 - relay | composed + direct; all ops implemented |
| 512 | 2x256 - single-cache-line data domain | composed + direct; all ops implemented |
| 768 | 3x256 - extra-large coordinates | direct; `mulHigh` is a recorded architectural shortfall (see `docs/BENCH.md`) |
| 1024 | 4x256 - double's exponent ceiling | direct; `mulHigh` is a recorded architectural shortfall (see `docs/BENCH.md`) |

Benchmark numbers (BigInteger side by side) and all negative results: `docs/BENCH.md`.

**Not covered**: raw coordinates near 1e306 (~1020 bits). Raw coordinates stay
on the "big coordinate + local window" layered design; this library covers the
key / hash / short big-integer math inside it.

## Layout

```
src/main/java/com/scalarwide/     Limbs (reference engine), Wide128/256/384/512/768/1024, MutableWide128/256
src/test/java/com/scalarwide/     BigInteger differential tests (incl. consumer contract gate)
src/jmh/java/com/scalarwide/bench JMH benches & diagnostics (BigInteger side by side)
docs/                             DESIGN / BENCH / BRANCHES / COMPATIBILITY / FUTURE-WORK
```

## Roadmap

- **Done**: 0.1.x baseline -> enchantment rounds (register/unroll) -> direct engine +
  Knuth-D division + quotient-only division + byte serialization; (the `mc` line adds
  the hard-wiring layer and the Java 17+ multi-release overlay).
- **Frozen (`1.0.0`)**: maintenance mode - deferred work (batch/SoA, large-table
  sharding, `rem` q-skip, 768/1024 `mulHigh`) in `docs/FUTURE-WORK.md` (with trigger
  conditions).
- **Long term**: SIMD stays frozen as an experiment (scalar is the product, SIMD is
  the experiment against the scalar oracle).

## License

MIT (see `LICENSE.txt`).
