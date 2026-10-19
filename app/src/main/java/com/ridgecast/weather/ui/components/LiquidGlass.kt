package com.ridgecast.weather.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ridgecast.weather.R
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

/**
 * 液态玻璃（Liquid Glass）修饰符 v1.0.4。
 *
 * 架构与 IT之家底栏同源（QmDeve AndroidLiquidGlassView，MIT）：
 *
 * 1. **只采样背景**：从 Haze 的 backdrop 图层里裁出卡片背后的真实画面，
 *    绘制到独立的 GraphicsLayer 中，绝不再把卡片自身内容送进 shader
 *    （早期版本把 renderEffect 挂在卡片内容层上，导致文字被折射、出现灰色矩形罩）。
 * 2. **先模糊后折射**：RenderEffect 链为 `shader(blur(backdrop))`，
 *    与 IT之家/上游库一致；模糊让背景失去细节，折射带再把边缘「拉」出透镜感。
 * 3. **独立图层**：玻璃背景与卡片内容互为兄弟层，文字、图表始终保持锐利。
 * 4. **防御性降级**：无 Haze 源（省电模式/预览）或 shader 构建失败时，
 *    自动退化为带轮廓的半透底色玻璃，绝不闪退、绝不出现全透明面板。
 *
 * 关键约定：AGSL 里 `uniform shader content` 的名称必须与
 * `RenderEffect.createRuntimeShaderEffect(shader, "content")` 的第二个参数一致。
 */
private const val liquidGlassDebugLog = false

/** 背景模糊半径（dp）。IT之家底栏观感偏重，卡片略轻。 */
private val GlassBlurRadius = 4.dp

/** 折射带宽（dp）：从卡片边缘向内多少距离开始产生位移。 */
private val GlassRefractionHeight = 28.dp

/** 折射位移强度（dp）。 */
private val GlassRefractionAmount = 34.dp

/** 卡片投影：模糊半径、Y 偏移、不透明度。 */
private val GlassShadowBlur = 10.dp
private val GlassShadowOffsetY = 4.dp
private const val GlassShadowAlpha = 0.17f

@Composable
fun Modifier.liquidGlass(
    radius: Dp,
    isDark: Boolean,
    hazeState: HazeState? = null,
    backgroundColor: Color = Color.White
): Modifier {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dark = if (isDark) 1f else 0f
    val cornerPx = with(density) { radius.toPx() }
    val blurPx = with(density) { GlassBlurRadius.toPx() }
    val refractionPx = with(density) { GlassRefractionAmount.toPx() }
    val refractionHeightPx = with(density) { GlassRefractionHeight.toPx() }
    // 位移 + 色散采样可能超出卡片，pad 必须覆盖最大采样半径，否则边缘会采到图层外
    val padPx = maxOf(blurPx * 2f, refractionPx + refractionHeightPx * 0.45f + 12f)
    val shadowBlurPx = with(density) { GlassShadowBlur.toPx() }
    val shadowDyPx = with(density) { GlassShadowOffsetY.toPx() }
    val shadowPadPx = shadowBlurPx * 2f + 2f
    val shape = remember(radius) { ContinuousCornerShape(radius, exponent = 4.2f) }
    // 玻璃罩色：不能直接用背景色（会和背景融为一体，面板「消失」）；
    // 浅色用乳白、深色用冷黑，保持玻璃的存在感与文字对比度。
    val tintColor = if (isDark) Color(0xFF1A1C1F) else Color.White
    val tintAlpha = if (isDark) 0.08f else 0.055f

    // RuntimeShader 一次构建；AGSL 非法等异常直接降级为 null（纯毛玻璃）。
    val shader = remember {
        runCatching {
            val text = context.resources
                .openRawResource(R.raw.liquid_glass)
                .bufferedReader().use { it.readText() }
            RuntimeShader(text)
        }.onFailure {
            android.util.Log.e("LiquidGlass", "RuntimeShader build failed", it)
        }.getOrNull()
    }

    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    // 卡片在窗口中的位置：绘制阶段读取，避免滚动时触发组合期失效。
    val position = remember { FloatArray(2) }

    // 注意：必须先把 uniform 写进 RuntimeShader，再创建 RenderEffect。
    // 本机实测：createRuntimeShaderEffect 之后再 setFloatUniform，shader 侧仍读到 0
    //（表现为 size=0 的透传分支），因此把「写 uniform」与「建 effect」绑定在同一次 remember。
    fun applyUniforms(s: RuntimeShader, w: Int, h: Int) {
        s.setFloatUniform("size", floatArrayOf(w.toFloat(), h.toFloat()))
        // 图层四周多留出模糊缓冲，卡片原点位于 (pad, pad)
        s.setFloatUniform("offset", floatArrayOf(-padPx, -padPx))
        s.setFloatUniform("cornerRadii", floatArrayOf(cornerPx, cornerPx, cornerPx, cornerPx))
        s.setFloatUniform("refractionHeight", with(density) { GlassRefractionHeight.toPx() })
        s.setFloatUniform("refractionAmount", with(density) { GlassRefractionAmount.toPx() })
        s.setFloatUniform("depthEffect", 0.45f)
        s.setFloatUniform("dispersion", 0.10f)
        s.setFloatUniform("isDark", dark)
        s.setFloatUniform("rimStrength", 0.58f)
        s.setFloatUniform("contrast", 0.08f)
        s.setFloatUniform("whitePoint", 0f)
        s.setFloatUniform("chromaMultiplier", 1.12f)
        s.setFloatUniform("tintColor", floatArrayOf(tintColor.red, tintColor.green, tintColor.blue))
        s.setFloatUniform("tintAlpha", tintAlpha)
    }

    // 模糊 + 折射链：先对背景做高斯模糊，再做透镜折射。
    val glassEffect = remember(shader, layoutSize, isDark) {
        shader?.let { s ->
            if (layoutSize.width <= 0 || layoutSize.height <= 0) return@let null
            runCatching {
                applyUniforms(s, layoutSize.width, layoutSize.height)
                val shaderEffect = RenderEffect.createRuntimeShaderEffect(s, "content")
                val blurEffect = RenderEffect.createBlurEffect(
                    blurPx, blurPx, Shader.TileMode.CLAMP
                )
                if (liquidGlassDebugLog) {
                    android.util.Log.i(
                        "LiquidGlass",
                        "effect created size=${layoutSize.width}x${layoutSize.height} pad=$padPx"
                    )
                }
                RenderEffect.createChainEffect(shaderEffect, blurEffect)
                    .asComposeRenderEffect()
            }.onFailure {
                android.util.Log.e("LiquidGlass", "create glass effect failed", it)
            }.getOrNull()
        }
    }

    // 独立投影层：黑色超椭圆 → 高斯模糊 → 垫在玻璃下方。
    val shadowEffect = remember(shadowBlurPx) {
        runCatching {
            RenderEffect.createBlurEffect(shadowBlurPx, shadowBlurPx, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }.getOrNull()
    }

    val backdropLayer = rememberGraphicsLayer()
    val shadowLayer = rememberGraphicsLayer()

    return this
        .onSizeChanged { layoutSize = it }
        .onGloballyPositioned { coordinates ->
            if (coordinates.isAttached) {
                val p = coordinates.positionInWindow()
                position[0] = p.x
                position[1] = p.y
            }
        }
        // 投影层必须在 clip 之外，否则会被卡片形状裁掉。
        .drawWithCache {
            val shadowPath = createContinuousCornerPath(
                width = size.width,
                height = size.height,
                targetRadius = cornerPx,
                exponent = 4.2f
            )
            onDrawBehind {
                if (shadowEffect == null || size.width <= 0f || size.height <= 0f) {
                    return@onDrawBehind
                }
                val w = size.width.roundToInt()
                val h = size.height.roundToInt()
                val layerW = (w + shadowPadPx * 2f).roundToInt().coerceAtLeast(1)
                val layerH = (h + shadowPadPx * 2f).roundToInt().coerceAtLeast(1)
                shadowLayer.record(IntSize(layerW, layerH)) {
                    translate(shadowPadPx, shadowPadPx + shadowDyPx) {
                        drawPath(shadowPath, Color.Black.copy(alpha = GlassShadowAlpha))
                    }
                }
                shadowLayer.renderEffect = shadowEffect
                translate(-shadowPadPx, -shadowPadPx) {
                    drawLayer(shadowLayer)
                }
            }
        }
        .clip(shape)
        .drawWithCache {
            val shapePath = createContinuousCornerPath(
                width = size.width,
                height = size.height,
                targetRadius = cornerPx,
                exponent = 4.2f
            )

            onDrawBehind {
                val w = size.width.roundToInt()
                val h = size.height.roundToInt()
                if (w <= 0 || h <= 0) return@onDrawBehind
                val sourceLayer = hazeState?.contentLayer
                val sourcePos = hazeState?.positionOnScreen
                if (glassEffect != null &&
                    sourceLayer != null &&
                    !sourceLayer.isReleased &&
                    sourcePos != null &&
                    sourcePos.isSpecified
                ) {
                    // 把背景裁到卡片背后：源图层坐标为源左上角，卡片原点在屏幕 (position)
                    val dx = position[0] - sourcePos.x
                    val dy = position[1] - sourcePos.y
                    val layerW = (w + padPx * 2f).roundToInt().coerceAtLeast(1)
                    val layerH = (h + padPx * 2f).roundToInt().coerceAtLeast(1)

                    backdropLayer.record(IntSize(layerW, layerH)) {
                        // 兜底底色保证图层始终不透明，避免模糊边缘出现透明衰减
                        drawRect(backgroundColor)
                        translate(padPx - dx, padPx - dy) {
                            drawLayer(sourceLayer)
                        }
                    }
                    backdropLayer.renderEffect = glassEffect

                    // 图层多留了一圈 pad，绘制时向左上偏移回去
                    translate(-padPx, -padPx) {
                        drawLayer(backdropLayer)
                    }
                    if (liquidGlassDebugLog) {
                        android.util.Log.i(
                            "LiquidGlass",
                            "draw glass ${w}x$h src=${sourceLayer.size} d=($dx,$dy)"
                        )
                    }
                } else {
                    if (liquidGlassDebugLog) {
                        android.util.Log.i(
                            "LiquidGlass",
                            "draw fallback ${w}x$h src=${sourceLayer?.size} " +
                                "effect=${glassEffect != null} pos=$sourcePos"
                        )
                    }
                    // 无模糊源 / shader 失败：半透底色 + 轻轮廓，保证可读与层次
                    drawPath(
                        path = shapePath,
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                tintColor.copy(alpha = if (isDark) 0.34f else 0.32f),
                                tintColor.copy(alpha = if (isDark) 0.28f else 0.24f)
                            )
                        )
                    )
                    drawPath(
                        path = shapePath,
                        color = Color.White.copy(alpha = if (isDark) 0.16f else 0.30f),
                        style = Stroke(width = with(density) { 1.dp.toPx() })
                    )
                }
            }
        }
}
