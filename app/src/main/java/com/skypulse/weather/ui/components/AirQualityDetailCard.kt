package com.skypulse.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.model.AirQuality
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
import com.skypulse.weather.ui.theme.TextTertiary

// ============ AQI 色阶 (与 HourlyForecast 保持一致) ============
private val AqiColorExcellent = Color(0xFF43A047)  // 优 ≤50
private val AqiColorGood = Color(0xFFFFD54F)        // 良 51-100
private val AqiColorLight = Color(0xFFFFB74D)       // 轻度 101-150
private val AqiColorModerate = Color(0xFFE57373)    // 中度 151-200
private val AqiColorHeavy = Color(0xFFCE93D8)       // 重度 201-300
private val AqiColorSevere = Color(0xFFA1887F)      // 严重 300+

private fun aqiColor(aqi: Int): Color = when {
    aqi <= 50 -> AqiColorExcellent
    aqi <= 100 -> AqiColorGood
    aqi <= 150 -> AqiColorLight
    aqi <= 200 -> AqiColorModerate
    aqi <= 300 -> AqiColorHeavy
    else -> AqiColorSevere
}

private fun aqiCategory(aqi: Int): String = when {
    aqi <= 50 -> "优"
    aqi <= 100 -> "中等"
    aqi <= 150 -> "轻度不健康"
    aqi <= 200 -> "不健康"
    aqi <= 300 -> "重度不健康"
    else -> "危险"
}

// ============ 污染物定义 ============
private data class PollutantItem(
    val label: String,
    val value: Double?,
    val unit: String
)

private fun formatPollutantValue(value: Double?): String {
    if (value == null) return "--"
    return if (value >= 100) value.toInt().toString()
    else if (value >= 10) String.format("%.0f", value)
    else if (value >= 1) String.format("%.1f", value)
    else String.format("%.2f", value)
}

@Composable
fun AirQualityDetailCard(
    airQuality: AirQuality?,
    modifier: Modifier = Modifier
) {
    if (airQuality == null) return

    val aqiValue = airQuality.aqi?.usa?.toInt() ?: airQuality.aqi?.chn?.toInt()
    if (aqiValue == null) return

    val category = airQuality.description?.usa ?: aqiCategory(aqiValue)
    // "优"(Good)不显示等级
    val showCategory = aqiValue > 50 && category != null

    val pollutants = listOf(
        PollutantItem("PM2.5", airQuality.pm25, "μg/m³"),
        PollutantItem("PM10", airQuality.pm10, "μg/m³"),
        PollutantItem("O₃", airQuality.o3, "ppb"),
        PollutantItem("SO₂", airQuality.so2, "ppb"),
        PollutantItem("NO₂", airQuality.no2, "ppb"),
        PollutantItem("CO", airQuality.co, "ppm")
    )

    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // ---- 标题行: "空气质量" + AQI 数值( + 等级) ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "空气质量",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$aqiValue",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    if (showCategory) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = TextSecondary
                        )
                    }
                }
            }

            // ---- 首要污染物 ----
            if (!airQuality.primaryPollutant.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "首要污染物  ${airQuality.primaryPollutant}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )
            }

            // ---- 污染物浓度网格 ----
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pollutants.take(3).forEach { item ->
                    PollutantCell(item, Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pollutants.drop(3).forEach { item ->
                    PollutantCell(item, Modifier.weight(1f))
                }
            }

            // ---- 健康建议 ----
            val hasHealth = !airQuality.healthEffect.isNullOrBlank() ||
                !airQuality.healthAdviceGeneral.isNullOrBlank() ||
                !airQuality.healthAdviceSensitive.isNullOrBlank()

            if (hasHealth) {
                Spacer(modifier = Modifier.height(12.dp))
                if (!airQuality.healthEffect.isNullOrBlank()) {
                    Text(
                        text = airQuality.healthEffect,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                if (!airQuality.healthAdviceGeneral.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "一般人群：${airQuality.healthAdviceGeneral}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
                if (!airQuality.healthAdviceSensitive.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "敏感人群：${airQuality.healthAdviceSensitive}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun PollutantCell(
    item: PollutantItem,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = TextTertiary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatPollutantValue(item.value),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = item.unit,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = TextTertiary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}
