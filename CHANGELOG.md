# ScalarWide - Changelog

> Current version: see repo-root `VERSION` (the single authority).

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
