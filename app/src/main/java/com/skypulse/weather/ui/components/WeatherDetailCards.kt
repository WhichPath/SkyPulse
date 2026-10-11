package com.skypulse.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.model.RealtimeWeather
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
import com.skypulse.weather.util.WbgtCalculator
import com.skypulse.weather.util.WeatherUtils

private data class DetailItem(
    val label: String,
    val value: String,
    val unit: String,
    val error: String = "",
    val iconName: String,
    val iconTint: Color = TextSecondary,
    val iconFillColor: Color = Color.Unspecified,
    val cardBgColor: Color? = null,
    val onClick: (() -> Unit)? = null
)

@Composable
fun WeatherDetailCards(
    realtime: RealtimeWeather?,
    location: List<Double>? = null,
    modifier: Modifier = Modifier
) {
    val explanationController = LocalExplanationController.current

    val wbgtResult = remember(realtime, location) {
        WbgtCalculator.calculate(
            temperature = realtime?.temperature,
            humidity = realtime?.humidity,
            windSpeedKmh = realtime?.wind?.speed,
            pressurePa = realtime?.pressure,
            cloudCover = realtime?.cloudrate,
            latitude = location?.getOrNull(1),
            longitude = location?.getOrNull(0)
        )
    }

    val wbgtValue = if (wbgtResult != null) "%.1f".format(wbgtResult.wbgt) else "--"
    val wbgtError = if (wbgtResult != null) "±${"%.1f".format(wbgtResult.uncertainty)}" else ""
    val wbgtUnit = if (wbgtResult != null) "℃" else ""

    val wbgtRiskColor = when {
        wbgtResult == null -> null
        wbgtResult.wbgt < 21.0 -> WbgtColorGreen
        wbgtResult.wbgt < 25.0 -> WbgtColorYellow
        wbgtResult.wbgt < 28.0 -> WbgtColorOrange
        wbgtResult.wbgt < 31.0 -> WbgtColorRed
        else -> WbgtColorBlack
    }

    val windSpeedLevel = WeatherUtils.formatWindSpeed(realtime?.wind?.speed)
    val windDir = WeatherUtils.formatWindDirection(realtime?.wind?.direction)
    val humidity = WeatherUtils.formatHumidity(realtime?.humidity)

    val uvIndex = realtime?.life_index?.ultraviolet?.index ?: "--"
    val uvDesc = realtime?.life_index?.ultraviolet?.desc ?: ""

    val pressureRaw = realtime?.pressure
    val pressureValue = if (pressureRaw != null) "${(pressureRaw / 100).toInt()}" else "--"
    val pressureUnit = "百帕"

    val visRaw = realtime?.visibility
    val visValue = if (visRaw != null) {
        if (visRaw >= 1000) "${"%.1f".format(visRaw / 1000)}" else "${visRaw.toInt()}"
    } else "--"
    val visUnit = "千米"

    val windLevelNum = windSpeedLevel.replace("级", "")
    val windLevel = windLevelNum.toIntOrNull()
    val gustLevel = WeatherUtils.formatWindSpeed(realtime?.wind?.gust).replace("级", "").toIntOrNull()

    val windUnit = if (gustLevel != null && windLevel != null && gustLevel > windLevel) {
        "级 (阵风${gustLevel}级)"
    } else {
        "级"
    }

    val items = listOf(
        DetailItem("紫外线", uvIndex, uvDesc, iconName = "sun"),
        DetailItem(
            label = "湿球黑球温度",
            value = wbgtValue,
            unit = wbgtUnit,
            error = wbgtError,
            iconName = if (wbgtResult != null) "flag" else "thermometer",
            iconTint = wbgtRiskColor ?: TextSecondary,
            iconFillColor = wbgtRiskColor?.copy(alpha = 0.85f) ?: Color.Unspecified,
            cardBgColor = wbgtRiskColor?.copy(alpha = 0.26f),
            onClick = { explanationController.showWbgt(wbgtResult?.wbgt, wbgtResult?.uncertainty) }
        ),
        DetailItem("湿度", humidity, "", iconName = "droplet"),
        DetailItem(windDir, windLevelNum, windUnit, iconName = "wind"),
        DetailItem("气压", pressureValue, pressureUnit, iconName = "gauge"),
        DetailItem("能见度", visValue, visUnit, iconName = "eye")
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.take(3).forEach { item ->
                DetailSquareCard(
                    iconName = item.iconName,
                    iconTint = item.iconTint,
                    iconFillColor = item.iconFillColor,
                    cardBgColor = item.cardBgColor,
                    label = item.label,
                    value = item.value,
                    error = item.error,
                    unit = item.unit,
                    onClick = item.onClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.drop(3).forEach { item ->
                DetailSquareCard(
                    iconName = item.iconName,
                    iconTint = item.iconTint,
                    iconFillColor = item.iconFillColor,
                    cardBgColor = item.cardBgColor,
                    label = item.label,
                    value = item.value,
                    error = item.error,
                    unit = item.unit,
                    onClick = item.onClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DetailSquareCard(
    iconName: String,
    label: String,
    value: String,
    unit: String = "",
    error: String = "",
    iconTint: Color = TextSecondary,
    iconFillColor: Color = Color.Unspecified,
    cardBgColor: Color? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cardModifier = if (onClick != null) {
        modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
    } else {
        modifier.aspectRatio(1f)
    }

    GlassCard(
        modifier = cardModifier
    ) {
        if (cardBgColor != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(cardBgColor)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            LucideIcon(
                name = iconName,
                contentDescription = label,
                tint = iconTint,
                fillColor = iconFillColor,
                size = 24.dp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = if (label.length > 4) 10.5.sp else 12.sp
                ),
                color = TextSecondary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (unit.isNotBlank() || error.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = TextPrimary,
                    )
                    if (error.isNotBlank()) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 10.sp
                            ),
                            color = TextSecondary,
                            maxLines = 1,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    if (unit.isNotBlank()) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = unit,
                            style = if (error.isNotBlank()) {
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                MaterialTheme.typography.labelMedium.copy(
                                    fontSize = when {
                                        unit.length > 5 -> 9.5.sp
                                        unit.length > 3 -> 10.5.sp
                                        else -> 12.sp
                                    }
                                )
                            },
                            color = if (error.isNotBlank()) TextPrimary else TextSecondary,
                            maxLines = 1,
                            modifier = if (error.isNotBlank()) Modifier else Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextPrimary
                )
            }
        }
    }
}