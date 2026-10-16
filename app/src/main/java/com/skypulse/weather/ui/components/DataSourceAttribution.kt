package com.skypulse.weather.ui.components

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.skypulse.weather.R
import com.skypulse.weather.ui.theme.TextSecondary

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