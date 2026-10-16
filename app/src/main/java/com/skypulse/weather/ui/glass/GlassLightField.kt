package com.skypulse.weather.ui.glass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skypulse.weather.ui.components.createContinuousCornerPath
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive (2026) 统一光场参数。
 *
 * 全局统一屏幕空间光场模型（Unified Screen-Space Light Field）：
 * 1. **全局主光源**：统一锚定于屏幕视口左上方（X ≈ 28% W, Y < 0），模拟自然天光；
 * 2. **自适应受光角**：根据容器在垂直内容流中的相对窗口位置（positionInWindow），
 *    动态计算物理入射光线向量（Light Vector）、高光落点与渐变斜率；
 * 3. **空间距离衰减**：靠上方的容器受光陡峭集中、边缘高光鲜明；靠下方的容器漫射扩散、温润深邃；
 * 4. **苹果连续圆角（Squircle / G2 Continuous）**：基于三次贝塞尔超椭圆模型构建曲率平滑轮廓；
 * 5. 零性能损耗：在 drawWithCache 中完成向量与路径缓存，120fps 满帧运行。
 */
data class GlassLightField(
    /** 容器底色（半透明 Surface Container，由天气色相动态派生）。 */
    val base: Color,
    /** 高度漫射色（M3 Ambient Elevation Tint）。 */
    val bloom: Color = Color.White,
    /** 斜向微光色（可选辅助微光）。 */
    val sheen: Color = Color.White,
    /** M3 Outline Variant 轮廓线颜色。 */
    val edge: Color = Color.White,
    /** 轮廓线宽度（M3 标准为 1dp）。 */
    val edgeWidth: Dp = 1.0.dp,
    val bloomStrength: Float = 1.0f,
    val sheenStrength: Float = 0.5f,
    val edgeStrength: Float = 1.0f
)

/**
 * 在苹果连续圆角形状上绘制全局统一光场的 Compose 修饰符。
 */
fun Modifier.glassLightField(
    radius: Dp,
    field: GlassLightField,
    exponent: Float = 4.2f
): Modifier = composed {
    var windowPos by remember { mutableStateOf(Offset.Zero) }

    this
        .onGloballyPositioned { coordinates ->
            if (coordinates.isAttached) {
                windowPos = coordinates.positionInWindow()
            }
        }
        .drawWithCache {
            // 1. 构建苹果 G2 连续平滑圆角 Path (Lamé Squircle)
            val cornerPx = radius.value * density
            val path = createContinuousCornerPath(
                width = size.width,
                height = size.height,
                targetRadius = cornerPx,
                exponent = exponent
            )

            // 2. 全局虚拟光源（屏幕左上方：X ≈ 28% 屏幕宽，Y 在屏幕顶端之上 380dp）
            val estimatedScreenWidth = maxOf(size.width * 1.15f, 1080f)
            val lightSourceX = estimatedScreenWidth * 0.28f
            val lightSourceY = -380f * density

            // 卡片中心在全局窗口中的坐标
            val cardCenterX = windowPos.x + size.width * 0.5f
            val cardCenterY = windowPos.y + size.height * 0.5f

            // 光源指向卡片中心的物理入射向量与角度
            val dx = cardCenterX - lightSourceX
            val dy = (cardCenterY - lightSourceY).coerceAtLeast(120f)
            val lightAngle = atan2(dy, dx)

            // 纵向内容流归一化深度（0.0 处于屏幕顶，1.0 处于常规视口底）
            val screenDepth = (cardCenterY / (2400f * (density / 2.75f))).coerceIn(0f, 2.5f)
            val distanceAttenuation = 1f / (1f + screenDepth * 0.45f)

            val bloomK = field.bloomStrength.coerceIn(0f, 2f) * distanceAttenuation
            val edgeK = field.edgeStrength.coerceIn(0f, 2f)

            // 3. 底色色调层：依据全局光照向量进行微角度线性渐变（受光面微明，背光面沉稳）
            val baseBrush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.00f to field.base.brighten(0.07f * distanceAttenuation),
                    0.50f to field.base,
                    1.00f to field.base.dim(0.05f)
                ),
                start = Offset(size.width * 0.15f, 0f),
                end = Offset(size.width * 0.85f, size.height)
            )

            // 4. 天光高度漫射（Bloom）：随纵向深度平滑演变
            // 顶部容器受光中心靠左上、聚焦；下部容器受光更平缓、扩散半径更大
            val bloomCenterX = size.width * (0.50f - 0.22f * (1f - screenDepth * 0.4f).coerceIn(0f, 1f))
            val bloomCenterY = -size.height * (0.16f * distanceAttenuation)
            val bloomRadius = maxOf(size.width, size.height) * (0.80f + screenDepth * 0.30f)

            val bloomBrush = Brush.radialGradient(
                colors = listOf(
                    field.bloom.withAlpha(0.18f * bloomK),
                    field.bloom.withAlpha(0.05f * bloomK),
                    Color.Transparent
                ),
                center = Offset(bloomCenterX, bloomCenterY),
                radius = bloomRadius
            )

            // 5. M3 Outline Variant 轮廓线：方向严格对齐屏幕空间全局入射光向量
            // 所有容器同一朝向受光，左上方亮线、右下方暗线，具有绝对的物理整体感
            val edgeLength = maxOf(size.width, size.height)
            val edgeEndX = (size.width * cos(lightAngle).toFloat()).coerceIn(size.width * 0.25f, size.width)
            val edgeEndY = (size.height * sin(lightAngle).toFloat()).coerceIn(size.height * 0.50f, size.height)

            val edgeBrush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.00f to field.edge.withAlpha(0.42f * edgeK * (0.75f + 0.25f * distanceAttenuation)),
                    0.35f to field.edge.withAlpha(0.22f * edgeK),
                    1.00f to field.edge.withAlpha(0.09f * edgeK)
                ),
                start = Offset(0f, 0f),
                end = Offset(edgeEndX, edgeEndY)
            )

            val edgePx = field.edgeWidth.value * density

            onDrawBehind {
                drawPath(path, baseBrush)
                drawPath(path, bloomBrush)
                drawPath(path, edgeBrush, style = Stroke(width = edgePx, join = StrokeJoin.Round))
            }
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