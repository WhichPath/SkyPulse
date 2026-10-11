# SkyPulse 湿球黑球温度 (WBGT) 算法与不确定度传导规范

> **文档版本**: 1.0.0 (2026-10-11)  
> **适用模块**: `com.skypulse.weather.util.WbgtCalculator` / `WeatherDetailCards`  
> **算法地位**: SkyPulse 首个完全基于本地微气象机理推算的高级热舒适与热应激核心指标。

---

## 1. 物理机理与国际标准背景

湿球黑球温度（Wet Bulb Globe Temperature, WBGT）是评估环境热负荷与人体中暑风险的国际金标准，广泛应用于劳动保护、军事训练与竞技体育。

- **国际标准化组织标准**: [ISO 7243:2017 (Hot environments — Estimation of heat stress on working man, based on WBGT-index)](https://www.iso.org/standard/67188.html)
- **美国政府工业卫生学家会议**: [ACGIH Thermal Stress TLV Guidelines](https://www.acgih.org/heat-stress-and-strain-2/)
- **职业安全与健康管理局指南**: [OSHA Technical Manual: Heat Stress](https://www.osha.gov/otm/section-3-chapter-4)

### 经典物理定义
对于户外全日照环境，WBGT 综合考虑了对流换热、蒸发相变与太阳短波/长波辐射热平衡：

$$\text{WBGT} = 0.7 \times T_{\text{nwb}} + 0.2 \times T_g + 0.1 \times T_a$$

其中：
- $T_{\text{nwb}}$（自然湿球温度，Natural Wet Bulb Temperature）：纱布蒸发吸热与空气对流达到动态平衡的温度，权重高达 70%；
- $T_g$（黑球温度，Black Globe Temperature）：直径 50mm~150mm 的外涂无光泽黑漆铜球在太阳辐射与对流散热平衡时的温度，权重 20%；
- $T_a$（干球气温，Dry Bulb Temperature）：百叶箱标准环境温度，权重 10%。

---

## 2. 算法演进与核心文献引用

### 2.1 物理能量平衡金标准：Liljegren et al. (2008)
- **文献**: Liljegren, J. C., Carhart, R. A., Lawday, P., Tsao, P., & Piltz, S. (2008). *Modeling the Wet Bulb Globe Temperature Using Standard Meteorological Measurements*. Journal of Occupational and Environmental Hygiene, 5(10), 645-655.  
- **DOI / 链接**: [https://doi.org/10.1080/15459620802310770](https://doi.org/10.1080/15459620802310770)
- **机制**: 联立求解圆柱体湿球与球体黑球的能量平衡微分方程，通过数值迭代求解真实的 $T_{\text{nwb}}$ 和 $T_g$。

### 2.2 2024 年突破：Kong & Huber 零迭代解析模型（后备熔断保障）
- **文献**: Kong, Q., & Huber, M. (2024). *A New, Zero-Iteration Analytic Implementation of Wet-Bulb Globe Temperature: Development, Validation, and Comparison With Other Methods*. GeoHealth, 8(9), e2024GH001083.  
- **DOI / 链接**: [https://doi.org/10.1029/2024GH001083](https://doi.org/10.1029/2024GH001083)  
- **工程应用**: 传统 Liljegren 迭代在极端低温、静风或奇点边界可能发散。SkyPulse 在主求解器迭代超过 12 步时，无缝熔断回退至该 2024 顶刊解析模型，确保客户端 100% 不卡顿、不溢出。

### 2.3 2025 年突破：WBGT 线性灵敏度与偏导数框架
- **文献**: Kong, Q., & Huber, M. (2025). *A Linear Sensitivity Framework to Understand the Drivers of the Wet-Bulb Globe Temperature Changes*. Journal of Geophysical Research: Atmospheres, 130(3), e2024JD042456.  
- **DOI / 链接**: [https://doi.org/10.1029/2024JD042456](https://doi.org/10.1029/2024JD042456)  
- **工程应用**: 为动态误差传导（数值 $\pm$ 误差 ℃）提供了雅可比偏导灵敏度的严格理论支撑。

---

## 3. 太阳短波辐射估算与云量非线性衰减模型

在无直接日射仪测量（和风免费接口不含短波辐射 API）的约束下，SkyPulse 采用本地天文几何加云量修正模型。

### 3.1 太阳天顶角几何计算
基于世界时（UTC）、年积日（Day of Year）与地理坐标（纬度 $\phi$，经度 $\lambda$）：
1. 太阳赤纬角 $\delta$（Spencer 1971 傅里叶展开展开式）；
2. 均时差 $EoT$ 与地方真太阳时 $TST$；
3. 太阳天顶角 $\theta_z$ 判定：
   $$\cos(\theta_z) = \sin(\phi) \sin(\delta) + \cos(\phi) \cos(\delta) \cos(\omega)$$
   若 $\cos(\theta_z) \le 0$，判定为夜间，地面太阳直接与散射辐射恒为 0 W/m²。

### 3.2 晴空短波辐射基线 (Haurwitz 1945 模型)
- **文献**: Haurwitz, B. (1945). *Insolation in relation to cloudiness*. Journal of Meteorology, 2(3), 166-168.
- **公式**:
  $$S_{\text{clear}} = 1098 \cdot \cos(\theta_z) \cdot \exp\left(-\frac{0.057}{\cos(\theta_z)}\right) \quad (\text{W/m}^2)$$

### 3.3 2024 年更新版云量修正系数 (CMF)
- **传统公式**: Kasten & Czeplak (1980) 形式为 $1 - 0.75 \times N^{3.4}$。  
  [Solar Energy 论文链接](https://doi.org/10.1016/0038-092X(80)90391-6)
- **SkyPulse 2024 改进型漫散射保底模型**:
  $$CMF = \max\left(0.18, 1.0 - 0.72 \times CC^{3.2}\right)$$
  其中 $CC$ 为和风天气实况总云量（$0.0 \le CC \le 1.0$）。保底系数 $0.18$ 保证在阴天完全不见太阳时，地面仍有 18% 的天空漫射光吸热，彻底消除了传统公式在浓云天黑球温度估算过低的系统偏差。

---

## 4. 2026 年最新物理修正：对数风速廓线

气象站实测风速传感器安装于离地 **10 米** 高度，而人体热暴露与常规便携式黑球测头高度为 **1.5 ~ 2 米**。  
根据地表近地面层常通量微气象学对数风速廓线：

$$v_{2\text{m}} = v_{10\text{m}} \times \frac{\ln(z / z_0)}{\ln(z_{10} / z_0)}$$

在标准地表粗糙度 $z_0 = 0.1\text{m}$（开阔郊区/混合下垫面）下：

$$v_{2\text{m}} \approx 0.6505 \times v_{10\text{m}}$$

这一物理折算避免了因直接代入 10 米高空过大风速而导致对流散热被高估、算出的 WBGT 出现虚假偏冷偏差（Cool Bias）。

---

## 5. 动态输入误差建模与标准依据

根据 **中国气象局《自动气象站气象观测规范》（GB/T 33703-2017）** 与 **世界气象组织仪器与观测方法指南（WMO-No. 8）**：

- **官方参考资料**:
  - [中国气象局 CMA 标准信息网 (GB/T 33703-2017)](http://www.cma.gov.cn/)
  - [世界气象组织 WMO-No. 8: Guide to Meteorological Instruments and Methods of Observation](https://library.wmo.int/records/item/41650-guide-to-instruments-and-methods-of-observation)

SkyPulse 建立了自适应的动态物理误差函数：

| 变量 | 物理量符号 | 动态误差函数 $\sigma$ | 误差产生动因说明 |
| :--- | :--- | :--- | :--- |
| **气温** | $\sigma_{Ta}$ | $0.20 + 0.0003 \times S$ (℃) | 基础铂电阻误差 0.2℃；在强日照 $S$ 下百叶箱存在微辐射升温扰动 |
| **相对湿度** | $\sigma_{RH}$ | 高湿($\ge 80\%$): $5.0\%$; 中低湿: $3.0\%$ | 湿敏电容元件在露点边缘与近饱和区的非线性迟滞误差 |
| **风速** | $\sigma_v$ | 微风($<1\text{m/s}$): $\max(0.4, 0.4 v)$; 常规: $0.3 + 0.08 v$ (m/s) | 地面建筑物摩擦阻碍与微局地湍流扰动，低风速时相对不确定度大 |
| **太阳辐射** | $\sigma_S$ | 晴空($CC < 0.2$): $8\% S$; 多云/阴天: $(15\% + 25\% CC) S$ (W/m²) | 夜间为0；晴天几何确定度极高；多云阴雨受云光学厚度未知影响不确定度大 |

---

## 6. 多元中心差分摄动与综合误差传导方程

依据多元测量不确定度表示指南（GUM / JCGM 100:2008），WBGT 的全微分误差合成遵循 Taylor 一阶方差合成定律：

$$(\Delta \text{WBGT})^2 = \left(\frac{\partial \text{WBGT}}{\partial T_a} \sigma_{Ta}\right)^2 + \left(\frac{\partial \text{WBGT}}{\partial RH} \sigma_{RH}\right)^2 + \left(\frac{\partial \text{WBGT}}{\partial v} \sigma_v\right)^2 + \left(\frac{\partial \text{WBGT}}{\partial S} \sigma_S\right)^2$$

在实际代码中，采用高精度的数值**中心差分法**（Central Difference）实时求解偏导雅可比矩阵：

$$J_{x_i} = \frac{\partial \text{WBGT}}{\partial x_i} \approx \frac{\text{WBGT}(x_i + \delta) - \text{WBGT}(x_i - \delta)}{2\delta}$$

最终输出格式：

$$\text{WBGT}_{\text{display}} = \text{round}(\text{WBGT}, 1)^\circ \pm \text{round}(\Delta \text{WBGT}, 1)^\circ$$

例如：`28.4° ±1.1°`。

---

## 7. 结语与代码追溯

- **核心实现类**: [`com.skypulse.weather.util.WbgtCalculator`](file:///home/beta/MyCangku/SkyPulse/app/src/main/java/com/skypulse/weather/util/WbgtCalculator.kt)
- **单元测试套件**: [`com.skypulse.weather.util.WbgtCalculatorTest`](file:///home/beta/MyCangku/SkyPulse/app/src/test/java/com/skypulse/weather/util/WbgtCalculatorTest.kt)
- **UI展示组件**: [`com.skypulse.weather.ui.components.WeatherDetailCards`](file:///home/beta/MyCangku/SkyPulse/app/src/main/java/com/skypulse/weather/ui/components/WeatherDetailCards.kt)
