# APKRenamer — 手机上编译 & 使用指南

> 修改 APK 包名，让同一应用的两个版本在同一台手机上并存。

---

## 📱 第一步：注册 GitHub 账号

如果你已有 GitHub 账号，跳过。

1. 手机浏览器打开 **github.com**
2. 点 **Sign up**
3. 填邮箱 → 设密码 → 取用户名 → 完成验证
4. 免费账号就行

---

## 📂 第二步：把代码传到 GitHub

### 方法 A：手机浏览器直接上传（最简单）

1. 打开 **github.com**，登录
2. 点右上角 **＋** → **New repository**
3. Repository name 填 `APKRenamer`
4. 选 **Public**（必须 Public，否则 Actions 不免费）
5. **不要**勾 "Add a README file"
6. 点 **Create repository**
7. 点 **uploading an existing file**
8. 把下面的文件/文件夹结构原样上传上去。**注意**：GitHub 网页不支持传空文件夹，所以你需要按下面的"逐个文件上传"方式操作。

### 逐个文件上传

GitHub 网页上传时，需要手动创建文件夹。操作方法：

在 "uploading an existing file" 页面，你可以通过在文件名里加 `/` 来创建文件夹。比如把文件名写成 `.github/workflows/build.yml`，GitHub 会自动创建 `.github` 和 `workflows` 两层文件夹。

**需要上传的所有文件（共 14 个，按顺序来）：**

| # | 在 GitHub 上的路径 | 本地文件 |
|---|---|---|
| 1 | `settings.gradle.kts` | APKRenamer/settings.gradle.kts |
| 2 | `build.gradle.kts` | APKRenamer/build.gradle.kts |
| 3 | `gradle.properties` | APKRenamer/gradle.properties |
| 4 | `.gitignore` | APKRenamer/.gitignore |
| 5 | `gradle/wrapper/gradle-wrapper.properties` | APKRenamer/gradle/wrapper/gradle-wrapper.properties |
| 6 | `.github/workflows/build.yml` | APKRenamer/.github/workflows/build.yml |
| 7 | `app/build.gradle.kts` | APKRenamer/app/build.gradle.kts |
| 8 | `app/proguard-rules.pro` | APKRenamer/app/proguard-rules.pro |
| 9 | `app/src/main/AndroidManifest.xml` | APKRenamer/app/src/main/AndroidManifest.xml |
| 10 | `app/src/main/java/com/yitian/apkrenamer/MainActivity.kt` | APKRenamer/app/src/main/java/com/yitian/apkrenamer/MainActivity.kt |
| 11 | `app/src/main/java/com/yitian/apkrenamer/core/ApkRenamer.kt` | APKRenamer/app/src/main/java/com/yitian/apkrenamer/core/ApkRenamer.kt |
| 12 | `app/src/main/java/com/yitian/apkrenamer/core/ApkSignerImpl.kt` | APKRenamer/app/src/main/java/com/yitian/apkrenamer/core/ApkSignerImpl.kt |
| 13 | `app/src/main/java/com/yitian/apkrenamer/core/KeystoreManager.kt` | APKRenamer/app/src/main/java/com/yitian/apkrenamer/core/KeystoreManager.kt |
| 14 | `app/src/main/java/com/yitian/apkrenamer/core/ManifestRewriter.kt` | APKRenamer/app/src/main/java/com/yitian/apkrenamer/core/ManifestRewriter.kt |

还没完，继续：

| # | 在 GitHub 上的路径 |
|---|---|
| 15 | `app/src/main/java/com/yitian/apkrenamer/core/Models.kt` |
| 16 | `app/src/main/java/com/yitian/apkrenamer/core/PackageNameDeriver.kt` |
| 17 | `app/src/main/java/com/yitian/apkrenamer/ui/RenameViewModel.kt` |
| 18 | `app/src/main/res/layout/activity_main.xml` |
| 19 | `app/src/main/res/values/strings.xml` |
| 20 | `app/src/main/res/values/themes.xml` |

**上传技巧**：每次可以拖多个文件进去，只要它们的"相对路径"对了就行。GitHub 会自动按 `/` 创建文件夹。

---

## 🔨 第三步：触发编译

1. 打开你的仓库页面：`github.com/你的用户名/APKRenamer`
2. 点顶部的 **Actions** 标签
3. 首次进入会看到一个黄色提示 "Workflows aren't being run on this forked repository"，点 **I understand my workflows, go ahead and enable them**
4. 左侧找到 **Build APK**，点进去
5. 右侧点 **Run workflow** → 再点绿色的 **Run workflow** 按钮
6. 等待……（约 5-8 分钟）
7. 完成后（绿色 ✓），点进那次 run
8. 滚到最下面，**Artifacts** 区域有一个 **APKRenamer**，点它下载
9. 下载的是个 zip，解压得到 `APKRenamer.apk`
10. 传到手机，安装

---

## 📲 第四步：生成扫码下载链接（可选）

如果觉得从 GitHub 下载 zip 再解压太麻烦：

1. 把解压出的 `APKRenamer.apk` 上传到以下任一网站：
   - **installonair.com** — 上传后自动生成二维码
   - **diawi.com** — 同上
2. 用手机扫二维码，直接下载安装

---

## 🎮 使用方法

1. 打开"APK 改包名工具"
2. 点 **选择 APK 文件** → 找到要改的 APK
3. 输入新的 **显示名**（如"微信备份"）
4. 点 **开始** → 等进度条
5. 弹出保存对话框 → 选位置（如"下载"目录）
6. 用文件管理器找到那个 APK，安装
7. 现在手机上就有了原版 + 改名版两个应用

---

## ⚠ 已知局限

- ❌ 加固应用（360、爱加密、梆梆等）→ 改完闪退
- ❌ 微信/QQ/支付宝/银行类 → 有自校验，改完登录失败
- ❌ 用 Google Play 服务的应用 → 功能不可用
- ✅ 单机游戏、离线工具、自写应用 → 通常可以

---

## 🔧 常见问题

### Q: Actions 里看不到 "Build APK" 这个 workflow
A: 确保仓库是 **Public**。Private 仓库的 Actions 免费额度有限。

### Q: 编译失败，报 "Could not resolve com.github.REAndroid:ARSCLib:1.3.4"
A: 版本号可能过时了。去 https://jitpack.io/#REAndroid/ARSCLib 查最新版本号，然后修改 `app/build.gradle.kts` 第 60-63 行的版本号。

### Q: 安装时提示"未安装应用"
A: 手机设置 → 安全 → 允许安装未知来源应用。

### Q: 改完的 APK 装上去闪退
A: 这不是工具 bug。该应用在代码里校验了自己的包名或签名，改了就不认了。参见"已知局限"。
