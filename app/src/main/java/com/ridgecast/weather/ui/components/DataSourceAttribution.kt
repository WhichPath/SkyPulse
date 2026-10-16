package com.ridgecast.weather.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridgecast.weather.R
import com.ridgecast.weather.ui.theme.TextSecondary

/**
 * 数据来源署名（和风天气）。
 *
 * 遵循和风天气《注明来源》规范：
 * - 需清晰展示 `和风天气` 字样并附带超链接 https://www.qweather.com；
 * - 字体/颜色不得过于难以辨认（历史版本的 alpha = 0.4 过淡，现已上调）。
 *
 * 图形标为官方 currentColor 单色 SVG 矢量图（见 res/drawable/ic_qweather_logo.xml），
 * 经 Icon(tint) 着色，保证与文字颜色严格一致、任意字号下不失真。
 *
 * @param textAlpha 文字与图标的统一透明度
 */
@Composable
fun DataSourceAttribution(
    modifier: Modifier = Modifier,
    textAlpha: Float = 0.55f
) {
    val uriHandler = LocalUriHandler.current
    val attributionColor = TextSecondary.copy(alpha = textAlpha)

    Row(
        modifier = modifier
            .clip(ContinuousCornerShape(10.dp))
            .clickable {
                try {
                    uriHandler.openUri(QWEATHER_HOMEPAGE)
                } catch (e: Exception) {
                    Log.w(TAG, "failed to open QWeather homepage: ${e.message}")
                }
            }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "气象数据来自",
            style = MaterialTheme.typography.bodySmall,
            color = attributionColor
        )

        Spacer(modifier = Modifier.width(4.dp))

        // 官方图形标：12dp 与 12sp 正文等高对齐，图标高度约为大写字母高的 1.2 倍
        Icon(
            painter = painterResource(R.drawable.ic_qweather_logo),
            contentDescription = null,
            tint = attributionColor,
            modifier = Modifier.size(12.dp)
        )

        Spacer(modifier = Modifier.width(3.dp))

        Text(
            text = "和风天气",
            style = MaterialTheme.typography.bodySmall,
            color = attributionColor
        )
    }
}

private const val TAG = "DataSourceAttribution"
private const val QWEATHER_HOMEPAGE = "https://www.qweather.com"

/**
 * 逐条原样展示和风响应 `metadata.attributions` 中的归因声明。
 *
 * 官方《注明来源》规范对预警与空气质量有强制要求：必须**完整显示**归因内容，
 * 且**不能以任何形式修改**。因此这里：
 * - 不截断、不改写、不翻译，逐条整段渲染；
 * - 仅对纯 URL 条目额外附加点击跳转（纯增强，不影响原文）；
 * - 声明类文本（如"当前预警数据可能存在延迟…"）保持可读性优先，透明度不低于 0.6。
 *
 * @param attributions 原始 attributions 数组，为空时整个组件不渲染
 */
@Composable
fun AttributionNote(
    attributions: List<String>?,
    modifier: Modifier = Modifier,
    textAlpha: Float = 0.6f
) {
    val entries = attributions?.filter { it.isNotBlank() }.orEmpty()
    if (entries.isEmpty()) return

    val uriHandler = LocalUriHandler.current
    val color = TextSecondary.copy(alpha = textAlpha)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        entries.forEach { entry ->
            val isUrl = entry.startsWith("http://") || entry.startsWith("https://")
            Text(
                text = entry,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
            if (isUrl) {
                // URL 自身作为可点击区域，避免为了可点而改动正文文字
                Spacer(modifier = Modifier.height(0.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .clip(ContinuousCornerShape(0.5.dp))
                        .background(color)
                        .clickable {
                            try {
                                uriHandler.openUri(entry)
                            } catch (e: Exception) {
                                Log.w(TAG, "failed to open attribution url: ${e.message}")
                            }
                        }
                )
            }
        }
    }
}