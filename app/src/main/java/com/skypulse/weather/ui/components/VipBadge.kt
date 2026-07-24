package com.skypulse.weather.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.skypulse.weather.ui.theme.IosAccentBlue
import com.skypulse.weather.ui.theme.IosDividerColor
import com.skypulse.weather.ui.theme.IosTextPrimary
import com.skypulse.weather.ui.theme.IosTextSecondary

// VIP 金色渐变色
private val VipGoldStart = Color(0xFFFFD700)
private val VipGoldEnd = Color(0xFFFFA500)
private val VipGoldMid = Color(0xFFFFC125)
private val VipTextDark = Color(0xFF7A5A00)

// 磨砂玻璃浅色 - 与设置页面协调
private val GlassLight = Color(0xFFFAFAFA)
private val GlassLightMid = Color(0xFFF5F5F5)
private val GlassBorder = Color(0x33FFD700)

/**
 * VIP 永久会员勋章 — 金色渐变胶囊
 */
@Composable
fun VipBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(VipGoldStart, VipGoldMid, VipGoldEnd)
                )
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.WorkspacePremium,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = VipTextDark
        )
        Text(
            text = "永久会员",
            style = MaterialTheme.typography.labelMedium,
            color = VipTextDark,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

/**
 * VIP 设置卡片 — 方案A：磨砂玻璃 + 微光动效
 */
@Composable
fun VipStatusCard(
    deviceId: String = "",
    modifier: Modifier = Modifier
) {
    // 微光动画
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val cardShape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(GlassLight, GlassLightMid, GlassLight)
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        GlassBorder,
                        VipGoldMid.copy(alpha = 0.3f),
                        GlassBorder
                    )
                ),
                shape = cardShape
            )
    ) {
        // 微光层
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            VipGoldMid.copy(alpha = 0.05f),
                            VipGoldStart.copy(alpha = 0.10f),
                            VipGoldMid.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        start = Offset(shimmerOffset, 0f),
                        end = Offset(shimmerOffset + 300f, 300f)
                    )
                )
        )

        // 内容
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 皇冠图标 - 金色渐变背景
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                VipGoldStart.copy(alpha = 0.15f),
                                VipGoldEnd.copy(alpha = 0.15f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.WorkspacePremium,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = VipGoldMid
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "SkyPulse 永久会员",
                    style = MaterialTheme.typography.titleSmall,
                    color = VipTextDark,
                    fontWeight = FontWeight.Bold
                )
                if (deviceId.isNotEmpty()) {
                    Text(
                        text = deviceId,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        ),
                        color = VipTextDark.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}


