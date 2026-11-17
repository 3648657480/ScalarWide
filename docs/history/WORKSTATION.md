# 工作站（DESKTOP-8ADRQTA）控制与作业规范

> 7950X / 47.2GB DDR5 / RTX 4060 Ti 16G / Windows（**基准口径 OS = Windows**；Debian
> 双系统已装、暂不启用，见文末「Debian 双系统」）；与笔记本同网段
> （`192.168.1.40`，**IP 可能变**——变了改笔记本 `~/.ssh/config` 的 `Host ws`）。

## 连接

- 笔记本 `~/.ssh/config` 已有：`Host ws`（user eason、私钥 `ws_ed25519`、BatchMode、
  StrictHostKeyChecking accept-new）；
- 一律 `ssh ws "..."`；**远端默认 shell = PowerShell；远端命令里避免双引号**
  （Windows sshd 会剥掉）——字符串用单引号；复杂脚本走 stdin：
  `Get-Content -Raw tools\ws\xx.ps1 | ssh ws powershell -NoProfile -Command -`

## 环境

- **CPUID 指纹（实测 2026-10-11）**：`AMD64 Family 25 Model 97 Stepping 2, AuthenticAMD`
  （AMD Ryzen 9 7950X，32 线程）——出处用 CPU 做锚，主机名仅同型号双机兜底。
- JDK：17（PATH 默认）+ **Oracle 21**（`C:\Program Files\Java\jdk-21`，Gradle JVM 已在
  用户级 `~/.gradle/gradle.properties` 固定为它）+ 另有 11 + **JDK 25（2026-10-11 装入：
  `C:\Java\jdk-25.0.3.9-hotspot`，25.0.3 LTS，供 26.2/3 线使用）**；
- 基准 JDK 约定：ScalarWide 基准默认用 `C:\Program Files\Java\jdk-21\bin\java.exe`（显式全路径），
  与历史数据同 JDK 口径；
- 仓库：`C:\Work\ScalarWide`——经 **git bundle** 传输（工作站无代理、直连 GitHub 不通）；
- **同步模式**：笔记本 = 发布点，工作站 = 算力点：
  ```powershell
  git bundle create x.bundle --all
  scp x.bundle ws:C:/Work/x.bundle
  ssh ws "cd C:\Work\ScalarWide; git fetch C:\Work\x.bundle main; git checkout FETCH_HEAD"
  ```
  反向取结果：`scp ws:C:/Work/... .`
- 初始化脚本：`tools/ws/init-ws.ps1`（幂等）。

## 出处纪律（2026-10-11 起）

- 所有测量输出首行带 `PROVENANCE host=… cpu=… threads=… jdk=… commit=…`（探针主类或脚本回显）；
  BENCH 账本按 (cpu, jdk) 成对记账，跨机数据不混。
- 脚本引用产物必须显式核对（历史事故：改产物忘改脚本指针 → 假数据）。
- SSH 不可达 → **报错退出**，禁止静默换本地跑。

## 作业模式

- **先探活再派活（强制）**：任何工作站任务前先探测（短超时、失败即回退）：
  ```powershell
  ssh -o ConnectTimeout=5 ws "echo up" 2>$null
  if ($LASTEXITCODE -ne 0) { # 离线：走回退矩阵 }
  ```
- 短命令直接 ssh；**长任务（基准矩阵/模糊/战役）= 脚本进 `tools/ws/` → 远程启动 → 日志落文件 → 轮询收取**；
- 测量口径：Ryzen 无混合核，方差天然小（冒烟 ±<3%）；为与历史（笔记本）可比仍建议
  `start /affinity 1` 锁单核；
- **JDK 版本必须随结论记录**（工作站默认 21、笔记本 25——跨 JDK 数据不可混用）。

## 离线回退矩阵（工作站随时可能关机：无 ECC + 噪声大，不做 24h）

| 任务 | 工作站在线 | 工作站离线（默认回退） |
|---|---|---|
| 构建 / 单测 / 小实验 | 可发工作站 | **笔记本照跑**（本来也是主场） |
| JMH 全矩阵 / 权威基线 | 工作站（JDK21 口径） | 笔记本降规模；权威跑等 WS；**口径随数据标注** |
| 亿级模糊浸泡 | 分段（见下） | 笔记本降到 10⁶–10⁷ 规模 |
| 远域战役 | 分片并行 | 笔记本单片跑 |
| WS 结果抽查复核 | 笔记本复核 | —（本地即全量） |

## 分段与续跑（噪声 → 不做通宵长跑）- 所有 >1h 的任务设计为**确定性分段**：输入 = (seed, 段号/区间)，进度与结果落文件；
- 关机不丢进度：重跑从最后完成的段继续；
- **无 ECC 复核协议（R8 延伸）**：长时间计算的结果必须可复核——
  1. 模糊/战役全部**确定性**（固定种子 + 区间参数）；
  2. 每段输出**摘要（digest）**落盘；
  3. 笔记本上**随机抽段复跑**核对摘要；不一致 = 红字调查（可能是我们的 bug，也可能是位翻转——两者都值得抓）；
  4. 基准计时数据不受单比特噪声影响（平均化），无需复核。

## 首测（2026-10-09，JDK21，未锁核，冒烟设置）

Wide256：add 4.34ns / mul 8.48ns / mulFull 26.11ns（笔记本同口径慢约 20-30%）。

## 并行排程规程（实测导出，2026-10-09）

- 并行 JMH 必修：`-Djmh.ignoreLock=true`（全局锁会拒绝第二实例）；
- **延迟基准**：并行 ≤2 干净（+0.3%）；≤4 仅粗筛（赢家回独占复测）；≥8 不用（boost+JIT 风暴）；
- 绑核掩码用**偶数 LP**（0,2,4…）——对两种 SMT 拓扑都安全；≤8 路时 1 作业/核；
- **吞吐/浸泡/战役类**：16 路随便满载；与延迟基准混跑时，用不同核段（如延迟占 0-7，重活占 8-31）；
- 实验轨道并行 = 1 条延迟基准 + N 条吞吐类（互不污染）。

## Debian 双系统（2026-10-11 装，暂不启用）

- 状态：工作站已装 Debian（与 Windows 双启动）；**默认仍用 Windows 做基准口径**，
  历史权威数据全部为 (Ryzen 9 7950X, Windows, JDK21)——保持连续。
- 纪律：**不得在两个 OS 间混跑基准**；每次换 OS 启动都要在结果里声明 os 口径。
- 若将来决定切换到 Debian（一次性做干净）：
  1. `bench` 的 `Provenance` 增加 `os=` 字段（现记 host/cpu/threads/jdk/commit）；
  2. BENCH 账本记账键改 (os, cpu, jdk) 三元组；旧 Windows 数据保留原口径；
  3. 锁核从 `start /affinity` 换 `taskset -c`；建议 governor=performance、关 C-state 漂移；
  4. JDK 用 temurin tarball 固定 `/opt/jdk-21`、`/opt/jdk-25`（别用 apt，防升级漂移）；
  5. `tools/ws/*.ps1` 协议：装 PowerShell 7 零迁移，或整体 bash 化；
  6. 传输：可直连 GitHub 则 clone，否则沿用 git bundle 协议；
  7. 跑一轮冒烟（Wide256 add/mul/mulFull）立 Debian 新基线后才可开始采数。
- 提醒（无论哪个 OS）：双系统重装/换 key 后，笔记本 known_hosts 旧条目要删
  （`ssh-keygen -R 192.168.1.40`），`~/.ssh/config` 的 IP 可能要改。
