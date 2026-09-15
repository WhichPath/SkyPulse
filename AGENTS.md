# SkyPulse Weather - 项目记忆

## 构建环境
- **JAVA_HOME**: `C:\Program Files\Android\Android Studio\jbr`
- **Gradle Wrapper**: `gradlew.bat` (Gradle 8.5)
- **compileSdk / targetSdk**: 35
- **minSdk**: 26
- **Java Version**: 17

## 签名信息 (Release)
- **Keystore 文件**: `app/release-keystore.jks`
- **Store Password**: `weather123`
- **Key Alias**: `weather-app`
- **Key Password**: `weather123`

## QWeather (和风天气)
- **天气数据源**: 使用 QWeather API 提供天气/预警/空气质量/分钟降水数据
- **认证方式**: JWT (Ed25519 签名)，比 API Key 更安全
- **免费额度**: 每月 50,000 次请求（天气和基础服务）
- **API 配置位置**: `local.properties` 中的 `QWEATHER_PROJECT_ID`、`QWEATHER_KEY_ID`、`QWEATHER_PRIVATE_KEY`、`QWEATHER_API_HOST`
- **JWT 生成**: App 运行时使用 BouncyCastle (Ed25519) 在本地签名生成 JWT，私钥由用户保管
- **依赖库**: `org.bouncycastle:bcprov-jdk18on` (Ed25519 签名支持 minSdk 26)

### 已使用的 API 能力（每次刷新 6 个请求）
- **天气预报**: `/weather/v1/current/{lat}/{lon}`（实况，v1，含 uvIndex）、`/weather/v1/daily/{lat}/{lon}`（逐日，v1，最多 10 天）、`/weather/v1/hourly/{lat}/{lon}`（逐小时，v1，含真实逐小时 uvIndex）
- **分钟降水**: `/v7/minutely/5m`（未来 2 小时分钟级降水，和风无 v1 版本，保留 v7）
- **天气预警**: `/weatheralert/v1/current/{lat}/{lon}`
- **空气质量**: `/airquality/v1/current/{lat}/{lon}`
- **GeoAPI**: `/geo/v2/city/lookup`（城市搜索，用户输入城市名时触发）
- **天文（部分）**: 日出日落数据从 v1 逐日预报响应的 `astro` 中提取，映射到 `DailyAstro`，无需单独请求。v1 实况、逐小时、逐日均含 UV 数据（实况为实时值，逐小时为逐小时值，逐日为 `uvIndexMax` 当日最大值）

### 未使用的 API 能力（下一步计划）
- **天气指数** (`/v7/indices/1d`): 和风提供穿衣、洗车、感冒、运动、钓鱼、旅游、花粉、舒适度等 16+ 种生活指数。当前仅从日报 `uvIndex` 映射了紫外线。计划调用此端点补充完整生活指数卡片。
- **天文 - 月相**: 逐日预报响应已含 `moonrise`/`moonset`/`moonPhase`/`moonPhaseIcon`，但未映射到模型和 UI。计划在日出日落卡片中增加月相展示。
- **GeoAPI 扩展**: 当前仅用城市搜索。和风还提供 POI 搜索（`/geo/v2/poi/lookup`，搜索地标景区）、热门城市（`/geo/v2/city/top`）、POI 范围搜索（`/geo/v2/poi/range`）。按需添加。
- **时光机**: 历史天气数据，可视需要添加历史天气回顾功能。

## AMAP (高德地图)
- **定位服务**: 使用 AMAP Location SDK 进行 GPS 定位
- **API Key 配置位置**: `local.properties` 中的 `AMAP_API_KEY`

## 版本管理
- **版本号位置**: `app/build.gradle.kts` 中的 `versionCode` 和 `versionName`
- **版本号升级**: 发版时 patch +1、versionCode +1，无需单独的 bump 脚本。当小版本号（patch）超过 100 之后自动进位并迭代一次中版本号（minor），例如：`3.0.100` 之后的下一个版本就是 `3.1.0`。

## 发版规则
- **默认发版**: 每次代码改动完成并验证后，bump 版本并推送到 GitHub（普通 push 只做编译验证、不产出 APK），随后手动触发 CI 构建；构建成功后把 APK 下载回本工作区作为发版包体，并立即删除远端 artifact；除非用户明确要求暂不发版
- **CI 产物流程**: 只有手动触发（`workflow_dispatch`）才会上传 artifact，普通 push 不产包。发版时用 `gh workflow run "Build APK" -R WhichPath/SkyPulse --ref main` 触发构建，构建成功后用 `gh run download <run-id> -R WhichPath/SkyPulse` 将 APK 下载到工作区根目录并重命名为 `skypulse-v<versionName>.apk`；随后必须立即用 `gh api -X DELETE repos/WhichPath/SkyPulse/actions/artifacts/<artifact-id>` 删除远端 artifact，避免内嵌密钥的包长期挂在公开仓库上、被任何 GitHub 账号下载。注意本仓库另有 `upstream` 远端，所有 `gh` 命令必须显式带 `-R WhichPath/SkyPulse`，否则会误指向 upstream
- **GitHub 发版（永久禁止）**: release APK 通过 BuildConfig 内嵌了 QWeather Ed25519 私钥、PROJECT_ID/KEY_ID 和高德 API Key，反编译即可提取；且 `app/release-keystore.jks` 已入库、密码公开在本文档，任何人拿到包都能提取密钥、冒签重打包。因此 APK（含 CI artifact）只可私下分发，永远不要创建公开的 GitHub Release

## Git 操作规范
- **git操作**: 除非用户主动要求提交/推送（必须每次对话明确提出发版-不能根据上下文内容自己推测），否则不要提交/推送代码到远程仓库
- **提交信息**: git commit message 不得添加 Co-Authored-By 行或其他 AI 协作者标记
- **提交语言**: 所有 Git 提交描述（commit message）必须使用英文


## 包体命名
- **APK 命名**: `skypulse-v<versionName>.apk`
- 下载到工作区的 APK 必须使用该格式
- **APK 清理**: 每次构建成功并生成新的 APK 后，清理根目录中旧的 `skypulse-v*.apk` 包，仅保留最新构建产物；除非用户明确要求保留历史 APK

## 编码规范
- **所有源码文件统一使用 UTF-8 编码（无 BOM）**
- 涵盖文件类型：`*.kt`、`*.java`、`*.xml`、`*.gradle`、`*.kts`、`*.properties`、`*.md`、`*.json`、`*.pro`
- AI 工具执行命令时必须确保不破坏文件编码，避免使用可能导致 GBK/GB2312 混入的写入方式
- 如需写入文件内容，始终指定 UTF-8 编码
