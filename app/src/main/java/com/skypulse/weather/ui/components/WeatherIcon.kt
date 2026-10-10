package com.skypulse.weather.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skypulse.weather.ui.theme.LocalWeatherTheme

@Suppress("UNUSED_PARAMETER")
@Composable
fun WeatherIcon(
    iconType: String,
    size: Dp = 80.dp,
    animated: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val weatherTheme = LocalWeatherTheme.current
    val precipitationColor = weatherTheme.precipitationIconColor.toArgb()
    val sizePx = remember(size, density) {
        with(density) { size.roundToPx() }.coerceAtLeast(1)
    }
    val bitmap = remember(context, iconType, sizePx, precipitationColor) {
        WeatherSvgRenderer.renderBitmap(context, iconType, sizePx, precipitationColor)
    }
    val iconModifier = if (iconType == "clear-day") {
        modifier.size(size).graphicsLayer(scaleX = 0.85f, scaleY = 0.85f)
    } else {
        modifier.size(size)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = iconModifier
        )
    }
}
