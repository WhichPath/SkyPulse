package com.ridgecast.weather.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridgecast.weather.model.DataIssue
import com.ridgecast.weather.ui.theme.SkyPulseDesignSystem
import com.ridgecast.weather.ui.theme.TextPrimary
import com.ridgecast.weather.ui.theme.TextSecondary

@Composable
fun DataNoticeBanner(
    issues: List<DataIssue>,
    modifier: Modifier = Modifier
) {
    if (issues.isEmpty()) return
    GlassCard(
        modifier = modifier.padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal),
        radius = SkyPulseDesignSystem.Radius.pill
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFFA726)
                )
                Spacer(modifier = Modifier.height(0.dp).padding(start = 8.dp))
                Text(
                    text = "数据提醒",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            issues.forEach { issue ->
                val prefix = when (issue.severity) {
                    "error" -> "⚠ "
                    else -> "• "
                }
                Text(
                    text = "$prefix${issue.message}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            val hasLocal = issues.any { it.localResolution }
            if (hasLocal) {
                Text(
                    text = "部分指标已由本地规则兜底运算。",
                    color = TextSecondary.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}