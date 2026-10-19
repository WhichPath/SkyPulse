package com.ridgecast.weather.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * SkyPulse 统一液态玻璃表面（v1.0.5）。
 *
 * 视觉核心来自 Kyant0/AndroidLiquidGlass（Backdrop，Apache-2.0）：
 * backdrop 图层 → vibrancy/模糊 → SDF 折射 + 色散 → 高光/内外阴影 → 罩色。
 *
 * 关键约束：
 * 1. 只消费 [LocalGlassBackdrop] 提供的**实时背景图层**，绝不把卡片内容
 *    或背景快照塞进 shader；
 * 2. 面板移动时由 Backdrop 的 LayoutCoordinates 重新对齐采样，背景不会
 *    “粘”在面板上；
 * 3. 统一使用 `RoundedCornerShape`，与库的 rounded-rect SDF 一致，
 *    避免超椭圆裁剪与圆弧 shader 不匹配产生的黑角/缺角；
 * 4. 无背景源或省电模式时降级为半透明表面，不闪退、不消失。
 */
@Composable
fun Modifier.liquidGlass(
    radius: Dp,
    isDark: Boolean,
    backgroundColor: Color = if (isDark) Color(0xFF101214) else Color.White,
    tintAlpha: Float = if (isDark) 0.16f else 0.10f,
    blurRadius: Dp = 8.dp,
    refractionHeight: Dp = 18.dp,
    refractionAmount: Dp = 22.dp,
    backdrop: Backdrop? = LocalGlassBackdrop.current
): Modifier {
    val shape = RoundedCornerShape(radius)
    val tint = backgroundColor.copy(alpha = tintAlpha)
    val powerSave = rememberPowerSaveMode()
    val light = LocalGlassLight.current

    if (backdrop == null || powerSave) {
        return this
            .clip(shape)
            .background(tint)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (isDark) 0.16f else 0.30f),
                shape = shape
            )
    }

    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.toPx())
            lens(
                refractionHeight = refractionHeight.toPx(),
                refractionAmount = refractionAmount.toPx(),
                depthEffect = true,
                chromaticAberration = false
            )
        },
        highlight = {
            Highlight(
                width = 1.dp,
                blurRadius = 0.5.dp,
                alpha = (if (isDark) 0.58f else 0.82f) * light.intensity,
                style = HighlightStyle.Default(
                    color = if (light.isDaylight) Color.White else Color(0xFFD6E4FF),
                    angle = light.angleDegrees,
                    falloff = 1.2f
                )
            )
        },
        shadow = {
            Shadow(
                radius = 7.dp,
                offset = DpOffset(0.dp, 3.dp),
                color = Color.Black.copy(alpha = if (isDark) 0.28f else 0.13f)
            )
        },
        innerShadow = {
            InnerShadow(
                radius = 8.dp,
                offset = DpOffset(0.dp, 2.dp),
                color = Color.Black.copy(alpha = if (isDark) 0.14f else 0.05f)
            )
        },
    ).clip(shape)
}
