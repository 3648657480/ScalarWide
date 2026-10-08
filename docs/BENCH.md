# ScalarWide 基准记录（BENCH）

> 规则：一次一变量、同机同命令同版本；换设置必须整批重跑。只记录现象，不夸大。

## 2026-10-08 · 附魔轮 1（0.1.0 → 0.2.0，锁核权威）

**测量方法（本轮升级）**：混合核 CPU（Ultra 7 155H）上，JMH 逐基准 fork 的调度漂移
使 BigInteger 对照组跨 fork 抖动达 1.4–1.7×（误差 ±60%，见下节）。故本轮改用
**CPU 亲和锁定**：`cmd /c start /b /wait /affinity 1` 启动 JMH runner jar（fork 子进程继承亲和），
固定逻辑核 0；`-wi 5 -i 10 -f 1 -w 500ms -r 500ms`。误差棒降至 ±2~10%。

- 命令（前/后同一命令，仅换 jar）：

```
cmd /c start "sw" /b /wait /affinity 1 "<jdk25>\bin\java.exe" -jar "<before|after>.jar" "(Wide128Bench|Wide256Bench)\.(add|sub|mul|and|shl|ushr|bigAdd|bigSub|bigMul|bigAnd|bigShl|bigUshr)$" -wi 5 -i 10 -f 1 -w 500ms -r 500ms -rf csv -rff docs\BENCH-pinned-<before|after>.csv
```

- jar 来源：`gradlew jmhJar`。before = 0.1.0 行为等价构建（被测运算与 0.1.0 相同代码路径：
  `Limbs` 引擎 + pack/unpack 数组）；after = 0.2.0 寄存器化/展开版。同机同会话，先后测量。
- 数据：`BENCH-pinned-before.csv`、`BENCH-pinned-after.csv`。

### 结果（avgt，ns/op）

| 运算 | 128 前 | 128 后 | 128 后/前 | 256 前 | 256 后 | 256 后/前 | big128 | big256 | 后/big128 | 后/big256 |
|---|---|---|---|---|---|---|---|---|---|---|
| add | 2.627 | 2.730 | 1.0x | 13.365 | 5.833 | 2.29x | 10.232 | 14.711 | 3.7x | 2.5x |
| sub | 2.427 | 2.538 | 1.0x | 16.529 | 6.094 | 2.71x | 11.074 | 15.669 | 4.4x | 2.6x |
| mul | 15.318 | 2.671 | 5.7x | 49.646 | 11.367 | 4.37x | 15.794 | 31.187 | 5.9x | 2.7x |
| and | 2.895 | 2.594 | 1.1x | 16.093 | 3.745 | 4.30x | 9.955 | 10.514 | 3.8x | 2.8x |
| shl | 2.515 | 2.632 | 1.0x | 12.971 | 4.052 | 3.20x | 9.228 | 10.088 | 3.5x | 2.5x |
| ushr | 2.531 | 2.767 | 0.9x | 12.650 | 4.288 | 2.95x | 8.329 | 10.681 | 3.0x | 2.5x |

### 结论（仅限上述条件）

- **256：全运算 2.3–4.4× 提升，无回退**（mul 49.6 → 11.4 ns）。
- **128：mul 5.7×**（15.3 → 2.7 ns）；简单运算持平——本条件下，0.1.0 的 2-limb 引擎路径
  已被 JIT 逃逸分析消去中间数组，前值（~2.6 ns）已接近"单次对象分配"下限。
- **对 BigInteger 的比值**：128 为 3.0–5.9×，256 为 2.5–2.8×。
  **验收线（256 加减 ≥5×、位运算 ≥8×）尚未达标**；剩余瓶颈 = 结果对象分配（每调用 1 次），
  归附魔轮 2（零分配 API）。hash/compare 0.1.0 起就是直算，未纳入本轮对照。

## 2026-10-08 · 权威整批（未锁核，噪声过大，仅存证）

- 两次整批 `-wi 3 -i 5 -f 1 -w 1s -r 1s`（Gradle `jmh` 任务）：
  `BENCH-2026-10-08-0.1.0-authoritative.csv` / `BENCH-2026-10-08-0.2.0-authoritative.csv`。
- 问题：BigInteger 对照组跨 fork 漂移 1.4–1.7×（bigAdd 16.8 → 28.9 ns），误差 ±60%
  （0.1.0 的 256 mul 102.5 ± 66.1）→ 不可用于精细比值。该组数据推动了锁核方法升级，保留作历史。

## 2026-10-08 · 0.1.0 基线冒烟（harness 验证）

- `-wi 1 -i 1 -f 1 -w/-r 200ms`（Samples=1，无误差棒，非权威）：
  `BENCH-2026-10-08-smoke.csv`（本库）、`BENCH-2026-10-08-smoke-big.csv`（BigInteger）。
- 用途：验证 JMH 链路 + 给附魔轮前值提供粗参照；结论未采纳。
