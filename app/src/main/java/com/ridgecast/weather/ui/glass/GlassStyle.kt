package com.ridgecast.weather.ui.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃的视觉 token（v1.0.7 收口）。
 *
 * **设计意图**：所有玻璃面板共享同一套材质参数，**改一处即可全站生效**。
 * 之前 blur / tint / refraction 分散在 6 个调用点硬编码，新增面板时容易
 * 出现"看起来不一样"的历史遗留差异，现已全部收敛到本文件。
 *
 * 差异场景（次级页更实、浮窗更透）通过 [GlassStyles] 里的派生样式表达，
 * 不再由调用点自行拼装数值。
 *
 * ## 折射参数口径（对齐上游 Backdrop 官方文档）
 * `refractionHeight` = 折射带宽度 = 玻璃边缘倒角在屏幕上的投影宽度（"厚度"）；
 * `refractionAmount` = 贴边处背景被推开的最大位移量。
 * 物理上位移 ≈ 厚度 × tan(倒角倾角)，故 **2:1 约为 63° 陡倒角**（带透镜感，
 * 接近苹果液态玻璃；1:1 是纯 45° 物理倒角，>3:1 开始像鱼眼）。
 *
 * 上游硬约束（kyant.gitbook.io/backdrop · backdrop-effects）：
 * - `height` 必须 ≤ 形状的**最小圆角半径**，否则圆角处出现不连续伪影
 *   （本项目最小玻璃圆角 = `Radius.subCard` 16dp）；
 * - `amount` 必须 ≤ 面板短边。
 *
 * 官方教程取值：`blur(4dp) + lens(16dp, 32dp)`（即 amount = 2 × height）。
 * 本项目沿用 blur 4dp 与 2:1 比例，height 按各样式圆角上限收敛。
 *
 * ## 边缘存在感（v1.0.9 收敛）
 * 玻璃的「边缘」是 4 层叠加：折射带 + 高光描边 + 内投影 + 外投影。
 * v1.0.9 起统一收敛成**薄片**观感：折射带宽砍半、高光变细变柔且只留在迎光角、
 * 内投影（厚玻璃感的大头）收小；内/外投影此前硬编码在 `LiquidGlass.kt`，
 * 现已一并收口到本文件，改一处即全站生效。
 *
 * ⚠️ **描边宽度会被 ×2 放大**：vendored `HighlightModifier` 里
 * `strokeWidth = ceil(width.toPx()) * 2f`。420dpi 屏上 1dp → 2.625px → ceil 3 → **实际 6px**；
 * 且 ceil 有量化台阶（0.5dp → 4px，0.25dp → 2px，0 → 关闭）。
 * 调 `highlightWidth` 时务必按这个倍数换算，别被"我明明只写了 1dp"骗了。
 */
@Immutable
data class GlassStyle(
    /** 背景高斯模糊半径（Dp）。上游官方教程取 4dp。 */
    val blurRadius: Dp = 4.dp,
    /** 深色主题下的表面雾化（罩色）不透明度。 */
    val tintAlphaDark: Float = 0.20f,
    /** 浅色主题下的表面雾化（罩色）不透明度。 */
    val tintAlphaLight: Float = 0.12f,
    /** 折射层厚度（倒角投影宽度）：越大边缘弯曲带越宽。 */
    val refractionHeight: Dp = 7.dp,
    /** 折射位移量：越大边缘把背景掰得越狠。建议 = 2 × refractionHeight。 */
    val refractionAmount: Dp = 14.dp,
    /** 边缘高光描边宽度。 */
    val highlightWidth: Dp = 0.5.dp,
    /** 边缘高光羽化半径。 */
    val highlightBlurRadius: Dp = 1.2.dp,
    /** 边缘高光衰减锐度（越大越集中）。 */
    val highlightFalloff: Float = 1.9f,
    /** 边缘高光整体增益（1.0 = 标准）。 */
    val highlightAlphaScale: Float = 0.62f,
    /** 外投影半径（Dp）：面板离地感。 */
    val shadowRadius: Dp = 7.dp,
    /** 外投影垂直偏移（Dp）。 */
    val shadowOffsetY: Dp = 3.dp,
    /** 深色主题下的外投影不透明度。 */
    val shadowAlphaDark: Float = 0.28f,
    /** 浅色主题下的外投影不透明度。 */
    val shadowAlphaLight: Float = 0.13f,
    /** 内投影半径（Dp）：玻璃「有厚度」感的主要来源。 */
    val innerShadowRadius: Dp = 5.dp,
    /** 内投影垂直偏移（Dp）。 */
    val innerShadowOffsetY: Dp = 2.dp,
    /** 深色主题下的内投影不透明度。 */
    val innerShadowAlphaDark: Float = 0.08f,
    /** 浅色主题下的内投影不透明度。 */
    val innerShadowAlphaLight: Float = 0.05f
) {
    /** 按主题深浅取雾化不透明度。 */
    fun tintAlpha(isDark: Boolean): Float = if (isDark) tintAlphaDark else tintAlphaLight

    /** 按主题深浅取外投影不透明度。 */
    fun shadowAlpha(isDark: Boolean): Float = if (isDark) shadowAlphaDark else shadowAlphaLight

    /** 按主题深浅取内投影不透明度。 */
    fun innerShadowAlpha(isDark: Boolean): Float =
        if (isDark) innerShadowAlphaDark else innerShadowAlphaLight
}

/**
 * 全局玻璃样式。面板可用 `LocalGlassStyle provides ...` 局部覆盖。
 */
val LocalGlassStyle = compositionLocalOf { GlassStyles.Surface }

/**
 * 预置玻璃样式。
 *
 * - [Surface]：主页卡片（基准样式）。
 * - [Secondary]：次级页面（城市管理/预警详情/设置），背景是平涂底色，
 *   文字对比度要求更高，因此罩色更实。
 * - [Floating]：浮动说明弹窗，背后是整屏实时背景，折射更强、更通透。
 * - [Onboarding]：引导页，背景简单，折射收敛避免过度变形。
 */
object GlassStyles {
    val Surface = GlassStyle()

    val Secondary = GlassStyle(
        tintAlphaDark = 0.28f,
        tintAlphaLight = 0.22f
    )

    /**
     * 浮动说明弹窗：背后是整屏实时背景，折射比卡片强一档更通透，
     * 但**边缘存在感保持与卡片一致**（折射带宽按同一比例收敛，
     * 否则浮窗会变成一块突兀的厚玻璃）。
     */
    val Floating = GlassStyle(
        tintAlphaDark = 0.26f,
        tintAlphaLight = 0.20f,
        refractionHeight = 11.dp,
        refractionAmount = 22.dp
    )

    /** 引导页：背景简单，折射进一步收敛避免过度变形。 */
    val Onboarding = GlassStyle(
        tintAlphaDark = 0.20f,
        tintAlphaLight = 0.20f,
        refractionHeight = 6.dp,
        refractionAmount = 12.dp
    )

    /** 引导页主按钮：背景是明亮渐变，需要更实的罩色保证文字对比度。 */
    val OnboardingAction = GlassStyle(
        tintAlphaDark = 0.38f,
        tintAlphaLight = 0.38f,
        refractionHeight = 5.dp,
        refractionAmount = 10.dp
    )
}
