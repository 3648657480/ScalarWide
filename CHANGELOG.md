# ScalarWide - Changelog

> Current version: see repo-root `VERSION` (the single authority).

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
