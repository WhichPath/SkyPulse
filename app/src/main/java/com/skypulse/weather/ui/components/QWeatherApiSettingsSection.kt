package com.skypulse.weather.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.data.remote.qweather.QWeatherConnectionResult
import com.skypulse.weather.data.remote.qweather.QWeatherCredentials
import com.skypulse.weather.data.remote.qweather.QWeatherCredentialSource
import com.skypulse.weather.ui.theme.LocalSecondaryPageTheme
import com.skypulse.weather.ui.theme.SkyPulseDesignSystem
import kotlinx.coroutines.launch

/**
 * 设置页「和风天气 API」分区。
 *
 * 目的：把密钥环节与编译解耦。默认优先使用本机填写的凭据，
 * 未填写时回退到编译期注入的内置凭据（历史行为保持不变）。
 *
 * 凭据仅写入应用私有 SharedPreferences，不随构建产物分发，
 * 因此 APK 可以在完全没有密钥的情况下编译、发布与运行。
 */
@Composable
fun QWeatherApiSettingsSection(
    credentials: QWeatherCredentials,
    buildInjectedAvailable: Boolean,
    onSave: (projectId: String, keyId: String, privateKeyPem: String, apiHost: String) -> Unit,
    onClear: () -> Unit,
    onTest: suspend (QWeatherCredentials) -> QWeatherConnectionResult,
    modifier: Modifier = Modifier
) {
    val page = LocalSecondaryPageTheme.current

    var expanded by rememberSaveable { mutableStateOf(false) }
    var projectId by rememberSaveable { mutableStateOf("") }
    var keyId by rememberSaveable { mutableStateOf("") }
    // 私钥刻意不进 rememberSaveable：SavedState 会被序列化进系统进程并落盘，
    // 不应该让密钥跟着 Activity 状态一起被持久化。
    var privateKeyPem by remember { mutableStateOf("") }
    var apiHost by rememberSaveable { mutableStateOf("") }
    var showPrivateKey by rememberSaveable { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<QWeatherConnectionResult?>(null) }

    val scope = rememberCoroutineScope()
    val isUserProvided = credentials.source == QWeatherCredentialSource.USER

    // 展开时用当前生效凭据回填输入框
    val backfill = {
        if (isUserProvided) {
            projectId = credentials.projectId
            keyId = credentials.keyId
            privateKeyPem = credentials.privateKeyPem
            apiHost = credentials.apiHost
        } else {
            projectId = ""
            keyId = ""
            privateKeyPem = ""
            apiHost = QWeatherCredentials.DEFAULT_API_HOST
        }
        testResult = null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)
            .secondaryGlassSurface(SkyPulseDesignSystem.Radius.settingsCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SkyPulseDesignSystem.TouchTarget.listRow)
                .clickable {
                    expanded = !expanded
                    if (expanded) backfill()
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LucideIcon(
                name = "key-round",
                contentDescription = "和风天气 API",
                size = 20.dp,
                tint = page.accentBlue
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "和风天气 API",
                    style = MaterialTheme.typography.bodyLarge,
                    color = page.textPrimary
                )
                Text(
                    text = credentialSourceLabel(isUserProvided, buildInjectedAvailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!isUserProvided && !buildInjectedAvailable) page.accentBlue
                    else page.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            LucideIcon(
                name = "chevron-right",
                contentDescription = if (expanded) "收起" else "展开",
                size = 18.dp,
                tint = page.textSecondary,
                modifier = Modifier.rotate(if (expanded) 90f else 0f)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(200)) + expandVertically(tween(240)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                QWeatherTextField(
                    value = projectId,
                    onValueChange = { projectId = it; testResult = null },
                    label = "项目 ID (Project ID)",
                    placeholder = "和风控制台项目 ID"
                )

                Spacer(modifier = Modifier.height(10.dp))

                QWeatherTextField(
                    value = keyId,
                    onValueChange = { keyId = it; testResult = null },
                    label = "凭据 ID (Key ID)",
                    placeholder = "JWT header 的 kid"
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column {
                    QWeatherTextField(
                        value = privateKeyPem,
                        onValueChange = { privateKeyPem = it; testResult = null },
                        label = "私钥 (Ed25519 PEM)",
                        placeholder = "-----BEGIN PRIVATE KEY-----\n…\n-----END PRIVATE KEY-----",
                        singleLine = false,
                        minLines = 4,
                        monospace = true,
                        visualTransformation = if (showPrivateKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailing = {
                            Text(
                                text = if (showPrivateKey) "隐藏" else "显示",
                                style = MaterialTheme.typography.labelMedium,
                                color = page.accentBlue,
                                modifier = Modifier
                                    .clickable { showPrivateKey = !showPrivateKey }
                                    .padding(horizontal = 4.dp, vertical = 6.dp)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                QWeatherTextField(
                    value = apiHost,
                    onValueChange = { apiHost = it; testResult = null },
                    label = "API Host",
                    placeholder = QWeatherCredentials.DEFAULT_API_HOST
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassActionButton(
                        text = "保存",
                        primary = true,
                        onClick = {
                            onSave(projectId, keyId, privateKeyPem, apiHost)
                            testResult = null
                        }
                    )
                    GlassActionButton(
                        text = "测试连接",
                        primary = false,
                        enabled = !testing,
                        onClick = {
                            val candidate = QWeatherCredentials(
                                projectId = projectId.trim(),
                                keyId = keyId.trim(),
                                privateKeyPem = privateKeyPem.trim(),
                                apiHost = apiHost.trim()
                                    .ifEmpty { QWeatherCredentials.DEFAULT_API_HOST },
                                source = QWeatherCredentialSource.USER
                            )
                            testing = true
                            testResult = null
                            scope.launch {
                                testResult = onTest(candidate)
                                testing = false
                            }
                        }
                    )
                    if (isUserProvided) {
                        GlassActionButton(
                            text = "清除",
                            primary = false,
                            onClick = {
                                onClear()
                                projectId = ""
                                keyId = ""
                                privateKeyPem = ""
                                apiHost = QWeatherCredentials.DEFAULT_API_HOST
                                testResult = null
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val result = testResult
                if (result != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val isSuccess = result is QWeatherConnectionResult.Success
                        val tint = if (isSuccess) page.accentGreen else page.accentBlue
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(tint)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (result) {
                                is QWeatherConnectionResult.Success -> result.detail
                                is QWeatherConnectionResult.Failure -> result.message
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = tint,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Text(
                    text = if (buildInjectedAvailable) {
                        "密钥仅保存在本机应用私有目录，不会随应用更新分发；留空则继续使用编译内置的凭据。"
                    } else {
                        "当前安装包未内置任何凭据，请填写你自己的和风天气密钥后使用。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = page.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

private fun credentialSourceLabel(isUserProvided: Boolean, buildInjectedAvailable: Boolean): String = when {
    isUserProvided -> "当前来源：本机填写"
    buildInjectedAvailable -> "当前来源：编译内置"
    else -> "当前来源：未配置"
}

@Composable
private fun QWeatherTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    singleLine: Boolean = true,
    minLines: Int = 1,
    monospace: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    val page = LocalSecondaryPageTheme.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = { Text(placeholder, fontSize = 12.sp, maxLines = 3) },
        singleLine = singleLine,
        minLines = minLines,
        trailingIcon = trailing,
        visualTransformation = visualTransformation,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 13.sp,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = page.accentBlue,
            unfocusedBorderColor = page.divider,
            focusedContainerColor = if (page.isDark) Color.White.copy(alpha = 0.04f) else Color.White,
            unfocusedContainerColor = if (page.isDark) Color.White.copy(alpha = 0.04f) else Color.White,
            focusedTextColor = page.textPrimary,
            unfocusedTextColor = page.textPrimary,
            cursorColor = page.accentBlue,
            focusedLabelColor = page.accentBlue,
            unfocusedLabelColor = page.textSecondary,
            focusedPlaceholderColor = page.textSecondary,
            unfocusedPlaceholderColor = page.textSecondary
        )
    )
}

@Composable
private fun GlassActionButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val page = LocalSecondaryPageTheme.current
    val bg = when {
        !enabled -> page.divider
        primary -> page.accentBlue
        else -> if (page.isDark) Color.White.copy(alpha = 0.10f) else Color.White
    }
    val fg = when {
        !enabled -> page.textSecondary
        primary -> Color.White
        else -> page.textPrimary
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .border(
                width = 1.dp,
                color = if (primary && enabled) Color.Transparent else page.divider,
                shape = CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (text == "测试连接" && !enabled) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = page.textSecondary
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                fontSize = 13.sp
            )
        }
    }
}