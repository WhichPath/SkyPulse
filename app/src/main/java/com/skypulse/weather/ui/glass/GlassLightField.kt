package com.skypulse.weather.ui.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃光场参数。
 *
 * 光场由四层构成，全部使用 Compose 原生 Brush 在 [drawWithCache] 中一次性构造并缓存：
 * 1. **底色层** [base]——玻璃自身的颜色（必须由天气色相派生，否则玻璃"无光"会退化成灰黑板）；
 * 2. **天顶光晕** [bloom]——顶部的大尺度受光，形成"光从上方来"的体积感；
 * 3. **各向异性斜面光泽** [sheen]——左上到右下的斜向高光，拉宽拉扁形成金属/玻璃特有的宽光带；
 * 4. **边缘折射高光** [edge]——沿轮廓的渐变描边，顶部最亮、向下迅速衰减。
 *
 * > 为何不用 AGSL `RuntimeShader`：着色器在设备上即时编译，CI 无法校验，
 * > 且一旦某机型驱动拒绝或 uniform 语义有偏差，作为基础填充层就会退化成不透明黑板
 * > （v4.0.2 实测教训）。Brush 方案表现可预期，且 `drawWithCache` 只在尺寸变化时重建画笔，
 * > 静态卡片零逐帧开销。
 */
data class GlassLightField(
    /** 玻璃底色（半透明，天气色相派生）。 */
    val base: Color,
    /** 天顶光晕色。 */
    val bloom: Color = Color.White,
    /** 斜面光泽色。 */
    val sheen: Color = Color.White,
    /** 边缘折射高光色。 */
    val edge: Color = Color.White,
    /** 边缘高光描边宽度。 */
    val edgeWidth: Dp = 1.2.dp,
    val bloomStrength: Float = 1.0f,
    val sheenStrength: Float = 1.0f,
    val edgeStrength: Float = 1.0f
)

/**
 * 在任意 [shape] 上绘制液态玻璃光场。
 *
 * 全部通过 `drawOutline` 绘制，因此**不需要调用方预先 clip**，也不会溢出到圆角之外；
 * 与 haze 模糊层、卡片底色互不冲突，可安全叠加。
 */
fun Modifier.glassLightField(
    shape: Shape,
    field: GlassLightField
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val edgePx = field.edgeWidth.toPx()
    val shortSide = minOf(size.width, size.height).coerceAtLeast(1f)
    val longSide = maxOf(size.width, size.height).coerceAtLeast(1f)

    // 底色：上亮下暗的纵向层理（动态亮度分层）
    val baseBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to field.base.brighten(0.18f),
            0.45f to field.base,
            1.00f to field.base.dim(0.16f)
        ),
        startY = 0f,
        endY = size.height
    )

    // 天顶光晕：以顶部偏中为圆心的径向受光
    val bloomBrush = Brush.radialGradient(
        colors = listOf(
            field.bloom.withAlpha(0.26f * field.bloomStrength.coerceIn(0f, 2f)),
            Color.Transparent
        ),
        center = Offset(size.width * 0.5f, -shortSide * 0.08f),
        radius = longSide * 0.92f
    )

    // 各向异性斜面光泽：左上 → 右下的斜向宽带
    val sheenBrush = Brush.linearGradient(
        colors = listOf(
            field.sheen.withAlpha(0.18f * field.sheenStrength.coerceIn(0f, 2f)),
            field.sheen.withAlpha(0.05f * field.sheenStrength.coerceIn(0f, 2f)),
            Color.Transparent
        ),
        start = Offset(size.width * 0.04f, 0f),
        end = Offset(size.width * 0.92f, size.height * 0.86f)
    )

    // 边缘折射高光：顶部最亮 → 向下衰减（各向异性天顶斜面）
    val edgeBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to field.edge.withAlpha(0.72f * field.edgeStrength.coerceIn(0f, 2f)),
            0.30f to field.edge.withAlpha(0.26f * field.edgeStrength.coerceIn(0f, 2f)),
            0.75f to field.edge.withAlpha(0.10f * field.edgeStrength.coerceIn(0f, 2f)),
            1.00f to field.edge.withAlpha(0.22f * field.edgeStrength.coerceIn(0f, 2f))
        ),
        startY = 0f,
        endY = size.height
    )

    // 内侧锐利描边：塑造玻璃厚度
    val innerEdgeBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to Color.White.withAlpha(0.30f * field.edgeStrength.coerceIn(0f, 2f)),
            0.55f to Color.White.withAlpha(0.05f * field.edgeStrength.coerceIn(0f, 2f)),
            1.00f to Color.Transparent
        ),
        startY = 0f,
        endY = size.height
    )

    onDrawBehind {
        drawOutline(outline, baseBrush, style = Fill)
        drawOutline(outline, bloomBrush, style = Fill)
        drawOutline(outline, sheenBrush, style = Fill)
        drawOutline(outline, edgeBrush, style = Stroke(width = edgePx, join = StrokeJoin.Round))
        drawOutline(
            outline,
            innerEdgeBrush,
            style = Stroke(width = (edgePx * 0.55f).coerceAtLeast(0.5f), join = StrokeJoin.Round)
        )
    }
}

/** 调整 RGB 亮度并保留原有 alpha。 */
internal fun Color.brighten(amount: Float): Color = Color(
    red = (red + amount).coerceIn(0f, 1f),
    green = (green + amount).coerceIn(0f, 1f),
    blue = (blue + amount).coerceIn(0f, 1f),
    alpha = alpha
)

/** 压暗 RGB 并保留原有 alpha。 */
internal fun Color.dim(amount: Float): Color = Color(
    red = (red - amount).coerceIn(0f, 1f),
    green = (green - amount).coerceIn(0f, 1f),
    blue = (blue - amount).coerceIn(0f, 1f),
    alpha = alpha
)

/** 直接覆写 alpha。 */
internal fun Color.withAlpha(alpha: Float): Color = copy(alpha = alpha.coerceIn(0f, 1f))