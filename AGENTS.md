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
- **API 配置位置（v4.1.1 起完全运行时解析，彻底零内置密钥）**: 
  1. **运行时专享**：用户在设置页「和风天气 API」分区填写，存于 `QWeatherCredentialStore`（应用私有 SharedPreferences，不随构建产物分发）；
  2. **本地密钥生成**：App 本地自动生成 RFC 8410 标准 Ed25519 密钥对，支持在设置页一键复制标准 X.509 SPKI 公钥与核对 SHA-256 指纹，方便上传至和风控制台；
  3. **沙盒安全防丢**：凭据仅保存在应用私有内部存储与专属外部沙盒 `getExternalFilesDir("credentials")`，严禁在手机根目录产生孤立文件；应用覆盖安装升级自动完整保留；
  4. **编译期零密钥**：`build.gradle.kts` 与编译产物彻底清除 `QWEATHER_*` 注入，包体绝对零密钥。
- **JWT 生成**: App 运行时使用 BouncyCastle (Ed25519) 在本地签名生成 JWT，私钥由用户保管
- **依赖库**: `org.bouncycastle:bcprov-jdk18on` (Ed25519 签名支持 minSdk 26；ProGuard 与打包已裁剪排除无关加密套件与 pqc 资源)

### API Host 规范（v4.1.2 血泪教训，严禁重犯）
- **API Host 是开发者账号级随机分配的专属地址**，形如 `xxxxxx.xx.qweatherapi.com`，且**属于身份认证的一部分**，无法由 Project ID 推导，只能从控制台 [设置页](https://console.qweather.com/setting) 复制；
- **官方不存在任何通用/开发域名**：旧公共域名 `api.qweather.com`(2026-06-01 停服)、`devapi.qweather.com`(2026-01-01 停服)、`geoapi.qweather.com`(2026-06-01 停服) 已全部陆续停服；
- **兜底常量只能放显眼占位串**：`QWeatherCredentials.DEFAULT_API_HOST` 保留 `REPLACE_WITH_YOUR_API_HOST` 占位（开发者替换为真实 Host），并在 `isApiHostUsable()` 中拦截——**严禁臆造任何 `*.qweatherapi.com` 之外的域名作为默认值**，v4.1.1 事故即源于臆造出并不存在的 `devapi.qweatherapi.com`；
- **存储层双重兜底**：启动时 `dropInvalidApiHostEntry()` 清除历史遗留的无效 Host，沙盒恢复时 `restoredApiHostOrDefault()` 拒绝带病恢复。

### v7 端点现状（停服公告 2026-07-31）
- **仅 `/v7/grid-weather/*` 与 `/v7/weather/*` 于 2027-08-01 停服**，本项目全部天气接口早已迁移至 v1（`/weather/v1/current|daily|hourly`、`/weatheralert/v1/*`、`/airquality/v1/*`、`/geo/v2/*`）；
- **唯一保留的 v7 端点是 `/v7/minutely/5m`**（和风至今未提供分钟级降水 v1 版本，官方文档当前仍为该路径），**不在停服范围内，无需迁移**。

## UI 架构与设计规范

### 1. 苹果连续平滑圆角（Continuous Corner / Squircle）
- **架构级统一**：全项目严禁硬编码普通 `RoundedCornerShape`（曲率突变会导致机械生硬感），统一收敛至 `SkyPulseDesignSystem.Shapes` 与 `ContinuousCornerShape`；
- **数学模型**：采用 Apple 工业级超椭圆公式（Lamé Superellipse）：$|x/a|^n + |y/b|^n = 1$（采用苹果黄金指数 $n = 4.2$）；
- **覆盖范围**：不仅首页卡片，搜索页城市列表框、对话框、设置页、底部面板与所有裁剪组件全量使用超椭圆圆角；
- **M3 主题联动**：`SkyPulseShapes` (MaterialTheme) 深度集成连续圆角，基础 M3 组件自动继承。

### 2. 首页六项实时气象指标卡片尺寸与留白
- **比例优化**：空气质量下方的 6 个实时信息块（紫外线、湿球黑球温度、湿度、风向/风速、气压、能见度）采用 `aspectRatio(1.22f)`；
- **消除空旷**：摒弃传统 1:1 正方形造成的上下大面积无意义留白，内边距规范为 `start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp`，保持垂直与水平留白视觉均衡适中。

### 3. 全局屏幕空间统一光场（Unified Light Field）
- **物理光源**：屏幕空间全局虚拟光源锚定在左上方（$X \approx 28\% W, Y = -380\text{dp}$）；
- **视差衰减**：各卡片依据在视口中的绝对坐标同心发散受光，上下深度随列表垂直滑动动态演变高光聚散度与轮廓微光。

### 4. 按钮组排版规范（v4.1.2 修复「重新生成」按钮重绘换行）
- **同排按钮一律等分宽度**：按钮放入 `Row` 时每个按钮必须带 `modifier = Modifier.weight(1f)`，父 `Row` 必须 `fillMaxWidth()`；
- **按钮文字单行省略**：`Text` 必须设 `maxLines = 1` + `softWrap = false` + `overflow = TextOverflow.Ellipsis`，**严禁让按钮文案换行撑高**；
- **状态切换文案不得改变宽度**：文案由「复制公钥」变为「已复制 ✓」这类变长时，因为等分宽度才不会引发兄弟按钮重新测量与抖动；
- **加载态用显式参数**：`GlassActionButton(loading = true)`，**严禁用 `text == "xxx"` 这类字符串比较来判断是否展示 loading**。

## 离线逆地理编码 (Offline Geocoding)
- **定位与编码方案**: 采用全离线两阶段空间查询体系（阶段一：天地图 2,900 个法定区县多边形射线法命中；阶段二：民政部 2026 最新 39,188 个乡镇街道驻地最近邻匹配）
- **数据管线位置**: `data/geocoding/`（全流程构建脚本 `build_offline_geodata.py`、区县边界 `counties_2026.geojson`、乡镇标准字典 `townships_2026.json`）
- **拓扑特性**: 包含对两江新区（500157）与加格达奇区（232761）执行硬外壳差集剪裁后的 100% 紧密填充无缝多边形
- **设计规范**: 详见 `docs/OFFLINE_GEOCODING_SPECIFICATION.md` 与 `data/geocoding/README.md`

## AMAP (高德地图)
- **定位服务**: 历史版本曾使用 AMAP Location SDK 与 Web API 进行定位与逆地理编码；现已由本地全离线逆地理编码体系取代，无需外部网络请求与 API Key。

## 版本管理
- **版本号位置**: `app/build.gradle.kts` 中的 `versionCode` 和 `versionName`
- **版本号升级**: 发版时 patch +1、versionCode +1，无需单独的 bump 脚本。当小版本号（patch）超过 100 之后自动进位并迭代一次中版本号（minor），例如：`3.0.100` 之后的下一个版本就是 `3.1.0`。

## 发版与构建规则
- **本地编译优先**: 以后所有 Release 版本采用本地优先编译验证（`./gradlew assembleRelease`），不再推送到 GitHub Actions 远程编译；
- **GitHub 只提交代码，不编译（v4.1.2 起）**: 仓库不再保留任何 CI 编译工作流（`.github/workflows/build.yml` 已删除），push 仅用于同步源码，任何 APK 一律由本地编译产出；
- **编译产物归档**: 本地编译成功后，将 `app/build/outputs/apk/release/app-release.apk` 复制重命名为工作区根目录的 `skypulse-v<versionName>.apk`；
- **包体安全性**: 由于已彻底清除编译期密钥注入，发布包不包含任何敏感信息，但仍保持私下分发规范；
- **GitHub 发版（永久禁止）**: 永远不要创建公开的 GitHub Release。

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
