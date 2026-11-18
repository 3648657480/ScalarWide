# 发版手册

> 覆盖一次完整发布：版本号 → 构建 → 打标签 → GitHub Release → JitPack → Maven Central。
> 2026-11-18 首次发布到 Maven Central 时踩的坑都写在 §6，照着走不会重踩。

## 1. 版本号与变更日志

```powershell
# VERSION 是唯一权威
'2.0.4' | Set-Content VERSION -Encoding utf8

# CHANGELOG 顶部加一节（patch=性能/修复/纯增量，minor=语义收紧，major=破坏性）
```

**Central 的版本号一经发布永久不可改**。写错只能发下一个版本，不要试图复用。

## 2. 构建与门禁

```powershell
.\gradlew.bat test          # 必须全绿，含 BigInteger 差分门禁
.\gradlew.bat build         # 产物：jar / sources / javadoc
.\gradlew.bat :jmhJar       # 需要跑基准时
```

## 3. 提交与打标签

```powershell
git add -A
git commit -m "release: 2.0.4——<一句话>"
git tag -a v2.0.4 -m "2.0.4 — <一句话>"
```

## 4. 推送与 GitHub Release

```powershell
# 本机 22 端口被封，走 SSH over 443（无需改远端配置）
$env:GIT_SSH_COMMAND = 'ssh -o StrictHostKeyChecking=accept-new -o BatchMode=yes'
git -c url."ssh://git@ssh.github.com:443/".insteadOf="git@github.com:" push origin main v2.0.4

gh release create v2.0.4 build/libs/ScalarWide-2.0.4.jar build/libs/ScalarWide-2.0.4-sources.jar `
  --title '2.0.4 — <一句话>' --notes-file build\release-notes.md
```

`release-notes.md` 从 CHANGELOG 对应小节截取即可。

## 5. JitPack

JitPack **按需构建**：推完标签后不会自动构建，第一次有人请求依赖时才会触发。

```powershell
# 主动触发并等待
curl.exe -s -o NUL -w "%{http_code}" https://jitpack.io/com/github/3648657480/ScalarWide/v2.0.4/ScalarWide-v2.0.4.pom
# 几分钟后查状态
curl.exe -s https://jitpack.io/api/builds/com.github.3648657480/ScalarWide
```

状态出现 `v2.0.4: ok` 才算可用。**没触发过就等于没发布**——2.x 很长时间里就是这么"消失"的。

## 6. Maven Central

### 6.1 一次性准备（已完成，仅供重装参考）

- GPG 密钥（RSA 4096，密码记牢）：`gpg --full-generate-key`
- 公钥上传：`gpg --keyserver hkps://keyserver.ubuntu.com --send-keys <KEYID>`
- 私钥导出（**必须用 cmd 重定向**，见坑 2）：
  ```
  cmd /c '"C:\Program Files\GnuPG\bin\gpg.exe" --armor --export-secret-keys <KEYID> > build\signing-key.asc 2>nul'
  ```
- 命名空间认领：<https://central.sonatype.com> → `io.github.3648657480`（GitHub 自动验证）
- 生成 User Token（username + password）

### 6.2 发布

```powershell
cd C:\Project-ScalarWide

$sec = Read-Host -AsSecureString "GPG passphrase"
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
$env:SIGNING_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)

$env:CENTRAL_USER     = '<token user>'
$env:CENTRAL_PASSWORD = '<token password>'

.\tools\publish-central.ps1 -Group io.github.3648657480
```

脚本依次：发到 mavenLocal（无签名）→ **gpg CLI 签名** → 补 **md5/sha1** → 打 bundle → 用 **curl** 上传 → 轮询。

### 6.3 发布后

1. <https://central.sonatype.com/publishing/deployments> → 状态 `VALIDATED` → 点 **Publish**
   （没有发布 API，只能网页点）。
2. 等 5–30 分钟，验证：
   ```powershell
   curl.exe -s -o NUL -w "%{http_code}`n" https://repo1.maven.org/maven2/io/github/3648657480/scalarwide/2.0.4/scalarwide-2.0.4.pom
   ```

### 6.4 四个坑（都已在脚本里修掉）

| # | 现象 | 真因 | 修法 |
|---|---|---|---|
| 1 | `javadoc` 任务失败 | 历史注释缺 `@param`/`@return`，JDK 21+ doclint 报错 | `options.addStringOption('Xdoclint:none', '-quiet')` |
| 2 | `Could not read PGP secret key` | Windows PowerShell 5.1 的 `>` 把密钥写成 **UTF-16LE**（开头 `FF FE`） | 用 `cmd /c "... > file"` 导出原始字节 |
| 3 | 上传 `HTTP 403` + HTML 页面 | Portal 的 WAF 拦 .NET `HttpClient` | 改用 `curl.exe -F bundle=@...` |
| 4 | `Missing md5/sha1 checksum` | `publishToMavenLocal` 不生成校验和 | 脚本为每个文件补 `.md5` / `.sha1` |

另外：本机 `~/.gradle/init.d/aliyun-mirror.init.gradle` 会把 `repo1.maven.org` 换成阿里云镜像，
**镜像滞后期间用 Gradle 拉不到新版本**——这是本地环境问题，直连 repo1 是通的。

## 7. 发布后检查清单

- [ ] `VERSION` 与标签一致
- [ ] `gh release view v<X>` 有 jar + sources
- [ ] JitPack `v<X>: ok`，jar 可下载
- [ ] Central `repo1.maven.org/.../<X>/` 全部 200，含 `.asc/.md5/.sha1`
- [ ] 用一个干净工程 / 直接 `javac` 引一次，确认能编译运行
- [ ] `git status` 干净、`origin/main` 与本地一致
- [ ] 删除 `build/signing-key.asc`（私钥不入库、不留工作区）
