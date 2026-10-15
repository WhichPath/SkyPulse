# SkyPulse v4.0.1 改造进度与待续记录

## 一、已完成部分 (Completed)

1. **全离线四级行政区划正向检索引擎与二进制资产 (`divisions.bin`)**
   - 更新 `data/geocoding/build_mobile_assets.py` 并已编译生成 `app/src/main/assets/geo/divisions.bin`（收录全国 42,419 个省/市/县/乡四级节点，含 114 个回退上级区县中心点的待补全乡镇标记）。
   - 修复香港特别行政区逆地理编码前缀重复问题（统一输出 `香港特别行政区 中西区`）。
   - 重写 `OfflineGeocodingProvider.kt` 与 `GeocodingService.kt`，实现 100% 本地离线名称搜索（零外部 API 请求）：
     - 自动剥离并忽略行政区划通名与民族修饰词（如“广西省”自动解析为“广西壮族自治区”）；
     - 严格控制显示层级在用户输入的下一级（如输入“四川”仅返回下辖地市或省直管县）；
     - 同名歧义拦截（如输入“城中”或“城中区”仅罗列 `柳州市 城中区`、`西宁市 城中区`，不展开下一级）；
     - 全量严格按民政部官方区划代码升序排列；
     - 缺失离散驻地坐标的 114 个乡镇自动回退至上级区县中心点，并透传 `isApproximateCenter = true` 与 `parentCountyName`。
   - 更新 `CitySearchViewModel.kt` 透传完整离线检索字段（`savedName`、`isApproximateCenter`、`parentCountyName`、`drillDownQuery`）。

2. **清理旧壳与构建配置升级 (`minSdk = 34`, `v4.0.1`)**
   - `app/build.gradle.kts` 已升级 `minSdk = 34` (Android 14+)，版本号更新为 `versionCode = 1006` / `versionName = "4.0.1"`。
   - 已移除高德定位/逆地理 (`AmapLocationProvider`、`AmapGeocodingProvider`、`libs.amap.location`、`AndroidManifest.xml` 中的 `APSService`)、旧版会员/打赏/更新检查组件（`MembershipRepository`、`MembershipDialog`、`VipBadge`、`DonateDialog`、`qr_wechat.png`、`CheckUpdateUseCase`、`GithubApi`）及废弃脚本。
   - `LocationManager.kt` 已精简为纯 `SystemLocationProvider` + `OfflineGeocodingProvider` 架构。
   - 已新增 `app/src/main/assets/lucide/circle-alert.svg`（灰色带圈感叹号）与 `circle-help.svg` 图标资源。

---

## 二、下次开机待继续收尾部分 (Remaining Tasks) (2026-10-15 continued: Task 1-2 completed; Task 3 remains)

1. **移除剩余文件中的 `MembershipRepository` / `CheckUpdateUseCase` 引用**
   - `SettingsViewModel.kt`：移除 `MembershipRepository` 注入与激活码方法。
   - `WeatherViewModel.kt`：移除 `CheckUpdateUseCase` 注入与 `updateState`。
   - `WeatherWidgetUpdater.kt`、`WeatherNotificationWorker.kt`、`UrgentNotificationWorker.kt`、`DailyForecast.kt`：移除 `MembershipRepository` / `isPremium` 门控判断。

2. **设置页精简与搜索候选项 UI/QA 弹窗实装**
   - `SettingsScreen.kt`：删除顶部会员状态卡片、开关会员锁、「权益」栏目、「关于」栏目、底部 QQ 群与邮箱联系方式，仅保留版本号。
   - `CityListScreen.kt` & `CityListRow.kt`：
     - 候选项使用 `result.savedName` 添加城市；
     - 对 `isApproximateCenter == true` 的候选项在名称后展示灰色带圈感叹号 (`circle-alert`)；
     - 提供点击感叹号 / 顶部说明入口弹出 QA 说明面板（提示选择此项实际经纬度将采用上级区县中心点数据）；
     - 支持候选项右侧“下辖”快捷下钻。

3. **2026 液态玻璃 (Liquid Glass) + ColorOS 16 光场设计 UI 体系重构与 GitHub CI 编译发版**
   - 基于 Android 14+ 原生 AGSL (`RuntimeShader`) 与 `RenderEffect` 升级 `GlassCard` 与次级页面光场材质（各向异性天顶高光边、圆角边缘折射微光、动态亮度分层与阻尼流体按压反馈）。
   - 推送 GitHub 编译验证并触发 `Build APK` 产出 `skypulse-v4.0.1.apk`（下载后立即删除远端 artifact 并清理旧包）。
