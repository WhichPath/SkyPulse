package com.skypulse.weather.ui.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃光场参数。
 *
 * 光场由四层构成，全部使用 Compose 原生 Brush 在 [drawWithCache] 中一次性构造并缓存：
 * 1. **底色层** [base]——玻璃自身的颜色（必须由天气色相派生，否则玻璃"无光"会退化成灰黑板）；
 * 2. **天顶光晕** [bloom]——顶部的大尺度受光，形成"光从上方来"的体积感；
 * 3. **各向异性斜面光泽** [sheen]——左上到右下的斜向高光，拉宽拉扁形成玻璃特有的宽光带；
 * 4. **边缘折射高光** [edge]——沿轮廓的渐变描边，顶部最亮、向下迅速衰减。
 *
 * > 为何不用 AGSL `RuntimeShader`：着色器在设备上即时编译，CI 无法校验。
 * > v4.0.2 实测中它被当作玻璃的**基础填充层**，一旦着色器未按预期渲染，
 * > 所有玻璃面直接塌成不透明黑板（设置页深色文字因此完全不可读）。
 * > Brush 方案表现可预期；`drawWithCache` 只在尺寸变化时重建画笔，静态卡片零逐帧开销。
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
 * 在圆角矩形上绘制液态玻璃光场。
 *
 * 使用显式构造的圆角矩形 Path 绘制，不依赖 [DrawScope.drawOutline]——
 * 后者并非 `DrawScope` 接口成员，在当前 Compose 版本上不可用。
 * 调用方仍需自行 `.clip()` 同半径圆角以保证抗锯齿边缘干净。
 */
fun Modifier.glassLightField(
    radius: Dp,
    field: GlassLightField
): Modifier = drawWithCache {
    // 圆角半径不得超过短边一半，否则形状退化
    val cornerPx = minOf(radius.value * density, minOf(size.width, size.height) / 2f)
    val corner = CornerRadius(cornerPx, cornerPx)
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                topLeftCornerRadius = corner,
                topRightCornerRadius = corner,
                bottomRightCornerRadius = corner,
                bottomLeftCornerRadius = corner
            )
        )
    }

    val edgePx = field.edgeWidth.value * density
    val innerEdgePx = (edgePx * 0.55f).coerceAtLeast(0.5f)
    val shortSide = minOf(size.width, size.height).coerceAtLeast(1f)
    val longSide = maxOf(size.width, size.height).coerceAtLeast(1f)

    val bloomK = field.bloomStrength.coerceIn(0f, 2f)
    val sheenK = field.sheenStrength.coerceIn(0f, 2f)
    val edgeK = field.edgeStrength.coerceIn(0f, 2f)

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
            field.bloom.withAlpha(0.26f * bloomK),
            field.bloom.withAlpha(0.08f * bloomK),
            Color.Transparent
        ),
        center = Offset(size.width * 0.5f, -shortSide * 0.08f),
        radius = longSide * 0.92f
    )

    // 各向异性斜面光泽：左上 → 右下的斜向宽带
    val sheenBrush = Brush.linearGradient(
        colors = listOf(
            field.sheen.withAlpha(0.18f * sheenK),
            field.sheen.withAlpha(0.05f * sheenK),
            Color.Transparent
        ),
        start = Offset(size.width * 0.04f, 0f),
        end = Offset(size.width * 0.92f, size.height * 0.86f)
    )

    // 边缘折射高光：顶部最亮 → 向下衰减（各向异性天顶斜面）
    val edgeBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to field.edge.withAlpha(0.72f * edgeK),
            0.30f to field.edge.withAlpha(0.26f * edgeK),
            0.75f to field.edge.withAlpha(0.10f * edgeK),
            1.00f to field.edge.withAlpha(0.24f * edgeK)
        ),
        startY = 0f,
        endY = size.height
    )

    // 内侧锐利描边：塑造玻璃厚度
    val innerEdgeBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to Color.White.withAlpha(0.30f * edgeK),
            0.55f to Color.White.withAlpha(0.05f * edgeK),
            1.00f to Color.Transparent
        ),
        startY = 0f,
        endY = size.height
    )

    onDrawBehind {
        drawPath(path, baseBrush)
        drawPath(path, bloomBrush)
        drawPath(path, sheenBrush)
        drawPath(path, edgeBrush, style = Stroke(width = edgePx, join = StrokeJoin.Round))
        drawPath(path, innerEdgeBrush, style = Stroke(width = innerEdgePx, join = StrokeJoin.Round))
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

/** 供测试与预览使用的空尺寸。 */
internal val EmptySize: Size = Size.Zero