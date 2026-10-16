<a id="english"></a>

<div align="center">

<img src="screenshot.jpg" width="200" alt="RidgeCast Logo" style="border-radius: 24px;"/>

# RidgeCast

**English** | [中文](#chinese)

---

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)]()
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)]()
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)]()
[![Material 3](https://img.shields.io/badge/Design-Material%203-005A9C?style=flat-square)]()
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)]()
[![Release](https://img.shields.io/badge/Version-1.0.0-brightgreen?style=flat-square)]()
[![Min SDK](https://img.shields.io/badge/Min%20SDK-34-orange?style=flat-square)]()

A beautifully crafted, feature-rich Android weather app built with **Kotlin** and **Jetpack Compose**, featuring Apple-style continuous rounded corners, glassmorphism UI, animated weather backgrounds, and smooth animations.

</div>

---

## ✨ Highlights

| | Feature | Description |
|---|---|---|
| 🌡️ | **Real-time Weather** | Current temperature, humidity, wind, pressure, visibility, and AQI |
| 🕐 | **240h Hourly Forecast** | 10-day windowed view with a smooth Bezier temperature curve and precipitation probability |
| 📅 | **10-day Daily Forecast** | Temperature range bars with weather icons |
| ⚠️ | **Severe Weather Alerts** | Animated alert carousel with detailed warnings and full data attribution |
| 📍 | **Offline Location** | GPS auto-detection + fully offline reverse geocoding (county + township), multi-city management |
| 🍎 | **Weather Icons** | Bundled Meteocons SVG set rendered with AndroidSVG, plus Lucide UI icons |
| 🔮 | **Glassmorphism UI** | Frosted glass cards with dynamic day/night themes |
| 🎨 | **Dynamic Backgrounds** | Animated weather-themed backgrounds that change with conditions |
| 🖼️ | **Home Widgets** | Small / medium / 4×2 widgets showing real-time temperature, weather icon, and high/low |
| 🌍 | **Multi-city** | Manage and switch between multiple cities with smooth transitions |
| 🔒 | **Zero Built-in Keys** | QWeather JWT (Ed25519) credentials are generated and stored entirely on-device |

## 📸 Screenshots

<div align="center">
<img src="screenshot.jpg" width="280" alt="RidgeCast Main Screen"/>
</div>

## 🏗️ Architecture

```
MVVM + Repository Pattern
├── data/           → Retrofit API (QWeather), Room, offline geocoding, repositories
├── di/             → Hilt dependency injection modules
├── domain/         → Domain models & use cases
├── model/          → Data classes (Moshi codegen)
├── notification/   → WorkManager workers for widget & notification updates
├── repository/     → Data layer abstraction
├── sync/           → Background sync coordination
├── ui/             → Compose screens, reusable components & theme
├── util/           → Helpers (AQI / WBGT algorithms, weather utilities)
├── viewmodel/      → State management with StateFlow & Coroutines
└── widget/         → AppWidgetProvider home widgets
```

## 🛠️ Tech Stack

| Category | Technology |
|----------|-----------|
| **UI** | Jetpack Compose, Material 3, Canvas API, AndroidSVG (Meteocons & Lucide SVG) |
| **Architecture** | ViewModel, StateFlow, Coroutines |
| **Networking** | Retrofit 2.11 + Moshi 1.15.1 (KSP codegen) + OkHttp Logging |
| **Auth** | QWeather JWT (Ed25519) signed on-device with BouncyCastle |
| **Location** | Google Play Services Location + fully offline reverse geocoding |
| **Storage** | Room + DataStore |
| **Widgets** | AppWidgetProvider (RemoteViews) + WorkManager |
| **Build** | Gradle KTS, AGP 8.8.2, Kotlin 2.0.21, Java 17 |

## 🚀 Getting Started

### Prerequisites

- **Android Studio Ladybug** (2024.2.1) or later
- JDK 17
- A physical device recommended (for GPS location)

### Build & Run

```bash
# Clone the repository
git clone git@github.com:WhichPath/SkyPulse.git
cd SkyPulse

# Open in Android Studio, sync Gradle, and run on device
```

> 💡 **Tip:** The emulator uses a default location. A physical device is recommended for accurate GPS-based weather.

> 🔑 **Release signing:** `assembleRelease` requires the `RELEASE_*` keystore credentials in `local.properties` (or matching environment variables) — see [`local.properties.example`](local.properties.example). The build fails if they are missing, so an unsigned APK is never produced.

## ☁️ Weather API

Powered by [QWeather API](https://dev.qweather.com/) with on-device JWT (Ed25519) authentication:

```
GET weather/v1/current/{lat}/{lon}
GET weather/v1/hourly/{lat}/{lon}
GET weather/v1/daily/{lat}/{lon}
GET /v7/minutely/5m?location={lon},{lat}
GET weatheralert/v1/current/{lat}/{lon}
GET airquality/v1/current/{lat}/{lon}
GET geo/v2/city/lookup
```

> The API Host is account-specific (e.g. `xxxxxx.xx.qweatherapi.com`) and is configured at runtime in the in-app settings. Credentials are generated and kept on-device — no keys are embedded in the build.

## 📦 Download

APKs are **not** published on GitHub Releases. The app is built locally with `./gradlew assembleRelease` and distributed privately; the artifact is named `ridgecast-v<versionName>.apk`.

## 🤝 Contributing

Contributions are welcome! Feel free to:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m "feat: add amazing feature"`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License.

---

<div align="center">

### ⭐ Star History

[![Star History Chart](https://api.star-history.com/svg?repos=WhichPath/SkyPulse&type=Date)](https://star-history.com/#WhichPath/SkyPulse&Date)

</div>

---

<a id="chinese"></a>

# 脊线天气 (RidgeCast)

<div align="center">

<img src="screenshot.jpg" width="200" alt="脊线天气 Logo" style="border-radius: 24px;"/>

**[English](#english)** | 中文

---

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)]()
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)]()
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)]()
[![Material 3](https://img.shields.io/badge/Design-Material%203-005A9C?style=flat-square)]()
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)]()
[![Release](https://img.shields.io/badge/Version-1.0.0-brightgreen?style=flat-square)]()
[![Min SDK](https://img.shields.io/badge/Min%20SDK-34-orange?style=flat-square)]()

一款精心打造的 Android 天气应用，使用 **Kotlin** + **Jetpack Compose** 构建，采用苹果风格连续平滑圆角、毛玻璃 UI、天气主题动态背景与丝滑动画。

</div>

---

## ✨ 功能亮点

| | 功能 | 说明 |
|---|---|---|
| 🌡️ | **实时天气** | 当前温度、湿度、风力风向、气压、能见度、空气质量 |
| 🕐 | **240 小时预报** | 10 天窗口化视图，贝塞尔曲线平滑温度图，逐时降水概率 |
| 📅 | **10 天预报** | 温度区间条 + 天气图标，一目了然 |
| ⚠️ | **灾害预警** | 动画轮播预警信息，点击查看详细预警内容与完整归因声明 |
| 📍 | **离线定位** | GPS 自动定位 + 全离线逆地理编码（区县 + 乡镇街道），支持多城市管理 |
| 🍎 | **天气图标** | 内置 Meteocons SVG 图标集（AndroidSVG 渲染），界面图标采用 Lucide |
| 🔮 | **毛玻璃 UI** | 磨砂玻璃卡片，日夜主题动态切换 |
| 🎨 | **动态背景** | 天气主题动画背景，随天气变化实时切换 |
| 🖼️ | **桌面小组件** | 小 / 中 / 4×2 三种小组件，显示实时温度、天气图标、最高/最低温 |
| 🌍 | **多城市** | 多城市管理，平滑切换动画 |
| 🔒 | **零内置密钥** | 和风天气 JWT（Ed25519）凭据全部在本机生成与保存 |

## 📸 截图

<div align="center">
<img src="screenshot.jpg" width="280" alt="脊线天气 主界面"/>
</div>

## 🏗️ 项目架构

```
MVVM + Repository 模式
├── data/           → Retrofit API（和风天气）、Room、离线逆地理编码、数据仓库
├── di/             → Hilt 依赖注入模块
├── domain/         → 领域模型与用例
├── model/          → 数据类（Moshi codegen 生成）
├── notification/   → WorkManager 小组件与通知更新任务
├── repository/     → 数据层抽象
├── sync/           → 后台同步调度
├── ui/             → Compose 页面、可复用组件与主题
├── util/           → 工具（AQI / WBGT 算法、天气工具）
├── viewmodel/      → StateFlow 状态管理 + 协程
└── widget/         → AppWidgetProvider 桌面小组件
```

## 🛠️ 技术栈

| 类别 | 技术 |
|------|------|
| **UI** | Jetpack Compose, Material 3, Canvas API, AndroidSVG（Meteocons & Lucide SVG） |
| **架构** | ViewModel, StateFlow, Coroutines |
| **网络** | Retrofit 2.11 + Moshi 1.15.1 (KSP codegen) + OkHttp Logging |
| **鉴权** | 和风天气 JWT（Ed25519），BouncyCastle 本机签名 |
| **定位** | Google Play Services Location + 全离线逆地理编码 |
| **存储** | Room + DataStore |
| **小组件** | AppWidgetProvider (RemoteViews) + WorkManager |
| **构建** | Gradle KTS, AGP 8.8.2, Kotlin 2.0.21, Java 17 |

## 🚀 快速开始

### 环境要求

- **Android Studio Ladybug** (2024.2.1) 或更高版本
- JDK 17
- 建议使用真机（GPS 定位需要）

### 构建运行

```bash
# 克隆仓库
git clone git@github.com:WhichPath/SkyPulse.git
cd SkyPulse

# 用 Android Studio 打开，同步 Gradle，运行到设备
```

> 💡 **提示：** 模拟器使用默认定位位置，建议使用真机以获得准确的 GPS 定位天气。

> 🔑 **Release 签名：** `assembleRelease` 需要在 `local.properties`（或同名环境变量）中配置 `RELEASE_*` 密钥库口令，详见 [`local.properties.example`](local.properties.example)。口令缺失时构建直接失败，绝不产出未签名 APK。

## ☁️ 天气 API

由 [和风天气 API](https://dev.qweather.com/) 提供数据，采用本机 JWT（Ed25519）鉴权：

```
GET weather/v1/current/{lat}/{lon}
GET weather/v1/hourly/{lat}/{lon}
GET weather/v1/daily/{lat}/{lon}
GET /v7/minutely/5m?location={lon},{lat}
GET weatheralert/v1/current/{lat}/{lon}
GET airquality/v1/current/{lat}/{lon}
GET geo/v2/city/lookup
```

> API Host 为账号专属地址（形如 `xxxxxx.xx.qweatherapi.com`），在 App 设置页运行时填写。凭据全部在本机生成与保存，包体内不内置任何密钥。

## 📦 下载

本项目的 APK **不通过 GitHub Releases 公开发布**。请使用 `./gradlew assembleRelease` 本地编译并私下分发，产物命名为 `ridgecast-v<versionName>.apk`。

## 🤝 参与贡献

欢迎参与贡献！你可以：

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m "feat: add amazing feature"`)
4. 推送到远程 (`git push origin feature/amazing-feature`)
5. 提交 Pull Request

## 📄 开源协议

本项目基于 MIT 协议开源。

---

<div align="center">

### ⭐ Star 趋势

[![Star History Chart](https://api.star-history.com/svg?repos=WhichPath/SkyPulse&type=Date)](https://star-history.com/#WhichPath/SkyPulse&Date)

如果觉得不错，请给个 ⭐ Star 支持一下！

</div>
