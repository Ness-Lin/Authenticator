# 开发规范与模块架构

- 文档版本：0.1
- 状态：开发前规范草案，待评审
- 更新日期：2026-09-07
- 适用范围：Android 首版及后续 KMP 扩展
- 需求基线：[需求文档](需求文档.md)

本文定义目标架构，不表示当前工程已经完成模块拆分。当前仅有 `:app` 模板模块。本轮不修改构建脚本、应用代码或依赖版本。

## 1. 强制原则

1. 核心业务与 Android 应用代码使用 Kotlin，Gradle 使用 Kotlin DSL。
2. 功能模块必须采用 api-impl-ui 三层 Gradle 模块隔离，不能只用包名模拟模块边界。
3. common/basic 基础模块可以不拆三层；工具、Repo 与公共技术能力统一归属 arch，不在各功能模块复制。
4. 功能模块间通信必须依赖 api 契约，不直接引用其他模块的实现类、ViewModel 或 UI 类型。
5. 禁止 EventBus、全局 emitter、全局 SharedFlow 事件通道及类似发布订阅总线。
6. 所有面向用户的文本必须来自资源；禁止硬编码文案和自然语言拼接。
7. 所有项目文档统一置于 `docs/` 或子目录，不在模块目录散落 README、设计或 API 说明。源码 KDoc 作为接口注释允许保留。
8. 密钥不得明文落盘、进入日志或作为导航参数传递。不得自实现密码学原语。
9. 新增依赖、抽象或模块必须服务于已确认需求，不为假设中的平台创建空壳实现。

## 2. 目标目录

以下为实施阶段的目标，不要求本次预先创建空目录：

```text
app/                         Android 入口、导航宿主、依赖装配
common/
  basic/                     纯 Kotlin 基础类型，避免无边界工具集合
arch/
  api/                       Repo、时钟、密码学与存储等公共契约
  impl/                      Repo 实现、存储、Keystore、平台适配、内部工具
  ui/                        主题、公共组件、资源文案映射、平台 UI 能力
feature/
  token/
    api/                     验证码计算及认证配置解析契约
    impl/                    TOTP 编排、Base32/URI 适配
    ui/                      账户列表、详情及验证码展示
  enrollment/
    api/                     添加账户用例和页面目的地契约
    impl/                    参数验证、重复检测、保存编排
    ui/                      扫描、手动输入、确认页
  transfer/
    api/                     导出请求、快照与结果契约
    impl/                    导出序列化、口令加密、写入编排
    ui/                      账户选择、格式选择、口令与导出交互
  security/
    api/                     锁定状态、会话与敏感操作授权契约
    impl/                    锁定策略、会话失效与授权校验
    ui/                      锁定页面和系统认证交互
  settings/
    api/                     设置读取与更新契约
    impl/                    非敏感偏好设置编排
    ui/                      设置、隐私说明与关于页
  # settings 在确有独立设置页面时创建；首版不为单个按钮建空模块

docs/
  requirements.md
  development-guidelines.md
  # 后续按需增加 design/、api/、testing/、decisions/
```

`:arch:api`、`:arch:impl`、`:arch:ui` 是公共能力边界，不是业务功能集合。无 UI 的工具不强行添加 UI 壳；arch:ui 只承载真实共享 UI 能力。资源、工具和 Repo 的归属遵循下面规则，禁止用 arch 绕过功能隔离。

## 3. 分层职责与依赖

### 3.1 api 层

- 包含接口、不可变模型、请求、结果、业务错误类型及平台无关目的地标识。
- 不包含业务实现、Compose 页面、Android Context、资源 ID、数据库实体、第三方扫码类型或 DI 框架注解。
- 对外业务模型按领域归属；共享的账户存储模型置于 arch:api，避免 arch 反向依赖 feature。
- 公开模型不默认携带 secret；展示模型与含密钥内部配置分开，避免 data class 自动生成的 toString 泄露密钥。
- api 变更必须同步检查消费者和契约测试，不能以暴露实现类的方式绕过接口设计。

### 3.2 impl 层

- 实现本功能 api，通过构造参数注入其他 api 接口。
- 处理业务编排，不持有 Activity、Composable 或页面导航控制器。
- 数据访问统一经 arch:api 的 Repo 契约；Repo 实现与通用工具只能位于 arch:impl。
- 对外只暴露必要装配入口，具体实现默认 internal；必要的公开工厂返回 api 接口，不返回具体类。
- 密码学提供方和 Keystore 包装属于 arch:impl；token:impl 负责 TOTP 规则而不是重新实现 HMAC。

### 3.3 ui 层

- Compose 页面、ViewModel、不可变 UiState、输入处理与资源映射。
- 只依赖本功能与其他功能 api，不能依赖任何 impl。
- ViewModel 通过用例接口读取与修改数据，UI 不自行计算 TOTP、操作数据库或加解密。
- UI 的系统交互在本模块或 arch:ui 中完成，通过 api 定义的结果返回业务层。
- 扫描 SDK 相机视图及生命周期适配放 enrollment:ui，二维码文本交给 api 解析；不跨层传播 SDK 对象。

### 3.4 允许依赖表

| 使用方 | 允许依赖 | 禁止依赖 |
| --- | --- | --- |
| common:basic | Kotlin 标准库及必要的可移植基础库 | arch、feature、app、Android UI |
| arch:api | common:basic、可移植基础类型 | arch:impl/ui、feature、app |
| arch:impl | arch:api、common:basic、内部平台库 | arch:ui、feature、app |
| arch:ui | arch:api、common:basic、UI 库 | arch:impl、feature、app |
| feature:X:api | arch:api、common:basic | 所有 impl/ui、其他 feature api（默认禁止，避免契约耦合） |
| feature:X:impl | 自身 api、所需其他 feature api、arch:api、common:basic | 其他 impl、所有 ui、app |
| feature:X:ui | 自身及其他 feature api、arch:api/ui、common:basic | 所有 impl、其他 feature ui、app |
| app | 页面入口、api 和装配入口 | 在入口层以外编写业务逻辑 |

应用根是唯一 composition root：启动装配代码可依赖 impl 的工厂或 DI 绑定以建立对象图；导航宿主可挂载各 ui 页面入口。这不是允许功能模块依赖实现，也不允许 app 页面持有实现类。装配例外严格限制在启动 wiring 包，不得向 api 暴露 DI 容器。

示例调用链：`enrollment:ui → enrollment:api → enrollment:impl → token:api / arch:api`。实际实现由 app 注入，调用方永远只认识接口。Gradle 项目依赖必须保持有向无环；除导出的公共签名确需传递类型外，优先使用 `implementation` 而非 `api`。

## 4. 公共能力与接口约定

建议契约职责如下，具体签名在技术设计时确定：

| 契约 | 所属 | 职责 |
| --- | --- | --- |
| AccountRepository | arch:api | 账户摘要订阅、受控读取配置、事务保存/删除、导出快照 |
| Clock | arch:api | 提供 UTC 时间，测试中可替换 |
| CryptoProvider | arch:api | HMAC、AEAD、KDF、安全随机能力；不暴露 Android 类型 |
| SecureStorage | arch:api | 加密记录的存取与版本管理，不与 AccountRepository 重复承担业务职责 |
| TokenService | token:api | 根据配置和时间计算验证码及有效区间 |
| OtpConfigurationParser | token:api | 解析、验证和规范化 otpauth 配置 |
| EnrollmentService | enrollment:api | 校验、重复确认、添加编排 |
| ExportService | transfer:api | 生成一致快照、序列化并通过抽象输出目标写出 |
| SecuritySession | security:api | 观察锁定状态、撤销会话、校验敏感操作授权 |

接口须明确线程、取消、错误、所有权与敏感数据生命周期。基础数据流可使用 Flow；可变 StateFlow 保持私有。接口返回值使用密封结果/错误模型，不把异常 message 当作用户文案。

导出输出目标不能使用 Android Uri 作为公共签名：由平台层将 SAF 目标适配为受控写入接口，或采用生命周期受控的字节流抽象。不把整个大文件跨导航传递。授权结果需要绑定当前会话与操作，不能仅以 UI 传入的 `authenticated = true` 判断是否可导出。

Repo 可以是难以细分的公共技术能力，但不是免除接口的理由：契约在 arch:api，实现在 arch:impl，调用者禁止直接构造 Repo。纯函数工具保持 internal 并放 arch:impl；确需跨模块的能力用 arch:api 小接口开放。

## 5. KMP 预留策略

- 首期先保持普通 Kotlin/JVM 与 Android Library 的清晰边界，不为了预留能力强制现在配置多平台构建。
- api 及可共享业务逻辑禁止导入 android.*、androidx.compose.*、Context、Uri、Room 实体、R 或平台 Keystore 类型。
- 协程、序列化、时间与 IO 库选型须检查 KMP 支持及许可证；统一使用可共享模型，避免公共 API 暴露 java.time 等 JVM 专属类型。
- 算法编排、校验、导出 schema 是后续 commonMain 候选；Keystore、系统认证、相机、SAF 和数据库驱动留在平台适配层。
- 优先通过接口与构造注入隔离平台能力；只在确有平台实现需求时引入 expect/actual。
- Android strings.xml 留在 UI 层；业务返回错误枚举或密封类型，由各平台 UI 映射资源。
- JVM 上的 HMAC/AES/PBKDF2 通过成熟平台 API 实现，后续平台使用其成熟密码学实现并共享测试向量。
- 迁移时维持 api-impl-ui 逻辑边界，并为 shared 实现运行跨平台契约测试，不承诺 Android UI 可直接迁移。

## 6. Kotlin 与状态管理

- 遵循 Kotlin 官方风格；类和接口 PascalCase，函数和属性 camelCase，常量 UPPER_SNAKE_CASE，包名小写。
- 优先 val、不可变集合与密封状态；避免 Any、未经检查的强转、非必要 `!!`、全局单例可变状态。
- 使用结构化并发：ViewModel 使用 viewModelScope，平台长期对象有明确作用域及销毁机制，禁止 GlobalScope。
- I/O、KDF 与批量计算切换到注入的合适 dispatcher，Compose 中不执行昂贵操作。
- CancellationException 必须重新抛出；资源关闭使用受控作用域，不用 catch-all 将取消伪装成成功。
- 页面通过明确方法/回调提交操作，业务状态通过 StateFlow 暴露；订阅使用生命周期感知收集。
- 禁止跨模块全局事件流。一次性提示优先使用状态中的可确认消息或调用返回结果，由 UI 消费后回传确认，避免旋转后重复执行。
- 导航由 app 宿主统一协调，通过回调与目的地契约通信；参数仅携带账户 ID 等非敏感值。
- 敏感表单不进入 rememberSaveable、SavedStateHandle 或系统恢复 Bundle；进程死亡后重新输入，不能用持久化换取密钥泄露。

## 7. 验证码与存储实现规则

### 7.1 时间与列表

- 使用一个前台时间源服务列表，不为每张卡片创建常驻计时协程。
- 验证码必须从真实时间重新计算，不能依靠上次计数递减推进。
- 可见账户按有效区间刷新，后台停止计算；恢复时先校验会话，再读取数据和重新生成。
- 复制动作重新验证有效区间，必要时重新生成，避免复制过期 UI 缓存。
- 时钟变化与账户变化均触发正确刷新；注入时钟用于边界测试。

### 7.2 存储

- 默认方案：Room 持久化不敏感 ID/schema 和整体 AES-GCM 加密负载；实现藏在 arch:impl。
- 账户名称、发行方、密钥与生成参数不得以明文列建立索引；首版解锁后在内存搜索摘要。
- Android Keystore 生成本地 AES 密钥，不把密钥写入配置或数据库；加密负载含独立 nonce 和认证标签，AAD 绑定记录 ID/schema。
- 本地加密与导出加密使用不同密钥体系；导出格式不能使用不可迁移的本机 Keystore 密钥。
- schema 迁移提供显式路径及迁移测试，不使用破坏性降级或删除数据库处理加密错误。
- 重复检查与写入需在可保证一致性的受控事务/串行流程中处理，防止连续点击绕过校验。
- 导出读取一致快照，避免导出中删除或编辑导致文件内部不一致。
- 锁定或后台时清除可清理的明文缓存；内存清除为尽力措施，不声明可清除所有 JVM 副本。

### 7.3 安全 UI 与授权

- 系统认证、应用锁、生命周期变化和导出状态组成显式状态机；认证取消不保存、不导出。
- API 24–29 的系统凭据流程与较新 API 行为有差异，需使用经验证的兼容方案，不假定同一认证器组合在所有版本可用。
- SAF 返回时重新确认会话有效；不得将授权令牌通过 Bundle 持久化。进程被杀后取消导出，重新认证和输入口令。
- 对保存、加密及系统文件写入提供忙碌状态，禁止重复提交；对不可中断的 KDF 在返回后检查取消状态。
- 关闭备份并配置 Android 12 前后排除规则；截图防护及剪贴板敏感标记需有版本兼容测试。

## 8. 字符串与资源规范

“禁止硬编码字符串”按以下可执行边界落实，避免将机器协议常量错误地当成可翻译文案：

| 内容 | 存放方式 |
| --- | --- |
| 标题、按钮、提示、错误、安全说明、无障碍标签 | 对应 ui 模块 strings.xml |
| 数量文本、复数、带参数句子 | plurals 和带位置占位符的 string 资源 |
| 共享通用文案 | arch:ui 资源，确有复用才提取 |
| 协议键、schema 字段、算法标识、AAD、格式版本 | 所属协议实现的集中常量或类型定义，不参与本地化 |
| 日志事件码、测试向量、测试夹具 | 专门常量或测试资源，不使用真实账户信息 |
| 用户输入与发行方名称 | 视作数据，不写进资源或翻译 |

上述机器标识是非文案常量的限定例外，不能借此在 Kotlin 中写错误提示；进入实现前需确认此解释符合“全局禁止硬编码字符串”的期望。

具体规则：

- 资源名采用 `功能_页面_用途`，例如 `enrollment_manual_secret_label`。
- Compose 用 stringResource/pluralStringResource；传统平台层用资源接口；ViewModel 只暴露错误类型和格式化参数。
- 禁止自然语言字符串拼接；完整句子放资源，使用位置参数，便于不同语言调整语序。
- 所有生产 UI 与预览中的可见文本均使用资源；Preview 不含真实密钥。
- 默认 values 提供完整简体中文，values-en 提供完整英文；未知 locale 回退中文，后续可随产品市场调整。
- 单位、日期、时间、数字使用 locale 感知格式化；验证码本身固定为 ASCII 数字，不使用本地化数字替换。
- 资源不得存放共享密钥、导出口令、私有凭据或生产机密。
- 颜色、尺寸和间距使用主题 token；图标描述来自资源，纯装饰图标不重复添加无障碍描述。

## 9. 依赖与构建规范

- 版本集中在 Gradle Version Catalog，不在模块脚本散布版本号；禁止动态版本与未经批准的快照版本。
- 沿用当前 Compose 与 Material 3 方向；升级 Kotlin/AGP/Gradle/JDK 前核验官方兼容矩阵，并单独验证，不与业务变更混杂。
- 扫码选型必须满足离线、minSdk 24、Compose 生命周期适配及许可证要求；未评审前不指定某依赖已经可用。
- 依赖注入可先使用手动构造注入；采用框架时不得污染纯 api 模块，不能使用 Service Locator 让模块任意获取实现。
- API 和可共享逻辑使用最小插件集；平台适配模块使用 Android Library，app 为 Android Application。
- 发布构建启用合理的 R8/资源缩减并验证反射及序列化规则；不得以混淆替代加密。
- 签名文件和密码由本地安全配置或 CI 密钥注入，禁止入库；不修改用户本地 SDK 路径配置。

## 10. 测试与质量门禁

### 10.1 必测范围

| 层级 | 测试内容 |
| --- | --- |
| 纯逻辑单测 | RFC 6238 所有算法向量、RFC 4226 截断、前导零、周期边界和时间变化 |
| 解析单测 | Base32 填充/尾位、Unicode/百分号编码、重复参数、冲突 issuer、非法类型及超长输入 |
| 用例测试 | 重复确认、双击提交、锁定后调用、取消、事务失败、不泄露错误信息 |
| 格式测试 | 加密格式固定向量及独立解密验证、明文 URI 回读、元数据和密文篡改、错误口令 |
| 存储测试 | 迁移、重启、认证标签失败、Keystore 不可用、备份排除、无明文落盘 |
| UI 测试 | 扫码/手动添加/列表/导出完整流程，权限拒绝、锁定、进程重建、字体和 TalkBack |
| 性能测试 | 按需求文档设备及数据量执行，保存可复现结果 |
| 架构测试 | Gradle 依赖白名单、循环依赖、api 平台类型泄露、跨层 import |

加密测试使用专用测试随机源构造固定向量，生产环境不得注入确定性随机源。格式测试不能只让同一套实现自加密再自解密，以免对称错误漏检；使用独立实现或可信向量交叉核对。

### 10.2 静态检查与 CI

实施阶段建立以下门禁，当前模板尚未配置完成：

1. Kotlin 格式检查与静态分析（ktlint、detekt 或等效工具），Android Lint。
2. HardcodedText、缺失翻译、字符串拼接等检查；Compose 字面量和 ViewModel 文案补充自定义规则或评审，不能声称默认 Lint 覆盖所有情况。
3. api/impl/ui 项目依赖白名单与 api 中 Android 类型禁用检查。
4. 禁用 EventBus、全局 emitter、GlobalScope、敏感日志与真实密钥测试夹具。
5. 单元测试、debug 构建、release 构建与必要的模拟器仪器测试。
6. 依赖漏洞、许可证及秘密扫描；密码学和导出相关变更需要专项审查。

Windows 下使用 Gradle Wrapper 执行检查。模块拆分和插件配置完成后，可使用如下命令；当前不保证所有任务已存在，也未运行这些命令：

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:connectedDebugAndroidTest
```

## 11. 文档与变更管理

- 所有项目文档放 `docs/`，文件名采用英文小写与连字符；正文统一中文，协议字段与 API 名保持原样。
- 本需求文档和开发规范为入口，后续文档使用相对链接连接，不重复维护互相冲突的规则。
- 架构决策记录按需放 docs/decisions，接口说明放 docs/api，测试方案和结果放 docs/testing；不提前创建无内容文档。
- 文档标记版本、状态、日期；明确区分已实现、目标、假设和待决策，不将计划写成验证结果。
- 修改算法支持范围、存储 schema、导出格式或模块契约时，在同一变更中更新对应文档与测试。
- 示例只使用公开 RFC 测试向量或明显的测试数据，禁止真实账户二维码、密钥与截图进入文档或仓库。
- 评审先检查正确性、安全、架构边界和测试，再检查风格；不夹带无关重构。

## 12. 建议实施顺序

1. 评审需求边界、应用锁行为、明文导出策略和文件恢复延期风险。
2. 建立模块骨架、依赖规则、公共资源与 api 契约。
3. 以 RFC 向量驱动实现 TOTP、URI 解析、加密存储和会话锁定。
4. 实现手动添加与验证码列表，贯通最小可用流程。
5. 接入离线二维码扫描、重复确认与失败处理。
6. 实现导出与独立格式校验，验证系统文件选择器和认证生命周期。
7. 完成多系统测试、性能基准、无障碍与发布安全检查。

## 13. 完成定义

一个功能只有在需求验收项通过、架构依赖合规、资源文案完整、敏感数据路径验证完成、相关测试通过且文档同步后才可标记完成。未执行的测试必须明确记录，不得以代码生成完毕替代验收。
