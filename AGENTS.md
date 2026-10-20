# SkyPulse Weather - 项目记忆

## 构建环境
- **JAVA_HOME**: `C:\Program Files\Android\Android Studio\jbr`
- **Gradle Wrapper**: `gradlew.bat` (Gradle 8.13)
- **compileSdk / targetSdk**: 35
- **minSdk**: 34
- **Java Version**: 17

## 签名信息 (Release)
- **Keystore 文件**: `app/release-keystore.jks`（**不入库**，`.gitignore` 已忽略 `*.jks`）
- **Key Alias**: `weather-app`（默认值，可用 `RELEASE_KEY_ALIAS` 覆盖）
- **口令来源**: 仅从 `local.properties` / 环境变量读取（`RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_PASSWORD`），**严禁写入仓库或文档**
- **构建行为**: 口令缺失时 `assembleRelease` 直接失败，绝不产出未签名 APK
- **更新注意**: 换 key 必须同步本地 `local.properties` 并重新构建，否则覆盖安装会因签名不一致失败

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
- **保留的 v7 端点**：`/v7/minutely/5m`（和风至今未提供分钟级降水 v1 版本，官方文档当前仍为该路径），**不在停服范围内，无需迁移**；
- **天文类 v7 端点（v1.0.7 新增，非核心增强）**：`/v7/astronomy/solar-elevation-angle`（太阳高度角/方位角）与 `/v7/astronomy/moon`（月升月落 + 逐小时月相 `illumination`），用于给玻璃高光提供真实光照数据。二者**不在停服名单内**，但属于"锦上添花"：
  - 调用失败、超时、返回非数值（和风把数值当**字符串**返回，错误时也可能是字符串）时**一律不抛错**，直接置空该字段；
  - UI 侧回落到本地 NOAA 太阳位置算法 + Meeus 月历算法（`ui/glass/SkyPosition.kt`），**功能不依赖这两个端点**；
  - 这两个端点**不记 DataIssue**（本地可兜底，不打扰用户）。

### v1 字段层陷阱（v4.1.3 修正）
- **`wind.scale`（蒲福风级）位于 `wind` 内、与 `speed` 同级**，**不是** `wind.speed.scale`。曾建错层级导致该字段永远解析不到，现已修正并全链路启用：`QWeatherMapper` → `Wind.scale` / `HourlyWind.scale` → `WeatherUtils.windLevelLabel(scale, speed)`（官方优先、本地换算兜底），实况卡与逐小时行均已接入；
- **`metadata.attributions` 是强制展示项**：和风《注明来源》要求使用预警/空气质量的产品**完整展示且不得修改**其内容；预警响应里该数组可能同时包含 URL 与自然语言声明。已由 `AttributionNote` 组件逐条原样渲染（预警详情页底部 + 空气质量卡片底部），禁止截断/改写；
- **astro 扩展字段已全量建模并投入使用**：日出日落卡新增「正午/子夜/天亮(民用晨光)/天黑(民用暮光)」，月出月落卡新增「月上中天/月下中天」。

### 逐小时预报渲染规范（240h 窗口化）
- **数据请求**：主界面 `hours=240`（10 天拉满）；Widget/同步链路仍用 `72`（只需覆盖昨日窗口，省流量）；
- **严禁整条长 Canvas 一次性构建**：240 × 64dp = 15,360dp 宽，必须**窗口化**——`HourlyTemperatureChart` 仅构建视口 ±2 格缓冲范围内的元素，Canvas 用 `translate` 把内容坐标系映射到窗口画布坐标系（步长恒为全局 `HOUR_WIDTH`，不得按窗口缩放）；
- **快速滑动降级**：滚动速度 > 2200px/s 视为快速滑动，Canvas 只画曲线骨架、各行渲染 `HourlyCellPlaceholder` 占位，停止 ~140ms 后恢复完整渲染；
- **回到现在**：滑出 72h 后在卡片标题下方浮出按钮，点击 `animateScrollTo(0)`；时间轴在每天 0 点切换为「明天/后天/M月d日」标记。

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

### 4. 统一液态玻璃框架（v1.0.5）
- **唯一入口**：所有玻璃面板统一使用 `Modifier.liquidGlass(...)`（`ui/components/LiquidGlass.kt`）；
  主页用 `GlassCard`，次级页用 `secondaryGlassSurface`，弹窗用 `floatingGlassSurface`，
  引导页直接用 `liquidGlass`。**严禁再为单个面板手写材质 / blur / AGSL。**
- **背景源**：页面背景层必须加 `Modifier.layerBackdrop(backdrop)`，并通过
  `LocalGlassBackdrop` 下发给内容；背景层与内容层分离，玻璃只采样实时背景，
  不采样自身内容。
- **库来源**：`app/src/main/java/com/kyant/backdrop/` 是 Kyant0/AndroidLiquidGlass
  （Backdrop，Apache-2.0）的 Android-only vendored 子集。上游 2.x 依赖 Kotlin 2.4/Compose 1.12，
  本项目 Kotlin 2.0/Compose 1.7，故 vendored 并做了兼容适配；升级前先验证构建。
- **形状**：玻璃统一用 `RoundedCornerShape`，与 Backdrop 的 rounded-rect SDF 一致；
  不要再把超椭圆 Shape 传给 lens（会因 SDF 不匹配产生缺角/黑角）。
- **表面色**：v1.0.5 默认**不铺白色罩色**（Clear 玻璃），可读性由背景模糊、边缘高光、
  内外阴影提供；如果后续确实需要压暗/提亮，使用中性 dim 或自适应亮度，不要回退成
  大面积白罩。`onDrawSurface` 若启用，必须与背景层一样被 clip 到面板形状。
- **动态高光**：`LocalGlassLight` / `rememberGlassLight(...)` 计算高光轴向、颜色与强度；
  详见下方「玻璃物理光照模型」。新增玻璃面板只要走 `liquidGlass` 就自动继承，
  不要单独写死高光角度。
- **材质参数收口（v1.0.7）**：模糊半径、雾化浓度、折射厚度、高光宽度/衰减
  **全部收敛到 `ui/glass/GlassStyle.kt`**（`GlassStyles.Surface / Secondary /
  Floating / Onboarding / OnboardingAction`），`Modifier.liquidGlass` 通过
  `LocalGlassStyle` 取值，**调用点不得再硬编码 blur/tint/refraction**；
  要调参只改 `GlassStyle` 一处，全站生效。
- **关键坑（严禁重犯）**：
  1. `drawBackdrop` 为了采样模糊/折射，会把背景层向四周扩 `padding`；
     **必须把该背景层 clip 到面板形状再合成**，否则后绘制的面板会用扩边背景
     覆盖前一个面板的内容（v1.0.5 修过）。
  2. vendored `Lens.kt` 在 `onAttach()` 时 `size` 尚未就绪，必须先判 `size.isSpecified` 再应用；
     否则会抛 `Size is unspecified` 崩溃。
  3. 不要在 Compose 1.7 下恢复 `DrawBackdropNode` 的 `placeWithLayer`/LayoutModifier 方案；
     会触发 `LayoutNode should be attached to an owner`。
  4. 抬高 `blurRadius` 会同步抬高 Backdrop 的 `padding`（离屏层按面板 + 2×radius 记录），
     面板越多、radius 越大，显存与合成开销线性上涨；**模糊半径不要超过 25dp**。
- **验证方式**：临时在背景层画高对比网格，滚动页面确认网格在面板内外保持同一屏幕位置
  （背景不随面板粘滞），并检查四角无黑洞、相邻面板内容不被覆盖；验证后删除网格。

### 4.1 玻璃物理光照模型（v1.0.7，双光源 + 色温科学）
- **定位**：`ui/glass/` 下四个文件构成完整光源体系，**UI 层只消费结果，不自己算光**：
  - `BlackbodyColor.kt`：色温/光谱 → sRGB。口径 = Planck 定律 + CIE 1931 2° 标准观察者
    （Wyman et al. 2013 多瓣高斯拟合）+ CIELAB 色域映射 + sRGB/IEC 61966-2-1。
  - `SkyPosition.kt`：天体位置。太阳 = NOAA Solar Position；月亮 = Meeus 简化月历
    （含被照亮比例）。另含日出日落/各晨昏时刻/月升月落求解。
  - `GlassLighting.kt`：照度 → 感知强度（对数压缩）+ 屏幕空间三维投影。
  - `GlassStyle.kt`：材质 token（见上）。
- **双光源**：太阳与月亮可**同时存在**，各自有独立的角度、颜色与强度；
  vendored 的 `HighlightStyle.Default` 已扩展 `intensity` 与 `secondary`，
  AGSL shader 支持两条光轴（`DefaultHighlightShaderString`，缓存 key 改为 `DefaultDual`）。
- **强度口径（重要）**：太阳与月亮真实照度相差约 5.5 个数量级
  （正午直射日光 ≈ 1.2×10⁵ lux，满月中天 ≈ 0.25 lux，比值 ≈ 1/380 000）。
  直接渲染会让月亮高光彻底消失，故用**对数感知压缩**映射到 0..1，
  满月约为太阳的 30%。**严禁改成线性映射**。
- **色温依据**：
  - 太阳：有效温度 5778 K（IAU 2015）；经大气消光（Rayleigh + Ångström 气溶胶 +
    Kasten-Young 空气质量数）后，正午约 5100 K、地平线附近降到约 1250 K（深橙红）。
  - 月亮：其光谱几乎等同于阳光（月球反照率 0.136，近中性灰），光度学色温约 4100 K；
    夜间"看起来偏冷"是 **Purkinje 效应**（暗视觉蓝移）。本项目按**外观色温 9000 K**
    渲染（与 v1.0.5 夜间高光色 #D6E4FF 实测 9060 K 一致）。
- **屏幕空间投影**：把 (高度角, 方位角) 做三维投影（东向分量 → 屏幕 x，天顶分量 → 屏幕 y），
  因此**日出时高光在右缘、正午在上缘、日落在左缘**。纯屏幕自参考，
  **不依赖罗盘/重力传感器**（全项目零传感器 API）。
- **多城市各自成场**：城市分页里每页用**自己的经纬度与天文数据**计算光源，
  切换城市时高光方向/强度随地点变化；整屏共用一个 `rememberNowMillis()` 心跳，
  **不要每页各起一个定时器**。
- **无光源即无高光**：太阳月亮都不在地平线以上时，`Highlight` 返回 null，
  面板**完全不绘制边缘高光**（黑夜不该有反光）。
  但"完全没有数据源"（首次启动/引导页/缓存空）要回退到默认日光，
  避免玻璃变成死板方块——两者必须区分（见 `GlassLight.kt` 的 `defaultDaylight()`）。
- **数据来源优先级**：和风天文接口 → 本地 NOAA/Meeus → astro 时刻弧线 → 默认日光。
  接口返回的是**字符串数字**，必须用 `toDoubleOrNull()` 严格解析，
  解析失败即视为该来源不可用（`QWeatherMapper.mapAstronomy`）。
- **回归测试**：`app/src/test/java/com/ridgecast/weather/ui/glass/GlassLightingTest.kt`
  与 `ui/components/GlassLightStateTest.kt` 固化了上述物理口径，改动前先跑。

### 5. 按钮组排版规范（v4.1.2 修复「重新生成」按钮重绘换行）
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

## 数据完整性校验层与示例数据 (v1.0.7)
- **校验器**：`util/WeatherDataValidator.kt`，按统一口径检查一份 `WeatherResponse` 是否字段齐全，
  逐项给出路径（如 `daily.astro[3].sunrise`），并区分**必填缺失**与**可选缺失**（如天文快照）。
- **日志开关**：**默认关闭**（`WeatherDataValidator.logEnabled = false`，零 IO）。
  打开后每次刷新与每次生成示例数据都会写文件日志（TAG = `DataValidator`），
  全部齐全则打印「数据完整（已校验 N 项）」，有缺失则打印缺失清单。
- **示例数据**：`data/mock/MockWeatherData.kt`，字段与真实和风接口**完全同构**
  （实况/分钟级/24h 逐时/10 天逐日/预警/天文快照），可直接当作"接口返回"喂给校验器。
- **位置口径**：联合国大会 2018 年以 164 票通过决议，推荐用 **Equal Earth**
  （EPSG:8857，等积投影，Europe-Africa centred）替代墨卡托作为官方世界地图。
  该地图中央经线为 0°，图幅几何中心 = **赤道 × 本初子午线 = 北纬 0°、东经 0°**，
  落在**几内亚湾海域**（非洲西岸外海），故示例城市名为「几内亚湾」。
  气候按赤道热带海洋性（Af）编写：全年 24~30℃、湿度 > 60%、午后对流雷阵雨、
  西南季风、紫外线全年极强。
- **一致性保障**：`app/src/test/java/com/ridgecast/weather/data/mock/MockWeatherDataTest.kt`
  断言示例数据**零缺失**。真实接口字段升级 → 校验器同步升级 → 该测试立刻指出
  示例数据缺了什么，**不需要人工逐字段核对**。

## 版本管理
- **版本号位置**: `app/build.gradle.kts` 中的 `versionCode` 和 `versionName`
- **版本号升级**: 发版时 patch +1、versionCode +1，无需单独的 bump 脚本。当小版本号（patch）超过 100 之后自动进位并迭代一次中版本号（minor），例如：`3.0.100` 之后的下一个版本就是 `3.1.0`。
- **当前发布状态（v1.0.5 / 2026-10-19）**: 全 UI 切换至统一 Backdrop 液态玻璃框架（主页、设置、城市管理、预警详情、引导页、浮动弹窗）；Clear 玻璃无白罩，太阳/月亮轨迹驱动动态高光，阴影收敛；修复扩边背景覆盖相邻面板、直角白遮罩、黑角与背景采样粘滞。正式 APK 经本地签名后附带于 GitHub Release（v1.0.5）分发。
- **开发中（v1.0.7，未发版）**: 玻璃材质参数收口到 `GlassStyle`（统一 blur 10dp、雾化 0.20/0.12）；高光升级为**太阳 + 月亮双光源**（物理高度角/方位角 → 屏幕空间三维投影，CIE 统一口径色温）；多城市按各自经纬度各成一场；接入和风天文接口（带本地 NOAA/Meeus 兜底）；新增数据完整性校验层与重做后的示例数据（世界地图中心点·几内亚湾）。

## 发版与构建规则
- **本地编译优先**: 以后所有 Release 版本采用本地优先编译验证（`./gradlew assembleRelease`），不再推送到 GitHub Actions 远程编译；
- **GitHub 只提交代码，不编译（v4.1.2 起）**: 仓库不再保留任何 CI 编译工作流（`.github/workflows/build.yml` 已删除），push 仅用于同步源码，任何 APK 一律由本地编译产出；
- **编译产物归档**: 本地编译成功后，将 `app/build/outputs/apk/release/app-release.apk` 复制重命名为工作区根目录的 `ridgecast-v<versionName>.apk`；
- **包体安全性**: 由于已彻底清除编译期密钥注入，发布包不包含任何敏感信息，但仍保持私下分发规范；
- **同步推送到 GitHub Release（v1.0.2 起确立标准流程）**: 每次版本更新在本地构建验证通过后，**必须同步推送到 GitHub Release**：
  1. 提交代码并推送至 `origin main`；
  2. 创建对应版本 tag 并推送：`git tag v<versionName> && git push origin v<versionName>`；
  3. 通过 `gh release create v<versionName> ridgecast-v<versionName>.apk --title "RidgeCast (脊线天气) v<versionName>" --notes "..."` 将本地归档产物同步上传至 GitHub Release。Release notes 须包含变更说明并声明 key-free 与本地构建来源。上传前确保通过安全审计。

## Git 操作规范
- **git操作**: 除非用户主动要求提交/推送（必须每次对话明确提出发版-不能根据上下文内容自己推测），否则不要提交/推送代码到远程仓库
- **提交信息**: git commit message 不得添加 Co-Authored-By 行或其他 AI 协作者标记
- **提交语言**: 所有 Git 提交描述（commit message）必须使用英文


## 包体命名
- **APK 命名**: `ridgecast-v<versionName>.apk`
- 下载到工作区的 APK 必须使用该格式
- **APK 清理**: 每次构建成功并生成新的 APK 后，清理根目录中旧的 `ridgecast-v*.apk` 包，仅保留最新构建产物；除非用户明确要求保留历史 APK

## 参考素材与逆向分析 (ref/)
- **目录约定**：所有用于学习/对照的外部素材统一放在仓库根目录 `ref/` 下；APK 放在 `ref/apks/`，其他反编译产物、外部代码样本、设计参考等也放在 `ref/` 下（可按类型再分子目录）。
- **不纳入版本控制**：`ref/` 已加入 `.gitignore`（`/ref/`），严禁提交 APK、反编译产物或大体积二进制到仓库；只提交分析结论与必要的小体量文档。
- **APK 逆向分析**：使用已安装的 `reverse-engineer-anything` skill（来源 https://github.com/morluto/rea）分析 APK 时，APK 留在 `ref/apks/`，解码/反编译产物优先输出到 `/tmp` 或 `ref/decoded/` 等本地目录，不要写入 `app/` 源码树。
- **分析边界**：仅在用户明确要求时执行 APK/外部代码分析；分析结论应聚焦与本项目 UI/功能相关的可复用实现细节，避免把外部素材的版权代码直接复制进本项目。

## 编码规范
- **所有源码文件统一使用 UTF-8 编码（无 BOM）**
- 涵盖文件类型：`*.kt`、`*.java`、`*.xml`、`*.gradle`、`*.kts`、`*.properties`、`*.md`、`*.json`、`*.pro`
- AI 工具执行命令时必须确保不破坏文件编码，避免使用可能导致 GBK/GB2312 混入的写入方式
- 如需写入文件内容，始终指定 UTF-8 编码
