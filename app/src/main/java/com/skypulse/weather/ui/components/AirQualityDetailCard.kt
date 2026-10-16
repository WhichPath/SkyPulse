package com.skypulse.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import com.skypulse.weather.ui.theme.SkyPulseDesignSystem
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

// ============ AQI 色阶 (与全应用统一高对比度色阶保持一致) ============
private val AqiColorExcellent = SemanticColorGood          // 优 ≤50 - 亮翠绿
private val AqiColorGood = SemanticColorModerate           // 良 51-100 - 亮黄
private val AqiColorLight = SemanticColorLight             // 轻度 101-150 - 鲜橙
private val AqiColorModerate = SemanticColorUnhealthy      // 中度 151-200 - 亮红
private val AqiColorHeavy = SemanticColorVeryUnhealthy     // 重度 201-300 - 鲜紫
private val AqiColorSevere = SemanticColorHazardous        // 严重 300+ - 钛银白

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
    val explanationController = LocalExplanationController.current

    val aqhi = AqhiCalculator.calculate(
        pm25 = airQuality.pm25,
        pm10 = airQuality.pm10,
        o3 = airQuality.o3,
        no2 = airQuality.no2,
        so2 = airQuality.so2,
        o3Unit = airQuality.o3Unit,
        no2Unit = airQuality.no2Unit,
        so2Unit = airQuality.so2Unit
    )

    val pm25Unit = airQuality.pm25Unit ?: "μg/m³"
    val pm10Unit = airQuality.pm10Unit ?: "μg/m³"
    val o3Unit = airQuality.o3Unit ?: "μg/m³"
    val so2Unit = airQuality.so2Unit ?: "μg/m³"
    val no2Unit = airQuality.no2Unit ?: "μg/m³"
    val coUnit = airQuality.coUnit ?: "mg/m³"

    val pm25Iaqi = airQuality.pm25Iaqi ?: AqiCalculator.calculatePm25Aqi(airQuality.pm25)
    val pm10Iaqi = airQuality.pm10Iaqi ?: AqiCalculator.calculatePm10Aqi(airQuality.pm10)
    val o3Iaqi = airQuality.o3Iaqi ?: AqiCalculator.calculateO3Aqi(airQuality.o3, o3Unit)
    val so2Iaqi = airQuality.so2Iaqi ?: AqiCalculator.calculateSo2Aqi(airQuality.so2, so2Unit)
    val no2Iaqi = airQuality.no2Iaqi ?: AqiCalculator.calculateNo2Aqi(airQuality.no2, no2Unit)
    val coIaqi = airQuality.coIaqi ?: AqiCalculator.calculateCoAqi(airQuality.co, coUnit)

    val pollutants = listOf(
        PollutantItem("PM2.5", airQuality.pm25, pm25Unit, pm25Iaqi),
        PollutantItem("PM10", airQuality.pm10, pm10Unit, pm10Iaqi),
        PollutantItem("O₃", airQuality.o3, o3Unit, o3Iaqi),
        PollutantItem("SO₂", airQuality.so2, so2Unit, so2Iaqi),
        PollutantItem("NO₂", airQuality.no2, no2Unit, no2Iaqi),
        PollutantItem("CO", airQuality.co, coUnit, coIaqi)
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

            // ---- 指标行: AQI 宽槽位 (防换行) + AQHI 左对齐紧随其后 ----
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AQI 槽位增大至 168dp，避免最大评级组合（如 "AQI 300 重度不健康"）在紧凑屏幕上截断换行
                Box(
                    modifier = Modifier
                        .widthIn(min = 168.dp)
                        .clip(SkyPulseDesignSystem.Shapes.small)
                        .clickable { explanationController.showAqi(aqiValue) }
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
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                        maxLines = 1,
                        softWrap = false,
                        color = TextSecondary
                    )
                }

                // AQHI 紧随在 AQI 槽位右侧，点击弹出详细解释面板
                if (aqhi != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(SkyPulseDesignSystem.Shapes.small)
                            .clickable { explanationController.showAqhi(aqhi) }
                    ) {
                        Text(
                            text = "AQHI ${aqhi.displayValue} ${aqhi.category}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                            maxLines = 1,
                            softWrap = false,
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
}

@Composable
private fun PollutantCell(
    item: PollutantItem,
    modifier: Modifier = Modifier
) {
    // 提升背景对比度（减少透明度/提高 alpha），保证在各种天气背景下均清晰可辨，同时保持毛玻璃质感
    val backgroundColor = if (item.iaqi != null) {
        aqiColor(item.iaqi).copy(alpha = 0.28f)
    } else {
        Color.White.copy(alpha = 0.16f)
    }

    Box(
        modifier = modifier
            .clip(ContinuousCornerShape(com.skypulse.weather.ui.theme.SkyPulseDesignSystem.Radius.subCard))
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
