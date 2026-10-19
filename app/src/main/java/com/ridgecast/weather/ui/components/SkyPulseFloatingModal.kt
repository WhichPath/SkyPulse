package com.ridgecast.weather.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ridgecast.weather.ui.theme.SkyPulseDesignSystem

/**
 * 统一样式的全应用浮动毛玻璃详情弹窗/说明浮层基础组件。
 *
 * 核心规范：
 * 1. 纯淡入淡出动画（fadeIn/fadeOut），彻底消除尺寸缩放带来的收窄和中心缩进瑕疵；
 * 2. 外部点击半透明遮罩留白区域直接退出，原生 BackHandler 拦截返回；
 * 3. 内部搭载 [floatingGlassSurface] 磨砂玻璃光场材质与苹果连续超椭圆圆角；
 * 4. 自动适配纵向最大高度（屏幕高度 82%）并支持内部纵向顺滑滚动，防止长内容溢出屏幕；
 * 5. 阻止内部点击穿透触发关闭。
 *
 * @param visible 是否可见
 * @param onDismiss 退出回调
 * @param modifier 弹窗内部卡片的修饰符
 * @param radius 弹窗圆角半径
 * @param scrollable 是否启用卡片内部纵向滚动
 * @param content 弹窗内部插槽内容
 */
@Composable
fun SkyPulseFloatingModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = SkyPulseDesignSystem.Radius.extraLarge,
    scrollable: Boolean = true,
    content: @Composable () -> Unit
) {
    if (visible) {
        BackHandler { onDismiss() }
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = screenHeight * 0.82f

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 220)),
        exit = fadeOut(animationSpec = tween(durationMillis = 180))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(
                    horizontal = SkyPulseDesignSystem.Spacing.modalHorizontalMargin,
                    vertical = SkyPulseDesignSystem.Spacing.modalVerticalMargin
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .floatingGlassSurface(radius)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // 阻止内部点击触发外部 dismiss
                    )
                    .padding(horizontal = 20.dp, vertical = 22.dp)
                    .then(
                        if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
                    )
            ) {
                content()
            }
        }
    }
}

/**
 * 具有数据快照保持特性的浮动弹窗组件。
 *
 * 解决常见 Compose 动画缺陷：当外部驱动数据 [data] 变为 null 时（例如点击关闭触发状态置空），
 * 组件在淡出动画（fadeOut 180ms）运行期间自动保持上一次非空的 [data] 快照，
 * 彻底防止子视图因数据变为 null 瞬间清空而导致卡片坍塌为小框框的穿帮现象。
 *
 * @param data 驱动弹窗显示的数据，非空时显示，为 null 时淡出退出
 * @param onDismiss 退出回调
 * @param modifier 弹窗内部卡片的修饰符
 * @param radius 弹窗圆角半径
 * @param scrollable 是否启用卡片内部纵向滚动
 * @param content 弹窗内部内容插槽，接收非空的数据快照
 */
@Composable
fun <T : Any> FloatingStateModal(
    data: T?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = SkyPulseDesignSystem.Radius.extraLarge,
    scrollable: Boolean = true,
    content: @Composable (T) -> Unit
) {
    var cachedData by remember { mutableStateOf(data) }
    LaunchedEffect(data) {
        if (data != null) {
            cachedData = data
        }
    }

    SkyPulseFloatingModal(
        visible = data != null,
        onDismiss = onDismiss,
        modifier = modifier,
        radius = radius,
        scrollable = scrollable
    ) {
        cachedData?.let { current ->
            content(current)
        }
    }
}


@Composable
fun FloatingModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = SkyPulseDesignSystem.Radius.extraLarge,
    scrollable: Boolean = true,
    content: @Composable () -> Unit
) = SkyPulseFloatingModal(
    visible = visible,
    onDismiss = onDismiss,
    modifier = modifier,
    radius = radius,
    scrollable = scrollable,
    content = content
)
