package com.skypulse.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.skypulse.weather.ui.theme.TextDisabled
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
import com.skypulse.weather.ui.theme.TextTertiary

// ============ WBGT 国际运动风险五色旗阶 ============
val WbgtColorGreen = Color(0xFF4CAF50)     // 绿旗 (<21.0 ℃) 低风险
val WbgtColorYellow = Color(0xFFFFD54F)    // 黄旗 (21.0-25.0 ℃) 注意
val WbgtColorOrange = Color(0xFFFF9800)    // 橙旗 (25.0-28.0 ℃) 严密警戒
val WbgtColorRed = Color(0xFFEF5350)       // 红旗 (28.0-31.0 ℃) 危险
val WbgtColorBlack = Color(0xFF90A4AE)     // 黑旗 (≥31.0 ℃) 极度危险

data class WbgtLevelDesc(
    val band: String,
    val name: String,
    val color: Color,
    val description: String
)

val WBGT_LEVELS = listOf(
    WbgtLevelDesc(
        band = "绿旗 (< 21.0 ℃)",
        name = "低风险 (适合运动)",
        color = WbgtColorGreen,
        description = "热负荷低，发生劳力性热病的风险极低。所有人群均可安全正常进行各类室外体育活动与户外运动，按日常习惯适度补水即可。"
    ),
    WbgtLevelDesc(
        band = "黄旗 (21.0 – 25.0 ℃)",
        name = "注意 (积极补水)",
        color = WbgtColorYellow,
        description = "轻至中度热应激。长跑、足球等高强度耐力活动需定期（每 20～30 分钟）积极补充水分与电解质；密切观察自身体能状态与出汗情况。"
    ),
    WbgtLevelDesc(
        band = "橙旗 (25.0 – 28.0 ℃)",
        name = "严密警戒 (主动减负)",
        color = WbgtColorOrange,
        description = "热负荷显著增加。高强度对抗与耐力奔跑应主动降低强度、缩短训练时间，必须设置强制休息与遮阳降温；体弱或未适应高温者避免剧烈运动。"
    ),
    WbgtLevelDesc(
        band = "红旗 (28.0 – 31.0 ℃)",
        name = "危险 (剧烈运动禁区)",
        color = WbgtColorRed,
        description = "激烈的户外运动原则上必须中止！严禁长时间耐力长跑；体育赛事必须启动降温暂停（Cooling Break）；老人、体弱者及儿童应避免烈日下外出活动。"
    ),
    WbgtLevelDesc(
        band = "黑旗 (≥ 31.0 ℃)",
        name = "极度危险 (运动熔断)",
        color = WbgtColorBlack,
        description = "全球公认的运动熔断红线！原则上全面中止所有户外体育比赛、体能训练与剧烈活动；极易引发致命性劳力性热射病 (EHS)，应留在室内空调环境。"
    )
)

/**
 * 湿球黑球温度 (WBGT) 详情解释浮动居中弹窗
 */
@Composable
fun WbgtExplanationSheet(
    currentWbgt: Double?,
    currentUncertainty: Double?,
    onDismiss: () -> Unit
) {
    ExplanationDialog(onDismiss = onDismiss) {
        // ---- 顶部标题 ----
        Text(
            text = "湿球黑球温度 (WBGT)",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "依据 ACSM (美国运动医学会) 与日本环境省 / JSPO 运动热安全指引",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary
        )

        // ---- 当前计算值概览卡片 (若有) ----
        if (currentWbgt != null) {
            Spacer(modifier = Modifier.height(14.dp))
            val currentLevel = getWbgtLevel(currentWbgt)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(currentLevel.color.copy(alpha = 0.14f))
                    .border(1.dp, currentLevel.color.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "%.1f".format(currentWbgt),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    if (currentUncertainty != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "±${"%.1f".format(currentUncertainty)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "℃",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(
                        name = "flag",
                        contentDescription = currentLevel.name,
                        tint = currentLevel.color,
                        fillColor = currentLevel.color.copy(alpha = 0.85f),
                        size = 18.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentLevel.name,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = currentLevel.color
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ---- 各等级运动指导列表 ----
        Text(
            text = "各等级运动安全与防护指引",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))

        WBGT_LEVELS.forEach { level ->
            val isCurrent = isWbgtCurrentLevel(currentWbgt, level)
            LevelItemRow(
                label = "${level.band}   ${level.name}",
                description = level.description,
                color = level.color,
                isHighlighted = isCurrent
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---- 底部注记 ----
        Text(
            text = "注：人体热耐受受个体差异、热习服程度（是否适应高温）、衣着透气度与饮水状况显著影响。在 WBGT 达到 28 ℃ 以上时，请务必严加防范劳力性热射病，切勿盲目进行高强度耐力活动。",
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp),
            color = TextDisabled
        )
    }
}

private fun getWbgtLevel(wbgt: Double): WbgtLevelDesc {
    return when {
        wbgt < 21.0 -> WBGT_LEVELS[0]
        wbgt < 25.0 -> WBGT_LEVELS[1]
        wbgt < 28.0 -> WBGT_LEVELS[2]
        wbgt < 31.0 -> WBGT_LEVELS[3]
        else -> WBGT_LEVELS[4]
    }
}

private fun isWbgtCurrentLevel(wbgt: Double?, level: WbgtLevelDesc): Boolean {
    if (wbgt == null) return false
    return when (level.band) {
        "绿旗 (< 21.0 ℃)" -> wbgt < 21.0
        "黄旗 (21.0 – 25.0 ℃)" -> wbgt in 21.0..<25.0
        "橙旗 (25.0 – 28.0 ℃)" -> wbgt in 25.0..<28.0
        "红旗 (28.0 – 31.0 ℃)" -> wbgt in 28.0..<31.0
        "黑旗 (≥ 31.0 ℃)" -> wbgt >= 31.0
        else -> false
    }
}
