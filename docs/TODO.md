# RidgeCast 待办清单（2026-10-19 / v1.0.4）

> 本文件只记录待办，不代表已经排期或开始实现。
> 状态图例：⬜ 未开始 / 🟡 进行中 / ⏸ 等待外部条件 / 💬 待讨论 / ✅ 已完成
>
> 历史审计记录见 [`AUDIT_BACKLOG.md`](AUDIT_BACKLOG.md)。该文件第一批 1-21 项已完成，
> 第四批 22-25 项仍然有效，已在下文「B. 继承待办」中汇总。

---

## A. 液态玻璃（v1.0.4 后续）

### A1. 真实物理折射模型（替代经验参数）

- 状态：⬜
- 现状：`LiquidGlass` 使用 SDF + `lensCurve` + `depthEffect` / `dispersion` 的经验公式，
  视觉上接近 IT之家/QmDeve，但**不是严格的 3D 玻璃光学计算**。
- 目标：把玻璃当作有厚度的三维固体，用可解释的物理量驱动边缘折射：
  - 由玻璃厚度与倒角高度场（bevel height field）求表面法线；
  - 用 Snell 定律 `n1·sinθ1 = n2·sinθ2` 计算视线进入/离开玻璃的偏折；
  - 用 Fresnel 项（Schlick 近似即可）控制边缘反射/透射比例；
  - 用 Cauchy 色散 `n(λ) = A + B/λ²` 或近似波长采样产生 RGB 色散；
  - 通过射线与背景平面的交点得到采样 UV，而不是直接按 SDF 法线做固定 `refractionAmount` 位移。
- 说明：IT之家/QmDeve 的 AGSL 也是**物理启发式近似**，不是严格光线追踪；
  我们追求的是参数有物理含义、结果稳定，而不是拿“夸张参数”堆效果。
- 验收：同一套 IOR/厚度/倒角参数在不同尺寸卡片上表现一致；色散强度随波长与厚度可解释；
  不再依赖为每类卡片手工调 `refractionAmount`。

### A2. 移除或可选化等压线纹理

- 状态：⬜（用户倾向移除）
- 现状：`WeatherBackground.IsobarVeil` 是为平滑天气渐变补充的低频纹理，
  目的是让玻璃边缘的折射“有东西可弯”；它不是物理必需项。
- 待办：确认是否默认移除；若保留，建议做成开发者/设置开关，默认关闭。
- 注意：移除后，主页在纯渐变背景上的液态折射会明显更弱，这是预期现象。

### A3. 卡片“叠层滚动”（可选交互实验）

- 状态：💬 待讨论（非物理真实性的必要条件）
- 名词解释：当前主页是**单列纵向滚动**，卡片按顺序排布、互不重叠；
  所谓“叠层滚动”是指卡片在滚动时**互相覆盖/产生视差**，让上面一块玻璃从下面一块玻璃上滑过。
- 为什么现在不是：布局是普通 `Column + verticalScroll`，卡片没有重叠区域；
  每块玻璃只能采样它背后的页面背景，无法折射“另一块卡片”。
- 物理真实性与叠层滚动无关：即使没有叠层，A1 的 3D 折射模型仍然成立；
  叠层只是让折射更容易被看见、更有交互感。
- 待办：仅当明确想要“玻璃压玻璃”的视觉时再排期，属于交互/布局改造，不是材质修复。

### A4. 真实数据验证（和风天气）

- 状态：⏸ 等待外部条件
- 需要：QWeather `apiHost` / `keyId` / `projectId`（模拟器当前只有密钥对备份，没有完整凭据）。
- 待验证：AQI/AQHI 说明弹窗（当前演示数据空气为 `--`，无法触发）、真实天气主题下的玻璃染色、
  预警详情弹窗的归因展示。

### A5. 离屏玻璃卡片的性能优化

- 状态：⬜
- 现状：主页所有卡片（包括视口外）都会录制背景层并跑 blur + AGSL；
  这是为了保证滚动进入视口时立即可见。
- 已尝试：在 draw 阶段按 `positionInWindow` 跳过视口外卡片；
  但 `drawWithCache` 的结果被缓存，卡片滚入时不会自动重绘，导致六宫格玻璃背景消失，已回滚。
- 待办：需要结合 Lazy 布局/可见性状态/主动 `invalidateDraw` 的正确方案，
  或把玻璃背景改成按可见窗口统一绘制的共享层；不能再用简单的 draw 阶段 return。

---

## B. 继承待办（来自 v1.0.0 审计第四批）

> 来源：[`AUDIT_BACKLOG.md`](AUDIT_BACKLOG.md) #22-25。行号可能已漂移，以当前代码为准。

### B1. #22 UI 层城市刷新去重

- 状态：⬜（需复核优先级）
- 位置：`viewmodel/WeatherViewModel.kt` 的 `loadWeatherForCity` / `fetchWeatherForCity`。
- 现状：这两个入口仍未调用 `tryBeginUiRefresh`，没有走 `refreshingCityIds` 的 UI 层去重；
  但底层 `WeatherSyncManager.doRefreshWeather` 已有 5s 成功窗口 + 40s 尝试窗口去重，
  实际重复请求风险比原审计描述低。
- 待办：确认是否还有必要补 UI 层去重，避免和底层去重重复；若补，需处理 finally 释放。

### B2. #23 手动刷新缺少外层总超时

- 状态：⬜
- 位置：`sync/WeatherSyncManager.kt` 的 `refreshCurrentLocationManual`。
- 现状：定位有 7s 超时；取数 `doRefreshWeather` 内部有 40s 总预算（2 次重试，单次 12s）；
  但“定位 + 取数”没有统一的外层总超时，最坏仍可能约 47s。
- 待办：在手动刷新入口加一个统一 `withTimeoutOrNull`（例如 20-25s），
  超时后走缓存/失败 UI，避免用户长时间等待。

### B3. #24 Widget Provider / Worker 三份重复

- 状态：⬜
- 位置：`app/src/main/java/com/ridgecast/weather/widget/`。
- 现状：`WeatherWidgetProvider` / `WeatherWidgetMediumProvider` / `WeatherWidget4x2Provider`
  与三个对应 Worker 仍为三套近乎平行的实现（合计约 1700 行，含 `WeatherWidgetUpdater`）。
- 待办：抽公共基类或委托给 `WeatherWidgetUpdater`，只保留各尺寸的布局/尺寸差异；
  这是目前最大的冗余源，需专门一轮。

### B4. #25 核心同步管线零测试

- 状态：⬜
- 现状：`app/src/test` 共 16 个测试，覆盖 `RefreshPolicy`、`WidgetRefreshPolicy`、
  RemoteDataSource 等；`WeatherSyncManager` / `RefreshManager` 仍没有直接测试。
- 待办：至少补：
  - 5s 成功窗口与 40s 尝试窗口的去重行为；
  - 401/403 不重试、429 限流、超时/重试预算；
  - 并发 `refreshWeather` 的互斥与缓存复用。
- 说明：这是最关键的管线，优先级应高于 UI 细节。

---

## C. 已完成 / 已过时

- `AUDIT_BACKLOG.md` 第一批 1-21 项：✅ 已完成（安全、性能、文档清理）。
- `AUDIT_BACKLOG.md` 中提到的旧行号、旧包名、旧版本号：已过时，以当前代码/AGENTS.md 为准。
- v1.0.4 本轮已完成：背景采样液态玻璃、卡片内容与 shader 分离、投影、
  设置/城市搜索/WBGT 弹窗接入、灰矩形修复、uniform 创建顺序修复。
- 参考素材/逆向过程已归档到 `ref/`（gitignored）：`ref/ithome/`、`ref/qmdeve/`。

---

## D. v1.0.4 发布状态

- ✅ 本地 `assembleRelease` 已成功，产物：`ridgecast-v1.0.4.apk`
  （`versionCode=104`，`versionName=1.0.4`，已用本地 keystore 签名）。
- ✅ release APK 已在 Pixel_8_API_35 模拟器安装启动验证：无闪退，液态玻璃正常。
- ⬜ GitHub tag / push / Release 上传：未执行（本轮只要求本地提交与本地编译）。
- ⬜ `AGENTS.md` 的“当前发布状态”仍写 v1.0.2：等 v1.0.4 正式发布后再更新，避免把本地构建误写成已发布。
