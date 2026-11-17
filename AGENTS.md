# AGENTS.md

> Version 0.3.0 (authority: repo-root VERSION)

Working rules for any agent or contributor on ScalarWide. Binding.

## Line boundary

仓库是**一个模块、四个包**：`com.scalarwide.main.integer`（核心 + 加密）、`main.fp`（通用浮点）、
`mc.integer`（MC 组整数副本 + 宽键）、`mc.fp`（MC 组容器 256/640/1280）。
两组的同名类**不是同一个类**，各自独立优化；加密只进 `main.integer.ModArith`，
不得为它改 `Wide*`/`Limbs` 热路径。正确性缺陷必须双组同修。详见 `docs/design/TWO-LINES.md`。

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
  record commands and results in `docs/bench.md`.
- Enchantment order (from `docs/design/DESIGN.md`): register/unroll -> zero-allocation
  API -> inlining verification (`-XX:+PrintInlining`) -> `VarHandle` evaluation.
  SIMD stays frozen.

## Engineering

- Java toolchain 25, bytecode release 21 (Gradle 9.5 wrapper; Tencent mirror).
- Public API semantics live in Javadoc (unsigned compareTo, mul low/high/full,
  shift range, mod-2^N wrapping); internal code stays comment-free.
- Representation red line: flat little-endian limbs only - composition stays at
  the algorithm level, never object nesting in fields.
- Benchmarks: CPU-affinity-pinned JMH (`docs/bench.md`); every ratio must state
  its caliber (object API; cold/warm; low-half vs full product).
- Restoring file content from git: never pipe `git show` into PowerShell text and write it back —
  the pipeline decodes UTF-8 bytes with the console code page. Use `cmd /c "git cat-file blob ..."`
  for raw bytes, or the editor tools.
- Chinese docs/files: edit only via editor tools (UTF-8), never PowerShell
  Get-Content/Set-Content.
- Version bumps: `VERSION` + `CHANGELOG.md` only.
- Do not push to a remote unless the user's proxy is on (user controls it).
