package com.ridgecast.weather.ui.components

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 将组件裁剪为连续平滑超椭圆（Apple Continuous Squircle）形状。
 */
fun Modifier.clipContinuous(radius: Dp, exponent: Float = 4.2f): Modifier =
    this.clip(ContinuousCornerShape(radius, exponent))

fun Modifier.clipContinuous(shape: ContinuousCornerShape): Modifier =
    this.clip(shape)

/**
 * 构造连续平滑超椭圆形状。
 */
fun continuousCornerShape(radius: Dp, exponent: Float = 4.2f): ContinuousCornerShape =
    ContinuousCornerShape(radius, exponent)

/**
 * 苹果连续平滑圆角（Apple Continuous Corner / Lamé Squircle）。
 *
 * 采用 Apple 工业级超椭圆公式（Lamé Superellipse）：
 * |x/a|^n + |y/b|^n = 1 (苹果经典指数 n ≈ 4.2)
 *
 * 在四角构建曲率平滑过渡的超椭圆曲线，直线段与拐角切线完全平滑对接，
 * 彻底消除普通圆角在直曲交界处的机械生硬感，呈现出 iOS / ColorOS 17 顶级现代温润质感。
 * 继承 [CornerBasedShape]，与 Material 3 主题系统规范完全无缝兼容。
 *
 * @param exponent 超椭圆幂指数，默认 4.2f（苹果黄金 Squircle 指数，n=2 为普通圆弧，n=4.2 为苹果标准平滑倒角）
 */
class ContinuousCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    val exponent: Float = 4.2f
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    constructor(cornerRadius: Dp, exponent: Float = 4.2f) : this(
        topStart = CornerSize(cornerRadius),
        topEnd = CornerSize(cornerRadius),
        bottomEnd = CornerSize(cornerRadius),
        bottomStart = CornerSize(cornerRadius),
        exponent = exponent
    )

    constructor(cornerSize: CornerSize, exponent: Float = 4.2f) : this(
        topStart = cornerSize,
        topEnd = cornerSize,
        bottomEnd = cornerSize,
        bottomStart = cornerSize,
        exponent = exponent
    )

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize
    ): CornerBasedShape = ContinuousCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd,
        bottomStart = bottomStart,
        exponent = exponent
    )

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(Rect.Zero)
        }
        val path = createContinuousCornerPath(
            width = size.width,
            height = size.height,
            topStart = topStart,
            topEnd = topEnd,
            bottomEnd = bottomEnd,
            bottomStart = bottomStart,
            exponent = exponent,
            layoutDirection = layoutDirection
        )
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContinuousCornerShape) return false
        return topStart == other.topStart &&
            topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd &&
            bottomStart == other.bottomStart &&
            exponent == other.exponent
    }

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        result = 31 * result + exponent.hashCode()
        return result
    }

    override fun toString(): String {
        return "ContinuousCornerShape(topStart=$topStart, topEnd=$topEnd, bottomEnd=$bottomEnd, bottomStart=$bottomStart, exponent=$exponent)"
    }
}

/**
 * 构建 Apple 超椭圆（Lamé Squircle）连续平滑路径。
 *
 * 支持 4 个拐角独立半径配置，每个拐角采用 10 段自适应采样点，
 * 确保在高分辨率屏幕与硬件抗锯齿下呈现极致丝滑的 G2 曲率。
 */
fun createContinuousCornerPath(
    width: Float,
    height: Float,
    topStart: Float,
    topEnd: Float,
    bottomEnd: Float,
    bottomStart: Float,
    exponent: Float = 4.2f,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr
): Path {
    val path = Path()
    val minDimension = minOf(width, height)
    if (minDimension <= 0f) return path

    val isLtr = layoutDirection == LayoutDirection.Ltr
    val maxRadius = minDimension / 2f
    val rTL = (if (isLtr) topStart else topEnd).coerceIn(0f, maxRadius)
    val rTR = (if (isLtr) topEnd else topStart).coerceIn(0f, maxRadius)
    val rBR = (if (isLtr) bottomEnd else bottomStart).coerceIn(0f, maxRadius)
    val rBL = (if (isLtr) bottomStart else bottomEnd).coerceIn(0f, maxRadius)

    if (rTL <= 0.5f && rTR <= 0.5f && rBR <= 0.5f && rBL <= 0.5f) {
        path.addRect(Rect(0f, 0f, width, height))
        return path
    }

    val n = exponent.coerceIn(2.0f, 6.0f)
    val p = 2.0 / n.toDouble()
    val segments = 10

    val w = width
    val h = height

    path.reset()

    // 1. 顶边：从左上角出口到右上角入口
    path.moveTo(rTL, 0f)
    path.lineTo(w - rTR, 0f)

    // 2. 右上角超椭圆过渡 (t: π/2 -> 0)
    if (rTR > 0.5f) {
        val cxTR = w - rTR
        val cyTR = rTR
        for (i in 1..segments) {
            val t = (PI / 2.0) * (1.0 - i.toDouble() / segments)
            val cosT = cos(t).coerceAtLeast(0.0)
            val sinT = sin(t).coerceAtLeast(0.0)
            val x = (cxTR + rTR * cosT.pow(p)).toFloat()
            val y = (cyTR - rTR * sinT.pow(p)).toFloat()
            path.lineTo(x, y)
        }
    } else {
        path.lineTo(w, 0f)
    }

    // 3. 右边：从右上角出口到右下角入口
    path.lineTo(w, h - rBR)

    // 4. 右下角超椭圆过渡 (t: 0 -> -π/2)
    if (rBR > 0.5f) {
        val cxBR = w - rBR
        val cyBR = h - rBR
        for (i in 1..segments) {
            val t = (PI / 2.0) * (i.toDouble() / segments)
            val cosT = cos(t).coerceAtLeast(0.0)
            val sinT = sin(t).coerceAtLeast(0.0)
            val x = (cxBR + rBR * cosT.pow(p)).toFloat()
            val y = (cyBR + rBR * sinT.pow(p)).toFloat()
            path.lineTo(x, y)
        }
    } else {
        path.lineTo(w, h)
    }

    // 5. 底边：从右下角出口到左下角入口
    path.lineTo(rBL, h)

    // 6. 左下角超椭圆过渡 (t: π/2 -> 0)
    if (rBL > 0.5f) {
        val cxBL = rBL
        val cyBL = h - rBL
        for (i in 1..segments) {
            val t = (PI / 2.0) * (1.0 - i.toDouble() / segments)
            val cosT = cos(t).coerceAtLeast(0.0)
            val sinT = sin(t).coerceAtLeast(0.0)
            val x = (cxBL - rBL * cosT.pow(p)).toFloat()
            val y = (cyBL + rBL * sinT.pow(p)).toFloat()
            path.lineTo(x, y)
        }
    } else {
        path.lineTo(0f, h)
    }

    // 7. 左边：从左下角出口到左上角入口
    path.lineTo(0f, rTL)

    // 8. 左上角超椭圆过渡 (t: 0 -> -π/2)
    if (rTL > 0.5f) {
        val cxTL = rTL
        val cyTL = rTL
        for (i in 1..segments) {
            val t = (PI / 2.0) * (i.toDouble() / segments)
            val cosT = cos(t).coerceAtLeast(0.0)
            val sinT = sin(t).coerceAtLeast(0.0)
            val x = (cxTL - rTL * cosT.pow(p)).toFloat()
            val y = (cyTL - rTL * sinT.pow(p)).toFloat()
            path.lineTo(x, y)
        }
    } else {
        path.lineTo(0f, 0f)
    }

    path.close()
    return path
}

/**
 * 均等 4 角 Apple 超椭圆连续平滑路径快捷构造。
 */
fun createContinuousCornerPath(
    width: Float,
    height: Float,
    targetRadius: Float,
    exponent: Float = 4.2f
): Path = createContinuousCornerPath(
    width = width,
    height = height,
    topStart = targetRadius,
    topEnd = targetRadius,
    bottomEnd = targetRadius,
    bottomStart = targetRadius,
    exponent = exponent,
    layoutDirection = LayoutDirection.Ltr
)

