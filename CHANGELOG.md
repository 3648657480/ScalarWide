# ScalarWide - Changelog

> Current version: see repo-root `VERSION` (the single authority).

## 0.11.4

### Changed

- **Division rewritten as Knuth Algorithm D** (64-bit digits: normalization,
  128/64 quotient estimate with correction, multiply-subtract with rare
  add-back). The binary shift-subtract engine stays as the internal oracle
  (`Limbs.divmodBinary`). Drop-in: no API change.

### Measured (workstation, JDK 21)

- div/rem are now **ahead of BigInteger at every width**: 128: 15.8 vs 41.5 ns
  (2.63x), 256: 52.8 vs 82.7 (1.57x), 512: 94.2 vs 170.9 (1.81x), 1024: 216.9
  vs 421.0 (1.94x); the 0.11.3 v1 engine was 9-46x slower than the new one.
  Remaining known BigInteger regressions: only 1024.mulHigh 0.71x /
  768.mulHigh 0.92x (`docs/BENCH.md` P11).

## 0.11.3

### Added

- **Math-completeness ops on all six widths** (additive, drop-in):
  `div`/`rem` (unsigned, `ArithmeticException` on zero divisor), `pow`
  (mod 2^BITS square-and-multiply), `bitCount`, `min`/`max` (unsigned,
  consistent with `compareUnsigned`), `abs` (two's-complement; MIN_VALUE wraps
  to itself). Gated by `MathOpsTest` (BigInteger differential incl. edges) and
  pinned in the consumer contract (`ConsumerApiTest`).

### Measured (workstation, JDK 21)

- Division v1 (`Limbs.divmod`, binary shift-subtract - correctness first) is
  **behind BigInteger**: 128: 146.8 vs 41.7 ns (0.28x), 256: 1183 vs 81.5
  (0.07x), 512: 3286 vs 170.1 (0.05x), 1024: 9971 vs 418.9 (0.04x) ns. A
  Knuth-D fast path is the planned next measured round (`docs/BENCH.md` P10).

## 0.11.2

### Changed

- **`shr` (arithmetic right shift) direct-implemented for 384/512/768/1024**
  (was engine-backed): logical shift plus a conditional sign-fill mask
  (`MAX_VALUE.shl(N-k)` on the negative path); one allocation on the common
  non-negative path. 384: 6.74 -> 2.17 ns, 512: 13.31 -> 4.95 ns (was 0.92x
  behind `BigInteger.shiftRight`, now 2.5x ahead), 768: 11.66 -> 6.08 ns,
  1024: 16.23 -> 8.25 ns. Pure win ported back from the `mc` branch per
  `docs/BRANCHES.md`; drop-in (no API change, `ConsumerApiTest` green).
- **Repository split**: `main` = full library (all widths/tools); `mc` =
  MC-facing fork pruned to <=512 with zero known BigInteger regressions
  (first cut: `mc-v0.11.1`). Branch policy and sync rules: `docs/BRANCHES.md`.

## 0.11.1

### Changed

- **`mulHigh` tuning (768/1024)**: block-product arguments switched from
  `limb(i)` switch calls to direct field reads at the 16 call sites. 1024
  `mulHigh` 415.6 -> 311.9 ns (-25%), 1024 `mul` 178.3 -> 146.8 ns (-18%);
  768 unchanged (already at the atom floor). Root cause recorded in
  `docs/BENCH.md` P7: the 1509-byte `mulFullInto8` never inlines; per-call
  argument extraction dominated. Remaining gap to `BigInteger.multiply+shift`
  is architectural (16 non-inlinable atom calls vs a monolithic schoolbook).

### Added

- **Opt-in guardrails** (red-line detection): `-Dscalarwide.guard=true` makes
  `MutableWide128/256` detect cross-thread use and throw
  `IllegalStateException` (best-effort, thread-owner check; zero cost when the
  flag is off - the check is a constant-folded static final). Verified by
  `GuardTest` in child JVMs (guard on: shared use throws; guard off: silent,
  per-thread pattern stays correct).

## 0.11.0

### Changed

- **Compare-based carries**: every two-operand carry site (atom products,
  column assembly, class assembly, `Limbs`) now uses `Long.compareUnsigned`
  instead of the branched formula; three-operand (carry-in) sites keep the
  formula. Wide `mul` up to 2.3x faster (768: 160 -> 69 ns), 512 `mulHigh`
  118 -> 66 ns.
- **Direct implementations** replaced the generic engine for 768/1024
  add/sub/bitwise, 384/512 sub/bitwise, 384-1024 `shl`/`ushr`, 768/1024
  `compareUnsigned`/`compareSigned`, and 768/1024 `mulHigh` (zero-object
  scratch + block products). Selected results: 1024.add 28.8 -> 8.1 ns,
  768.add 22.6 -> 9.4 ns, 768.mulHigh 388 -> 148 ns, 1024.mulHigh 671 ->
  415 ns.

### Added

- Iron-man probes (in-repo, `src/jmh`): `ColdStartProbe` (cold vs warm under
  default/C1/-Xint), `PipelineProbe` (1M-key HashMap end to end), `ThreadProbe`
  (shared-mutable race, per-thread pattern, parallel scaling), `BatchProbe`
  (object vs flat-SoA vs BigInteger layouts), `RealKeyStats`/`RealKeyBench`
  (far-key workload distribution), plus `bigMulHigh`/`shr` comparators.
- `docs/BENCH.md` rounds K (compare carries), P0-P6 (direct-implementation
  sweep, gap audit) and the iron-man results, including negative results.

### Measured (workstation Ryzen 7950X, JDK 21)

- vs BigInteger: 51 of 54 benchmarked comparisons ahead; largest lead
  1024.and 11.3x; three still behind and tracked: 1024.mulHigh 0.53x,
  768.mulHigh 0.92x, 512.shr 0.92x.
- Zero-allocation promise verified with `-prof gc`: immutable ops allocate
  exactly one result object per op; `MutableWide*` allocate nothing even with
  `-XX:-DoEscapeAnalysis`.
- 1M-key HashMap lookup 1.30-1.35x ahead of BigInteger maps; GC pauses halved
  (5 pauses / 36 ms vs 9 / 69 ms) at 1M keys.

## 0.10.1

### Changed

- **Consumable as a library**: `maven-publish` added (coordinates
  `com.scalarwide:scalarwide`), `sourcesJar` added, and `jitpack.yml` added so
  the repo builds on JitPack (`com.github.3648657480:ScalarWide:v0.10.1`).
- **Portable build**: the machine-specific JDK path moved out of the committed
  `gradle.properties` (now in the user-level `~/.gradle/gradle.properties`);
  the build uses the running JVM with `--release 21` instead of a pinned
  toolchain - `git clone` + `gradlew build` now works on any machine with
  JDK 21+.
- README (EN/zh) gained a "Using as a dependency" section.

## 0.10.0

### Added

- `MutableWide128` / `MutableWide256`: reusable mutable accumulators whose
  operations (set/add/sub/mul/bitwise/shifts/compare) write into the instance
  and allocate nothing - the zero-allocation ("*Into") track of the API.
  Not thread-safe; never valid as map keys (documented red line). Verified by a
  4000-step differential state-machine fuzz through one reused instance.

### Measured

- Pinned JMH (with `-prof gc`): for in-method chains the immutable API is
  already allocation-free thanks to escape analysis (48 B/op = result only,
  same as the mutable chain), so the carrier API wins nothing there; its value
  is limited to scratch living across method boundaries or in fields
  (`docs/BENCH.md`, Round F).

## 0.9.0

### Changed

- `Wide768.mul` and `Wide1024.mul` are now composed from 256-bit atom block
  products (768: 3 full + 3 low-block; 1024: 6 full + 4 low-block). Pinned JMH
  vs 0.8.0 (engine-backed): 768: 502.2 -> 253.9 ns/op (1.98x); 1024:
  770.5 -> 540.2 ns/op (1.43x). Versus `BigInteger.multiply` the composed
  versions are 1.42x / 1.81x behind - the "wide widths bow to the intrinsic"
  expectation is now measured. No differential-test catches this round (all
  three carry traps were handled in the derivation phase).
- `Wide512.lowHalf` / `highHalf` became package-private (block composition
  support). `mulHigh` for 768/1024 is still engine-backed (last two methods).

## 0.8.0

### Changed

- `Wide384.mulHigh` is now composed from one 256-bit atom product and five
  128-bit cross products (with exact carry fix-ups). Pinned JMH vs 0.7.0:
  122.5 -> 74.9 ns/op (1.64x; ~parity with `BigInteger.multiply`, 68.4 ns/op).
  Three-way differential testing caught three carry bugs during development -
  the gate works.

### Reverted (measured, no gain)

- The `Wide512.mulBoth` experiment (shared-quarter low+high product API)
  measured 210.7 ns/op vs 208.3 ns/op for separate `mul` + `mulHigh`; the
  sharing does not materialize under the C2 inlining budget, so the API,
  benchmark and test were removed and the negative result recorded in
  `docs/BENCH.md`.

## 0.7.0

### Changed

- `Wide384` addition, comparison and multiplication are now register-specialized;
  `mul` composes one 256-bit atom product plus two 128-bit cross products
  (exact for the low 384 bits). Pinned JMH vs 0.6.2 (engine-backed):
  add 22.8 -> 6.5 ns/op (3.5x), mul 272.5 -> 26.8 ns/op (10.2x; 2.55x faster
  than `BigInteger.multiply`), compare 1.15 -> 0.81 ns/op; allocation 64 B/op
  (escape-analysis clean). `mulHigh` is still engine-backed (composition
  pending); 768/1024 composition is the next step.

## 0.6.2

### Fixed

- `Wide512.mulHigh` escape analysis: rewritten as a single open-coded method
  (four quarter products + one limb-level carry pass). Allocation rate
  832 -> 80 B/op (now identical to `mul`); latency 148 -> 137 ns/op (pinned JMH).
  Three-way differential testing caught two formulation bugs during the rewrite
  (wrong identity; a missing low-limb carry) - the gate works.

### Known issue (narrowed)

- `mulHigh` is still ~1.6x slower than `BigInteger.multiply` (86 ns/op):
  137 ns is about 4x the standalone cost of one 256-bit quarter product - the
  four independent chains are not overlapped (suspected JIT scheduling /
  register pressure). Needs assembly-level dissection.

## 0.6.1

### Changed

- `Wide512.add` now uses a single carry computation per limb (valid because the
  carry-in is provably in {0,1}); bytecode 319 -> 235B. Pinned JMH: add
  14.5 -> 10.2 ns/op (1.4x), mul 58.5 -> 50.4 ns/op (1.2x); control stable.
- `Wide512.mulHigh` rewritten branchless (neutral performance, simpler shape).

### Known issue

- `Wide512.mulHigh` still fails escape analysis (832 B/op vs 80 B/op for mul):
  its inline tree (~1.5kB) exceeds the C2 inlining budget; two attempted fixes
  (branchless body, smaller `add`) did not change the allocation rate. Parked
  with measurements in `docs/BENCH.md` and `docs/INLINE.md`.

## 0.6.0

### Changed

- `Wide512` addition, comparison and multiplication are now register-specialized;
  `mul` / `mulHigh` compose four 256-bit atom products (`Wide256.mulFull`).
  Pinned JMH vs 0.5.0 (engine-backed): add 22.6 -> 12.1 ns/op (1.9x), mul
  199.9 -> 63.4 ns/op (3.2x; now 1.5x faster than `BigInteger.multiply`),
  mulHigh 190.8 -> 174.8 ns/op (1.1x only - restructuring target), compare flat.
- `384` (3x128) composition is still pending.

## 0.5.0

### Added

- `Wide768` (12-limb) and `Wide1024` (16-limb): the width ladder is complete
  through 1024 bits (double's exponent ceiling). Flat limbs, engine-backed
  (correctness first), full differential suites (`Wide768Test`, `Wide1024Test`).

### Notes

- Composition from the atoms (384 = 3x128, 512/768/1024 = n x 256) is the next
  step (1.5b); these types currently route through the `Limbs` engine.

## 0.4.0

### Changed

- `Wide256` multiplication (`mul` / `mulHigh` / `mulFull`) is restructured into a
  small inlinable orchestrator plus per-column chunks (`col0..col7`), with the
  running carry passed through a scalar-replaceable `MulAcc`.
  Probe (PrintInlining): orchestrators 56/92B, chunks <= 243B - all hot-inlined;
  escape analysis removes `MulAcc` (allocation rate unchanged: 80.001 B/op).
- Performance: neutral within harness noise (pinned JMH; unchanged 128 code as
  drift control) - call overhead was not the bottleneck. This is the structural
  prerequisite for the 1.5 composition step.

### Docs

- `docs/INLINE.md`: inline probe records (0.3.0 monolith verdicts, 0.4.0
  re-verification).

## 0.3.0

### Added

- `Wide128.mulFull(Wide128) -> Wide256` and `Wide256.mulFull(Wide256) ->
  Wide512`: the full 2N-bit product in a single carry-chain pass.
- `Wide512`: flat 8-limb width (engine-backed, correctness first) - the
  `Wide256.mulFull` carrier and a member of the width ladder.
- `Carry`: single definition point of the atom-layer carry/borrow primitives
  (`addCarryOut` / `subBorrowOut`); `Limbs` delegates to it.
- `MulFullTest` (BigInteger differential + view-consistency + `Limbs`
  cross-oracle) and `Wide512Test` (full differential suite).

### Changed

- Public API now carries its semantics in Javadoc (unsigned `compareTo`,
  `mul` low / `mulHigh` high / `mulFull` full product, shift range, wrapping).
- `docs/DESIGN.md`: v2 conclusions landed (dual-atom structure, flat-limb red
  line, `mulFull`-only multiplication primitive, SoA dual-track API, inline
  probe before step 1.6, benchmark calibers).

### Performance

- `mulFull` vs `BigInteger.multiply` (CPU-pinned, full-product caliber):
  Wide128 3.1x faster (6.16 vs 19.25 ns/op); Wide256 at parity (34.94 vs
  35.59 ns/op). Data in `docs/BENCH.md`.

## 0.2.0

### Changed

- `Wide128` / `Wide256` arithmetic is now register-specialized and hand-unrolled:
  no intermediate arrays or loops in add / sub / neg / bitwise / shifts / compare,
  and carry-chain schoolbook multiplication (`Math.unsignedMultiplyHigh`) for
  mul / mulHigh. `Wide384` keeps the generic `Limbs` engine (correctness-first).
  The BigInteger differential suite is unchanged and green.

### Performance (CPU-pinned JMH, see docs/BENCH.md)

- Wide256: 2.3x-4.4x faster than 0.1.0 across add/sub/mul/and/shl/ushr; no
  regressions. Wide128: mul 5.7x faster; simple ops unchanged (the 0.1.0
  path was already at the allocation floor under this measurement).
- Remaining gap to the >=5x / >=8x vs BigInteger targets: the per-call result
  allocation - target of enchantment round 2 (zero-allocation API).
- Measurement note: benchmark runs are now CPU-affinity-pinned; unpinned
  hybrid-core scheduling had produced +/-60% noise.

## 0.1.0

- Initial scalar baseline: `Wide128`, `Wide256`, `Wide384` (two's-complement,
  little-endian limbs).
- Operations: add / sub / neg / mul (low) / mulHigh / and / or / xor / not /
  andNot / shl / ushr / shr / compareUnsigned / compareSigned / bitLength /
  fromBigInteger / toBigInteger / toHexString.
- BigInteger oracle differential tests: extreme values pairwise + 5000 seeded
  random pairs per width, shift boundaries, constructor boundaries.
- JMH harness (`src/jmh`, `gradlew jmh`, `gradlew jmhJar`) with BigInteger
  side-by-side baselines.
- The package-private `Limbs` engine is the shared reference implementation;
  register-specialized hot paths and zero-allocation APIs are future
  enchantment rounds (see `docs/DESIGN.md`).
