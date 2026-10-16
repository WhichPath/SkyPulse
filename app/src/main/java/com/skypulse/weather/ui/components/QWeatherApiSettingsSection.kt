package com.skypulse.weather.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.data.remote.qweather.Ed25519KeyPairPem
import com.skypulse.weather.data.remote.qweather.QWeatherConnectionResult
import com.skypulse.weather.data.remote.qweather.QWeatherCredentials
import com.skypulse.weather.data.remote.qweather.QWeatherCredentialSource
import com.skypulse.weather.ui.theme.LocalSecondaryPageTheme
import com.skypulse.weather.ui.theme.SkyPulseDesignSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 设置页「和风天气 API」配置与公钥导出分区。
 *
 * 核心特性：
 * 1. 自动为用户生成符合 RFC 8410 标准的 Ed25519 密钥对；
 * 2. 一键复制 PEM 格式公钥并计算 SHA-256 指纹，方便用户上传至和风天气控制台；
 * 3. 一键直达和风天气开发者控制台进行凭据绑定；
 * 4. 凭据仅存储于应用私有沙盒中（不污染手机根目录），覆盖安装与升级永久保留。
 */
@Composable
fun QWeatherApiSettingsSection(
    credentials: QWeatherCredentials,
    buildInjectedAvailable: Boolean,
    onSave: (projectId: String, keyId: String, privateKeyPem: String, apiHost: String) -> Unit,
    onClear: () -> Unit,
    onTest: suspend (QWeatherCredentials) -> QWeatherConnectionResult,
    onGetKeyPair: () -> Ed25519KeyPairPem = { Ed25519KeyPairPem("", "", "") },
    onRegenerateKeyPair: () -> Ed25519KeyPairPem = { Ed25519KeyPairPem("", "", "") },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val page = LocalSecondaryPageTheme.current

    var expanded by rememberSaveable { mutableStateOf(false) }
    var projectId by rememberSaveable { mutableStateOf("") }
    var keyId by rememberSaveable { mutableStateOf("") }
    var privateKeyPem by remember { mutableStateOf("") }
    var publicKeyPem by remember { mutableStateOf("") }
    var publicKeySha256 by remember { mutableStateOf("") }
    var apiHost by rememberSaveable { mutableStateOf("") }

    var showPrivateKey by rememberSaveable { mutableStateOf(false) }
    var showAdvancedKey by rememberSaveable { mutableStateOf(false) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }
    var copiedRecently by remember { mutableStateOf(false) }

    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<QWeatherConnectionResult?>(null) }

    val scope = rememberCoroutineScope()
    val isUserProvided = credentials.source == QWeatherCredentialSource.USER

    // 初始化/刷新密钥信息
    val refreshKeys = {
        val pair = onGetKeyPair()
        if (privateKeyPem.isEmpty() && pair.privateKeyPem.isNotEmpty()) {
            privateKeyPem = pair.privateKeyPem
        }
        publicKeyPem = pair.publicKeyPem
        publicKeySha256 = pair.publicKeySha256
    }

    // 展开时回填
    val backfill = {
        if (isUserProvided) {
            projectId = credentials.projectId
            keyId = credentials.keyId
            privateKeyPem = credentials.privateKeyPem
            // 无效/占位 Host 回落为空串，让输入框显示正确格式的 placeholder，
            // 避免用户看到并原样保留臆造域名
            apiHost = credentials.apiHost.takeIf { QWeatherCredentials.isApiHostUsable(it) }.orEmpty()
        } else {
            projectId = ""
            keyId = ""
            privateKeyPem = ""
            apiHost = QWeatherCredentials.DEFAULT_API_HOST
        }
        refreshKeys()
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
                // ---- 第一步：公钥导出卡片 ----
                StepHeader(stepNumber = "1", title = "复制公钥并上传到和风控制台")

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SkyPulseDesignSystem.Shapes.medium)
                        .background(if (page.isDark) Color.White.copy(alpha = 0.05f) else Color(0xFFF1F5F9))
                        .border(
                            width = 1.dp,
                            color = page.divider.copy(alpha = 0.6f),
                            shape = SkyPulseDesignSystem.Shapes.medium
                        )
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Ed25519 公钥 (Public Key)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = page.textPrimary
                            )
                            if (publicKeySha256.isNotEmpty()) {
                                Text(
                                    text = "SHA-256: ${publicKeySha256.take(17)}…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = page.textSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = publicKeyPem.ifEmpty { "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            ),
                            color = page.textPrimary.copy(alpha = 0.85f),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 三个按钮等分宽度 + 文字单行省略：
                        // 「复制公钥」变为「已复制公钥 ✓」等状态切换时按钮宽度不再抖动、不会换行
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassActionButton(
                                text = if (copiedRecently) "已复制 ✓" else "复制公钥",
                                primary = true,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (publicKeyPem.isNotEmpty()) {
                                        clipboardManager.setText(AnnotatedString(publicKeyPem))
                                        Toast.makeText(context, "公钥已复制到剪贴板，请前往控制台添加凭据", Toast.LENGTH_SHORT).show()
                                        copiedRecently = true
                                        scope.launch {
                                            delay(2000)
                                            copiedRecently = false
                                        }
                                    }
                                }
                            )

                            GlassActionButton(
                                text = "控制台",
                                primary = false,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.qweather.com/setting"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "无法打开浏览器: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            GlassActionButton(
                                text = "重新生成",
                                primary = false,
                                modifier = Modifier.weight(1f),
                                onClick = { showRegenerateConfirm = true }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ---- 第二步：凭据回填 ----
                StepHeader(stepNumber = "2", title = "填入控制台生成的凭据信息")

                QWeatherTextField(
                    value = projectId,
                    onValueChange = { projectId = it; testResult = null },
                    label = "项目 ID (Project ID)",
                    placeholder = "和风控制台创建的项目 ID"
                )

                Spacer(modifier = Modifier.height(10.dp))

                QWeatherTextField(
                    value = keyId,
                    onValueChange = { keyId = it; testResult = null },
                    label = "凭据 ID (Key ID / kid)",
                    placeholder = "添加凭据后生成的 Key ID"
                )

                Spacer(modifier = Modifier.height(10.dp))

                QWeatherTextField(
                    value = apiHost,
                    onValueChange = { apiHost = it; testResult = null },
                    label = "API Host",
                    placeholder = "控制台-设置页的专属 Host，如 xxxxxx.xx.qweatherapi.com"
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 高级选项（自定义私钥）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { showAdvancedKey = !showAdvancedKey }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = if (showAdvancedKey) "收起高级私钥设置 ▲" else "高级选项：自定义私钥 ▼",
                        style = MaterialTheme.typography.labelSmall,
                        color = page.accentBlue,
                        fontSize = 11.sp
                    )
                }

                AnimatedVisibility(visible = showAdvancedKey) {
                    Column(modifier = Modifier.padding(top = 6.dp)) {
                        QWeatherTextField(
                            value = privateKeyPem,
                            onValueChange = {
                                privateKeyPem = it
                                testResult = null
                                // 若用户手动修改了私钥，自动同步推导公钥
                                val derived = com.skypulse.weather.data.remote.qweather.QWeatherKeyManager.derivePublicKeyPem(it)
                                if (derived != null) {
                                    publicKeyPem = derived
                                    publicKeySha256 = com.skypulse.weather.data.remote.qweather.QWeatherKeyManager.calculatePublicKeySha256(derived).orEmpty()
                                }
                            },
                            label = "私钥 (Ed25519 PEM)",
                            placeholder = "-----BEGIN PRIVATE KEY-----\n…\n-----END PRIVATE KEY-----",
                            singleLine = false,
                            minLines = 3,
                            monospace = true,
                            visualTransformation = if (showPrivateKey) VisualTransformation.None else PasswordVisualTransformation(),
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
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ---- 第三步：保存与验证 ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassActionButton(
                        text = "保存并生效",
                        primary = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSave(projectId, keyId, privateKeyPem, apiHost)
                            Toast.makeText(context, "凭据已保存并立即生效", Toast.LENGTH_SHORT).show()
                            testResult = null
                        }
                    )
                    GlassActionButton(
                        text = "测试连接",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        enabled = !testing,
                        loading = testing,
                        onClick = {
                            val candidate = QWeatherCredentials(
                                projectId = projectId.trim(),
                                keyId = keyId.trim(),
                                privateKeyPem = privateKeyPem.trim(),
                                apiHost = apiHost.trim().ifEmpty { QWeatherCredentials.DEFAULT_API_HOST },
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
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onClear()
                                projectId = ""
                                keyId = ""
                                privateKeyPem = ""
                                publicKeyPem = ""
                                publicKeySha256 = ""
                                apiHost = QWeatherCredentials.DEFAULT_API_HOST
                                testResult = null
                                refreshKeys()
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
            }
        }
    }

    if (showRegenerateConfirm) {
        AlertDialog(
            onDismissRequest = { showRegenerateConfirm = false },
            title = { Text("重新生成密钥对？", fontWeight = FontWeight.SemiBold) },
            text = { Text("重新生成后将替换当前私钥与公钥。若您已在和风控制台配置了旧公钥，需将新公钥重新上传至控制台，否则现有请求将无法通过鉴权。") },
            confirmButton = {
                TextButton(onClick = {
                    showRegenerateConfirm = false
                    val newPair = onRegenerateKeyPair()
                    privateKeyPem = newPair.privateKeyPem
                    publicKeyPem = newPair.publicKeyPem
                    publicKeySha256 = newPair.publicKeySha256
                    testResult = null
                    Toast.makeText(context, "已生成全新密钥对", Toast.LENGTH_SHORT).show()
                }) {
                    Text("确认生成", color = page.accentBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateConfirm = false }) {
                    Text("取消", color = page.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun StepHeader(stepNumber: String, title: String) {
    val page = LocalSecondaryPageTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(page.accentBlue.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = page.accentBlue,
                fontSize = 10.sp
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = page.textPrimary
        )
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
        shape = ContinuousCornerShape(12.dp),
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
    loading: Boolean = false,
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
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = page.textSecondary
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                // 单行 + 省略号：按钮文案变长（如「已复制 ✓」）时只截断，绝不换行撑高
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}