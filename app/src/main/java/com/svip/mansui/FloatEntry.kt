package com.svip.mansui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.svip.mansui.IPCurbco.TClass.SPUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

// ═══════════════════════════════════════════════
//  悬浮窗「进入页」(移植自 G:\qing 的 VetileMiniLauncher 启动页):
//   背景图渐显 2s → 右侧平行四边形玻璃带自右划入 1.1s → 文案/按钮淡入 → 「进入首页」
//   点「进入首页」= 入场的对称反向退场:
//       ① 玻璃带/文案原路滑回右侧 (620ms) ② 面板在不透明背景图背后淡入 (240ms)
//       ③ 背景图再淡出一次, 就地溶成面板 ④ 才移除进入页窗口
//   点右上 ✕ 或标题 = 关闭; 每进程只展示一次 (之后"启动数据"直达悬浮球)
// ═══════════════════════════════════════════════
object FloatEntry {

    /** 进入页背景素材 (assets)。想换图改这里; 也可用 float_entry_img 指定自有图片路径 */
    private const val ASSET = "官方背景.jpg"

    private var win: View? = null
    private var ctxRef: WeakReference<Context>? = null
    private var art: EntryArt? = null
    private var loading = false
    private var shownOnce = false

    fun isShown(): Boolean = win != null

    /** 本进程是否还没看过进入页 (true = 该先展示进入页) */
    fun shouldShowEntry(): Boolean = !shownOnce

    /**
     * 展示进入页; onEnter = 点「进入首页」后的回调 (进入页已先销毁)。
     * 素材解码放后台线程, 解好再开窗 (避免首帧卡顿)。
     */
    fun show(context: Context, onEnter: () -> Unit) {
        if (win != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            return
        }
        val app = context.applicationContext
        ctxRef = WeakReference(app)
        if (art == null) {
            if (loading) return
            loading = true
            val handler = Handler(Looper.getMainLooper())
            Thread {
                val decoded = runCatching { decodeArt(app, entryAsset(app), withBlur = true) }.getOrNull()
                handler.post {
                    loading = false
                    if (decoded != null) art = decoded
                    if (win == null) runCatching { attach(app, onEnter) }.getOrNull()
                }
            }.start()
            return
        }
        attach(app, onEnter)
    }

    /** 自定义进入页背景优先 (float_entry_img 指定文件), 否则内置素材 */
    private fun entryAsset(app: Context): String {
        val custom = SPUtils.getParam(app, "float_entry_img", "") as String
        return if (custom.isNotEmpty() && java.io.File(custom).exists()) "" else ASSET
    }

    private fun attach(app: Context, onEnter: () -> Unit) {
        shownOnce = true
        val rect = panelRect(app)
        val cv = ComposeView(app)
        val owner = EntryOwner().also { it.markResumed() }
        cv.setViewTreeLifecycleOwner(owner)
        cv.setViewTreeViewModelStoreOwner(owner)
        cv.setViewTreeSavedStateRegistryOwner(owner)
        cv.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        cv.setContent {
            EntryPage(
                art = art,
                onEnter = {
                    // 退场动画第 ②~③ 步才走到这: 此时进入页仍是完全不透明的一张图,
                    // 面板在它背后淡入(240ms)被完全盖住, 不会漏出桌面/游戏画面。
                    onEnter()
                },
                onClose = {
                    // 没进首页: 丢弃预热窗口, 不留隐形窗; 关闭时给个淡出
                    FloatWindow.discardPreload()
                    fadeOutAndHide()
                },
                onFinish = {
                    // 背景图已淡到 0, 底下就是完全展开的面板 → 直接摘窗, 无残留、无闪
                    hide()
                },
            )
        }
        val lp = WindowManager.LayoutParams(
            rect[0],
            rect[1],
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            // 与悬浮窗面板完全同一矩形 (同尺寸 + 同落点) → 「进入首页」原地无缝接住
            gravity = Gravity.TOP or Gravity.START
            x = rect[2]
            y = rect[3]
            WinCompat.applyCutoutMode(this)   // 失败忽略: 不能让可选适配拖垮建窗
        }
        win = try {
            val windowManager = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.addView(cv, lp)
            // 部分 ROM(MIUI/ColorOS) 在 Activity 前台 addView 后不立即 layout,
            // 要等下一次窗口变化(切后台再回来)才显示 —— 这里主动触发一次重排/重设
            cv.post {
                runCatching {
                    cv.requestLayout()
                    windowManager.updateViewLayout(cv, lp)
                }
            }
            cv
        } catch (e: Exception) {
            null
        }
        ctxRef = WeakReference(app)

        // 预热悬浮窗视图树: 进入页入场动画(2s 渐显)期间把面板整套建好并布局完,
        // 点「进入首页」时就只剩"淡出 + 圆形展开"两个动画 → 不再卡一下
        Handler(Looper.getMainLooper()).postDelayed({
            if (win != null) runCatching { FloatWindow.preload(app) }.getOrNull()
        }, 240)
    }

    /**
     * 进入页退场 (仅 ✕ 关闭时用): 纯淡出, 不做缩放 —— 缩放会在边缘露出主页。
     * 「进入首页」不走这里, 走 [EntryPage] 内部的对称反向动画。
     */
    private fun fadeOutAndHide() {
        shownOnce = true
        val v = win ?: return
        win = null
        try {
            v.animate()
                .alpha(0f)
                .setDuration(240)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    try {
                        val app = ctxRef?.get() ?: return@withEndAction
                        (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
                    } catch (_: Exception) {
                    }
                }
                .start()
        } catch (_: Exception) {
            hide()
        }
    }

    fun hide() {
        shownOnce = true
        val v = win ?: return
        win = null
        try {
            val app = ctxRef?.get() ?: return
            (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (_: Exception) {
        }
    }

    /**
     * 进入页窗口矩形 —— 直接复用悬浮窗面板的**同一个** [FloatWindow.panelRectPx],
     * 保证两者尺寸与落点完全一致 (并已被夹进屏宽 92% / 屏高 70%, 不会超出屏幕)。
     * 返回 [宽, 高, x, y] (px)
     */
    private fun panelRect(app: Context): IntArray {
        return FloatWindow.panelRectPx(app)
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

    // 悬浮窗专用宿主: 生命周期直接 RESUMED (窗口在即视为前台)
    private class EntryOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedController = SavedStateRegistryController.create(this)
            .also { it.performAttach() }
        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore: ViewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry get() = savedController.savedStateRegistry

        // 仿 Activity onCreate 顺序: 先 restore 再推进生命周期
        fun markResumed() {
            savedController.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }
    }
}

// ═══════════════════════════════════════════════
//  进入页 UI (1:1 移植 qing VetileMiniLauncher 的 START 页)
// ═══════════════════════════════════════════════
private object GlassUi {
    val panelWidthFraction = 0.37f
    val slantFraction = 0.07f
    val rightGapFraction = 0.018f
    val scrimColor = Color(0xFF26212E).copy(alpha = 0.40f)
    val glassBtnBg = Color.White.copy(alpha = 0.18f)
    val glassBtnBorder = Color.White.copy(alpha = 0.35f)
    val nameAlpha = 0.95f
}

// 右侧平行四边形条带: 距右缘留 rightGap
private class RightBandShape(
    private val bandWidthFraction: Float,
    private val slantFraction: Float,
    private val rightGapFraction: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val right = w * (1f - rightGapFraction)
        val x0 = right - w * bandWidthFraction
        val sx = w * slantFraction
        val path = Path().apply {
            moveTo(x0, 0f)
            lineTo(right - sx, 0f)
            lineTo(right, h)
            lineTo(x0 + sx, h)
            close()
        }
        return Outline.Generic(path)
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun EntryPage(
    art: EntryArt?,
    onEnter: () -> Unit,
    onClose: () -> Unit,
    onFinish: () -> Unit,
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val bgFade = remember { Animatable(0f) }
        val slideP = remember { Animatable(0f) }
        val uiFade = remember { Animatable(0f) }
        val welcomeFade = remember { Animatable(0f) }
        // 就绪门: 首次纹理上传等重活先以透明态跑完 (~3 帧), 首帧卡顿不落在可见动画上
        var ready by remember { mutableStateOf(false) }
        // 退场中: 锁住重入, 并让入场动画的 LaunchedEffect 以 exiting 为 key 自动作废
        // (否则入场里 delay(2000) 后那次 slideP.animateTo(1f) 会把退场又拽回去)
        var exiting by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            withFrameNanos { }
            withFrameNanos { }
            withFrameNanos { }
            ready = true
        }
        LaunchedEffect(ready, exiting) {
            if (!ready || exiting) return@LaunchedEffect
            launch { bgFade.animateTo(1f, tween(2000, easing = FastOutSlowInEasing)) }
            launch {
                delay(2000)
                // 平行四边形透明度由滑行进度驱动: 滑行一开始就现身, 全程可见
                slideP.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
            }
            launch {
                delay((2000 + 1100 * 0.45f).toLong())
                uiFade.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
                welcomeFade.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
            }
        }

        /** 「进入首页」= 把入场原路倒放一遍, 最后溶进面板 */
        fun enterHome() {
            if (exiting) return
            exiting = true
            scope.launch {
                // ① 文案/按钮在滑走的后半程收掉, 眼睛看到的是"整条带子退回侧边"
                launch {
                    delay(360)
                    welcomeFade.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                    uiFade.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                }
                slideP.animateTo(0f, tween(620, easing = FastOutSlowInEasing))
                // ② 面板在这张还不透明的背景图背后淡入(240ms) —— 被完全盖住, 不漏底
                onEnter()
                delay(260)
                // ③ 背景图再淡出一次: 就是入场那 2s 渐显的倒放, 溶成面板
                bgFade.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
                // ④ 此刻底下已是完全展开的面板, 摘窗无残留
                onFinish()
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val winW = maxWidth
            val winH = maxHeight
            val slant = winW * GlassUi.slantFraction
            val gap = winW * GlassUi.rightGapFraction
            val density = androidx.compose.ui.platform.LocalDensity.current
            val slideFromPx = with(density) { (winW * 0.46f).toPx() }
            val curArt = art

            // ① 主体: 清晰背景图渐显 (水平镜像翻转: scaleX = -1)
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = bgFade.value
                scaleX = -1f
            }) {
                if (curArt == null) {
                    Box(Modifier.fillMaxSize().background(Color(0x9926212E)))
                } else {
                    Image(
                        bitmap = curArt.crisp,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // ② 滑入层: 页面 + 右上按钮组
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = (1f - slideP.value) * slideFromPx }
            ) {
                StartPageLayer(
                    art = curArt,
                    slideP = slideP,
                    uiFade = uiFade,
                    welcomeFade = welcomeFade,
                    onEnter = { enterHome() },
                    onTitleClose = { if (!exiting) onClose() },
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = slant + gap + 8.dp)
                        .graphicsLayer { alpha = uiFade.value },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RoundIconButton(
                        icon = Icons.Filled.Close,
                        desc = "关闭",
                        onClick = { if (!exiting) onClose() },
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
    bg: Color = Color.Black.copy(alpha = 0.25f),
    iconTint: Color = Color.White.copy(alpha = 0.9f),
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(bg, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = iconTint,
            modifier = Modifier.size(13.dp)
        )
    }
}

// ── 启动页层: 平行四边形玻璃 (模糊带 + 蒙层) + 贴右 37% 的内容盒 ──
@Composable
private fun StartPageLayer(
    art: EntryArt?,
    slideP: Animatable<Float, AnimationVector1D>,
    uiFade: Animatable<Float, AnimationVector1D>,
    welcomeFade: Animatable<Float, AnimationVector1D>,
    onEnter: () -> Unit,
    onTitleClose: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val band = RightBandShape(GlassUi.panelWidthFraction, GlassUi.slantFraction, GlassUi.rightGapFraction)
        val gap = maxWidth * GlassUi.rightGapFraction
        val winH = maxHeight

        // 透明度随滑行进度现身 (前 ~35% 行程内到位); 下限 0.004 = 纹理预热
        fun bandAlphaOf(p: Float): Float = ((p / 0.35f).coerceIn(0f, 1f)).coerceAtLeast(0.004f)

        val blurBmp = art?.blur
        if (blurBmp != null) {
            Image(
                bitmap = blurBmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(band)
                    .graphicsLayer {
                        alpha = bandAlphaOf(slideP.value)
                        scaleX = -1f   // 与主体背景同向镜像, 两块不脱节
                    }
                    .drawWithContent {
                        val shift = (1f - slideP.value) * size.width * 0.46f
                        translate(left = -shift) { this@drawWithContent.drawContent() }
                    }
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .clip(band)
                .graphicsLayer { alpha = bandAlphaOf(slideP.value) }
                .background(GlassUi.scrimColor)
        )
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(GlassUi.panelWidthFraction)
                .align(Alignment.CenterEnd)
                .padding(end = gap)
                .graphicsLayer { alpha = uiFade.value }
        ) {
            StartContent(
                welcomeFade = welcomeFade,
                enterHome = onEnter,
                onTitleClose = onTitleClose,
            )
        }
    }
}

@Composable
private fun StartContent(
    welcomeFade: Animatable<Float, AnimationVector1D>,
    enterHome: () -> Unit,
    onTitleClose: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 子盒坐标系 = 面板宽: 斜切量按 (整窗斜切/面板占比) 折算, 与平行四边形同角
        val slant = maxWidth * (GlassUi.slantFraction / GlassUi.panelWidthFraction)
        val sidePad = (maxWidth * 0.15f).coerceIn(12.dp, 84.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = sidePad)
                .padding(top = 56.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "离情梦",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = GlassUi.nameAlpha),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .offset(x = -slant * 0.18f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onTitleClose)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Text(
                text = "Welcome",
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .graphicsLayer { alpha = welcomeFade.value }
            )
            Spacer(Modifier.weight(1f))
            StartButton(onClick = enterHome)
        }
    }
}

@Composable
private fun StartButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 150.dp)
            .height(36.dp)
            .clip(shape)
            .background(GlassUi.glassBtnBg)
            .border(1.dp, GlassUi.glassBtnBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "进入首页",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// ═══════════════════════════════════════════════
//  素材解码 + 软件盒式模糊 (移植自 qing)
// ═══════════════════════════════════════════════
internal class EntryArt(
    val crisp: ImageBitmap,
    val blur: ImageBitmap?,
    private val raw: Bitmap,
    private val blurred: Bitmap?,
) {
    fun dispose() {
        if (!raw.isRecycled) raw.recycle()
        if (blurred != null && !blurred.isRecycled) blurred.recycle()
    }
}

internal fun decodeArt(context: Context, asset: String, withBlur: Boolean): EntryArt? {
    return try {
        val raw = if (asset.isEmpty()) {
            // 自定义图片路径
            val path = SPUtils.getParam(context, "float_entry_img", "") as String
            if (path.isEmpty()) return null
            BitmapFactory.decodeFile(path)
        } else {
            context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
        } ?: return null
        if (!withBlur) return EntryArt(raw.asImageBitmap(), null, raw, null)
        val w = raw.width
        val h = raw.height
        val sw = (w / 5).coerceAtLeast(2)
        val sh = (h / 5).coerceAtLeast(2)
        val small = Bitmap.createScaledBitmap(raw, sw, sh, true)
        val blured = boxBlur(small, 3, 2)
        if (small !== blured) small.recycle()
        val back = Bitmap.createScaledBitmap(blured, w, h, true)
        if (back !== blured) blured.recycle()
        EntryArt(raw.asImageBitmap(), back.asImageBitmap(), raw, back)
    } catch (e: Exception) {
        null
    }
}

// 滑动窗口盒式模糊
private fun boxBlur(src: Bitmap, radius: Int, passes: Int): Bitmap {
    val w = src.width
    val h = src.height
    val out = Bitmap.createBitmap(src)
    val pix = IntArray(w * h)
    val tmp = IntArray(w * h)
    out.getPixels(pix, 0, w, 0, 0, w, h)
    val window = radius * 2 + 1
    repeat(passes) {
        for (y in 0 until h) {
            var r = 0; var g = 0; var b = 0; var a = 0
            val row = y * w
            for (x in -radius until w + radius) {
                if (x + radius < w) {
                    val c = pix[row + x + radius]
                    r += (c shr 16) and 0xFF
                    g += (c shr 8) and 0xFF
                    b += c and 0xFF
                    a += (c ushr 24) and 0xFF
                }
                if (x - radius > 0) {
                    val c = pix[row + x - radius - 1]
                    r -= (c shr 16) and 0xFF
                    g -= (c shr 8) and 0xFF
                    b -= c and 0xFF
                    a -= (c ushr 24) and 0xFF
                }
                if (x in 0 until w) {
                    tmp[row + x] = ((a / window) shl 24) or ((r / window) shl 16) or
                            ((g / window) shl 8) or (b / window)
                }
            }
        }
        for (x in 0 until w) {
            var r = 0; var g = 0; var b = 0; var a = 0
            for (y in -radius until h + radius) {
                if (y + radius < h) {
                    val c = tmp[(y + radius) * w + x]
                    r += (c shr 16) and 0xFF
                    g += (c shr 8) and 0xFF
                    b += c and 0xFF
                    a += (c ushr 24) and 0xFF
                }
                if (y - radius > 0) {
                    val c = tmp[(y - radius - 1) * w + x]
                    r -= (c shr 16) and 0xFF
                    g -= (c shr 8) and 0xFF
                    b -= c and 0xFF
                    a -= (c ushr 24) and 0xFF
                }
                if (y in 0 until h) {
                    out.setPixel(
                        x, y,
                        ((a / window) shl 24) or ((r / window) shl 16) or
                                ((g / window) shl 8) or (b / window)
                    )
                }
            }
        }
    }
    return out
}
