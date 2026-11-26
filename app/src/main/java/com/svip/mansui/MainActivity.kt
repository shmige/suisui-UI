package com.svip.mansui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.svip.mansui.IPCurbco.TClass.FloatTool
import com.svip.mansui.IPCurbco.TClass.SPUtils
import com.svip.mansui.card.AuthApi
import com.svip.mansui.card.DeviceStore
import com.svip.mansui.card.NoticeApi
import com.svip.mansui.lua.FuncBridge
import com.svip.mansui.ui.component.bottombar.BottomBarMain
import com.svip.mansui.ui.component.bottombar.LocalMainPagerState
import com.svip.mansui.ui.component.bottombar.rememberMainPagerState
import com.svip.mansui.ui.component.dialog.KsuDialogController
import com.svip.mansui.ui.component.dialog.KsuDialogHost
import com.svip.mansui.ui.component.dialog.KsuDialogSpec
import com.svip.mansui.ui.navigation3.LocalNavigator
import com.svip.mansui.ui.navigation3.Navigator
import com.svip.mansui.ui.navigation3.Route
import com.svip.mansui.ui.navigation3.rememberNavigator
import com.svip.mansui.ui.screen.about.AboutScreenActions
import com.svip.mansui.ui.screen.about.AboutScreenMaterial
import com.svip.mansui.ui.screen.about.AboutUiState
import com.svip.mansui.ui.screen.about.LinkInfo
import com.svip.mansui.ui.screen.background.BackgroundScreenActions
import com.svip.mansui.ui.screen.background.BackgroundScreenMaterial
import com.svip.mansui.ui.screen.background.BackgroundUiState
import com.svip.mansui.ui.screen.boot.BootScreen
import com.svip.mansui.ui.screen.boot.ModeSelectScreen
import com.svip.mansui.ui.screen.colorpalette.ColorPaletteScreenActions
import com.svip.mansui.ui.screen.colorpalette.ColorPaletteScreenMaterial
import com.svip.mansui.ui.screen.colorpalette.ColorPaletteUiState
import com.svip.mansui.ui.screen.colorpalette.warmUpPaletteSchemes
import com.svip.mansui.ui.screen.floatwindow.FloatWindowScreenActions
import com.svip.mansui.ui.screen.floatwindow.FloatWindowScreenMaterial
import com.svip.mansui.ui.screen.floatwindow.FloatWindowUiState
import com.svip.mansui.ui.screen.home.AppVersion
import com.svip.mansui.ui.screen.home.HomeActions
import com.svip.mansui.ui.screen.home.HomePagerMaterial
import com.svip.mansui.ui.screen.home.HomeUiState
import com.svip.mansui.ui.screen.home.getAppVersion
import com.svip.mansui.ui.screen.settings.SettingPagerMaterial
import com.svip.mansui.ui.screen.settings.SettingsScreenActions
import com.svip.mansui.ui.screen.settings.SettingsUiState
import com.svip.mansui.ui.theme.AppSettings
import com.svip.mansui.ui.theme.ColorMode
import com.svip.mansui.ui.theme.LocalColorMode
import com.svip.mansui.ui.theme.LocalControlAlpha
import com.svip.mansui.ui.theme.LocalEnableBlur
import com.svip.mansui.ui.theme.LocalEnableFloatingBottomBar
import com.svip.mansui.ui.theme.LocalEnableFloatingBottomBarBlur
import com.svip.mansui.ui.theme.LocalGlassMode
import com.svip.mansui.ui.theme.TemplateTheme
import com.svip.mansui.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import java.io.File
import kotlin.concurrent.thread

// ═══════════════════════════════════════════════
//  主 Activity (2026-09-30 全 Compose 化):
//  进门检测 / 运行环境选择 / 主页+设置 全部 Compose (含 MD3/KSU 弹窗),
//  子页面: 主题设置 / 背景设置 / 悬浮窗设置 / 关于 —— 统一 KSU 分段列表布局
//  保留: 权限 / 模式选择逻辑 / 卡密登录 / 云配置 / 悬浮窗 / 背景与悬浮窗设置的数据逻辑
// ═══════════════════════════════════════════════
@SuppressLint("NewApi")
class MainActivity : ComponentActivity() {

    // ── 子模块 (只保留数据/动作逻辑, 页面已 Compose 化) ──
    internal lateinit var backgroundMgr: BackgroundManager
    internal lateinit var floatSettings: FloatSettingsManager

    // ── Compose 状态 ──
    internal val bgVersion = mutableIntStateOf(0)      // 背景变化 → 背景层/玻璃模式刷新
    internal val resumeTick = mutableIntStateOf(0)     // 从权限页/选择器返回后刷新界面状态
    internal val kami = mutableStateOf("")             // 卡密输入
    internal val kamiVisible = mutableStateOf(false)   // 卡密明文显示
    internal val floatShownState = mutableStateOf(false)
    internal val islandState = mutableStateOf(false)
    internal val channelSel = mutableIntStateOf(0)
    /** 主页/设置页控件不透明度 (背景设置里可调; 仅在媒体背景下生效) */
    internal val controlAlpha = mutableFloatStateOf(0.85f)

    // ── 进门流程 (0=检测中 1=选择运行环境 2=主界面) ──
    internal val bootPhase = mutableIntStateOf(0)
    internal val bootBanner = mutableStateOf<String?>(null)
    internal val rootOkState = mutableStateOf(false)
    internal val rootUnknownState = mutableStateOf(false)
    internal val selectedModeState = mutableIntStateOf(-1)
    internal val rootCheckingState = mutableStateOf(false)

    // 登录成功但悬浮窗权限未授予时置 true: 授权页返回后自动拉起悬浮球
    private var pendingFloatStart = false
    // 登录成功但文件访问未授权: 授权后自动补写 km/imei
    private var pendingStoreKami: String? = null

    override fun onResume() {
        super.onResume()
        // 日志诊断: 从"悬浮窗权限"设置页返回后走的就是这条 (闪退问题的高发路径)
        // 刚拿到"所有文件访问" → 把回退目录里的日志迁回 /sdcard/离情梦/
        resumeTick.intValue++
        runCatching { tryStartFloatAfterPermission() }.getOrNull()
        runCatching { retryPendingStore() }.getOrNull()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 卡密文件路径立即初始化: 主页会读取 km 预填
        runCatching { DeviceStore.initPaths(this) }.getOrNull()
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        runCatching { applyEdgeToEdge() }.getOrNull()
        // ⚠️ 权限跳转: 未授权时会直接拉起"悬浮窗权限"设置页 (回来即触发 onResume 路径)
        runCatching { requestPermission() }.getOrNull()
        runCatching { 储存权限() }.getOrNull()
        AppState.getContext = this
        runCatching { com.svip.mansui.esp.EspCtl.init(applicationContext) }.getOrNull()
        runCatching { Island.ensure(applicationContext) }.getOrNull()
        runCatching { FuncBridge.sendDllInject(false) }.getOrNull()
        runCatching { FuncBridge.deployDllAssets(applicationContext) }.getOrNull()
        runCatching { FuncBridge.deployTipsIds(applicationContext) }.getOrNull()
        runCatching { FuncBridge.resetAttackFiles() }.getOrNull()

        runCatching {
            backgroundMgr = BackgroundManager(this)
            floatSettings = FloatSettingsManager(this)
        }.getOrNull()

        runCatching {
            kami.value = DeviceStore.readKami()
            channelSel.intValue = channelSelIndex(this)
            islandState.value = Island.isOn(this)
            floatShownState.value = AppState.floatWindowShown
            controlAlpha.floatValue = (SPUtils.getParam(this, "ui_alpha", 0.85f) as Float).coerceIn(0.1f, 1f)
        }.getOrNull()

        // 第一次进入: 申请"所有文件访问"权限 (Android 11+, 用于写 /sdcard/离情梦/)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val askedAllFiles = SPUtils.getParam(this, "asked_all_files", false) as Boolean
            if (!askedAllFiles) {
                SPUtils.setParam(this, "asked_all_files", true)
                Handler(Looper.getMainLooper()).postDelayed({ requestAllFilesAccess() }, 1200)
            }
        }

        runCatching { buildComposeShell() }.getOrNull()

        handleDebugIntent(intent)
        try {
            startBootDetect()
        } catch (t: Throwable) {
            // 兜底: 检测流程异常也直接进主页, 不白屏
            ActivityMain()
        }
    }

    /**
     * DEBUG 专用触发 (本机 MIUI 禁止 adb 注入触摸, 用它验证悬浮窗/进入页/背景, release 不生效):
     *   adb shell am start -S -n com.svip.mansui/.MainActivity -a com.svip.mansui.DEBUG_ENTRY
     *   adb shell am start -S -n com.svip.mansui/.MainActivity -a com.svip.mansui.DEBUG_FLOAT
     *   adb shell am start -S -n com.svip.mansui/.MainActivity -a com.svip.mansui.DEBUG_BG --ei bg_mode 1 --ez glass true
     * (onCreate 与 onNewIntent 都会调用, 免得 MIUI 复用已有 Activity 时钩子不生效)
     */
    private fun handleDebugIntent(src: Intent?) {
        if (!BuildConfig.DEBUG) return
        when (src?.action) {
            "com.svip.mansui.DEBUG_ENTRY" -> Handler(Looper.getMainLooper()).postDelayed({
                runCatching { FloatEntry.show(this) { FloatWindow.show(this, expand = true) } }
            }, 1500)
            "com.svip.mansui.DEBUG_FLOAT" -> Handler(Looper.getMainLooper()).postDelayed({
                runCatching { FloatWindow.show(this, expand = true) }
            }, 1500)
            "com.svip.mansui.DEBUG_BG" -> Handler(Looper.getMainLooper()).postDelayed({
                runCatching {
                    val m = src.getIntExtra("bg_mode", -1)
                    if (m >= 0) backgroundMgr.setMode(m)
                    if (src.hasExtra("video_glass")) {
                        SPUtils.setParam(applicationContext, "dbg_video_glass",
                            src.getBooleanExtra("video_glass", true))
                        bgVersion.intValue++
                    }
                    if (src.hasExtra("glass")) {
                        templateApp.getSharedPreferences("settings", MODE_PRIVATE).edit()
                            .putBoolean("enable_floating_bottom_bar_blur",
                                src.getBooleanExtra("glass", false)).apply()
                    }
                }
            }, 2000)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDebugIntent(intent)
    }

    private fun applyEdgeToEdge() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }

    /** 宿主布局 + Compose 根 (进门页/模式选择/主界面全部由 AppRoot 分发) */
    private fun buildComposeShell() {
        val host = FrameLayout(this)
        host.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setContentView(host)
        val cv = ComposeView(this)
        cv.setContent { AppRoot() }
        host.addView(cv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    // ═══════════════════════════════════════════════
    //  Compose 根: 主题 + 弹窗宿主 + 三阶段分发
    // ═══════════════════════════════════════════════
    @Composable
    private fun AppRoot() {
        val vm = viewModel<SettingsViewModel>()
        val uiState by vm.uiState.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val activity = context as MainActivity

        val colorMode = ColorMode.fromValue(uiState.themeMode)
        val paletteStyle = remember(uiState.colorStyle) {
            runCatching { PaletteStyle.valueOf(uiState.colorStyle) }.getOrDefault(PaletteStyle.TonalSpot)
        }
        val colorSpec = remember(uiState.colorSpec) {
            runCatching { ColorSpec.SpecVersion.valueOf(uiState.colorSpec) }.getOrDefault(ColorSpec.SpecVersion.Default)
        }
        val appSettings = AppSettings(colorMode, uiState.keyColor, paletteStyle, colorSpec)

        val bgMode = remember(activity.bgVersion.intValue) {
            (SPUtils.getParam(context.applicationContext, "bg_mode", 0) as Int).coerceIn(0, 3)
        }
        val glass = bgMode != 0 && activity.bootPhase.intValue == 2
        // 控件不透明度: 只在有媒体背景时生效 (纯白底模式保持完全不透明)
        val contentAlpha = if (glass) activity.controlAlpha.floatValue else 1f

        val systemDensity = LocalDensity.current
        val density = remember(systemDensity, uiState.pageScale) {
            Density(systemDensity.density * uiState.pageScale, systemDensity.fontScale)
        }

        // 性能: 后台预热 15 个色板的 materialkolor 配色 (进"主题设置"不再卡一下)
        val isDarkNow = colorMode.isDark || (colorMode.isSystem && isSystemInDarkTheme())
        LaunchedEffect(isDarkNow, paletteStyle, colorSpec) {
            withContext(Dispatchers.Default) {
                warmUpPaletteSchemes(isDarkNow, paletteStyle, colorSpec)
            }
        }

        CompositionLocalProvider(
            LocalColorMode provides colorMode.value,
            LocalEnableBlur provides uiState.enableBlur,
            LocalEnableFloatingBottomBar provides uiState.enableFloatingBottomBar,
            LocalEnableFloatingBottomBarBlur provides uiState.enableFloatingBottomBarBlur,
            LocalGlassMode provides glass,
            LocalControlAlpha provides contentAlpha,
            LocalDensity provides density,
        ) {
            TemplateTheme(appSettings = appSettings) {
                // 全局 MD3/KSU 弹窗 (公告 / 渠道 / 提示 / 选项)
                KsuDialogHost()

                when (activity.bootPhase.intValue) {
                    0 -> BootScreen()
                    1 -> {
                        // 模式未选定前吞掉返回键 (原弹窗 setCancelable(false) 同语义)
                        BackHandler(enabled = true) { }
                        ModeSelectScreen(
                            banner = activity.bootBanner.value,
                            rootOk = activity.rootOkState.value,
                            rootUnknown = activity.rootUnknownState.value,
                            rootChecking = activity.rootCheckingState.value,
                            selectedMode = activity.selectedModeState.intValue,
                            onSelect = { activity.selectedModeState.intValue = it },
                            onRetryRoot = { activity.retryRootDetect() },
                            onCancel = { activity.finish() },
                            onConfirm = { activity.confirmMode() },
                        )
                    }
                    else -> MainScreen(vm, uiState, glass)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════
    //  主界面: 主页 / 设置 两页 + 子页面路由
    // ═══════════════════════════════════════════════
    @Composable
    private fun MainScreen(
        vm: SettingsViewModel,
        uiState: SettingsUiState,
        glass: Boolean,
    ) {
        val activity = LocalContext.current as MainActivity
        val navigator = rememberNavigator(Route.Main)
        val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
        val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
        val pagerState = rememberPagerState(pageCount = { 2 })
        val mainPagerState = rememberMainPagerState(pagerState)
        val surfaceColor = MaterialTheme.colorScheme.surface
        val backdrop = rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }

        val route = navigator.current()
        val isSubPage = route != Route.Main

        // 卡顿修复: 视频背景默认不参与 layerBackdrop 采样 (每帧重录全屏纹理会明显掉帧)。
        // 注意用 isVideoMode() 按模式判断 —— 用 isVideoBackground() 会读到上一帧构建的视图,
        // 切背景那一帧还是旧值, 视频会被误挂进 layerBackdrop → 底栏玻璃采到黑底。
        val videoBg = remember(activity.bgVersion.intValue) { activity.backgroundMgr.isVideoMode() }
        // 视频背景是否也允许走玻璃采样 (TextureView 进硬件层在部分机器上会采到黑;
        // 用 SP dbg_video_glass 可在真机上直接对比切换, 默认开)
        val videoGlass = remember(activity.bgVersion.intValue) {
            SPUtils.getParam(activity.applicationContext, "dbg_video_glass", true) as Boolean
        }
        val useBackdrop = enableFloatingBottomBar && enableFloatingBottomBarBlur &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (!videoBg || videoGlass)

        // ── 返回键 (2026-10-01 去掉预测性返回手势跟手动画, 改回普通返回) ──
        val scope = rememberCoroutineScope()
        val subPageOffset = remember { Animatable(0f) }
        val isBackEnabled = isSubPage || mainPagerState.selectedPage != 0

        BackHandler(enabled = isBackEnabled) {
            scope.launch {
                if (navigator.backStackSize() > 1) {
                    // 子页: 先滑出再弹出 (纯退出动画, 不跟手)
                    subPageOffset.animateTo(1f, tween(140))
                    navigator.pop()
                    subPageOffset.snapTo(0f)
                } else {
                    // 设置页: 滚回主页
                    mainPagerState.animateToPage(0)
                }
            }
        }

        CompositionLocalProvider(
            LocalMainPagerState provides mainPagerState,
            LocalNavigator provides navigator,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (glass) ComposeColor.Transparent else MaterialTheme.colorScheme.surface)
            ) {
                // 基础层: 主页/设置分页 (子页打开时淡出)
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (isSubPage) subPageOffset.value else 1f },
                    containerColor = ComposeColor.Transparent,
                    bottomBar = {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            BottomBarMain(
                                blurBackdrop = null,
                                backdrop = backdrop,
                                // 没录制内容时底栏必须退回实心胶囊, 否则采空底 → 黑条
                                blurReady = useBackdrop,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                ) { innerPadding ->
                    val bottomInnerPadding = innerPadding.calculateBottomPadding()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (useBackdrop) Modifier.layerBackdrop(backdrop) else Modifier)
                    ) {
                        BackgroundLayer(activity)
                        HorizontalPager(
                            modifier = Modifier.fillMaxSize(),
                            state = pagerState,
                            // 性能: 预组合相邻页 → 主页⇄设置切换不再"卡一下"
                            beyondViewportPageCount = 1,
                            // 禁止左右滑动切页: 只能点底栏切换 (程序化的 animateToPage 不受影响)
                            userScrollEnabled = false,
                        ) { page ->
                            when (page) {
                                0 -> HomeRoute(activity, bottomInnerPadding)
                                1 -> SettingRoute(activity, uiState, vm, navigator, bottomInnerPadding)
                            }
                        }
                    }
                }

                // 子页层: 跟手右移滑出
                if (isSubPage) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = size.width * subPageOffset.value }
                    ) {
                        when (route) {
                            Route.ColorPalette -> ColorPaletteRoute(vm, uiState, navigator)
                            Route.Background -> BackgroundRoute(activity, navigator)
                            Route.FloatWindow -> FloatWindowRoute(activity, navigator)
                            Route.About -> AboutRoute(navigator)
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun BackgroundLayer(activity: MainActivity) {
        val version = activity.bgVersion.intValue
        val bgView = remember(version) { activity.backgroundMgr.backgroundView() }
        if (bgView != null) {
            // ⚠️ 必须用 key(version) 包住: AndroidView 的 factory 只在"节点首次创建"时调用一次,
            // 换了新 View 实例但节点没变的话它不会重新调用 factory → 切背景看起来"没生效"
            // (典型: 官方图库 ⇄ 官方动态 互切)。key 变化会重建节点 → 旧视图 detach
            // (Md3VideoBackground.onDetachedFromWindow 会 stopPlayback), 新视图正确挂上。
            key(version) {
                AndroidView(
                    factory = { bgView },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    // ═══ 主页 ═══
    @Composable
    private fun HomeRoute(activity: MainActivity, bottomInnerPadding: Dp) {
        val context = LocalContext.current
        val appVersion: AppVersion = remember { getAppVersion(context) }
        val state = HomeUiState(
            appVersion = appVersion,
            modeName = MODE_NAMES[AppState.app_Operation_mode],
            channelName = CHANNEL_NAMES[activity.channelSel.intValue.coerceIn(0, CHANNEL_NAMES.size - 1)],
            kami = activity.kami.value,
            kamiVisible = activity.kamiVisible.value,
            floatShown = activity.floatShownState.value,
            latestVersion = AppUpdate.latestVersion,
            hasUpdate = AppUpdate.hasUpdate,
        )
        val actions = HomeActions(
            onKamiChange = { activity.kami.value = it },
            onToggleKamiVisible = { activity.kamiVisible.value = !activity.kamiVisible.value },
            onChannelClick = { activity.showChannelDialog() },
            onStart = { activity.kaiqiClicked() },
            onUnbind = { activity.unbindClicked() },
            onBuy = { activity.buyKami() },
            onUpdate = { activity.showUpdateDialog() },
        )
        HomePagerMaterial(
            state = state,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )
    }

    // ═══ 设置 ═══
    @Composable
    private fun SettingRoute(
        activity: MainActivity,
        uiState: SettingsUiState,
        vm: SettingsViewModel,
        navigator: Navigator,
        bottomInnerPadding: Dp,
    ) {
        val context = LocalContext.current
        val bgName = remember(activity.bgVersion.intValue) { activity.backgroundMgr.currentModeDesc() }
        val actions = SettingsScreenActions(
            onSetCheckUpdate = vm::setCheckUpdate,
            onOpenTheme = { navigator.push(Route.ColorPalette) },
            onOpenBackground = { navigator.push(Route.Background) },
            onOpenFloatWindow = { navigator.push(Route.FloatWindow) },
            onOpenAbout = { navigator.push(Route.About) },
            onSetIsland = { on ->
                Island.applySetting(context, on)
                activity.islandState.value = on
            },
            onResetEnv = { resetEnv(context) },
            onUninstall = { activity.showUninstallDialog() },
        )
        SettingPagerMaterial(
            uiState = uiState.copy(
                envName = MODE_NAMES[AppState.app_Operation_mode],
                backgroundName = bgName,
                island = activity.islandState.value,
            ),
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )
    }

    // ═══ 背景设置 ═══
    @Composable
    private fun BackgroundRoute(activity: MainActivity, navigator: Navigator) {
        val state = remember(activity.bgVersion.intValue, activity.resumeTick.intValue) {
            BackgroundUiState(
                mode = activity.backgroundMgr.mode(),
                dynamicSel = activity.backgroundMgr.dynamicSel(),
                officialImage = activity.backgroundMgr.officialImage(),
                customLabel = activity.backgroundMgr.customLabel(),
                officialImages = BackgroundManager.OFFICIAL_BG_IMAGES.toList(),
                controlAlpha = activity.controlAlpha.floatValue,
            )
        }
        val actions = BackgroundScreenActions(
            onBack = { navigator.pop() },
            onSetMode = { activity.backgroundMgr.setMode(it) },
            onSetDynamicSel = { activity.backgroundMgr.setDynamicSel(it) },
            onSetOfficialImage = { activity.backgroundMgr.setOfficialImage(it) },
            onPickImage = { activity.backgroundMgr.pickCustomImage() },
            onPickVideo = { activity.backgroundMgr.pickCustomVideo() },
            onSetControlAlpha = { value ->
                val v = value.coerceIn(0.1f, 1f)
                activity.controlAlpha.floatValue = v
                SPUtils.setParam(activity.applicationContext, "ui_alpha", v)
            },
        )
        BackgroundScreenMaterial(state = state, actions = actions)
    }

    // ═══ 悬浮窗设置 (含悬浮窗权限) ═══
    @Composable
    private fun FloatWindowRoute(activity: MainActivity, navigator: Navigator) {
        val context = LocalContext.current
        val state = remember(activity.resumeTick.intValue, activity.bgVersion.intValue) {
            FloatWindowUiState(
                overlayGranted = Settings.canDrawOverlays(context),
                ballMode = activity.floatSettings.ballMode(),
                ballCustom = activity.floatSettings.hasCustomBallImage(),
                bgMode = activity.floatSettings.bgMode(),
            )
        }
        val actions = FloatWindowScreenActions(
            onBack = { navigator.pop() },
            onRequestOverlay = { activity.requestOverlayPermission() },
            onSetBallMode = {
                activity.floatSettings.setBallMode(it)
                activity.resumeTick.intValue++
            },
            onPickBallImage = { activity.floatSettings.pickBallImage() },
            onResetBallImage = {
                activity.floatSettings.resetBallImage()
                activity.resumeTick.intValue++
            },
            onPickPanelImage = { activity.floatSettings.pickPanelImage() },
            onSetBgMode = {
                activity.floatSettings.setBgMode(it)
                activity.resumeTick.intValue++
            },
        )
        FloatWindowScreenMaterial(state = state, actions = actions)
    }

    // ═══ 主题设置 (ColorPalette) ═══
    @Composable
    private fun ColorPaletteRoute(
        vm: SettingsViewModel,
        uiState: SettingsUiState,
        navigator: Navigator,
    ) {
        val context = LocalContext.current
        val currentPaletteStyle = runCatching { PaletteStyle.valueOf(uiState.colorStyle) }
            .getOrDefault(PaletteStyle.TonalSpot)
        val currentColorSpec = runCatching { ColorSpec.SpecVersion.valueOf(uiState.colorSpec) }
            .getOrDefault(ColorSpec.SpecVersion.Default)
        val state = ColorPaletteUiState(
            uiState = uiState,
            currentColorMode = ColorMode.fromValue(uiState.themeMode),
            currentPaletteStyle = currentPaletteStyle,
            currentColorSpec = currentColorSpec,
        )
        val actions = ColorPaletteScreenActions(
            onBack = { navigator.pop() },
            onSetThemeMode = vm::setThemeMode,
            onSetMiuixMonet = vm::setMiuixMonet,
            onSetKeyColor = vm::setKeyColor,
            onSetColorMode = vm::setColorMode,
            onSetColorStyle = vm::setColorStyle,
            onSetColorSpec = vm::setColorSpec,
            onSetEnableBlur = vm::setEnableBlur,
            onSetEnableFloatingBottomBar = vm::setEnableFloatingBottomBar,
            onSetEnableFloatingBottomBarBlur = vm::setEnableFloatingBottomBarBlur,
            onSetPageScale = vm::setPageScale,
        )
        ColorPaletteScreenMaterial(state = state, actions = actions)
    }

    // ═══ 关于 ═══
    @Composable
    private fun AboutRoute(navigator: Navigator) {
        val context = LocalContext.current
        val appVersion = remember { getAppVersion(context) }
        val state = AboutUiState(
            title = "关于",
            appName = context.getString(R.string.app_name),
            versionName = "v${appVersion.versionName}",
            // 开源版不含作者联系方式与社群入口（原 Telegram 频道 / QQ 群链接已移除）
            links = emptyList<LinkInfo>(),
        )
        val actions = AboutScreenActions(
            onBack = { navigator.pop() },
            onOpenLink = { url ->
                when {
                    // QQ 群: 复制群号 (没有可直接打开的 URL)
                    url.startsWith("qq:") -> {
                        val num = url.removePrefix("qq:")
                        try {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("QQ群号", num))
                            md3Toast(context, "QQ群号已复制：$num")
                        } catch (_: Exception) {
                            md3Toast(context, "复制失败，请手动搜索群号 $num")
                        }
                    }
                    url.isNotEmpty() -> runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
            },
        )
        AboutScreenMaterial(state = state, actions = actions)
    }

    // ═══════════════════════════════════════════════
    //  进门检测 / 运行环境选择
    // ═══════════════════════════════════════════════
    private fun startBootDetect() {
        val bootStart = SystemClock.uptimeMillis()
        // 框架模式不依赖 ROOT: 跳过 su 等待, 固定 900ms 展示后直连
        val earlyInit = (SPUtils.getParam(applicationContext, "app_init_mode", false) as Boolean)
        val earlyMode = if (earlyInit) (SPUtils.getParam(applicationContext, "app_Operation_mode", 0) as Int) else -1
        val needRootWait = !(earlyInit && earlyMode == 1)
        Thread {
            try {
                if (needRootWait) {
                    if (Util.getRootCache() == null) Util.detectRootAsync(null)
                    // 等 su 授权最长 8s (授权弹窗期间继续转圈), 避免界面无限白屏
                    var waited = 0
                    while (Util.getRootCache() == null && Util.isRootDetecting() && waited < 8000) {
                        Thread.sleep(100)
                        waited += 100
                    }
                }
                val remain = 900L - (SystemClock.uptimeMillis() - bootStart)
                if (remain > 0) Thread.sleep(remain)
            } catch (t: Throwable) {
            }
            // 上次异常结束 + 已确认有 ROOT → 附上 native tombstone 现场 (失败静默)
            runOnUiThread {
                try {
                    resolveBoot()
                } catch (t: Throwable) {
                    showModeSelect(null)
                }
            }
        }.start()
    }

    private fun resolveBoot() {
        val init = (SPUtils.getParam(applicationContext, "app_init_mode", false) as Boolean)
        val mode = if (init) (SPUtils.getParam(applicationContext, "app_Operation_mode", 0) as Int) else -1
        // 直连前先同步内存态: ActivityMain() 会把 AppState 写回 SP,
        // 冷启动默认 0 若不同步会把已保存模式覆写成 ROOT → 下次启动误判"环境变更"重选
        fun enterSavedMode(savedMode: Int) {
            AppState.app_Operation_mode = savedMode
            AppState.app_Operation_mode_root = savedMode == 0
            ActivityMain()
        }
        var banner: String? = null
        if (init) when (mode) {
            1 -> {
                enterSavedMode(1)
                return
            }
            0 -> when (Util.getRootCache()) {
                true -> {
                    enterSavedMode(0)
                    return
                }
                false -> banner =
                    "运行环境变更：设备未检测到 ROOT，已保存的 ROOT 模式不可用，请重新选择运行环境"
                null -> {
                    // su 授权偶发超时未决: 不算环境变更 → 按保存模式直连
                    enterSavedMode(0)
                    return
                }
            }
            2 -> {
                enterSavedMode(2)
                return
            }
        }
        if (banner != null) {
            // 环境变更: 清除已保存模式, 强制重选
            SPUtils.setParam(applicationContext, "app_init_mode", false)
            AppState.app_init_mode = false
        }
        showModeSelect(banner)
    }

    /** 展示运行环境选择页 */
    private fun showModeSelect(banner: String?) {
        val cached = Util.getRootCache()
        rootOkState.value = cached == true
        rootUnknownState.value = cached == null
        bootBanner.value = banner
        selectedModeState.intValue = if (cached == true) 0 else 1
        bootPhase.intValue = 1
    }

    /** ROOT 置灰时重测 (原 ModeSelectDialog.retryRootDetect) */
    internal fun retryRootDetect() {
        if (rootCheckingState.value) {
            md3Toast(this, "正在重新检测中…")
            return
        }
        rootCheckingState.value = true
        md3Toast(this, "正在重新检测 ROOT…")
        Util.detectRootAsync {
            runOnUiThread {
                rootCheckingState.value = false
                val cached = Util.getRootCache()
                rootOkState.value = cached == true
                rootUnknownState.value = cached == null
                when (cached) {
                    true -> {
                        selectedModeState.intValue = 0
                        md3Toast(this, "已检测到 ROOT，Root 模式可用")
                    }
                    null -> md3Toast(this, "授权未响应：请在 Root 管理器(面具/KSU)中允许本应用后点此重试")
                    else -> md3Toast(this, "设备未检测到 ROOT，请检查后重试")
                }
            }
        }
    }

    /** 确认运行环境 (原 ModeSelectDialog.onConfirmClick) */
    internal fun confirmMode() {
        val mode = selectedModeState.intValue
        if (mode == -1) {
            md3Toast(this, "请先选择运行环境")
            return
        }
        AppState.app_Operation_mode = mode
        AppState.app_Operation_mode_root = mode == 0
        ActivityMain()
    }

    // ═══════════════════════════════════════════════
    //  主界面装配 (原 ActivityMain)
    // ═══════════════════════════════════════════════
    private fun ActivityMain() {
        // 纯 UI 版本：原 ROOT 服务/后台连接已删除（无 root、无内存服务）

        AppState.app_init_mode = true
        SPUtils.setParam(applicationContext, "app_init_mode", AppState.app_init_mode)
        SPUtils.setParam(applicationContext, "app_Operation_mode", AppState.app_Operation_mode)
        channelSel.intValue = channelSelIndex(this)
        islandState.value = Island.isOn(this)

        bootPhase.intValue = 2

        // 更新/公告检测后置: 等主页首帧稳定后再发起
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (DeviceStore.readKami().isNotEmpty()) {
                    thread {
                        try {
                            val pkg = CHANNEL_PACKAGES[channelSelIndex(this@MainActivity)]
                            MemBridge.setTarget(pkg)
                            if (AppState.app_Operation_mode == 2) {
                                com.svip.mansui.esp.EspCtl.tprtBypass(true)
                                startUidWatcher()
                                return@thread
                            }
                            repeat(240) {
                                if (MemBridge.attach(pkg)) {
                                    com.svip.mansui.esp.EspCtl.ensure()
                                    return@thread
                                }
                                Thread.sleep(500)
                            }
                        } catch (t: Throwable) {
                        }
                    }
                    if (CloudConfig.lastConfig.isEmpty()) {
                        CloudConfig.fetch {}
                    }
                }
                NoticeApi.start(this@MainActivity)
            } catch (t: Throwable) {
            }
        }, 600)
    }

    // ═══════════════════════════════════════════════
    //  权限
    // ═══════════════════════════════════════════════
    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) return
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")))
                return
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    return
                } catch (_: Exception) {
                    md3Toast(this, "请到系统设置中开启「所有文件访问」权限")
                }
            }
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 102)
        }
    }

    private fun retryPendingStore() {
        val k = pendingStoreKami ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) return
        pendingStoreKami = null
        val ok = DeviceStore.writeKami(k) && DeviceStore.writeImei(DeviceStore.getDeviceCode(this))
        if (ok) md3Toast(this, "卡密已保存到 /sdcard/离情梦/", long = true)
    }

    private fun 储存权限() {
        if (Build.VERSION.SDK_INT < 23) return
        val granted = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), 102)
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            val perms = arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            val granted = perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
            if (!granted) requestPermissions(perms, 102)
            if (!Settings.canDrawOverlays(this)) {
                startActivityForResult(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")), 111)
            }
        }
    }

    // ═══════════════════════════════════════════════
    //  业务入口
    // ═══════════════════════════════════════════════

    /** 渠道选择 (MD3/KSU 弹窗) */
    internal fun showChannelDialog() {
        KsuDialogController.show(
            KsuDialogSpec(
                title = "渠道版本",
                items = CHANNEL_NAMES.toList(),
                selectedIndex = channelSel.intValue,
                negativeText = "取消",
                onItemSelected = { which ->
                    channelSel.intValue = which
                    SPUtils.setParam(applicationContext, "channel_sel", which)
                    saveChannelPackage()
                    FuncBridge.deployDllAssets(applicationContext)
                FuncBridge.deployTipsIds(applicationContext)
                },
            )
        )
    }

    internal fun buyKami() {
        md3Toast(this, "开源版无需卡密", long = true)
    }

    /** 更新弹窗 (主页「应用版本」行的更新按钮 / 启动检查提示共用; 与公告弹窗同一套风格) */
    internal fun showUpdateDialog() {
        UpdateDialog.show(this)
    }

    /**
     * 进入悬浮窗: 本进程首次先展示"进入页"(进入页 → 点「进入首页」), 之后直达悬浮球。
     * 只有卡密校验通过后的流程才会走到这里 (权限入口不会调用本方法)。
     */
    internal fun enterFloatWindow() {
        if (FloatEntry.isShown()) return
        if (AppState.floatWindowShown) {
            floatShownState.value = true
            return
        }
        if (FloatEntry.shouldShowEntry()) {
            val ok = runCatching {
                FloatEntry.show(this) {
                    // 首次「进入首页」: 悬浮窗直接以展开态出现 (与进入页同尺寸同位)
                    if (runCatching {
                            FloatWindow.show(this@MainActivity, expand = true)
                        }.getOrNull() == true) {
                        AppState.floatWindowShown = true
                        floatShownState.value = true
                    } else {
                        // 部分 ROM 刚授权完 AppOps 还没生效 → 800ms 后自动重试一次再报错
                        Handler(Looper.getMainLooper()).postDelayed({
                            val retry = runCatching {
                                FloatWindow.show(this@MainActivity, expand = true)
                            }.getOrNull()
                            if (retry == true) {
                                AppState.floatWindowShown = true
                                floatShownState.value = true
                            } else {
                                md3Toast(this@MainActivity,
                                    "悬浮窗启动失败：\n" + FloatWindow.overlayFixHint(this@MainActivity),
                                    long = true)
                            }
                        }, 800)
                    }
                }
            }.getOrNull()
            return
        }
        if (runCatching { FloatWindow.show(this) }.getOrNull() == true) {
            AppState.floatWindowShown = true
            floatShownState.value = true
        } else {
            Handler(Looper.getMainLooper()).postDelayed({
                val retry = runCatching { FloatWindow.show(this@MainActivity) }.getOrNull()
                if (retry == true) {
                    AppState.floatWindowShown = true
                    floatShownState.value = true
                } else {
                    md3Toast(this@MainActivity,
                        "悬浮窗启动失败：\n" + FloatWindow.overlayFixHint(this@MainActivity),
                        long = true)
                }
            }, 800)
        }
    }

    /**
     * 悬浮窗权限入口 (已从主页移到"悬浮窗设置"页)。
     * ⚠️ 只申请权限, 绝不拉起悬浮窗 —— 悬浮球必须走"启动数据"卡密校验后的流程,
     *    否则已授权用户点一下就能白嫖 (2026-10-01 修复)。
     */
    internal fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            md3Toast(this, "已具备悬浮窗权限")
            return
        }
        startActivityForResult(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), 4444)
    }

    // 把当前渠道包名写入 proc 文件, C 层 GetCurrentChannelPackage() 据此选择配置组
    private fun saveChannelPackage() {
        try {
            val f = File(
                Environment.getExternalStorageDirectory(),
                "Android/data/${packageName}/files/proc")
            f.parentFile?.mkdirs()
            f.writeText(CHANNEL_PACKAGES[channelSel.intValue.coerceIn(0, CHANNEL_PACKAGES.size - 1)])
        } catch (e: Exception) {
        }
    }

    /**
     * 「当前环境」重置: 清掉"已选运行环境"标记 → 回到运行环境选择页。
     *
     * ⚠️ 注意: **不会删除卡密 / 设备码**(km、imei 都原样保留)。
     * 这里原来直接 `System.exit(0)` 硬杀进程 —— 用户侧看起来像闪退, 且退出时
     * 退场/收尾逻辑全都不跑。现在改成"先把新 Activity 拉起来再退", 是正常重启。
     */
    private fun resetEnv(context: Context) {
        // commit() 同步落盘 (SPUtils 内部就是 commit), 保证重启后一定读到 false
        SPUtils.setParam(context.applicationContext, "app_init_mode", false)
        kotlin.runCatching {
            val i = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(i)
        }
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { android.os.Process.killProcess(android.os.Process.myPid()) }
            kotlin.system.exitProcess(0)
        }, 400)
    }

    // ═══════════════════════════════════════════════
    //  卸载 (照搬 KernelSU settings_uninstall: 完全卸载 / 普通卸载 两项 → 确认 → 执行)
    //  KSU ksud `uninstall` 的顺序 = 先清目录/模块, 最后 `pm uninstall <包名>` 卸载自身
    // ═══════════════════════════════════════════════
    internal fun showUninstallDialog() {
        KsuDialogController.show(
            KsuDialogSpec(
                title = getString(R.string.settings_uninstall),
                // 照 KSU 源码: 选项只写名称, 不解释具体删什么
                items = listOf(
                    getString(R.string.settings_uninstall_full),
                    getString(R.string.settings_uninstall_normal),
                ),
                negativeText = "取消",
                onItemSelected = { which ->
                    val full = which == 0
                    KsuDialogController.show(
                        KsuDialogSpec(
                            title = getString(
                                if (full) R.string.settings_uninstall_full else R.string.settings_uninstall_normal
                            ),
                            message = getString(
                                if (full) R.string.settings_uninstall_confirm_full
                                else R.string.settings_uninstall_confirm_normal
                            ),
                            positiveText = getString(R.string.settings_uninstall_action),
                            negativeText = "取消",
                            onPositive = { uninstallSelf(full) },
                        )
                    )
                },
            )
        )
    }

    /**
     * 执行卸载。
     * 完全卸载 = 先删本地生成的文件 (离情梦目录等) → 再卸载自身 (同 KSU uninstall 顺序)
     * 普通卸载 = 直接卸载自身 (本地文件保留)
     * 有 ROOT 时静默 `pm uninstall <包名>`; 无 ROOT 回落到系统卸载确认页。
     */
    internal fun uninstallSelf(full: Boolean) {
        md3Toast(this, if (full) "正在清理本地文件…" else "正在卸载…", long = true)
        thread {
            if (full) deleteLocalGenerated()
            if (Util.getRootCache() == true) {
                runCatching { FloatTool.RunShell("pm uninstall $packageName", true) }
            }
            // root 卸载成功的话进程已被杀掉, 走不到这里; 未 root / 卸载失败 → 系统卸载页
            runOnUiThread { startSystemUninstall() }
        }
    }

    /** 删除本地生成的文件 (仅"完全卸载"调用) */
    private fun deleteLocalGenerated() {
        val ext = Environment.getExternalStorageDirectory()
        val targets = listOf(
            File(ext, "离情梦"),                                  // 卡密 / 设备码 / 各功能参数文件
            File(ext, "日志.log"),                                  // 注入与调试日志
            File(ext, "Android/data/$packageName/files"),           // proc / esp_*.cfg / esp_render.dat
            File(filesDir, "江"),                                   // 裁剪图 / 背景图 / 悬浮窗图片缓存
        )
        for (f in targets) {
            runCatching {
                if (f.exists()) {
                    if (f.isDirectory) f.deleteRecursively() else f.delete()
                }
            }
        }
    }

    private fun startSystemUninstall() {
        try {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            md3Toast(this, "无法调起卸载，请到系统设置中手动卸载", long = true)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (backgroundMgr.handleActivityResult(requestCode, resultCode, data)) return
        if (floatSettings.handleActivityResult(requestCode, resultCode, data)) return
        if (requestCode == 4444 || requestCode == 111) {
            runCatching { tryStartFloatAfterPermission() }.getOrNull()
        }
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun tryStartFloatAfterPermission() {
        if (!pendingFloatStart) return
        if (AppState.floatWindowShown) {
            pendingFloatStart = false
            floatShownState.value = true
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            return
        }
        pendingFloatStart = false
        enterFloatWindow()
    }

    // ── 自身 APK 证书 SHA-256 (签名校验用) ──
    private fun selfApkSignSha256(): String {
        return try {
            val cert: ByteArray = if (Build.VERSION.SDK_INT >= 28) {
                val sc = packageManager.getPackageInfo(
                    packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                sc.signingInfo?.apkContentsSigners?.get(0)?.toByteArray() ?: return ""
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                    .signatures!![0].toByteArray()
            }
            val md = java.security.MessageDigest.getInstance("SHA-256")
            md.digest(cert).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { "" }
    }

    // ── UID 模式: 专属包残留清理 + 进程看护 ──
    private val uidWatcherRunning = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun uidCleanGameFiles(pkg: String) {
        kotlin.concurrent.thread {
            try {
                val anoTmp = File("/data/user/0/$pkg/files/ano_tmp")
                if (anoTmp.exists()) anoTmp.deleteRecursively()
                val cacheDir = File("/data/user/0/$pkg/cache")
                if (cacheDir.exists()) cacheDir.deleteRecursively()
            } catch (_: Exception) {}
        }
    }

    private fun startUidWatcher() {
        if (!uidWatcherRunning.compareAndSet(false, true)) return
        val pkg = CHANNEL_PACKAGES[channelSelIndex(this)]
        thread {
            var attached = false
            while (AppState.app_Operation_mode == 2) {
                if (!MemBridge.isAttached()) {
                    if (attached) {
                        attached = false
                        uidCleanGameFiles(pkg)
                    }
                    if (MemBridge.attach(pkg)) {
                        attached = true
                        com.svip.mansui.esp.EspCtl.ensure()
                    }
                }
                Thread.sleep(500)
            }
            uidWatcherRunning.set(false)
        }
    }

    internal fun kaiqiClicked() {
        // 签名校验 (debug 构建跳过, 免重签直装测试)
        val selfSign = if (BuildConfig.DEBUG) "" else selfApkSignSha256()
        if (selfSign.isNotEmpty()) {
            try { MansuiNative.verifyApkSign(selfSign) } catch (_: Exception) {}
        }
        // UID 模式前置校验 (仅在点"启动数据"登录时弹窗)
        if (AppState.app_Operation_mode == 2) {
            val chk = uidChannelCheck(this)
            if (chk != 0) {
                KsuDialogController.show(
                    KsuDialogSpec(
                        title = "UID 校验未通过",
                        message = if (chk == 1) {
                            "UID 模式需安装对应渠道的离情梦专属游戏包(同签名共享 UID)，未启动悬浮窗"
                        } else {
                            "请搭配当前离情梦专属包游戏安装包进行使用"
                        },
                        positiveText = "前往下载",
                        negativeText = "取消",
                        onPositive = {
                            // 开源版不含专属包分发：云端 Exclusivepackage 与内置外链兜底均已移除
                            md3Toast(this, "开源版不含专属包下载", long = true)
                        },
                    )
                )
                return
            }
        }
        val k = kami.value.trim()
        if (k.isEmpty()) {
            md3Toast(this, "请输入卡密")
            return
        }
        // 排查"输入正确卡密却提示卡密不存在": 只打**编号/长度/可疑字符位置**, 绝不输出卡密内容
        // 预热悬浮窗 (2026-10-01): 趁卡密校验的网络等待时间, 把悬浮窗整套视图树建好并布局完,
        // 登录成功后 show() 只剩"显隐 + 动画" → 启动悬浮球/进入首页都不会再卡一下。
        // 延迟 260ms 起, 先让点击反馈画完再干活。
        Handler(Looper.getMainLooper()).postDelayed({
            if (Settings.canDrawOverlays(this@MainActivity)) {
                val ok = runCatching { FloatWindow.preload(this@MainActivity) }.getOrNull()
            } else {
            }
        }, 260)
        AuthApi.login(k, this, object : AuthApi.LoginListener {
            override fun onSuccess(expiryDate: String) {
                md3Toast(this@MainActivity, "登录成功，到期时间：$expiryDate", long = true)
                if (AppState.app_Operation_mode == 2) {
                    uidCleanGameFiles(CHANNEL_PACKAGES[channelSelIndex(this@MainActivity)])
                    com.svip.mansui.esp.EspCtl.tprtBypass(true)
                    startUidWatcher()
                }
                val attachPkg = CHANNEL_PACKAGES[channelSelIndex(this@MainActivity)]
                MemBridge.setTarget(attachPkg)
                kotlin.concurrent.thread {
                    try {
                        val attached = MemBridge.attach(attachPkg)
                        CloudConfig.fetch {}
                    } catch (t: Throwable) {
                    }
                    runOnUiThread {
                        if (selfSign.isNotEmpty()) {
                            try { MansuiNative.verifyApkSign(selfSign) } catch (_: Exception) {}
                        }
                        if (AppState.floatWindowShown) {
                            floatShownState.value = true
                            return@runOnUiThread
                        }
                        if (!Settings.canDrawOverlays(this@MainActivity)) {
                            pendingFloatStart = true
                            md3Toast(this@MainActivity, "请先授予悬浮窗权限", long = true)
                            startActivityForResult(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${this@MainActivity.packageName}")), 4444)
                            return@runOnUiThread
                        }
                        // 首次进本进程 → 先展示"进入页", 点「进入首页」再开悬浮球
                        runCatching { enterFloatWindow() }.getOrNull()
                    }
                }
            }

            override fun onFailure(error: String) {
                // 校验失败: 丢弃预热窗口, 别留个隐形窗
                runCatching { FloatWindow.discardPreload() }
                md3Toast(this@MainActivity, error, long = true)
            }

            override fun onStoreFailed(expiryDate: String) {
                md3Toast(this@MainActivity, "登录成功，但本地保存失败，请授权文件访问", long = true)
                KsuDialogController.show(
                    KsuDialogSpec(
                        title = "需要文件访问权限",
                        message = "卡密验证已通过（到期：$expiryDate），但无法写入 /sdcard/离情梦/。\n\n" +
                                "请授予「所有文件访问」权限，授权后将自动重新保存。",
                        positiveText = "去授权",
                        negativeText = "稍后",
                        onPositive = { requestAllFilesAccess() },
                    )
                )
                pendingStoreKami = k
            }
        })
    }

    internal fun unbindClicked() {
        val k = kami.value.trim()
        if (k.isEmpty()) {
            md3Toast(this, "请输入捐赠密钥")
            return
        }
        AuthApi.unbind(k, this, object : AuthApi.UnbindListener {
            override fun onSuccess(remainingTimes: String) {
                md3Toast(this@MainActivity, "解绑成功，剩余次数：$remainingTimes", long = true)
                kami.value = ""
                DeviceStore.deleteKami()
            }

            override fun onFailure(error: String) {
                md3Toast(this@MainActivity, error, long = true)
            }
        })
    }
}
