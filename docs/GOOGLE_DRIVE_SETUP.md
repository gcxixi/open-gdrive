# Google Drive 与 OAuth 详细配置指南

本文用于配置 Open GDrive 的 Google Drive 授权，适用于仓库当前的 Android 16 版本。

完成后，应用可以请求用户授权、浏览 Google Drive 中的全部文件和文件夹、预览常用文件，并将 Markdown 修改写回原文件。

## 1. 配置前先确认这些值

| 项目 | 当前值 |
| --- | --- |
| Android application ID / package name | `dev.opengdrive` |
| 请求的 OAuth scope | `https://www.googleapis.com/auth/drive` |
| Release 证书 SHA-1 | `C9:9F:66:48:3A:5F:F3:CE:5E:F6:89:A8:FF:71:E0:57:16:F5:CA:32` |
| 最低及目标系统 | Android 16 / API 36 |
| OAuth 实现 | Google Identity Services `AuthorizationClient` |

Google 用“包名 + 签名证书 SHA-1”识别 Android 应用。只填写包名不够；同一份代码由不同证书签名后，在 OAuth 看来就是不同的客户端。

本项目不使用 Firebase，也不需要：

- `google-services.json`
- 在源代码里填写 OAuth client ID
- Android OAuth client secret
- API key

Android 原生应用是 public client，不能安全保存 client secret。Google Play services 会根据已安装 APK 的包名和签名证书匹配 Google Cloud 中的 Android OAuth client。

## 2. 创建或选择 Google Cloud 项目

1. 打开 [Google Cloud Console](https://console.cloud.google.com/)。
2. 点击顶部项目选择器。
3. 选择已有项目，或者点击 **New project / 新建项目**。
4. 项目名称可以填写 `Open GDrive`。
5. 创建完成后，确认控制台顶部显示的是刚才选择的项目。

建议为这个应用单独使用一个 Cloud project，避免 OAuth scope、测试用户和其他应用混在一起。

后续每次打开控制台页面，都先确认页面顶部的项目 ID 正确。

## 3. 启用 Google Drive API

1. 打开 [Google Drive API Library](https://console.cloud.google.com/apis/library/drive.googleapis.com)。
2. 确认当前项目正确。
3. 点击 **Enable / 启用**。
4. 等待页面跳转到 API 详情页。
5. 在 **APIs & Services > Enabled APIs & services** 中确认 `Google Drive API` 状态为 Enabled。

本应用直接调用 Drive REST v3。没有启用 API 时，OAuth 可能成功，但列文件请求会返回 `403 accessNotConfigured` 或类似错误。

## 4. 配置 Google Auth Platform 品牌信息

打开 [Google Auth Platform > Branding](https://console.cloud.google.com/auth/branding)。首次进入时可能先显示 **Get started**。

建议填写：

| 字段 | 建议值 |
| --- | --- |
| App name | `Open GDrive` |
| User support email | 你能接收邮件的地址 |
| App logo | 测试阶段可暂不上传；生产验证前应使用正式图标 |
| Developer contact information | 项目维护者邮箱 |

如果只是自己使用或少数账号测试，可以先完成必填项。准备公开发布时，还应补齐：

- Application home page
- Privacy policy URL
- Terms of service URL（可选但推荐）
- Authorized domains

公开验证使用的 URL 必须真实可访问。Authorized domains 中的域名通常需要由项目 Owner 或 Editor 在 Google Search Console 完成所有权验证。

## 5. 配置 Audience 和测试用户

打开 [Google Auth Platform > Audience](https://console.cloud.google.com/auth/audience)。

### 个人账号或任意 Google 账号

选择 **External**。

开发阶段保持 **Testing**，然后在 **Test users** 中加入实际安装应用并登录的 Google 账号。未列入测试用户的账号可能看到 `Access blocked`，无法完成授权。

Testing 状态适合：

- 自己使用
- 家人或团队内部小范围测试
- 提交公开 OAuth verification 前的开发阶段

Google 当前对 Testing 项目设置最多 100 个 test users。个人用途或少于 100 个用户的有限使用通常可以保持未验证状态，但用户可能仍会看到“应用未经验证”的提醒。

### Google Workspace 组织内部使用

如果 Cloud project 属于你的 Google Workspace organization，并且应用只给该组织成员使用，可以选择 **Internal**。个人 Gmail 项目通常没有这个选项。

组织管理员可能限制高风险 Drive scope。即使用户已经列为 tester，也可能遇到 `admin_policy_enforced`，此时需要 Workspace 管理员允许该 OAuth 应用或 scope。

## 6. 声明 Drive OAuth scope

打开 [Google Auth Platform > Data Access](https://console.cloud.google.com/auth/scopes)。

1. 点击 **Add or remove scopes**。
2. 搜索 Google Drive API。
3. 添加下面这个精确 scope：

   ```text
   https://www.googleapis.com/auth/drive
   ```

4. 保存更改。

代码也在 `DriveAuthorization.kt` 中请求同一个 scope。Cloud Console 声明的是应用允许申请的最高权限，代码决定本次授权实际申请什么权限，两边应保持一致。

### 为什么不用 `drive.file`

`drive.file` 只能访问由本应用创建，或用户通过文件选择器明确交给本应用的文件。Open GDrive 的核心需求是自动发现 Drive 中已经存在的 Markdown 文件，因此当前 MVP 使用完整 Drive scope。

完整 `drive` scope 可以查看和管理用户的所有 Drive 文件，属于 restricted scope。应用会列出用户可访问的文件；只有在用户选中文件时才下载预览内容，且当前只允许修改 Markdown。Google 的授权页仍会按照完整 scope 展示权限范围。

如果以后改成“用户逐个选择文件”的产品交互，可以迁移到 Google Picker + `drive.file`，从而显著简化公开验证。

## 7. 创建 Release Android OAuth client

打开 [Google Auth Platform > Clients](https://console.cloud.google.com/auth/clients)。

1. 点击 **Create client**。
2. Application type 选择 **Android**。
3. Name 填写 `Open GDrive Release`。
4. Package name 填写：

   ```text
   dev.opengdrive
   ```

5. SHA-1 certificate fingerprint 填写：

   ```text
   C9:9F:66:48:3A:5F:F3:CE:5E:F6:89:A8:FF:71:E0:57:16:F5:CA:32
   ```

6. 点击 **Create**。

这个 SHA-1 对应 GitHub Release workflow 使用的签名密钥。通过项目 Release 页面下载的 APK 应使用这条 OAuth client。

Android client 创建后显示的 client ID 不需要复制到本项目，Android client 也不应有需要写入 APK 的 client secret。

配置传播可能需要几分钟到数小时。刚创建后仍出现授权错误时，先等待一段时间再重试，不要连续创建重复 client。

## 8. 为本地 Debug APK 创建另一个 OAuth client

Android Studio/Gradle 的 debug APK 通常由 `~/.android/debug.keystore` 签名，它和 Release 证书不同。因此，本地调试需要在同一个 Cloud project 中再创建一个 Android client。

先在项目根目录运行：

```sh
./gradlew signingReport
```

在输出中找到 `Variant: debug` 下的 `SHA1`。也可以直接运行：

```sh
keytool -list -v \
  -keystore "$HOME/.android/debug.keystore" \
  -alias androiddebugkey \
  -storepass android \
  -keypass android
```

然后在 **Google Auth Platform > Clients** 创建第二个 Android client：

| 字段 | 值 |
| --- | --- |
| Name | `Open GDrive Debug` |
| Package name | `dev.opengdrive` |
| SHA-1 | `signingReport` 输出的 debug SHA-1 |

不要把 debug SHA-1 覆盖到 Release client 中。正确做法是保留两个 Android client，它们包名相同、证书 SHA-1 不同。

如果换电脑后生成了新的 debug keystore，还需要为新 SHA-1 再创建 client，或者安全地复制原来的 debug keystore。

## 9. 安装并验证 Release APK

1. 从 [GitHub Releases](https://github.com/gcxixi/open-gdrive/releases) 下载最新 APK。
2. 在 Android 16 设备上允许从当前来源安装应用。
3. 安装 APK。使用 ADB 时可运行：

   ```sh
   adb install -r open-gdrive-v0.3.1.apk
   ```

4. 确认用于测试的 Google 账号已经加入 Audience 的 Test users。
5. 在 Google Drive 中准备一个普通文本文件，例如 `welcome.md`。Google Docs 原生文档不是 Markdown blob 文件。
6. 打开 Open GDrive，点击 **Connect Google Drive**。
7. 选择测试账号，并批准 Drive 权限。
8. 文件列表应显示账号可访问的全部文件。选择 `welcome.md`，点击 **Edit**，修改内容并等待顶部状态回到 **Saved**。
9. 回到 Google Drive 下载或打开原文件，确认内容已经更新。

应用只把短期 access token 保存在内存中。系统结束进程、token 过期或 Drive 返回 401 后，可能需要再次点击连接；已有授权通常不需要重复显示完整 consent screen。

## 10. 核对 APK 的真实签名证书

如果不确定 APK 是否由预期证书签名，使用 Android SDK Build Tools 36：

```sh
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify \
  --verbose \
  --print-certs \
  open-gdrive-v0.3.1.apk
```

输出中的 signer certificate SHA-1 应为：

```text
C9:9F:66:48:3A:5F:F3:CE:5E:F6:89:A8:FF:71:E0:57:16:F5:CA:32
```

如果不同，必须使用 APK 实际证书的 SHA-1 创建 Android OAuth client。

## 11. GitHub Actions Release 签名配置

Release workflow 使用以下 repository secrets：

| Secret | 内容 |
| --- | --- |
| `SIGNING_KEY_BASE64` | Release JKS 文件的 Base64 内容 |
| `KEYSTORE_PASSWORD` | JKS 密码 |
| `KEY_ALIAS` | 当前为 `open-gdrive` |
| `KEY_PASSWORD` | 私钥密码 |

当前机器上的 JKS 备份位于：

```text
~/Library/Application Support/open-gdrive/signing/open-gdrive-release.jks
```

密码保存在 macOS Keychain，service 名称为 `open-gdrive-release-keystore`，account 为 `gcxixi`。查看密码会把敏感信息打印到终端：

```sh
security find-generic-password \
  -a gcxixi \
  -s open-gdrive-release-keystore \
  -w
```

需要重新写入 GitHub Secrets 时，在仓库目录执行：

```sh
KEYSTORE="$HOME/Library/Application Support/open-gdrive/signing/open-gdrive-release.jks"

base64 < "$KEYSTORE" | gh secret set SIGNING_KEY_BASE64
security find-generic-password \
  -a gcxixi \
  -s open-gdrive-release-keystore \
  -w | gh secret set KEYSTORE_PASSWORD
security find-generic-password \
  -a gcxixi \
  -s open-gdrive-release-keystore \
  -w | gh secret set KEY_PASSWORD
printf '%s' 'open-gdrive' | gh secret set KEY_ALIAS
```

首个 Release 发布后不要更换或删除 Release 私钥。Android 只允许由同一证书签名的 APK 覆盖升级现有安装；丢失私钥将导致后续 APK 无法作为原应用的更新安装。

## 12. 触发新的 GitHub Release

确保 `version_code` 比所有历史版本大，然后运行：

```sh
gh workflow run release.yml \
  -f version=0.2.0 \
  -f version_code=2
```

查看运行状态：

```sh
gh run list --workflow release.yml --limit 3
gh run watch
```

workflow 会执行单元测试、构建并签名 minified release APK、创建 Git tag、创建 GitHub Release，并上传 APK。

## 13. 公开发布前的 OAuth verification

仅自己或有限测试用户使用时，可以保持 Testing。要让任意 Google 账号正常授权，并去掉 unverified app 提示，应在 Google Auth Platform 中切换到生产状态并提交验证。

准备材料通常包括：

1. 完整的 Branding 信息。
2. 可公开访问的主页和隐私政策。
3. 已验证所有 Authorized domains 的所有权。
4. 对 restricted Drive scope 的详细用途说明。
5. 展示完整 OAuth 授权流程和 Drive 功能的演示视频。
6. 说明数据如何存储、处理、共享和删除。

可使用下面的 scope justification 作为草稿，再按实际产品情况调整：

> Open GDrive is an Android file browser and Markdown productivity editor. It lists files and folders the user can access in Google Drive, downloads only the file selected for preview, renders supported content locally, and allows Markdown edits to be uploaded back to the same Drive file. The narrower drive.file scope cannot discover or access files that already exist in Drive unless every file is separately opened through a picker. Open GDrive does not transmit Drive file content or OAuth tokens to a developer-operated server.

完整 `drive` scope 属于 restricted scope。Google 官方说明：如果应用把 restricted-scope 数据存储到服务器或传输到服务器，通常还需要安全评估。当前 Open GDrive 直接在 Android 设备和 Google Drive 之间传输内容，没有开发者后端；最终需要哪些验证步骤仍以 Google Auth Platform 提交页面和审核团队的判断为准。

## 14. 常见错误排查

### `ApiException: 10`、`DEVELOPER_ERROR` 或授权立即失败

通常是 Android OAuth client 不匹配。逐项检查：

1. 安装包的 package name 是否确实为 `dev.opengdrive`。
2. Cloud Console 中是否选择了正确项目。
3. client 类型是否为 Android，而不是 Web application。
4. 填写的 SHA-1 是否来自当前安装 APK 的真实签名证书。
5. 同一个 Cloud project 中是否同时创建了 Debug 和 Release client。
6. 新配置是否已经等待足够时间传播。

### `Access blocked` 或账号无法选择

- Audience 为 External + Testing 时，把该 Google 账号加入 Test users。
- 检查登录账号是否和添加的 tester 完全一致。
- Workspace 账号需要管理员允许第三方应用和 Drive scope。

### 出现“Google 尚未验证此应用”

这是 Testing 或 restricted scope 尚未通过公开验证时的预期行为。确认 Cloud project 和应用确实由你控制后，测试用户可以按照页面提供的高级选项继续；面向普通用户发布前应完成 OAuth verification。

### Drive 请求返回 403

- `accessNotConfigured`：Google Drive API 尚未在当前 project 启用。
- `insufficientPermissions`：授权 token 没有代码所需的 Drive scope，撤销旧授权后重新连接。
- `admin_policy_enforced`：Google Workspace 管理策略阻止了 scope，需要联系管理员。
- `rateLimitExceeded`：等待后重试，并检查 Cloud project 的 Drive API quota。

### 登录成功但文件列表为空，或找不到某个文件

1. 确认授权的是存放目标文件的那个 Google 账号。
2. 已进入回收站的文件不会显示。
3. 点击刷新按钮重新获取 Drive 列表。
4. 打开文件夹时，列表会切换为该文件夹的直接子项；点击顶部返回按钮可回到 All files。
5. Google Workspace 管理策略可能隐藏或阻止部分共享文件。

### 创建 client 时提示包名和 SHA-1 已存在

每一对 Android package name + SHA-1 在 Google/Firebase 项目中必须唯一。它可能已经存在于另一个 Cloud 或 Firebase project。找到原 project 并复用或删除旧 client；如果无法定位，需要联系 Firebase/Google Cloud 支持。

### 修改 OAuth 配置后仍然使用旧权限

OAuth grant 可能已经缓存。可以在 Google Account 的第三方应用访问页面撤销 Open GDrive 权限，强制停止应用并重新打开，再次执行连接。也可以等待旧 access token 过期。

## 15. 官方参考资料

- [Manage OAuth Clients](https://support.google.com/cloud/answer/15549257)
- [Choose Google Drive API scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
- [AuthorizationClient API](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationClient)
- [Manage App Audience](https://support.google.com/cloud/answer/15549945)
- [Submit an app for OAuth verification](https://support.google.com/cloud/answer/13461325)
- [OAuth verification requirements](https://support.google.com/cloud/answer/13464321)
- [When verification is not needed](https://support.google.com/cloud/answer/13464323)
- [Google Workspace API user data policy](https://developers.google.com/workspace/workspace-api-user-data-developer-policy)
