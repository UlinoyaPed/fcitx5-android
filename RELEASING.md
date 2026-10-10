# 个人版构建与签名

应用安装包名为 `org.ulinoyaped.fcitx5.android`，调试版加 `.debug`，插件使用同一前缀的 `.plugin.*`。源码 namespace 和 JNI 名称保持原样，避免大规模重命名。安装包名不同，个人版可与 boomker 版本共存；原版数据不会自动迁移，请先在原版导出用户数据，再在个人版导入。导入兼容本个人版、boomker 版和官方版的备份。

## 上传签名密钥

可以使用仓库 Secrets：[Settings → Secrets and variables → Actions](https://github.com/UlinoyaPed/fcitx5-android/settings/secrets/actions)。添加三个 Repository secrets：

| 名称 | 内容 |
| --- | --- |
| `SIGNING_KEY` | 整个 JKS/PKCS12 keystore 文件的 Base64 内容，单行、无换行 |
| `KEY_ALIAS` | keystore 中签名密钥的别名 |
| `KEY_PASSWORD` | 密码；当前构建要求 keystore 密码和该别名的私钥密码相同 |

`GRADLE_ENCRYPTION_KEY` 是可选的 Gradle 缓存加密 Secret，不影响签名。

如果已有自己的正式签名密钥，请继续使用它。新建密钥可在本机运行下列命令，按提示输入密码并保留 keystore 的安全备份；不要把密钥或密码提交到 Git：

```bash
keytool -genkeypair -keystore ulinoyaped-release.jks -alias ulinoyaped -keyalg RSA -keysize 4096 -validity 10000
```

安装 GitHub CLI 并登录后，可以直接从标准输入上传，无需把 Base64 内容显示在终端（Linux）：

```bash
base64 -w 0 ulinoyaped-release.jks | gh secret set SIGNING_KEY --repo UlinoyaPed/fcitx5-android
gh secret set KEY_ALIAS --repo UlinoyaPed/fcitx5-android --body ulinoyaped
gh secret set KEY_PASSWORD --repo UlinoyaPed/fcitx5-android
```

macOS 第一行换成 `base64 < ulinoyaped-release.jks | tr -d '\n' | gh secret set SIGNING_KEY --repo UlinoyaPed/fcitx5-android`。最后一行会交互式要求输入密码。网页方式也是把 Base64 内容填入 `SIGNING_KEY`，不能直接上传二进制文件。

nightly 和 release 必须一直使用同一密钥，应用及其 IPC 插件也使用同一密钥。丢失或更换密钥会导致 Android 无法覆盖更新；Secrets 是构建所需副本，不代替本机备份。工作流缺少任意一项签名配置时会提前失败，且发布前使用 `apksigner` 验证全部 APK；不会用调试密钥作为发布回退。

## 发布

- **Nightly**：推送 `main` 的代码变动或在 Actions 手动运行 Nightly，生成标记为预发布的 `latest`；默认接收 nightly 更新。
- **Release**：为经过验证的提交推送 `v` 开头的数字版本标签，例如 `v0.1.0-personal.1`，自动创建正式 GitHub Release；默认只接收正式更新。
- 两个工作流共用 `build-apks.yml`，目前均构建 arm64-v8a 应用及全部插件，与原有 nightly ABI 范围一致。
- “关于”页的“接收 nightly 更新”可随时切换渠道。应用和插件共用选择，更新来源仅为 `UlinoyaPed/fcitx5-android`。正式更新使用 `/releases/latest`，nightly 使用 `/releases/tags/latest`。
- release 标签用于发布名称；APK 的版本名保持日期加提交哈希，保留现有更新比较方式。版本号使用固定构建时间的 UTC 分钟乘 10 加 ABI 编号，保证后续构建可覆盖安装。

例如发布当前经过验证的 `main`：

```bash
git tag v0.1.0-personal.1 origin/main
git push origin v0.1.0-personal.1
```

本次代码修改不创建正式标签，也不替用户生成或上传生产密钥。首次公开发布须先设置以上三个 Secrets；现有生产签名是否配置需要仓库管理员在上述页面确认。
