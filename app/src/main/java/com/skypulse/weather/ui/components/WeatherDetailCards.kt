package com.skypulse.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.model.RealtimeWeather
import androidx.compose.runtime.remember
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
import com.skypulse.weather.util.WbgtCalculator
import com.skypulse.weather.util.WeatherUtils

private data class DetailItem(
    val label: String,
    val value: String,
    val unit: String,
    val iconName: String
)

@Composable
fun WeatherDetailCards(
    realtime: RealtimeWeather?,
    location: List<Double>? = null,
    modifier: Modifier = Modifier
) {
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

    val wbgtValue = if (wbgtResult != null) "${"%.1f".format(wbgtResult.wbgt)}°" else "--"
    val wbgtUnit = if (wbgtResult != null) "±${"%.1f".format(wbgtResult.uncertainty)}°" else ""

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
        DetailItem("紫外线", uvIndex, uvDesc, "sun"),
        DetailItem("湿球黑球", wbgtValue, wbgtUnit, "thermometer"),
        DetailItem("湿度", humidity, "", "droplet"),
        DetailItem(windDir, windLevelNum, windUnit, "wind"),
        DetailItem("气压", pressureValue, pressureUnit, "gauge"),
        DetailItem("能见度", visValue, visUnit, "eye")
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
                    label = item.label,
                    value = item.value,
                    unit = item.unit,
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
                    label = item.label,
                    value = item.value,
                    unit = item.unit,
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
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.aspectRatio(1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            LucideIcon(
                name = iconName,
                contentDescription = label,
                tint = TextSecondary,
                size = 24.dp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (unit.isNotBlank()) {
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
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = if (unit.length > 3) 10.sp else 12.sp
                        ),
                        color = TextSecondary,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
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