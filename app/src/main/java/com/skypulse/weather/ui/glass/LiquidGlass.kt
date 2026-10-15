package com.skypulse.weather.ui.glass

import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skypulse.weather.util.FileLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 2026 液态玻璃（Liquid Glass）光场引擎。
 *
 * 材质由两层叠加而成：
 * 1. 底层模糊（由 haze / RenderEffect 提供）——让背景内容透过玻璃失真散开；
 * 2. 上层光场（本文件的 AGSL 着色器）——在模糊之上补齐真实玻璃的「光」：
 *    - 各向异性天顶斜面高光：朝向光源的斜面更亮，切向水平拉长形成宽而扁的高光带；
 *    - 圆角边缘折射微光：沿边缘做 RGB 相位分离的低频噪声色散，随时间缓慢流动；
 *    - 动态亮度分层：上亮下暗的纵向层理 + 低频呼吸，按压时整体内缩压暗；
 *    - 透镜中心柔光与 1px 锐利内描边，塑造玻璃厚度感。
 *
 * 降级策略：AGSL 着色器在设备上即时编译，若某机型驱动拒绝该着色器，
 * [LiquidGlassRenderer.isSupported] 为 false，自动改用等价的线性渐变材质，
 * 不会出现崩溃或空白卡片。
 */
object LiquidGlass {

    private const val TAG = "LiquidGlass"

    /**
     * AGSL 着色器源码。
     *
     * 严格遵循 AGSL 子集：不使用循环、不使用动态数组下标、
     * 标量统一使用 float、颜色统一使用 float4 uniform、返回值统一 half4。
     */
    const val SKSL = """
uniform float2 size;
uniform float radius;
uniform float press;
uniform float lightAngle;
uniform float phase;
uniform float4 glassTint;
uniform float4 edgeTint;
uniform float4 specularTint;
uniform float luminance;
uniform float bloom;
uniform float dispersion;
uniform float shimmer;

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, float2(0.0, 0.0))) + min(max(q.x, q.y), 0.0) - r;
}

float hash21(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 w = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    return mix(mix(a, b, w.x), mix(c, d, w.x), w.y);
}

half4 main(float2 fragCoord) {
    float2 hs = size * 0.5;
    float2 p = fragCoord - hs;
    float2 b = hs - float2(radius);

    float d = sdRoundRect(p, b, radius);
    if (d > 0.0) {
        return half4(half3(0.0, 0.0, 0.0), half(0.0));
    }

    float inside = -d;
    float shortSide = min(size.x, size.y);
    float band = max(2.0, min(radius * 0.9, shortSide * 0.18));

    float gx = sdRoundRect(p + float2(1.0, 0.0), b, radius) - sdRoundRect(p - float2(1.0, 0.0), b, radius);
    float gy = sdRoundRect(p + float2(0.0, 1.0), b, radius) - sdRoundRect(p - float2(0.0, 1.0), b, radius);
    float2 n = normalize(float2(gx, gy) + float2(0.0, 0.001));

    float2 L = float2(sin(lightAngle), -cos(lightAngle));
    float facing = clamp(dot(n, L) * 0.5 + 0.5, 0.0, 1.0);

    float bevel = exp(-inside / band);
    float2 tangent = float2(-n.y, n.x);
    float aniso = mix(1.0, 0.40 + 1.35 * abs(tangent.x), facing);
    float zenSpec = bevel * facing * facing * aniso;

    float flow = phase * (0.05 + 0.09 * shimmer);
    float2 flowP = fragCoord / max(band * 1.8, 10.0);
    float nr = vnoise(flowP + float2(flow, -flow * 0.55));
    float ng = vnoise(flowP + float2(flow * 1.17 + 5.3, -flow * 0.55 + 2.7));
    float nb = vnoise(flowP + float2(flow * 0.83 + 11.9, -flow * 0.55 + 8.1));
    float3 disperse = float3(nr, ng, nb) * (bevel * shimmer * dispersion);

    float v = fragCoord.y / max(size.y, 1.0);
    float layerGrad = mix(0.76, 1.24, 1.0 - v);
    float layers = 0.93 + 0.07 * sin(v * 12.566 + phase * 0.3);
    float pressDim = mix(1.0, 0.84, clamp(press, 0.0, 1.0));
    float lum = layerGrad * layers * pressDim * luminance;

    float2 cuv = p / max(hs, float2(1.0));
    float centerGlow = exp(-dot(cuv, cuv) * 1.45) * 0.24;

    float hairline = smoothstep(1.8, 0.0, inside) * (0.30 + 0.70 * facing);

    float3 col = glassTint.rgb * lum;
    col += edgeTint.rgb * zenSpec * bloom;
    col += specularTint.rgb * centerGlow * bloom;
    col += disperse * edgeTint.rgb * bloom;
    col += edgeTint.rgb * hairline * 0.9 * bloom;

    float alpha = glassTint.a * pressDim;
    alpha += zenSpec * 0.28 + hairline * 0.22 + centerGlow * 0.30;
    alpha += dispersion * shimmer * bevel * 0.10;
    alpha = clamp(alpha, 0.0, 1.0);

    return half4(half3(col), half(alpha));
}
"""

    /** 创建着色器实例；任何失败都降级为 null，绝不抛出。 */
    fun createShaderOrNull(): RuntimeShader? {
        return try {
            RuntimeShader(SKSL).apply {
                setFloatUniform("size", 0f, 0f)
                setFloatUniform("radius", 0f)
                setFloatUniform("press", 0f)
                setFloatUniform("lightAngle", 0f)
                setFloatUniform("phase", 0f)
                setFloatUniform("glassTint", 0f, 0f, 0f, 0f)
                setFloatUniform("edgeTint", 0f, 0f, 0f, 0f)
                setFloatUniform("specularTint", 0f, 0f, 0f, 0f)
                setFloatUniform("luminance", 1f)
                setFloatUniform("bloom", 1f)
                setFloatUniform("dispersion", 0f)
                setFloatUniform("shimmer", 0f)
            }
        } catch (e: Exception) {
            FileLogger.w(TAG, "AGSL init failed, falling back to gradient glass: ${e.message}")
            null
        }
    }
}

/**
 * 光场材质参数。
 *
 * @param tint 基础玻璃色（罩色），alpha 决定主体通透度
 * @param edgeTint 边缘光/色散色，通常取高饱和的天气强调色
 * @param specularTint 透镜中心柔光色
 * @param radius 圆角半径，需与实际 clip 形状一致，否则边缘高光会错位
 * @param luminance 动态亮度分层总强度
 * @param bloom 各向异性高光强度
 * @param dispersion 边缘折射色散强度
 * @param shimmer 折射微光的流动强度
 * @param lightAngleDegrees 天顶光角度，0 为正上方，正值向顺时针偏转
 * @param press 按压进度 0..1，用于阻尼流体按压反馈
 */
data class LiquidGlassParams(
    val tint: Color,
    val edgeTint: Color,
    val specularTint: Color = Color.White,
    val radius: Dp = 20.dp,
    val luminance: Float = 1.0f,
    val bloom: Float = 1.0f,
    val dispersion: Float = 0.45f,
    val shimmer: Float = 0.5f,
    val lightAngleDegrees: Float = 0f,
    val press: Float = 0f
)

/**
 * 光场渲染器：持有一个 RuntimeShader 与一个复用的 Paint。
 *
 * 着色器实例在整棵 Compose 树内共享是安全的——Compose 在 UI 线程串行绘制，
 * 每次 draw 前都会完整重写全部 uniform，不会出现跨组件串值。
 */
class LiquidGlassRenderer(private val shader: RuntimeShader?) {

    /** 该机型是否成功编译 AGSL；false 时上层自动走渐变降级方案。 */
    val isSupported: Boolean = shader != null

    private val paint = Paint().apply {
        isAntiAlias = true
        this.shader = shader
    }

    fun draw(scope: DrawScope, params: LiquidGlassParams, phase: Float) {
        val target = shader ?: return
        val width = scope.size.width
        val height = scope.size.height
        if (width <= 0f || height <= 0f) return

        try {
            with(scope.density) {
                target.setFloatUniform("size", width, height)
                // 圆角半径不得超过短边一半，否则 SDF 退化为无效形状
                val maxRadiusPx = minOf(width, height) / 2f
                val radiusPx = minOf(params.radius.toPx(), maxRadiusPx)
                target.setFloatUniform("radius", radiusPx)
                target.setFloatUniform("lightAngle", Math.toRadians(params.lightAngleDegrees.toDouble()).toFloat())
            }
            target.setFloatUniform("press", params.press.coerceIn(0f, 1f))
            target.setFloatUniform("phase", phase)
            target.setFloatUniform(
                "glassTint",
                params.tint.red, params.tint.green, params.tint.blue, params.tint.alpha
            )
            target.setFloatUniform(
                "edgeTint",
                params.edgeTint.red, params.edgeTint.green,
                params.edgeTint.blue, params.edgeTint.alpha
            )
            target.setFloatUniform(
                "specularTint",
                params.specularTint.red, params.specularTint.green,
                params.specularTint.blue, params.specularTint.alpha
            )
            target.setFloatUniform("luminance", params.luminance.coerceIn(0f, 2f))
            target.setFloatUniform("bloom", params.bloom.coerceIn(0f, 2f))
            target.setFloatUniform("dispersion", params.dispersion.coerceIn(0f, 2f))
            target.setFloatUniform("shimmer", params.shimmer.coerceIn(0f, 2f))

            scope.drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRect(0f, 0f, width, height, paint)
            }
        } catch (e: Exception) {
            FileLogger.w(TAG, "light field draw failed, keeping base layer: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "LiquidGlass"
    }
}

/** 取得（或惰性创建）全局共享的光场渲染器。 */
@Composable
fun rememberLiquidGlassRenderer(): LiquidGlassRenderer =
    remember { LiquidGlassRenderer(LiquidGlass.createShaderOrNull()) }

/**
 * 光场相位（秒）。
 *
 * [animated] 为 false 时返回固定 0，保证静态卡片零重绘开销；
 * 为 true 时按 [fps] 限频推进相位（默认 12fps），
 * 只让光场微光缓慢流动，避免整页 60fps 重绘带来的功耗与掉帧。
 */
@Composable
fun rememberLiquidGlassPhase(animated: Boolean = false, fps: Int = 12): Float {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animated, fps) {
        if (!animated) {
            phase = 0f
            return@LaunchedEffect
        }
        val start = System.nanoTime()
        val interval = (1000L / fps.coerceIn(1, 60)).coerceAtLeast(1L)
        while (isActive) {
            delay(interval)
            phase = (System.nanoTime() - start) / 1_000_000_000f
        }
    }
    return phase
}

/**
 * 阻尼流体按压反馈。
 *
 * 低阻尼比（0.55）让高光在松手后有一次短促的「回弹涌起」，
 * 模拟液态玻璃被按下后表面张力回弹的手感。
 */
@Composable
fun rememberLiquidGlassPress(interactionSource: MutableInteractionSource): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "liquid_glass_press"
    ).value
}

/**
 * 在当前绘制区域叠加液态玻璃光场。
 *
 * 必须放在 `.clip(shape)` **之后**：着色器自身也按圆角矩形做 SDF 裁剪，
 * 两者一致才能让高光边严丝合缝地贴在卡片轮廓上。
 *
 * 降级方案：着色器不可用时绘制等价的线性渐变罩色，视觉上仍有玻璃通透感。
 */
@Composable
fun Modifier.liquidGlassSurface(
    params: LiquidGlassParams,
    phase: Float = 0f
): Modifier {
    val renderer = rememberLiquidGlassRenderer()
    return if (renderer.isSupported) {
        this.drawBehind { renderer.draw(this, params, phase) }
    } else {
        this.drawBehind {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        params.tint,
                        params.edgeTint.copy(alpha = params.tint.alpha * 0.65f)
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
        }
    }
}