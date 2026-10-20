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
import com.ridgecast.weather.ui.glass.GlassStyle
import com.ridgecast.weather.ui.glass.LocalGlassStyle

/**
 * SkyPulse 统一液态玻璃表面（v1.0.7）。
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
 *
 * ## 材质参数全部来自 [GlassStyle]
 * 模糊半径、雾化浓度、折射厚度、高光宽度/衰减都不再由调用点硬编码，
 * 改 [LocalGlassStyle] 提供的样式即可全站生效。
 *
 * ## 边缘高光
 * 最多两个光源（太阳 + 月亮）同时参与，各自拥有独立的角度、颜色与强度，
 * 由 [LocalGlassLight] 提供；**没有任何光源时直接不绘制高光**（黑夜里
 * 没有光就不该有反光）。
 */
@Composable
fun Modifier.liquidGlass(
    radius: Dp,
    isDark: Boolean,
    backgroundColor: Color = if (isDark) Color(0xFF101214) else Color.White,
    style: GlassStyle = LocalGlassStyle.current,
    backdrop: Backdrop? = LocalGlassBackdrop.current
): Modifier {
    val shape = RoundedCornerShape(radius)
    val tint = backgroundColor.copy(alpha = style.tintAlpha(isDark))
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
            blur(style.blurRadius.toPx())
            lens(
                refractionHeight = style.refractionHeight.toPx(),
                refractionAmount = style.refractionAmount.toPx(),
                depthEffect = true,
                chromaticAberration = false
            )
        },
        highlight = {
            val primary = light.sun ?: light.moon
            if (primary == null) {
                // 太阳、月亮都不在天空里 → 没有光源就没有边缘高光
                null
            } else {
                val secondary = if (light.sun != null && light.moon != null) light.moon else null
                Highlight(
                    width = style.highlightWidth,
                    blurRadius = style.highlightBlurRadius,
                    alpha = style.highlightAlphaScale * (if (isDark) 0.58f else 0.82f),
                    style = HighlightStyle.Default(
                        color = primary.color,
                        angle = primary.angleDegrees,
                        falloff = style.highlightFalloff,
                        intensity = primary.intensity,
                        secondary = secondary?.let {
                            HighlightStyle.Default.Secondary(
                                color = it.color,
                                angle = it.angleDegrees,
                                falloff = style.highlightFalloff,
                                intensity = it.intensity
                            )
                        }
                    )
                )
            }
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
