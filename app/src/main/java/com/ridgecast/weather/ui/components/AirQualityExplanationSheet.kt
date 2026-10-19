package com.ridgecast.weather.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.ridgecast.weather.ui.theme.SkyPulseDesignSystem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridgecast.weather.ui.theme.TextDisabled
import com.ridgecast.weather.ui.theme.TextPrimary
import com.ridgecast.weather.ui.theme.TextSecondary
import com.ridgecast.weather.ui.theme.TextTertiary
import com.ridgecast.weather.model.AlertContent
import com.ridgecast.weather.model.sortedByPublishTimeDescending
import com.ridgecast.weather.ui.theme.alertLevelColor
import com.ridgecast.weather.ui.components.LucideIcon
import com.ridgecast.weather.util.AqhiCalculator

// ============ AQI / AQHI 语义色阶 (高对比度磨砂通透配色) ============
internal val SemanticColorGood = Color(0xFF4ADE80)          // 优 / 低 (≤50 / 1-3) - 亮翠绿
internal val SemanticColorModerate = Color(0xFFFACC15)      // 中等 / 中 (51-100 / 4-6) - 亮黄
internal val SemanticColorLight = Color(0xFFFB923C)         // 轻度不健康 / 高 (101-150 / 7) - 鲜橙
internal val SemanticColorUnhealthy = Color(0xFFF87171)     // 不健康 / 甚高 (151-200 / 8-10) - 亮红
internal val SemanticColorVeryUnhealthy = Color(0xFFC084FC) // 重度不健康 / 严重 (201-300 / 10+) - 鲜紫
internal val SemanticColorHazardous = Color(0xFFCBD5E1)     // 危险 / 黑旗 (301+ / ≥31.0℃) - 钛银白


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
        color = SemanticColorGood,
        description = "空气质量令人满意，基本无空气污染风险。所有人均可正常进行日常户外活动。"
    ),
    AqiLevelDesc(
        range = "51 – 100",
        name = "中等 (Moderate)",
        color = SemanticColorModerate,
        description = "空气质量可接受。极少数对特定污染物异常敏感的人群，可能出现轻微呼吸道不适。"
    ),
    AqiLevelDesc(
        range = "101 – 150",
        name = "轻度不健康 (Unhealthy for Sensitive Groups)",
        color = SemanticColorLight,
        description = "敏感人群（儿童、老年人及心肺疾病患者）可能出现较明显症状；一般健康公众通常不受显著影响。"
    ),
    AqiLevelDesc(
        range = "151 – 200",
        name = "不健康 (Unhealthy)",
        color = SemanticColorUnhealthy,
        description = "任何人都可能开始受到健康影响。敏感人群可能遭受较严重损害，应减少长时间或剧烈户外运动。"
    ),
    AqiLevelDesc(
        range = "201 – 300",
        name = "重度不健康 (Very Unhealthy)",
        color = SemanticColorVeryUnhealthy,
        description = "健康警报：全体公众健康受损风险明显增加。所有人均应避免长时间户外停留，敏感人群应留在室内。"
    ),
    AqiLevelDesc(
        range = "301 – 500",
        name = "危险 (Hazardous)",
        color = SemanticColorHazardous,
        description = "紧急健康警告：所有人均可能受到严重健康损害。全体公众应避免所有户外体力活动，紧闭门窗。"
    )
)

private val AQHI_LEVELS = listOf(
    AqhiLevelDesc(
        band = "1 – 3 级",
        name = "低 (Low)",
        color = SemanticColorGood,
        description = "短期健康风险低。一般市民及敏感人群（儿童、长者、心脑呼吸道患者）均可正常开展日常户外活动。"
    ),
    AqhiLevelDesc(
        band = "4 – 6 级",
        name = "中 (Moderate)",
        color = SemanticColorModerate,
        description = "短期健康风险中等。一般市民可如常活动；极少数对空气污染特别敏感的人士若出现不适，应考虑减少户外体力消耗。"
    ),
    AqhiLevelDesc(
        band = "7 级",
        name = "高 (High)",
        color = SemanticColorLight,
        description = "短期健康风险高。易受影响人群（儿童、长者及慢性病患者）应减少户外剧烈运动；一般公众若感不适亦应减少消耗。"
    ),
    AqhiLevelDesc(
        band = "8 – 10 级",
        name = "甚高 (Very High)",
        color = SemanticColorUnhealthy,
        description = "短期健康风险甚高。易受影响人群应将户外活动减至最低并留在室内；一般市民应大幅减少户外体力消耗。"
    ),
    AqhiLevelDesc(
        band = "10+ 级",
        name = "严重 (Serious)",
        color = SemanticColorVeryUnhealthy,
        description = "短期健康风险严重。易受影响人群应避免所有户外活动；一般市民应避免一切户外体力消耗，留在室内并开启净化设施。"
    )
)
sealed interface ActiveExplanation {
    data class Aqi(val aqi: Int?) : ActiveExplanation
    data class Aqhi(val aqhi: AqhiCalculator.AqhiResult?) : ActiveExplanation
    data class Wbgt(val wbgt: Double?, val uncertainty: Double?) : ActiveExplanation
    data class Alert(
        val alerts: List<AlertContent>,
        val initialIndex: Int = 0,
        val attributions: List<String>? = null
    ) : ActiveExplanation
}

interface ExplanationController {
    fun showAqi(aqi: Int?)
    fun showAqhi(aqhi: AqhiCalculator.AqhiResult?)
    fun showWbgt(wbgt: Double?, uncertainty: Double?)
    fun showAlert(alerts: List<AlertContent>, initialIndex: Int = 0, attributions: List<String>? = null)
    fun dismiss()
}

val LocalExplanationController = staticCompositionLocalOf<ExplanationController> {
    object : ExplanationController {
        override fun showAqi(aqi: Int?) {}
        override fun showAqhi(aqhi: AqhiCalculator.AqhiResult?) {}
        override fun showWbgt(wbgt: Double?, uncertainty: Double?) {}
        override fun showAlert(alerts: List<AlertContent>, initialIndex: Int, attributions: List<String>?) {}
        override fun dismiss() {}
    }
}

/**
 * 统一样式的浮动玻璃质感详情弹窗 Overlay
 * 基于统一复用组件 [FloatingStateModal]，在同一个渲染树上承载 AQI / AQHI / WBGT / 预警详情。
 */
@Composable
fun ExplanationModalOverlay(
    active: ActiveExplanation?,
    onDismiss: () -> Unit
) {
    FloatingStateModal(
        data = active,
        onDismiss = onDismiss
    ) { current ->
        when (current) {
            is ActiveExplanation.Aqi -> AqiExplanationContent(current.aqi)
            is ActiveExplanation.Aqhi -> AqhiExplanationContent(current.aqhi)
            is ActiveExplanation.Wbgt -> WbgtExplanationContent(current.wbgt, current.uncertainty)
            is ActiveExplanation.Alert -> AlertExplanationContent(
                alerts = current.alerts,
                initialIndex = current.initialIndex,
                attributions = current.attributions
            )
        }
    }
}

/**
 * 美国 EPA AQI 指数详情内容组件
 */
@Composable
fun AqiExplanationContent(currentAqi: Int?) {
    Text(
        text = "空气质量指数 (AQI)",
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = TextPrimary
    )
    Spacer(modifier = Modifier.height(3.dp))
    Text(
        text = "依据美国环保署 (US EPA 40 CFR Part 58) 官方技术规范计算",
        style = MaterialTheme.typography.bodySmall,
        color = TextTertiary
    )

    Spacer(modifier = Modifier.height(18.dp))

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

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "注：美国 EPA 官方规范中，颗粒物（PM2.5/PM10）基准要求为 24 小时平均，臭氧（O3）为 8 小时平均。为向公众提供即时、灵敏的环境质量反馈，本应用实况基于当前实时 1 小时监测浓度进行即时分段插值计算。",
        style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp),
        color = TextDisabled
    )
}

/**
 * 香港 EPD AQHI 指数详情内容组件
 */
@Composable
fun AqhiExplanationContent(currentAqhi: AqhiCalculator.AqhiResult?) {
    Text(
        text = "空气质素健康指数 (AQHI)",
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = TextPrimary
    )
    Spacer(modifier = Modifier.height(3.dp))
    Text(
        text = "依据香港环境保护署 (EPD) 官方健康风险评价算法计算",
        style = MaterialTheme.typography.bodySmall,
        color = TextTertiary
    )

    Spacer(modifier = Modifier.height(18.dp))

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

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "注：香港环境保护署官方规范中，各污染物浓度基准为 3 小时移动平均值。本应用实况基于当前实时 1 小时监测浓度计算即时超额健康风险增幅（%AR），以便更灵敏、迅速地捕捉并预警突发短时污染。",
        style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp),
        color = TextDisabled
    )
}

@Composable
fun AqiExplanationSheet(
    currentAqi: Int?,
    onDismiss: () -> Unit
) {
    ExplanationModalOverlay(
        active = ActiveExplanation.Aqi(currentAqi),
        onDismiss = onDismiss
    )
}

@Composable
fun AqhiExplanationSheet(
    currentAqhi: AqhiCalculator.AqhiResult?,
    onDismiss: () -> Unit
) {
    ExplanationModalOverlay(
        active = ActiveExplanation.Aqhi(currentAqhi),
        onDismiss = onDismiss
    )
}

@Composable
internal fun LevelItemRow(
    label: String,
    description: String,
    color: Color,
    isHighlighted: Boolean
) {
    val bgModifier = if (isHighlighted) {
        Modifier
            .fillMaxWidth()
            .clip(SkyPulseDesignSystem.Shapes.medium)
            .background(color.copy(alpha = 0.22f))
            .border(1.dp, color.copy(alpha = 0.50f), SkyPulseDesignSystem.Shapes.medium)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .clip(SkyPulseDesignSystem.Shapes.medium)
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    }

    Column(modifier = bgModifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = color
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
            color = if (isHighlighted) TextPrimary else TextSecondary
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

/**
 * 气象预警详情内容组件（嵌入通用浮动弹窗展示）
 */
@Composable
fun AlertExplanationContent(
    alerts: List<AlertContent>,
    initialIndex: Int = 0,
    attributions: List<String>? = null
) {
    val sortedAlerts = remember(alerts) {
        alerts.sortedByPublishTimeDescending()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        LucideIcon(
            name = "triangle-alert",
            contentDescription = null,
            tint = TextPrimary,
            size = 22.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "气象预警详情",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (sortedAlerts.isEmpty()) {
        Text(
            text = "暂无生效中的预警信息",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            sortedAlerts.forEach { alert ->
                AlertItemCard(alert = alert)
            }
        }
    }

    if (!attributions.isNullOrEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        AttributionNote(attributions = attributions)
    }
}

@Composable
private fun AlertItemCard(alert: AlertContent) {
    val title = alert.title
        ?.replace(Regex("\\[.*?\\]"), "")
        ?.replace(Regex("^.*(?:发布|变更|解除|继续|更新)"), "")
        ?.replace(Regex("预警.*$"), "预警")
        ?.trim()
        ?.ifBlank { null } ?: alert.title ?: "气象预警"

    val alertColor = alertLevelColor(
        alert.level,
        alert.title,
        fallback = TextPrimary,
        whiteAlert = TextPrimary
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SkyPulseDesignSystem.Shapes.medium)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, alertColor.copy(alpha = 0.35f), SkyPulseDesignSystem.Shapes.medium)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .clip(SkyPulseDesignSystem.Shapes.extraSmall)
                    .background(alertColor.copy(alpha = 0.22f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = alert.level ?: "预警",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = alertColor
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = alertColor,
                modifier = Modifier.weight(1f)
            )
        }

        if (alert.publishTime != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "发布时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(alert.publishTime * 1000L))}",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }

        val description = alert.description
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = TextSecondary
            )
        }
    }
}
