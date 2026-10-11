package com.skypulse.weather.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * SkyPulse 设计系统规范 (基于 Material 3 Expressive 演进)
 *
 * 结合 Android 16/17 规范与项目专属沉浸式天气视觉：
 * - 物理感知弹簧动效 (Physics-based Spring Motion)
 * - M3 10 级圆角尺度容器系统 (Corner Radius Scale)
 * - Surface Container 容器色阶
 * - 无障碍触控与标准呼吸间距 (Spacing & Insets)
 */
object SkyPulseDesignSystem {

    object Colors {
        val settingsBackground = IosSettingsBg
        val settingsSurface = IosCardBg
        val settingsDivider = IosDividerColor

        // M3 Surface Container 角色别名
        val surfaceContainerLow = IosSettingsBg
        val surfaceContainer = IosCardBg
        val outlineVariant = IosDividerColor
    }

    /**
     * M3 10 级圆角尺度系统 (Corner Radius Scale)
     */
    object Radius {
        val none = 0.dp
        val extraSmall = 4.dp
        val small = 8.dp
        val medium = 12.dp
        val large = 16.dp
        val largeIncreased = 20.dp
        val extraLarge = 28.dp            // M3 Dialog & Modal Sheet 规范基准
        val extraLargeIncreased = 32.dp
        val extraExtraLarge = 48.dp
        val full = 50.dp                  // 胶囊 / 药丸

        // 业务组件语义映射
        val card = 20.dp
        val cityCard = 18.dp
        val settingsCard = 16.dp
        val pill = 50.dp
        val modal = 28.dp                 // 浮动毛玻璃面板基准
    }

    object Border {
        val hairline = 0.5.dp
        val thin = 1.dp
    }

    object Spacing {
        val screenHorizontal = 16.dp
        val homeHorizontal = 20.dp
        val sectionGap = 8.dp
        val contentGap = 12.dp

        // M3 对话框与屏幕边缘呼吸安全裕量
        val modalHorizontalMargin = 24.dp
        val modalVerticalMargin = 68.dp
    }

    object TypographyScale {
        val temperature = 100.sp
        val temperatureDegree = 48.sp
    }

    /**
     * Material 3 Expressive 物理动效系统 (Spring-based Motion)
     */
    object Motion {
        // 1. 空间变换（位移、缩放、卡片展开）：轻微弹性过冲，响应灵敏
        val springSpatialExpressive = spring<Float>(
            dampingRatio = 0.82f,
            stiffness = 380f
        )
        val springSpatialIntOffset = spring<IntOffset>(
            dampingRatio = 0.86f,
            stiffness = 420f
        )
        val springSpatialExit = spring<IntOffset>(
            dampingRatio = 0.90f,
            stiffness = 460f
        )

        // 2. 视觉效果（透明度淡入淡出、硬件高斯模糊平滑插值）：无回弹直接收敛
        val springEffects = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
        val springEffectsDp = spring<Dp>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )

        // 3. 模态面板入场/退场专用物理弹簧
        val springModalEnter = spring<Float>(
            dampingRatio = 0.82f,
            stiffness = 400f
        )
        val springModalExit = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumHigh
        )

        // 时间常量（过渡兼容）
        const val fastMillis = 200
        const val cardEnterMillis = 600
        const val heroEnterMillis = 800
        const val cardEnterDelayMillis = 300
        const val lifecycleSkipMillis = 1200L
    }

    object TouchTarget {
        val default = 48.dp
        val listRow = 52.dp
    }
}
