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
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
import com.skypulse.weather.ui.theme.TextTertiary
import com.skypulse.weather.util.AqhiCalculator
import com.skypulse.weather.util.AqiCalculator

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
    val unit: String,
    val iaqi: Int? = null
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

    var showAqiSheet by remember { mutableStateOf(false) }
    var showAqhiSheet by remember { mutableStateOf(false) }

    val aqhi = AqhiCalculator.calculate(
        pm25 = airQuality.pm25,
        pm10 = airQuality.pm10,
        o3 = airQuality.o3,
        no2 = airQuality.no2,
        so2 = airQuality.so2
    )

    val pm25Iaqi = AqiCalculator.calculatePm25Aqi(airQuality.pm25)
    val pm10Iaqi = AqiCalculator.calculatePm10Aqi(airQuality.pm10)
    val o3Iaqi = AqiCalculator.calculateO3Aqi(airQuality.o3)
    val so2Iaqi = AqiCalculator.calculateSo2Aqi(airQuality.so2)
    val no2Iaqi = AqiCalculator.calculateNo2Aqi(airQuality.no2)
    val coIaqi = AqiCalculator.calculateCoAqi(airQuality.co)

    val pollutants = listOf(
        PollutantItem("PM2.5", airQuality.pm25, "μg/m³", pm25Iaqi),
        PollutantItem("PM10", airQuality.pm10, "μg/m³", pm10Iaqi),
        PollutantItem("O₃", airQuality.o3, "ppb", o3Iaqi),
        PollutantItem("SO₂", airQuality.so2, "ppb", so2Iaqi),
        PollutantItem("NO₂", airQuality.no2, "ppb", no2Iaqi),
        PollutantItem("CO", airQuality.co, "ppm", coIaqi)
    )

    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // ---- 标题行: "空气质量" ----
            Text(
                text = "空气质量",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = TextPrimary
            )

            // ---- 指标行: AQI 固定宽槽位 + AQHI 左对齐紧随其后 ----
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AQI 固定 140dp 槽位，点击弹出详细解释面板
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAqiSheet = true }
                ) {
                    Text(
                        text = buildString {
                            append("AQI ")
                            append(aqiValue)
                            if (!category.isNullOrBlank()) {
                                append(" ")
                                append(category)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                // AQHI 紧随在 AQI 槽位右侧，固定间距 16dp，点击弹出详细解释面板
                if (aqhi != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showAqhiSheet = true }
                    ) {
                        Text(
                            text = "AQHI ${aqhi.displayValue} ${aqhi.category}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
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
        }
    }

    if (showAqiSheet) {
        AqiExplanationSheet(
            currentAqi = aqiValue,
            onDismiss = { showAqiSheet = false }
        )
    }

    if (showAqhiSheet && aqhi != null) {
        AqhiExplanationSheet(
            currentAqhi = aqhi,
            onDismiss = { showAqhiSheet = false }
        )
    }
}

@Composable
private fun PollutantCell(
    item: PollutantItem,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (item.iaqi != null) {
        aqiColor(item.iaqi).copy(alpha = 0.16f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
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
