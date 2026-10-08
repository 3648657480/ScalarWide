# AGENTS.md

> Version 0.1.0 (authority: repo-root VERSION)

Working rules for any agent or contributor on ScalarWide. Binding.

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
- Code is comment-free; semantics live in README / docs/DESIGN.md.
- Chinese docs/files: edit only via editor tools (UTF-8), never PowerShell
  Get-Content/Set-Content.
- Version bumps: `VERSION` + `CHANGELOG.md` only.
- Do not push to a remote unless the user's proxy is on (user controls it).
