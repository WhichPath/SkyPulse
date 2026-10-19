# 审计待办清单（2026-10-16）

> 2026-10-19 更新：v1.0.4 起的当前待办已汇总到 [`TODO.md`](TODO.md)。
> 本文件保留 v1.0.0 审计的原始记录与行号；第四批 #22-25 仍未完成，已在 TODO.md 中继承。
>
> 来源：对 RidgeCast（脊线天气）v1.0.0 工作区的系统性审计（4 个只读子代理 + 人工复核）。
> 所有条目均已亲自复核确认，未采信未验证的说法。
> 状态图例：⬜ 未开始 / 🟡 进行中 / ✅ 已完成

---

## 第一批：安全与正确性（低风险、高收益）

| # | 严重度 | 位置 | 问题 | 状态 |
|---|---|---|---|---|
| 1 | 🔴 HIGH | `data/LocationRequestCoordinator.kt:148-168` | `force=true` 覆盖 `pendingRequest` 时未 complete 旧 deferred，已合并的等待者永久 `await()` 并持有 `locationMutex`，导致整个刷新管线挂死 | ✅ |
| 2 | 🔴 HIGH | `app/build.gradle.kts:62-71` | 口令缺失时仅 `logger.warn` + `signingConfig=null`，静默产出未签名 APK（注释却写「立即失败」） | ✅ |
| 3 | 🔴 HIGH | `ui/screen/WeatherScreen.kt:679-681` | 3 个 `Regex(...)` 在 `WeatherContentBody` 体内，每次重组 ×N 条预警都重新编译 | ✅ |
| 4 | 🔴 HIGH | `ui/components/HourlyForecast.kt:473-539` | 逐小时图表每次 draw 重建全部 240 点几何 + 颜色表（窗口化只作用于绘制循环） | ✅ |
| 5 | 🟡 MED | `sync/RefreshManager.kt:56` | `isSyncing` 检查在锁外、锁内不复查 → 并发请求绕过 120s 限流，重复同步 | ✅ |
| 6 | 🟡 MED | `sync/WeatherSyncManager.kt:125` | 5s 去重只覆盖「5s 内成功」的首次请求；慢请求(>5s)/失败请求的排队第二个调用会重复发 API（白烧额度） | ✅ |
| 7 | 🟡 MED | `sync/WeatherSyncManager.kt:671` | `fetchWithRetry` 对 401/403 也重试 3 次 → 3× 额度浪费（只短路了 429） | ✅ |
| 8 | 🟡 MED | `AGENTS.md:12,14` | 仍留着旧 keystore 口令 `weather123` | ✅ |
| 9 | 🟡 MED | `AndroidManifest.xml:9,11,13,14` | 4 个权限从未使用：`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`、`CHANGE_WIFI_STATE`、`FOREGROUND_SERVICE_LOCATION`（无任何 `<service>`） | ✅ |
| 10 | 🟡 MED | `build.gradle.kts:133,137` + `libs.versions.toml:44` | 死依赖：`accompanist-permissions`（已归档）、`androidx.browser`、`material-icons-extended`（仅 catalog 声明） | ✅ |
| 11 | 🟡 MED | `res/xml/network_security_config.xml` | 给 `api.tianditu.gov.cn` 开 cleartext，但天地图早已不用（离线地理编码取代） | ✅ |
| 12 | 🟡 MED | `AndroidManifest.xml:17` | `allowBackup=true` 且无提取规则，凭据存储可被 adb backup 带走 | ✅ |

## 第二批：性能

| # | 严重度 | 位置 | 问题 | 状态 |
|---|---|---|---|---|
| 13 | 🟡 MED | `ui/screen/RadarMapScreen.kt:204-209` | WebView `allowFileAccess/allowContentAccess=true` + 混合内容 → 注入 JS 可读本沙箱文件（含和风私钥） | ✅ |
| 14 | 🟡 MED | `proguard-rules.pro:67` | `-keep class android.util.Log { *; }` 让 R8 永远无法删日志；叠加整库 keep，日志会进 release 包 | ✅ |
| 15 | 🟡 MED | `ui/components/HourlyForecast.kt:689,722` | 每格重复 `extractHour`（含 `Calendar.getInstance()`）+ 天文窗口线性扫描 | ✅ |
| 16 | 🟡 MED | `ui/components/WeatherEffectOverlay.kt:209` | 有风时每个雨滴每帧 new 一个 `withTransform` lambda（~12k/s） | ✅ |
| 17 | 🟡 MED | `ui/components/HourlyForecast.kt:307` | 快速滑动每帧 cancel/relaunch 协程（只为设同一个 `fast=true`） | ✅ |
| 18 | 🟡 MED | `ui/glass/GlassLightField.kt:128` | 每卡每次 draw 建 3 个渐变 Brush | ✅ |

## 第三批：文档与清理

| # | 严重度 | 位置 | 问题 | 状态 |
|---|---|---|---|---|
| 19 | 🟡 MED | `README.md` | 严重过时：版本 1.8.96、Min SDK 26、48h 逐小时、15 天预报、AMAP 逆地理、指向 GitHub Releases 的下载链接（AGENTS.md 明令禁止公开发 Release） | ✅ |
| 20 | 🟡 MED | `local.properties.example` | 完全没写 `RELEASE_STORE_FILE/PASSWORD/ALIAS`，新人不知道如何签名；且仍引用旧包名 | ✅ |
| 21 | 🟡 MED | `model/WeatherResponse.kt:91,386-389` + `QWeatherModels.kt` | 死字段/死类约 86 行可安全删（`dew_point`、`carWashing/dressing/coldRisk`、`LifeIndex.comfort`、14 个无消费者 QWeather 模型类） | ✅ |

## 第四批：大重构（需专门一轮，本次不做）

| # | 严重度 | 位置 | 问题 | 状态 |
|---|---|---|---|---|
| 22 | 🟡 MED | `viewmodel/WeatherViewModel.kt:293,540` | `loadWeatherForCity`/`fetchWeatherForCity` 绕过 `refreshingCityIds` 去重 | ⬜ |
| 23 | 🟡 MED | `sync/WeatherSyncManager.kt:278` | 手动刷新无总超时（定位 7s + 取数 40s ≈ 47s） | ⬜ |
| 24 | 🟡 MED | `widget/` | Provider 与 Worker 各三份近乎逐字符复制（~600 行），是最大冗余源 | ⬜ |
| 25 | 🟡 MED | 测试 | 16 个测试没有一个覆盖 `WeatherSyncManager`/`RefreshManager`——最关键的管线零测试 | ⬜ |

---

## 已同步处理的文档整理（非代码）

- 归档：`docs/V4_0_1_PROGRESS.md`、`docs/V4_0_3_PROGRESS.md` → `docs/archive/`（历史进度记录，根因已由 AGENTS.md 覆盖）
- 修正：`docs/WBGT_ALGORITHM_SPECIFICATION.md` 中 `com.skypulse.weather` → `com.ridgecast.weather`
- 删除：`screenshots/`（gitignored 临时产物，无引用）
