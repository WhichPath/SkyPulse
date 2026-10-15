# SkyPulse v4.0.2 改造进度与待续记录

## 一、已完成部分 (Completed)

### 1. 2026 液态玻璃（Liquid Glass）+ ColorOS 16 光场 UI 体系

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

### 3. 版本号

- `versionCode = 1007` / `versionName = "4.0.2"`。

---

## 二、待实际设备验证的项 (To verify on device)

AGSL 着色器在设备上即时编译，**编译期无法校验**，需真机确认：

1. 首页玻璃卡片是否出现天顶高光边与边缘色散，是否过亮/过暗；
2. 次级页（设置/城市管理/预警详情）分组卡片光场强度是否合适（当前刻意压得很轻）；
3. 说明弹窗的动态相位是否流畅（12fps 限频）；
4. 背景暗角是否过重；
5. 设置页密钥分区：填入自有密钥 → 测试连接 → 保存 → 回主页下拉刷新是否正常；
   清除后是否正确回退到编译内置凭据。

若真机上着色器被驱动拒绝，`FileLogger`（TAG `LiquidGlass`）会记录 `AGSL init failed`，
UI 自动降级为渐变材质，功能不受影响。