package com.ridgecast.weather.ui.screen

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.ridgecast.weather.ui.glass.weatherPageBackground
import com.ridgecast.weather.data.WeatherSettings
import com.ridgecast.weather.data.ThemeMode
import com.ridgecast.weather.domain.CitySelectionPolicy
import com.ridgecast.weather.model.sortedByPublishTimeDescending
import com.ridgecast.weather.util.AqhiCalculator
import com.ridgecast.weather.util.WeatherUtils
import com.ridgecast.weather.util.skyGradientColorStops
import com.ridgecast.weather.ui.components.*
import com.ridgecast.weather.ui.theme.*
import com.ridgecast.weather.viewmodel.AppScreen
import com.ridgecast.weather.viewmodel.CitySearchViewModel
import com.ridgecast.weather.viewmodel.RefreshPhase
import com.ridgecast.weather.viewmodel.SettingsViewModel
import com.ridgecast.weather.viewmodel.WeatherUiState
import com.ridgecast.weather.viewmodel.WeatherViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val LocalSkipCardAnimation = compositionLocalOf { false }

// 预警标题清洗正则：编译一次，避免在 WeatherContentBody 每次重组时重复编译
private val AlertTitleBracketRegex = Regex("\\[.*?\\]")
private val AlertTitleVerbRegex = Regex("^.*(?:\u53D1\u5E03|\u53D8\u66F4|\u89E3\u9664|\u7EE7\u7EED|\u66F4\u65B0)")
private val AlertTitleSuffixRegex = Regex("\u9884\u8B66.*$")

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel = hiltViewModel(),
    searchViewModel: CitySearchViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshPhase by viewModel.refreshPhase.collectAsStateWithLifecycle()
    val isLocating by viewModel.isLocating.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val savedCities by viewModel.savedCities.collectAsStateWithLifecycle()
    val cityWeatherMap by viewModel.cityWeatherMap.collectAsStateWithLifecycle()
    val searchResults by searchViewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by searchViewModel.isSearching.collectAsStateWithLifecycle()
    val isSearchActive by searchViewModel.isSearchActive.collectAsStateWithLifecycle()
    val selectedAlertIndex by viewModel.selectedAlertIndex.collectAsStateWithLifecycle()
    val selectedCityId by viewModel.selectedCityId.collectAsStateWithLifecycle()
    val onboardingReady by viewModel.onboardingReady.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val qWeatherCredentials by settingsViewModel.credentials.collectAsStateWithLifecycle()
    val effectiveLocationName by remember {
        derivedStateOf {
            when (val s = uiState) {
                is WeatherUiState.Success -> {
                    if (s.locationName.contains("Mountain View") || s.locationName.contains("美国") || s.locationName == "定位中...") {
                        "北京市"
                    } else {
                        s.locationName
                    }
                }
                else -> "北京市"
            }
        }
    }

    val effectiveCities by remember { derivedStateOf { savedCities } }

    val currentCityIndex by remember {
        derivedStateOf {
            CitySelectionPolicy.currentIndex(effectiveCities, selectedCityId)
        }
    }

    var previousScreen by remember { mutableStateOf(currentScreen) }
    val justEnteredCityDetail = remember { mutableStateOf(true) }
    LaunchedEffect(currentScreen) {
        if (currentScreen == AppScreen.CityDetail && previousScreen != AppScreen.CityDetail) {
            justEnteredCityDetail.value = true
            delay(600)
            justEnteredCityDetail.value = false
        }
        previousScreen = currentScreen
    }

    val showOnboarding by viewModel.showOnboarding.collectAsStateWithLifecycle()
    var allPermissionsHandled by rememberSaveable { mutableStateOf(false) }
    var homeBootstrapStarted by rememberSaveable { mutableStateOf(false) }

    var backgroundTimestamp by remember { mutableLongStateOf(0L) }
    var skipLifecycleCardAnimation by remember { mutableStateOf(false) }
    val lifecycleAnimationScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> backgroundTimestamp = System.currentTimeMillis()
                Lifecycle.Event.ON_RESUME -> {
                    if (backgroundTimestamp > 0L) {
                        skipLifecycleCardAnimation = true
                        lifecycleAnimationScope.launch {
                            delay(SkyPulseDesignSystem.Motion.lifecycleSkipMillis)
                            skipLifecycleCardAnimation = false
                        }
                        viewModel.onResume()
                        backgroundTimestamp = 0L
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(onboardingReady, allPermissionsHandled) {
        if (!onboardingReady || homeBootstrapStarted) return@LaunchedEffect
        if (showOnboarding) {
            if (!allPermissionsHandled) return@LaunchedEffect
            viewModel.completeOnboarding()
        }
        homeBootstrapStarted = true
        viewModel.ensureCurrentLocationCitySync()
        viewModel.fetchWeather()
    }

    val skycon = when (val s = uiState) {
        is WeatherUiState.Success -> s.weather.result?.realtime?.skycon
        else -> null
    }
    val daily = when (val s = uiState) {
        is WeatherUiState.Success -> s.weather.result?.daily
        else -> null
    }
    // 天文快照（接口优先的太阳高度角/方位角与月相照度）
    val astronomy = when (val s = uiState) {
        is WeatherUiState.Success -> s.weather.result?.astronomy
        else -> null
    }
    // 实时风数据（驱动粒子动画：雨丝倾斜、云层漂移方向）
    val wind = when (val s = uiState) {
        is WeatherUiState.Success -> s.weather.result?.realtime?.wind?.let { w ->
            val speed = w.speed
            val direction = w.direction
            if (speed != null && direction != null) {
                WindInfo(speedKmh = speed.toFloat(), directionDeg = direction.toFloat())
            } else {
                null
            }
        }
        else -> null
    }
    val isDay = WeatherUtils.isCurrentlyDay(daily)
    val weatherTheme = remember(skycon, daily, isDay) {
        WeatherUtils.getWeatherTheme(skycon, isDay)
    }

    BackHandler(enabled = currentScreen != AppScreen.CityDetail) {
        searchViewModel.clearSearchResults()
        viewModel.navigateBack()
    }

    // 主页沉浸天空恒用浅色图标；次级页面跟随深浅色主题（浅色主题深色图标，深色主题浅色图标）
    val isSystemDark = isSystemInDarkTheme()
    val resolvedDark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemDark
    }
    SetLightStatusBarEffect(lightStatusBar = currentScreen != AppScreen.CityDetail && !resolvedDark)

    if (!onboardingReady) {
        LoadingShimmer(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        )
        return
    }

    if (showOnboarding && !allPermissionsHandled) {
        val context = LocalContext.current
        PermissionOnboardingScreen(
            onFinished = { allPermissionsHandled = true },
            onPermissionDenied = {
                (context as? Activity)?.finish()
            }
        )
        return
    }

    // 全局玻璃光源：太阳 + 月亮两个真实光源（接口优先，本地 NOAA/Meeus 兜底）
    // 整屏共用一个时钟源，城市分页里的每个城市再按各自经纬度算自己的光照。
    val nowMillis = rememberNowMillis()
    val currentCityCoordinates by remember(effectiveCities, currentCityIndex) {
        derivedStateOf {
            effectiveCities.getOrNull(currentCityIndex)?.let { it.latitude to it.longitude }
        }
    }
    val glassLight = rememberGlassLight(
        daily = daily,
        astronomy = astronomy,
        latitude = currentCityCoordinates?.first,
        longitude = currentCityCoordinates?.second,
        nowMillis = nowMillis
    )

    // 次级页面（城市管理/预警详情/设置）深浅色配色，主页与小组件不受影响
    val secondaryPageColors = if (resolvedDark) SecondaryPageDarkColors else SecondaryPageLightColors

    CompositionLocalProvider(
        LocalWeatherTheme provides weatherTheme,
        LocalSecondaryPageTheme provides secondaryPageColors,
        LocalGlassLight provides glassLight
    ) {
        // 次级页（城市管理/设置/预警详情）玻璃背景源：屏幕底色刷（天气着色渐变）。
        // 主页 CityDetail 分支内层 WeatherBackground 会用自己的 LayerBackdrop 覆盖
        // LocalGlassBackdrop，因此两层互不干扰。
        val rootGlassBackdrop = rememberLayerBackdrop()
        val rootPowerSave = rememberPowerSaveMode()
        CompositionLocalProvider(
            LocalGlassBackdrop provides (if (rootPowerSave) null else rootGlassBackdrop)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(rootGlassBackdrop)
                        .background(currentScreen.screenBackgroundBrush(weatherTheme, secondaryPageColors))
                )
                AnimatedContent(
                targetState = currentScreen,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { skyPulseScreenTransition() },
                label = "screen_transition"
            ) { targetScreen ->
                when (targetScreen) {
            AppScreen.CityList -> {
                CityListScreen(
                    cities = savedCities,
                    cityWeatherMap = cityWeatherMap,
                    searchResults = searchResults,
                    isSearching = isSearching,
                    isSearchActive = isSearchActive,
                    onCityClick = { cityId -> viewModel.navigateToCityDetail(cityId) },
                    onAddCity = { result -> viewModel.addCity(result.savedName, result.longitude, result.latitude) },
                    onRemoveCity = { cityId -> viewModel.removeCity(cityId) },
                    onSearch = { query -> searchViewModel.searchCities(query) },
                    onClearSearch = { searchViewModel.clearSearchResults() },
                    onBack = {
                        searchViewModel.clearSearchResults()
                        viewModel.navigateBack()
                    }
                )
            }

            AppScreen.CityDetail -> {
                var activeExplanation by remember { mutableStateOf<ActiveExplanation?>(null) }
                // 弹窗与主页共用同一个背景源：玻璃采样的是弹窗背后真实的天气内容，
                // 而不是根级次级页底色。
                val cityGlassBackdrop = rememberLayerBackdrop()
                val explanationController = remember {
                    object : ExplanationController {
                        override fun showAqi(aqi: Int?) {
                            activeExplanation = ActiveExplanation.Aqi(aqi)
                        }
                        override fun showAqhi(aqhi: AqhiCalculator.AqhiResult?) {
                            activeExplanation = ActiveExplanation.Aqhi(aqhi)
                        }
                        override fun showWbgt(wbgt: Double?, uncertainty: Double?) {
                            activeExplanation = ActiveExplanation.Wbgt(wbgt, uncertainty)
                        }
                        override fun showAlert(alerts: List<com.ridgecast.weather.model.AlertContent>, initialIndex: Int, attributions: List<String>?) {
                            activeExplanation = ActiveExplanation.Alert(alerts, initialIndex, attributions)
                        }
                        override fun dismiss() {
                            activeExplanation = null
                        }
                    }
                }

                val blurRadius by animateDpAsState(
                    targetValue = if (activeExplanation != null) 24.dp else 0.dp,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                    label = "explanation_blur"
                )

                CompositionLocalProvider(
                    LocalExplanationController provides explanationController
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WeatherBackground(
                            skycon = skycon,
                            daily = daily,
                            wind = wind,
                            modifier = if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier,
                            backdrop = cityGlassBackdrop
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                when (val state = uiState) {
                                    is WeatherUiState.Loading -> {
                                        LoadingShimmer(
                                            modifier = Modifier.fillMaxSize().statusBarsPadding()
                                        )
                                    }
                            is WeatherUiState.Success -> {
                                val pagerState = rememberPagerState(
                                    initialPage = currentCityIndex,
                                    pageCount = { effectiveCities.size }
                                )

                                // Sync from ViewModel selection to PagerState
                                LaunchedEffect(selectedCityId, effectiveCities) {
                                    val targetIndex = effectiveCities.indexOfFirst { it.id == selectedCityId }
                                    if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
                                        pagerState.scrollToPage(targetIndex)
                                    }
                                }

                                // Sync from PagerState swiping to ViewModel
                                LaunchedEffect(pagerState.currentPage) {
                                    if (pagerState.currentPage < effectiveCities.size) {
                                        val targetCity = effectiveCities[pagerState.currentPage]
                                        if (targetCity.id != selectedCityId) {
                                            viewModel.navigateToCityDetail(targetCity.id)
                                        }
                                    }
                                }

                                val scrollStates = remember { mutableStateMapOf<String, ScrollState>() }
                                val activeCityId = effectiveCities.getOrNull(pagerState.currentPage)?.id ?: "current_location"
                                val activeScrollState = scrollStates[activeCityId]
                                val isScrolled by remember(activeScrollState) {
                                    derivedStateOf { (activeScrollState?.value ?: 0) > 0 }
                                }
                                var cityPagerScrollEnabled by remember { mutableStateOf(true) }
                                val setCityPagerScrollEnabled = remember {
                                    { enabled: Boolean -> cityPagerScrollEnabled = enabled }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .statusBarsPadding()
                                ) {
                                    Spacer(modifier = Modifier.height(12.dp))

                                    LocationHeader(
                                        locationName = effectiveLocationName,
                                        isLocating = isLocating,
                                        refreshPhase = refreshPhase,
                                        onListClick = { viewModel.navigateToCityList() },
                                        onSettingsClick = { viewModel.navigateToSettings() }
                                    )

                                    CityDotBar(
                                        cityCount = effectiveCities.size,
                                        currentIndex = pagerState.currentPage,
                                        isScrolled = isScrolled
                                    )

                                    CompositionLocalProvider(
                                        LocalCityPagerScrollEnabled provides setCityPagerScrollEnabled,
                                        LocalSkipCardAnimation provides (
                                            pagerState.isScrollInProgress ||
                                                justEnteredCityDetail.value ||
                                                skipLifecycleCardAnimation
                                            )
                                    ) {
                                        HorizontalPager(
                                            state = pagerState,
                                            modifier = Modifier.fillMaxSize(),
                                            userScrollEnabled = cityPagerScrollEnabled,
                                            key = { page -> effectiveCities.getOrNull(page)?.id ?: page.toString() }
                                        ) { page ->
                                            val city = effectiveCities.getOrNull(page)
                                            val contentState = remember(city, cityWeatherMap, effectiveLocationName, state.weather) {
                                                val weather = city?.let { cityWeatherMap[it.id]?.weather } ?: state.weather
                                                val resolvedName = if (city?.isCurrentLocation == true) {
                                                    effectiveLocationName
                                                } else {
                                                    city?.name?.takeIf { !it.contains("Mountain View") && !it.contains("美国") } ?: "北京市"
                                                }
                                                WeatherUiState.Success(
                                                    weather = weather,
                                                    locationName = resolvedName
                                                )
                                            }
                                            // 收藏状态：定位城市看是否已收藏，克隆城市看是否接近定位城市
                                            val isBookmarked = remember(city, savedCities) {
                                                if (city?.isCurrentLocation == true) {
                                                    viewModel.isCurrentLocationBookmarked
                                                } else if (city != null) {
                                                    viewModel.isBookmarkedCity(city)
                                                } else {
                                                    false
                                                }
                                            }
                                            val showBookmarkBtn = remember(city, isBookmarked) {
                                                (city?.isCurrentLocation == true && !isBookmarked) ||
                                                    (city?.isCurrentLocation != true && isBookmarked)
                                            }
                                            val pageScrollState = scrollStates.getOrPut(city?.id ?: "current_location") { ScrollState(0) }
                                            if (contentState != null) {
                                                // 每个城市按**自己的经纬度**计算光照：
                                                // 日出日落/太阳高度角随地点不同，高光方向与强度也随之变化。
                                                val cityGlassLight = rememberGlassLight(
                                                    daily = contentState.weather.result?.daily,
                                                    astronomy = contentState.weather.result?.astronomy,
                                                    latitude = city?.latitude,
                                                    longitude = city?.longitude,
                                                    nowMillis = nowMillis
                                                )
                                                CompositionLocalProvider(
                                                    LocalGlassLight provides cityGlassLight
                                                ) {
                                                    WeatherContentBody(
                                                        state = contentState,
                                                        scrollState = pageScrollState,
                                                        settings = settings,
                                                        onRefresh = { viewModel.refresh() },
                                                        onAlertClick = { idx ->
                                                            val successState = uiState as? WeatherUiState.Success
                                                            val alertMeta = successState?.weather?.result?.alert
                                                            val contents = alertMeta?.content.orEmpty()
                                                            activeExplanation = ActiveExplanation.Alert(
                                                                alerts = contents,
                                                                initialIndex = idx,
                                                                attributions = alertMeta?.attributions
                                                            )
                                                        },
                                                        showBookmark = showBookmarkBtn,
                                                        isBookmarked = isBookmarked,
                                                        onBookmarkClick = {
                                                            if (city?.isCurrentLocation == true) {
                                                                viewModel.bookmarkCurrentLocation()
                                                            } else if (city != null) {
                                                                // 取消收藏：先回到定位城市，再删除
                                                                val currentLocCity = savedCities.firstOrNull { it.isCurrentLocation }
                                                                if (currentLocCity != null) {
                                                                    viewModel.navigateToCityDetail(currentLocCity.id)
                                                                }
                                                                viewModel.removeCity(city.id)
                                                            }
                                                        },
                                                        onRadarClick = { viewModel.navigateToRadarMap() }
                                                    )
                                                }
                                            } else {
                                                LoadingShimmer(modifier = Modifier.fillMaxSize())
                                            }
                                        }
                                    }
                                }
                            }
                            is WeatherUiState.Error -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .statusBarsPadding()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    LocationHeader(
                                        locationName = "加载失败",
                                        isLocating = false,
                                        refreshPhase = RefreshPhase.Idle,
                                        onListClick = { viewModel.navigateToCityList() },
                                        onSettingsClick = { viewModel.navigateToSettings() }
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = TextPrimary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { viewModel.fetchWeather() },
                                        shape = SkyPulseDesignSystem.Shapes.medium
                                    ) {
                                        Text("重试定位")
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // 浮动毛玻璃详情面板 Overlay (同层渲染，底层完美硬件高斯模糊)
                CompositionLocalProvider(LocalGlassBackdrop provides cityGlassBackdrop) {
                    ExplanationModalOverlay(
                        active = activeExplanation,
                        onDismiss = { activeExplanation = null }
                    )
                }
            }
        }
    }

            AppScreen.Settings -> {
                SettingsScreen(
                    onBack = { viewModel.navigateBack() },
                    settings = settings,
                    onRainAlertChange = { settingsViewModel.setRainAlert(it) },
                    onWarningAlertChange = { settingsViewModel.setWarningAlert(it) },
                    onTempChangeAlertChange = { settingsViewModel.setTempChangeAlert(it) },
                    onWindAlertChange = { settingsViewModel.setWindAlert(it) },
                    onTyphoonAlertChange = { settingsViewModel.setTyphoonAlert(it) },
                    onShowHourlyAqiChange = { settingsViewModel.setShowHourlyAqi(it) },
                    onShowHourlyUvChange = { settingsViewModel.setShowHourlyUv(it) },
                    onShowHourlyWindChange = { settingsViewModel.setShowHourlyWind(it) },
                    onShowHourlyWindGustChange = { settingsViewModel.setShowHourlyWindGust(it) },
                    onShowCardDetailChange = { settingsViewModel.setShowCardDetail(it) },
                    onShowCardSunriseSunsetChange = { settingsViewModel.setShowCardSunriseSunset(it) },
                    onShowCardMinutelyChange = { settingsViewModel.setShowCardMinutely(it) },
                    onShowCardTyphoonChange = { settingsViewModel.setShowCardTyphoon(it) },
                    onThemeModeChange = { settingsViewModel.setThemeMode(it) },
                    qWeatherCredentials = qWeatherCredentials,
                    hasBuildInjectedCredentials = settingsViewModel.hasBuildInjectedCredentials,
                    onSaveQWeatherCredentials = { projectId, keyId, pem, host ->
                        settingsViewModel.saveQWeatherCredentials(projectId, keyId, pem, host)
                    },
                    onClearQWeatherCredentials = { settingsViewModel.clearQWeatherCredentials() },
                    onTestQWeatherCredentials = { settingsViewModel.testQWeatherCredentials(it) },
                    onGetKeyPair = { settingsViewModel.getOrGenerateKeyPair() },
                    onRegenerateKeyPair = { settingsViewModel.regenerateKeyPair() }
                )
            }

            AppScreen.AlertDetail -> {
                val alertMeta = when (val s = uiState) {
                    is WeatherUiState.Success -> s.weather.result?.alert
                    else -> null
                }
                val contents = alertMeta?.content.orEmpty().sortedByPublishTimeDescending()
                AlertDetailScreen(
                    alerts = contents,
                    initialSelectedIndex = selectedAlertIndex,
                    attributions = alertMeta?.attributions,
                    onBack = { viewModel.navigateBack() }
                )
            }

            AppScreen.RadarMap -> {
                val currentCity = remember {
                    savedCities.find { it.id == selectedCityId }
                        ?: savedCities.firstOrNull { it.isCurrentLocation }
                        ?: savedCities.firstOrNull()
                }
                RadarMapScreen(
                    latitude = currentCity?.latitude,
                    longitude = currentCity?.longitude,
                    locationName = effectiveLocationName,
                    onBack = { viewModel.navigateBack() }
                )
            }
        }
        }
        }
        }
    }
}

private fun dampedPullOffsetPx(
    progress: Float,
    maxOffsetPx: Float
): Float {
    val clampedProgress = progress.coerceAtLeast(0f)
    if (clampedProgress <= 1f) {
        return maxOffsetPx * 0.72f * clampedProgress
    }

    val extraProgress = (clampedProgress - 1f).coerceAtMost(2f)
    val dampedExtra = 1f - kotlin.math.exp(-extraProgress * 1.35f)
    return (maxOffsetPx * 0.72f + maxOffsetPx * 0.28f * dampedExtra)
        .coerceAtMost(maxOffsetPx)
}

private fun AnimatedContentTransitionScope<AppScreen>.skyPulseScreenTransition(): ContentTransform {
    // 台风路径是持续重绘的 WebView 全屏地图：过渡动画中的缩放/位移会强制 WebView
    // 走分层合成，且动画期间页面仍在重绘，极易掉帧。这里仅用物理弹簧淡入淡出，接近浏览器体验。
    if (initialState == AppScreen.RadarMap || targetState == AppScreen.RadarMap) {
        return (fadeIn(animationSpec = SkyPulseDesignSystem.Motion.springEffects) togetherWith
            fadeOut(animationSpec = SkyPulseDesignSystem.Motion.springEffects)) using
            SizeTransform(clip = false)
    }
    val direction = if (targetState.screenOrder >= initialState.screenOrder) 1 else -1
    val enterSpec = SkyPulseDesignSystem.Motion.springSpatialIntOffset
    val exitSpec = SkyPulseDesignSystem.Motion.springSpatialExit
    val fadeInSpec = SkyPulseDesignSystem.Motion.springEffects
    val fadeOutSpec = SkyPulseDesignSystem.Motion.springEffects
    val scaleSpec = SkyPulseDesignSystem.Motion.springSpatialExpressive

    return (slideInHorizontally(animationSpec = enterSpec) { width -> direction * width / 8 } +
        fadeIn(animationSpec = fadeInSpec) +
        scaleIn(initialScale = 0.985f, animationSpec = scaleSpec)) togetherWith
        (slideOutHorizontally(animationSpec = exitSpec) { width -> -direction * width / 16 } +
            fadeOut(animationSpec = fadeOutSpec) +
            scaleOut(targetScale = 0.995f, animationSpec = scaleSpec)) using
        SizeTransform(clip = true)
}

private fun AppScreen.screenBackgroundBrush(weatherTheme: WeatherTheme, secondaryPageColors: SecondaryPageColors): Brush {
    return when (this) {
        AppScreen.CityDetail -> Brush.verticalGradient(colorStops = *skyGradientColorStops(weatherTheme.backgroundGradient))
        // 次级页与主页同源：色相取自当前天气，再按深浅色模式调整明度，
        // 保证既看得出天气、又不会牺牲文字对比度
        AppScreen.CityList,
        AppScreen.Settings,
        AppScreen.AlertDetail -> weatherPageBackground(
            weatherGradient = weatherTheme.backgroundGradient,
            isDark = secondaryPageColors.isDark
        )
        AppScreen.RadarMap -> Brush.verticalGradient(
            listOf(secondaryPageColors.background, secondaryPageColors.background)
        )
    }
}

private val AppScreen.screenOrder: Int
    get() = when (this) {
        AppScreen.CityList -> -1
        AppScreen.CityDetail -> 0
        AppScreen.Settings -> 1
        AppScreen.AlertDetail -> 1
        AppScreen.RadarMap -> 1
    }

// ==================== Helper Composables ====================

@Composable
private fun CityDotBar(
    cityCount: Int,
    currentIndex: Int,
    isScrolled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dotsAlpha = animateFloatAsState(
        targetValue = if (isScrolled) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "dotsAlpha"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        if (cityCount > 1) {
            val dotColor = TextPrimary.copy(alpha = 0.5f)
            val activeDotColor = TextPrimary
            val dotRadius = 2.5.dp
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 26.dp)
                    .alpha(dotsAlpha.value),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until cityCount) {
                    val isActive = i == currentIndex
                    Canvas(
                        modifier = Modifier
                            .size(dotRadius * 2)
                            .alpha(if (isActive) 1f else 0.5f)
                    ) {
                        drawCircle(
                            color = if (isActive) activeDotColor else dotColor,
                            radius = dotRadius.toPx()
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
private fun WeatherContentBody(
    state: WeatherUiState.Success,
    scrollState: ScrollState,
    settings: WeatherSettings,
    onRefresh: () -> Unit = {},
    onAlertClick: (Int) -> Unit = {},
    showBookmark: Boolean = false,
    isBookmarked: Boolean = false,
    onBookmarkClick: () -> Unit = {},
    onRadarClick: () -> Unit = {}
) {
    val result = state.weather.result
    val realtime = result?.realtime
    val todayTemp = WeatherUtils.todayTemperature(result?.daily)
    val alertContents = remember(result?.alert?.content) {
        result?.alert?.content.orEmpty().sortedByPublishTimeDescending()
    }
    val alerts = alertContents.mapNotNull { content ->
        val title = content.title
            ?.replace(AlertTitleBracketRegex, "")
            ?.replace(AlertTitleVerbRegex, "")
            ?.replace(AlertTitleSuffixRegex, "\u9884\u8B66")
            ?.trim()
        if (!title.isNullOrBlank()) AlertItem(title, content.level) else null
    }

    val haptic = LocalHapticFeedback.current
    val pullRefreshState = rememberPullRefreshState(
        refreshing = false,
        onRefresh = onRefresh
    )
    val maxPullOffsetPx = with(LocalDensity.current) { 40.dp.toPx() }
    val pullOffsetPx = dampedPullOffsetPx(
        progress = pullRefreshState.progress,
        maxOffsetPx = maxPullOffsetPx
    )
    val displayedPullOffsetPx = remember { Animatable(0f) }

    LaunchedEffect(pullOffsetPx) {
        val currentOffset = displayedPullOffsetPx.value
        if (pullOffsetPx >= currentOffset) {
            displayedPullOffsetPx.snapTo(pullOffsetPx)
        } else {
            displayedPullOffsetPx.animateTo(
                targetValue = pullOffsetPx,
                animationSpec = tween(
                    durationMillis = 260,
                    easing = LinearOutSlowInEasing
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .graphicsLayer { translationY = displayedPullOffsetPx.value }
        ) {
            val dataIssues = state.weather.dataIssues.orEmpty()
            DataNoticeBanner(issues = dataIssues)

            AlertBannerSlot(alerts = alerts, onClick = { idx ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onAlertClick(idx)
            }, showBookmark = showBookmark, isBookmarked = isBookmarked, onBookmarkClick = onBookmarkClick)

            CurrentWeather(
                realtime = realtime,
                todayHigh = todayTemp?.max,
                todayLow = todayTemp?.min
            )

            Spacer(modifier = Modifier.height(32.dp))

            result?.forecastKeypoint?.let { keypoint ->
                GlassCard(
                    modifier = Modifier.padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal),
                    radius = SkyPulseDesignSystem.Radius.pill
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = keypoint,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.2.sp
                            ),
                            color = TextPrimary.copy(alpha = 0.92f),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(SkyPulseDesignSystem.Spacing.sectionGap))
            }

            val minutelyData = result?.minutely?.precipitation_2h
            val showMinutely = !minutelyData.isNullOrEmpty() && minutelyData.any { it != 0.0 }

            if (settings.showCardMinutely && showMinutely) {
                MinutelyPrecipitationCard(
                    minutely = result?.minutely,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)
                )
                Spacer(modifier = Modifier.height(SkyPulseDesignSystem.Spacing.sectionGap))
            }

            HourlyForecastCard(
                hourly = result?.hourly,
                astro = result?.daily?.astro,
                showAqi = settings.showHourlyAqi,
                showUv = settings.showHourlyUv,
                showWind = settings.showHourlyWind,
                showWindGust = settings.showHourlyWindGust,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)
            )

            Spacer(modifier = Modifier.height(SkyPulseDesignSystem.Spacing.sectionGap))

            DailyForecastCard(
                daily = result?.daily,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)
            )

            Spacer(modifier = Modifier.height(SkyPulseDesignSystem.Spacing.sectionGap))

            AirQualityDetailCard(
                airQuality = realtime?.air_quality,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)
            )

            Spacer(modifier = Modifier.height(SkyPulseDesignSystem.Spacing.sectionGap))

            if (settings.showCardDetail) {
                WeatherDetailCards(
                    realtime = realtime,
                    location = state.weather.location,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(SkyPulseDesignSystem.Spacing.sectionGap))
            }

            if (settings.showCardSunriseSunset) {
                SunriseSunsetCard(
                    astro = result?.daily?.astro,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)
                )
                Spacer(modifier = Modifier.height(SkyPulseDesignSystem.Spacing.sectionGap))
                MoonriseMoonsetCard(
                    astro = result?.daily?.astro,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)
                )
                Spacer(modifier = Modifier.height(SkyPulseDesignSystem.Spacing.sectionGap))
            }

            // 台风雷达图预览卡片（免费用户和会员均解锁）
            if (settings.showCardTyphoon) {
                RadarMapCard(
                    onClick = onRadarClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)
                )
            }

            // 数据来源署名：和风天气官方图形标 + 可点击超链接（合规要求，见 DataSourceAttribution）
            DataSourceAttribution(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 17.dp, bottom = 22.dp),
                textAlpha = 0.55f,
                attributions = realtime?.air_quality?.attributions
            )
            Spacer(modifier = Modifier.height(SkyPulseDesignSystem.Spacing.sectionGap))
        }
    }
}