# AGENTS.md

> Version 0.3.0 (authority: repo-root VERSION)

Working rules for any agent or contributor on ScalarWide. Binding.

## Line boundary

Main 组与 MC 组（`C:\Project-ScalarWide-2560`）是**有意分叉**的两组 INT+FP，
不是同一份源码的两个视图：两边的 `Wide*` 已经是不同的类，**两个 jar 永不同 classpath**。
加密代码只进 `ModArith`，不得改动 `Wide*`/`Limbs` 热路径。详见 `docs/TWO-LINES.md`。

## Correctness discipline

- **The BigInteger differential test is the gate.** No operation ships without
  extreme-value pairs + seeded random differential coverage.
- Shift amounts are validated (`[0, N)`, else IllegalArgumentException);
  extreme values include all-ones, all-zero, 0x8000..., +-1, and shift
  boundaries (0/63/64/N-1 and their neighbourhoods).
- Fixed seed convention: 12345.

## Performance discipline

- **Benchmark before optimizing.** One variable per round; if the JMH delta is
  not real, revert.
- JMH is the only accepted performance evidence (warmup + measurement + fork);
  record commands and results in `docs/BENCH.md`.
- Enchantment order (from `docs/DESIGN.md`): register/unroll -> zero-allocation
  API -> inlining verification (`-XX:+PrintInlining`) -> `VarHandle` evaluation.
  SIMD stays frozen.

## Engineering

- Java toolchain 25, bytecode release 21 (Gradle 9.5 wrapper; Tencent mirror).
- Public API semantics live in Javadoc (unsigned compareTo, mul low/high/full,
  shift range, mod-2^N wrapping); internal code stays comment-free.
- Representation red line: flat little-endian limbs only - composition stays at
  the algorithm level, never object nesting in fields.
- Benchmarks: CPU-affinity-pinned JMH (`docs/BENCH.md`); every ratio must state
  its caliber (object API; cold/warm; low-half vs full product).
- Chinese docs/files: edit only via editor tools (UTF-8), never PowerShell
  Get-Content/Set-Content.
- Version bumps: `VERSION` + `CHANGELOG.md` only.
- Do not push to a remote unless the user's proxy is on (user controls it).
