package com.skypulse.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.ui.theme.TextDisabled
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
import com.skypulse.weather.ui.theme.TextTertiary
import com.skypulse.weather.util.AqhiCalculator

// ============ AQI / AQHI 语义色阶 ============
private val LevelColorGood = Color(0xFF43A047)       // 优 / 低 (≤50 / 1-3)
private val LevelColorModerate = Color(0xFFFFD54F)   // 中等 / 中 (51-100 / 4-6)
private val LevelColorLight = Color(0xFFFFB74D)      // 轻度不健康 / 高 (101-150 / 7)
private val LevelColorUnhealthy = Color(0xFFE57373)  // 不健康 / 甚高 (151-200 / 8-10)
private val LevelColorVeryUnhealthy = Color(0xFFCE93D8) // 重度不健康 / 严重 (201-300 / 10+)
private val LevelColorHazardous = Color(0xFFA1887F)  // 危险 (301+)

private data class AqiLevelDesc(
    val range: String,
    val name: String,
    val color: Color,
    val description: String
)

private data class AqhiLevelDesc(
    val band: String,
    val name: String,
    val color: Color,
    val description: String
)

private val AQI_LEVELS = listOf(
    AqiLevelDesc(
        range = "0 – 50",
        name = "优 (Good)",
        color = LevelColorGood,
        description = "空气质量令人满意，基本无空气污染风险。所有人均可正常进行日常户外活动。"
    ),
    AqiLevelDesc(
        range = "51 – 100",
        name = "中等 (Moderate)",
        color = LevelColorModerate,
        description = "空气质量可接受。极少数对特定污染物异常敏感的人群，可能出现轻微呼吸道不适。"
    ),
    AqiLevelDesc(
        range = "101 – 150",
        name = "轻度不健康 (Unhealthy for Sensitive Groups)",
        color = LevelColorLight,
        description = "敏感人群（儿童、老年人及心肺疾病患者）可能出现较明显症状；一般健康公众通常不受显著影响。"
    ),
    AqiLevelDesc(
        range = "151 – 200",
        name = "不健康 (Unhealthy)",
        color = LevelColorUnhealthy,
        description = "任何人都可能开始受到健康影响。敏感人群可能遭受较严重损害，应减少长时间或剧烈户外运动。"
    ),
    AqiLevelDesc(
        range = "201 – 300",
        name = "重度不健康 (Very Unhealthy)",
        color = LevelColorVeryUnhealthy,
        description = "健康警报：全体公众健康受损风险明显增加。所有人均应避免长时间户外停留，敏感人群应留在室内。"
    ),
    AqiLevelDesc(
        range = "301 – 500",
        name = "危险 (Hazardous)",
        color = LevelColorHazardous,
        description = "紧急健康警告：所有人均可能受到严重健康损害。全体公众应避免所有户外体力活动，紧闭门窗。"
    )
)

private val AQHI_LEVELS = listOf(
    AqhiLevelDesc(
        band = "1 – 3 级",
        name = "低 (Low)",
        color = LevelColorGood,
        description = "短期健康风险低。一般市民及敏感人群（儿童、长者、心脑呼吸道患者）均可正常开展日常户外活动。"
    ),
    AqhiLevelDesc(
        band = "4 – 6 级",
        name = "中 (Moderate)",
        color = LevelColorModerate,
        description = "短期健康风险中等。一般市民可如常活动；极少数对空气污染特别敏感的人士若出现不适，应考虑减少户外体力消耗。"
    ),
    AqhiLevelDesc(
        band = "7 级",
        name = "高 (High)",
        color = LevelColorLight,
        description = "短期健康风险高。易受影响人群（儿童、长者及慢性病患者）应减少户外剧烈运动；一般公众若感不适亦应减少消耗。"
    ),
    AqhiLevelDesc(
        band = "8 – 10 级",
        name = "甚高 (Very High)",
        color = LevelColorUnhealthy,
        description = "短期健康风险甚高。易受影响人群应将户外活动减至最低并留在室内；一般市民应大幅减少户外体力消耗。"
    ),
    AqhiLevelDesc(
        band = "10+ 级",
        name = "严重 (Serious)",
        color = LevelColorVeryUnhealthy,
        description = "短期健康风险严重。易受影响人群应避免所有户外活动；一般市民应避免一切户外体力消耗，留在室内并开启净化设施。"
    )
)

/**
 * 美国 EPA AQI 指数详情解释面板
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AqiExplanationSheet(
    currentAqi: Int?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E),
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.25f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // ---- 标题与标准出处 ----
            Text(
                text = "空气质量指数 (AQI)",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "依据美国环保署 (US EPA 40 CFR Part 58) 官方技术规范计算",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ---- 等级列表 (当前等级半透明高亮，其他背景透明) ----
            Text(
                text = "各等级健康标准与防护建议",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))

            AQI_LEVELS.forEach { level ->
                val isCurrent = isAqiCurrentLevel(currentAqi, level.range)
                LevelItemRow(
                    label = "${level.range}   ${level.name}",
                    description = level.description,
                    color = level.color,
                    isHighlighted = isCurrent
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---- 算法公式解析 ----
            Text(
                text = "计算算法与短板原则",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))

            FormulaBox(
                formulaText = """
                    1. 各污染物分项分指数 (IAQI) 采用分段线性插值:
                       Ip = [(I_high - I_low) / (BP_high - BP_low)] × (Cp - BP_low) + I_low

                    2. 综合空气质量指数 (木桶短板原则):
                       AQI = max(I_PM2.5, I_PM10, I_O3, I_NO2, I_SO2, I_CO)
                       
                    * 最终数值由当前分指数最高的一项决定，该项即为首要污染物。
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ---- 底部注记 / 更改说明 ----
            Text(
                text = "注：美国 EPA 官方规范中，颗粒物（PM2.5/PM10）基准要求为 24 小时平均，臭氧（O3）为 8 小时平均。为向公众提供即时、灵敏的环境质量反馈，本应用实况基于当前实时 1 小时监测浓度进行即时分段插值计算。",
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp),
                color = TextDisabled
            )
        }
    }
}

/**
 * 香港 EPD AQHI 指数详情解释面板
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AqhiExplanationSheet(
    currentAqhi: AqhiCalculator.AqhiResult?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E),
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.25f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // ---- 标题与标准出处 ----
            Text(
                text = "空气质素健康指数 (AQHI)",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "依据香港环境保护署 (EPD) 官方健康风险评价算法计算",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ---- 等级列表 (当前等级半透明高亮，其他背景透明) ----
            Text(
                text = "各级别健康风险与公众指引",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))

            AQHI_LEVELS.forEach { level ->
                val isCurrent = isAqhiCurrentLevel(currentAqhi, level.band)
                LevelItemRow(
                    label = "${level.band}   ${level.name}",
                    description = level.description,
                    color = level.color,
                    isHighlighted = isCurrent
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---- 算法公式解析 ----
            Text(
                text = "计算算法与复合风险累加",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))

            FormulaBox(
                formulaText = """
                    1. 综合超额住院风险增幅 (%AR 累计累加):
                       %AR = %AR(NO2) + %AR(SO2) + %AR(O3) + max(%AR(PM2.5), %AR(PM10))

                    2. 个别污染物健康风险增幅 (C 为浓度，单位 μg/m³):
                       %AR(i) = [exp(β_i × C_i) - 1] × 100%

                    3. 官方回归系数 β (流行病学入院风险统计):
                       β(NO2)  = 0.0004462559
                       β(SO2)  = 0.0001393235
                       β(O3)   = 0.0004888034
                       β(PM10) = 0.0002821751
                       β(PM2.5)= 0.0002180567
                       
                    * 最终根据 %AR 对照官方健康风险临界值划分为 1-10 级及 10+ 级。
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ---- 底部注记 / 更改说明 ----
            Text(
                text = "注：香港环境保护署官方规范中，各污染物浓度基准为 3 小时移动平均值。本应用实况基于当前实时 1 小时监测浓度计算即时超额健康风险增幅（%AR），以便更灵敏、迅速地捕捉并预警突发短时污染。",
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp),
                color = TextDisabled
            )
        }
    }
}

/**
 * 等级描述项行组件
 * isHighlighted 为 true 时，背景呈现对应等级颜色的半透明高亮模块，未高亮时背景全透明且无边框视觉。
 */
@Composable
private fun LevelItemRow(
    label: String,
    description: String,
    color: Color,
    isHighlighted: Boolean
) {
    val bgModifier = if (isHighlighted) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    }

    Column(modifier = bgModifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            if (isHighlighted) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "• 当前状态",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = color
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
            color = if (isHighlighted) TextPrimary else TextSecondary
        )
    }
}

/**
 * 公式展示卡片组件
 */
@Composable
private fun FormulaBox(formulaText: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(12.dp)
    ) {
        Text(
            text = formulaText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                fontSize = 11.sp
            ),
            color = TextSecondary
        )
    }
}

private fun isAqiCurrentLevel(aqi: Int?, range: String): Boolean {
    if (aqi == null) return false
    return when (range) {
        "0 – 50" -> aqi in 0..50
        "51 – 100" -> aqi in 51..100
        "101 – 150" -> aqi in 101..150
        "151 – 200" -> aqi in 151..200
        "201 – 300" -> aqi in 201..300
        "301 – 500" -> aqi > 300
        else -> false
    }
}

private fun isAqhiCurrentLevel(aqhi: AqhiCalculator.AqhiResult?, band: String): Boolean {
    if (aqhi == null) return false
    return when (band) {
        "1 – 3 级" -> aqhi.index in 1..3
        "4 – 6 级" -> aqhi.index in 4..6
        "7 级" -> aqhi.index == 7
        "8 – 10 级" -> aqhi.index in 8..10
        "10+ 级" -> aqhi.index >= 11
        else -> false
    }
}
