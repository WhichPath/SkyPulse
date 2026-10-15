# SkyPulse v4.0.3 改造进度与待续记录

## 零、v4.0.3 紧急修复：玻璃面塌成黑板（v4.0.2 真机实测发现）

### 现象

v4.0.2 真机实测：所有玻璃区块**整块发黑**；设置页因浅色主题文字是深色，落在黑板上后
**完全无法阅读**。首页天气背景未正常作用到玻璃上，呈现"玻璃无光"。

### 根因（两个独立但叠加的问题）

1. **光场被当成了玻璃的「基础填充层」**。v4.0.2 用 AGSL `RuntimeShader` 直接铺满卡片区域，
   一旦着色器没有按预期渲染（驱动拒绝、uniform 语义偏差），
   这一层就从"加光"变成"铺底"，所有玻璃面直接塌成不透明黑板。
   更根本的是：**AGSL 在设备上即时编译，CI 根本无法校验**，
   一个不可验证的层被放在了决定成败的关键路径上。
2. **次级页根本没有天气颜色可传**。设置/城市/预警三页背景是写死的纯色 `#F2F2F7`，
   卡片下方没有任何天气色相，玻璃自然"无光"。

### 修复

- **移除 AGSL 渲染器**，改用 Compose 原生 Brush 光场（`ui/glass/GlassLightField.kt`）：
  - 显式构造圆角矩形 `Path` 并用 `drawPath` 分层绘制；
  - 四层：带色底色（上亮下暗层理）、天顶光晕、各向异性斜向光泽、边缘折射渐变描边 + 内描边；
  - 全部在 `drawWithCache` 内一次性构造并缓存，静态卡片零逐帧开销。
- **保证每个玻璃面都有显式颜色**：所有光场派生函数都必须给出带色底色，
  玻璃不会再出现"无色 = 灰黑"的退化态。
- **次级页背景改为天气着色**（`weatherPageBackground`）：色相取自当前天气渐变，
  浅色模式向白靠拢 0.86、深色模式向黑靠拢 0.52，两种模式都保持充足文字对比度
  （浅色约 14:1，深色约 11:1），既看得出天气又不牺牲可读性。
- **次级页根容器与 AppBar 改为透明**：原先 `.background(page.background)` 会盖住渐变，
  必须透传才能让卡片下方的天气色真正透上来。

### 踩坑记录

- `Dp.toPx()` 没有 receiver 版本；`DrawScope` 本身实现 `Density`，`scope.density` 就是缩放系数 `Float`；
- `DrawScope.drawOutline` **不是** `DrawScope` 接口成员，需自行构造 `Path`；
- `Brush.radialGradient` 的 center/radius 是**像素**坐标，用归一化值会让几乎整屏落到暗色端。

---

## 一、v4.0.2 已完成部分（AGSL 版，已被 v4.0.3 取代）

### 1. 2026 液态玻璃（Liquid Glass）UI 体系（已被 v4.0.3 的 Brush 光场取代）

- **光场引擎** (`ui/glass/LiquidGlass.kt`)
  - 基于 Android 14+ 原生 AGSL (`RuntimeShader`) 实现玻璃「光」，通过 `drawBehind` + 原生 `Paint` 绘制。
    刻意**不使用** `ShaderBrush`：自行控制全部 uniform 写入，避免框架隐式注入 uniform 名称带来的不确定性。
  - 四层光场叠加：
    1. **各向异性天顶斜面高光** — 由圆角矩形 SDF 梯度求外法线，与天顶光方向点乘得到斜面朝向度，
       切向水平分量驱动各向异性拉伸（`0.40 + 1.35 * |tangent.x|`），形成宽而扁的高光带；
    2. **圆角边缘折射微光** — 沿边缘做 RGB 三相位分离的低频值噪声色散，随时间缓慢流动；
    3. **动态亮度分层** — 上亮下暗的纵向层理 + 低频呼吸，按压时整体内缩压暗（阻尼流体反馈）；
    4. **透镜中心柔光 + 1px 锐利内描边** — 塑造玻璃厚度感。
  - **降级策略**：着色器创建或绘制异常时捕获并降级为等价线性渐变罩色，不崩溃、不出现空白卡片。
  - 圆角半径在 Kotlin 侧钳制到 `min(w, h) / 2`，规避 SDF 在超大圆角下的失效。

- **光场参数自动派生** (`ui/glass/LiquidGlassTokens.kt`)
  - `WeatherTheme.homeGlassLightField()` — 主页玻璃卡片，边缘光由天气渐变地平线色向白提亮得出；
  - `SecondaryPageColors.surfaceLightField()` — 次级页分组卡片，浅/深色各一套极轻光场；
  - `AtmosphereLightField` — 全屏背景氛围层。

- **接入点**
  | 位置 | 做法 |
  |------|------|
  | 主页玻璃卡片 | `GlassCard` 在 `clip(shape)` **之后**叠加光场（着色器自带的 SDF 裁剪必须与 clip 形状一致，高光边才能严丝合缝） |
  | 次级页面 | 新增 `Modifier.secondaryGlassSurface(shape, radius)`，替换原先的 `clip + background` |
  | 说明类弹窗 | 新增 `Modifier.floatingGlassSurface(...)`，替换原先的 `Brush.verticalGradient + border` |
  | 天气背景 | `WeatherBackground` 新增「天顶受光 + 地平线反光 + 四周暗角」三层静态氛围 |

- **性能约束**
  - 静态卡片 `phase` 固定为 0，零重绘开销；
  - 仅说明弹窗启用动态相位，且限频 12fps（`rememberLiquidGlassPhase`），避免整页 60fps 重绘；
  - 背景氛围层为静态画笔，不参与逐帧重绘。
  - 暗角 Brush 在 `drawBehind` 内按实际尺寸构造——`Brush.radialGradient` 的 center/radius 是**像素**坐标，
    用归一化值会让几乎整屏落到暗色端。

### 2. 和风天气密钥与编译解耦（运行时凭据）

- **两级解析**：用户设置页填写 →（回退）编译期 BuildConfig 注入。
  新增 `QWeatherCredentialStore`，凭据仅存应用私有 SharedPreferences，不随构建产物分发。
- **凭据指纹**：`QWeatherCredentials.fingerprint` 变化时 JWT 缓存立即失效重签，用户换密钥**无需重启进程**。
- **动态 Host**：`QWeatherApiProvider` 按 Host 缓存 Retrofit/API 实例（baseUrl 构建后不可变），
  用户切换 API Host 后下次刷新即生效；连通性测试用一次性实例，不污染正式链路。
- **连通性测试**：`QWeatherCredentialTester` 用候选凭据请求北京实况（覆盖 Host 可达 / JWT 签名 / ID 匹配 / 公钥上传全链路），
  区分 401 / 403 / 404 / DNS / 超时 / TLS 给出中文原因；不吞 `CancellationException`。
- **设置页**：新增折叠式「和风天气 API」分区，含来源指示（用户填写 / 编译内置 / 未配置）、
  私钥显示切换、保存 / 测试连接 / 清除。
  私钥输入刻意使用 `remember` 而非 `rememberSaveable`——SavedState 会被序列化进系统进程并落盘，不应让密钥随之持久化。
- **构建隔离**：CI 的 `local.properties` 生成本就容忍 secrets 为空，因此**删除仓库 secrets 后仍可编译发布**，
  包体不含任何密钥，用户在设置页自填即可；同时保留 secrets 时编译注入行为不变。

### 3. 收尾：清除会员体系遗留死代码

- `WeatherScreen.kt` 中恒为 `true` 的 `isPremium` 及全部 `isPremium && ...` 门控已移除，
  `effectiveCities` 直接等于全部已存城市，`WeatherContentBody` 去掉多余参数。
  至此仓库内不再有 `MembershipRepository` / `CheckUpdateUseCase` / `isPremium` 残留。

### 4. 版本号

- v4.0.2：`versionCode = 1007` / `versionName = "4.0.2"`；v4.0.3：`versionCode = 1008` / `versionName = "4.0.3"`。

---

## 二、待实际设备验证的项 (To verify on device)

1. **首要**：玻璃面**必须带颜色**，不能再出现任何黑板；设置页深色文字必须清晰可读；
2. 首页玻璃卡片的天顶光晕与边缘高光是否过亮/过暗（当前底色 = `GlassColors.tint`，
   顶部再提亮 0.18）；
3. 次级页背景的天气着色是否够淡——浅色模式若仍偏深会压低文字对比度，
   必要时调高 `weatherPageBackground` 的浅色 `amount`；
4. 卡片 alpha 是否合适：浅色 0.58 / 深色 0.30，过低会与页面底色糊成一片，过高会失去通透感；
5. 说明弹窗的玻璃通透度与边缘折射高光；
6. 背景暗角是否过重（浅色 0.10 / 深色 0.22）；
7. 设置页密钥分区：填入自有密钥 → 测试连接 → 保存 → 回主页下拉刷新是否正常；
   清除后是否正确回退到编译内置凭据。

> 下一版若要恢复更强的液态玻璃质感（色散、按压阻尼流体反馈），
> 建议**叠加式**引入（即在 Brush 光场之上再加一层），绝不可再让不可验证的着色器承担基础填充。