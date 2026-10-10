# 兼容性与版本策略

> 原则：**库独立演进，框架零改动吃性能**——版本号分离，性能升级直达消费者。

## 1. 版本号分离

- ScalarWide 的版本号（仓库根 `VERSION`）与任何消费者（改版 MC / V2 框架）的版本号
  **互不绑定**；消费者以依赖坐标钉住库版本，框架自身版本不受库升级影响。
- 性能升级以**补丁级**发布（如 `0.11.1 → 0.11.2`）：只含性能优化、bug 修复、纯增量 API。

## 2. `0.11` 线内的承诺（签名与语义冻结）

- 公开 API 的**签名、语义与行为约定不变**：二补码回绕、无符号 `compareTo`、
  移位 `[0,N)` 校验、`MutableWide*` 的单线程属主模型与零分配承诺。
- 消费者升级 = **只改一行依赖版本号 + 重构建**，零代码改动：

  ```groovy
  dependencies { implementation 'com.github.3648657480:ScalarWide:v0.11.2' }
  ```

- 回归保证：`ConsumerApiTest`（本仓 `src/test`）以消费者视角持续使用公开 API；
  它一旦被破坏即为**破坏性变更**，只能进入新次版本线（如 `0.12.0`）并附迁移说明。

## 3. 公开 API 面（受保护）

| 类型 | 内容 |
|---|---|
| `Wide128/256/384/512/768/1024` | 常量（`ZERO`/`ONE`/`MAX_VALUE`）、工厂（`of`/`ofSigned`/`fromBigInteger`）、算术（`add`/`sub`/`neg`/`mul`/`mulHigh`；128/256 另有 `mulFull`）、位运算、移位（`shl`/`ushr`/`shr`）、比较、`hashCode`/`equals`、`toBigInteger`/`toHexString` |
| `MutableWide128/256` | `setZero`/`set`/`add`/`sub`/`mul`/位运算/移位/`compareUnsigned`/`equalsTo`/`limb`/`longValue`/`toWide*` |
| 包私有（**非 API**） | `Carry`、`Limbs`、`Wide256$MulAcc`、`*Into*` 原子方法等 |

## 4. 版本段语义（0.x 期间）

| 变更性质 | 版本段 | 消费者动作 |
|---|---|---|
| 性能 / 修复 / 纯增量 | patch（`0.11.1 → 0.11.2`） | 直接替换 |
| 可能破坏性 | minor（`0.11 → 0.12`） | 读迁移说明 |
| `1.0` | major | 冻结保证起点 |

## 5. 性能升级直达路径

库发布新 patch（tag + GitHub Release，JitPack 自动生效）→ 框架改依赖版本号 →
重构建 → 发布新版模组。框架代码与功能不受影响，性能收益随依赖自动获得。
