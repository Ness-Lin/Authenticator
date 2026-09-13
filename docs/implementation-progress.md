# 实施进度与后续计划

更新日期：2026-09-11

## 已完成

- 已建立 `api / impl / ui` 模块骨架及 Gradle 依赖白名单检查。
- 已实现 RFC 6238 TOTP、SHA-1/SHA-256/SHA-512、6/8 位和周期边界测试。
- 已实现 Room + Android Keystore AES-GCM 加密记录存储、访问租约和加密/明文导出服务，并有格式单测。
- 已补安全会话、手动 enrollment 校验、`otpauth://totp` 解析、Base32 规范化和首页账户列表/验证码刷新。
- 已接入 CameraX + ML Kit 离线二维码扫描、相机权限处理和扫码账户确认保存流程。
- 已加入系统默认、简体中文和 English 三种语言选项，选择结果持久化并在切换后即时重建界面。
- 已关闭 Android 自动备份，并补充中英文首页/添加账户资源。

## 本轮验证

已使用 Android Studio JBR 执行 `gradlew.bat test :app:assembleDebug`，测试与 Debug 构建均通过。

## 后续实施顺序

1. 接入真实系统认证：`BiometricPrompt`/设备凭据、冷启动/后台锁定和安全窗口。
2. 将首页表单拆入 feature UI，补账户编辑、删除、重复凭据检测和剪贴板清理。
3. 接入离线二维码识别与相机生命周期，覆盖权限拒绝、重复帧和非法 URI。
4. 接入 SAF 导出页面、格式选择、风险确认、口令输入和导出授权生命周期。
5. 补齐英文/中文错误资源、TalkBack/窄屏/大字体 UI 测试和架构静态检查。
6. 在 API 24、29、31、33 及当前 target 上执行验收清单与 Release 性能基准。
