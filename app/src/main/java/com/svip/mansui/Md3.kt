package com.svip.mansui

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.svip.mansui.IPCurbco.TClass.SPUtils
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════
//  MD3 组件库 (纯代码, 零外部依赖; 配色/布局参考 G:\jiangxiaosui MD3 主题)
//  2026-09-01 UI 全面重构
// ═══════════════════════════════════════════════
object Md3 {
    // 与 G:\jiangxiaosui values/colors.xml 一致 (md3_* 角色)
    val primary = 0xFF415C8E.toInt()              // md3_primary 深蓝
    val onPrimary = 0xFFFFFFFF.toInt()
    val primaryContainer = 0xFFD6E3FF.toInt()     // md3_primary_container
    val onPrimaryContainer = 0xFF001B3E.toInt()
    val secondary = 0xFF565F71.toInt()
    val onSecondary = 0xFFFFFFFF.toInt()
    val surface = 0xFFFEFBFF.toInt()              // md3_surface
    val onSurface = 0xFF1B1B1F.toInt()
    val surfaceVariant = 0xFFE7E0EC.toInt()
    val onSurfaceVariant = 0xFF49454F.toInt()
    val error = 0xFFBA1A1A.toInt()
    val onError = 0xFFFFFFFF.toInt()
    val background = 0xFFF2F0F5.toInt()           // 页面浅灰紫底, 衬托白色无描边卡片
    val outline = 0xFFC7C8CC.toInt()
    val outlineVariant = 0xFFE4E1E6.toInt()
    val rippleColor = 0x24415C8E.toInt()          // primary @ ~14%
    val rippleOnPrimary = 0x33FFFFFF.toInt()
    val shadow = 0x14000000.toInt()

    fun dp(context: Context, v: Float): Int = (v * context.resources.displayMetrics.density).roundToInt()

    fun rounded(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }

    fun ripple(shape: GradientDrawable, ripple: Int = rippleColor): RippleDrawable =
        RippleDrawable(android.content.res.ColorStateList.valueOf(ripple), shape, shape)

    // ── MD3 按钮 (TextView 实现, 极简风: 无任何描边, 圆角 20dp + 水波) ──
    // filled=true  → 实心 primary 底 + 白字 (Filled Button)
    // filled=false → tonal 浅蓝底 (primaryContainer) + 深蓝字 (Tonal Button, 无边框)
    // weight != null 时占横向权重 (0 宽 + weight), 用于并排按钮; 否则 MATCH_PARENT
    fun button(
        context: Context, text: String, height: Int, filled: Boolean = true,
        corner: Float = 20f, bg: Int = if (filled) primary else primaryContainer,
        fg: Int = if (filled) onPrimary else onPrimaryContainer,
        weight: Float? = null
    ): TextView {
        val b = TextView(context)
        b.text = text
        b.textSize = 15f
        b.gravity = Gravity.CENTER
        b.setTypeface(b.typeface, android.graphics.Typeface.BOLD)
        b.setTextColor(fg)
        b.layoutParams = if (weight != null)
            LinearLayout.LayoutParams(0, height, weight)
        else
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        val shape = rounded(bg, corner)
        b.background = ripple(shape, if (filled) rippleOnPrimary else rippleColor)
        b.isClickable = true
        b.isFocusable = true
        return b
    }

    // ── MD3 卡片容器 (极简: 白底圆角 16dp 无描边 + 柔和投影, 媒体背景上仍可辨识) ──
    fun card(context: Context, corner: Float = 16f): LinearLayout {
        val c = LinearLayout(context)
        c.orientation = LinearLayout.VERTICAL
        c.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        c.background = rounded(surface, corner)
        c.elevation = dp(context, 3f).toFloat()
        c.setPadding(dp(context, 16f), dp(context, 12f), dp(context, 16f), dp(context, 12f))
        return c
    }

    fun text(context: Context, s: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    // 可点击行 (ripple + 高 48dp)
    fun row(context: Context, height: Int = 48): TextView {
        val r = TextView(context)
        r.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(context, 12f), 0, dp(context, 12f), 0)
        val shape = rounded(Color.TRANSPARENT, 12f)
        r.background = ripple(shape)
        r.isClickable = true
        r.isFocusable = true
        return r
    }
}

// ═══════════════════════════════════════════════
//  MD3 Switch (轨道/滑块 + 动画 + 水波, LSPosed 风格)
// ═══════════════════════════════════════════════
class Md3Switch(context: Context) : View(context) {
    var isOn: Boolean = false
        private set

    var onCheckedChangeListener: ((Boolean) -> Unit)? = null

    // 简约适中: 轨道 44×28, 滑块 20 (圆角胶囊滑轨)
    private val trackW = Md3.dp(context, 40f).toFloat()
    private val trackH = Md3.dp(context, 24f).toFloat()
    private val thumbR = Md3.dp(context, 9f).toFloat()
    private val rippleR = Md3.dp(context, 15f).toFloat()
    private val thumbMin = trackH / 2f
    private val thumbMax = trackW - trackH / 2f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    // 开关灰黑轮廓圈 (包住整个开关控件)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Md3.dp(context, 1f).toFloat()
        color = 0x2E000000   // 与开关胶囊描边同色 (0x2E000000)
    }
    private val trackRect = RectF()

    private var progress = 0f            // 0=关 1=开
    private var animator: ValueAnimator? = null
    private var pressed = false

    // 配色: 关=浅灰轨道+白滑块(细灰描边); 开=淡天蓝轨道+白滑块(细蓝描边)
    private val trackOff = 0xFFE2E0E5.toInt()
    private val trackOn = 0xFF9FC3F2.toInt()
    private val thumbFill = 0xFFFFFFFF.toInt()
    private val ringOff = 0xFFC2BEC8.toInt()
    private val ringOn = 0xFF6FA3E8.toInt()

    init {
        minimumWidth = trackW.roundToInt()
        minimumHeight = trackH.roundToInt()
        isClickable = true
        isFocusable = true
        setOnClickListener { setOn(!isOn, true) }
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { pressed = true; invalidate() }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pressed = false; invalidate()
                }
            }
            false
        }
    }

    fun setOn(on: Boolean, animate: Boolean) {
        if (isOn == on) return
        isOn = on
        animator?.cancel()
        val from = progress
        val to = if (on) 1f else 0f
        if (!animate) {
            progress = to
            invalidate()
        } else {
            animator = ValueAnimator.ofFloat(from, to).apply {
                duration = 200
                interpolator = Md3Motion.standard
                addUpdateListener { progress = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        onCheckedChangeListener?.invoke(on)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(trackW.roundToInt(), trackH.roundToInt())
    }

    override fun onDraw(canvas: Canvas) {
        // 轨道 (细胶囊)
        trackRect.set(0f, 0f, trackW, trackH)
        trackPaint.color = lerpColor(trackOff, trackOn, progress)
        canvas.drawRoundRect(trackRect, trackH / 2f, trackH / 2f, trackPaint)

        // 按压水波 (半透明浅蓝, 低调)
        if (pressed) {
            ripplePaint.color = lerpArgb(0x1E415C8E.toInt(), 0x264A80D8.toInt(), progress)
            canvas.drawCircle(thumbX(), thumbMin, rippleR, ripplePaint)
        }

        // 滑块: 白底 + 1px 细描边 (关=灰 / 开=淡蓝)
        thumbPaint.style = Paint.Style.FILL
        thumbPaint.color = thumbFill
        canvas.drawCircle(thumbX(), thumbMin, thumbR, thumbPaint)
        thumbPaint.style = Paint.Style.STROKE
        thumbPaint.strokeWidth = Md3.dp(context, 1f).toFloat()
        thumbPaint.color = lerpColor(ringOff, ringOn, progress)
        canvas.drawCircle(thumbX(), thumbMin, thumbR, thumbPaint)
        thumbPaint.style = Paint.Style.FILL

        // 灰黑轮廓圈: 最后绘制盖在轨道上 (整体收进一个线宽, 不贴边不裁切)
        val inset = ringPaint.strokeWidth
        canvas.drawRoundRect(inset, inset, trackW - inset, trackH - inset,
                             trackH / 2f - inset, trackH / 2f - inset, ringPaint)
    }

    private fun thumbX(): Float = thumbMin + (thumbMax - thumbMin) * progress

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt()
        return Color.rgb(r, g, b)
    }

    // ARGB 分量整体渐变 (含透明度), 用于按压水波等半透明主题色
    private fun lerpArgb(from: Int, to: Int, t: Float): Int {
        fun ch(shift: Int, f: Int, tt: Float): Int {
            val a = (f shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + ((b - a) * tt).toInt()) and 0xFF
        }
        return (ch(24, from, t) shl 24) or (ch(16, from, t) shl 16) or
            (ch(8, from, t) shl 8) or ch(0, from, t)
    }
}

// ═══════════════════════════════════════════════
//  主页循环动画背景 (浅蓝柔光斑缓慢漂移, 无限循环, 纯 Canvas)
// ═══════════════════════════════════════════════
class Md3AmbientBackground(context: Context) : View(context) {
    private val blobs = arrayListOf(
        Blob(Md3.primaryContainer, 0.26f, 0.22f, 190f, 0f, 0f),
        Blob(0xFFE3EDFA.toInt(), 0.75f, 0.30f, 160f, 2.1f, 0.8f),
        Blob(0xFFE8F0FC.toInt(), 0.30f, 0.72f, 210f, 1.3f, 1.9f),
        Blob(Md3.surfaceVariant, 0.72f, 0.78f, 150f, 0.5f, 2.6f)
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null

    private class Blob(val color: Int, val cx: Float, val cy: Float, val radius: Float, val px: Float, val py: Float)

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)   // RadialGradient 需要软件层
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 12000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val t = (System.currentTimeMillis() % 12000L) / 12000f * (2f * Math.PI).toFloat()

        for (b in blobs) {
            val x = w * b.cx + Math.sin((t * b.px * 2f + b.py).toDouble()).toFloat() * w * 0.06f
            val y = h * b.cy + Math.cos((t * b.py * 2f + b.px).toDouble()).toFloat() * h * 0.05f
            val r = b.radius * Md3.dp(context, 1f).toFloat()
            paint.shader = RadialGradient(x, y, r,
                intArrayOf(b.color, 0x00FFFFFF),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, r, paint)
        }
        paint.shader = null
    }
}

// ═══════════════════════════════════════════════
//  主页循环视频背景 (静音 + 顶部白色 55% 遮罩保证文字可读)
//  sources: 播放列表 (String=assets 路径 / Uri=自定义视频), 播完自动切下一个, 循环轮播 + 淡入
//  无视频时降级为纯色遮罩背景
// ═══════════════════════════════════════════════
class Md3VideoBackground(context: Context, private val sources: List<Any>) : FrameLayout(context) {
    private val texture = TextureView(context)
    private var player: MediaPlayer? = null
    private var index = 0

    constructor(context: Context, source: Any) : this(context, listOf(source))

    init {
        texture.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(texture)

        texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                startPlayback(surface)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    // 当前播放位置 (微秒) 与当前源 (液态玻璃抽帧对齐用)
    fun currentPositionUs(): Long = (player?.currentPosition ?: 0) * 1000L

    fun currentSource(): Any? = sources.getOrNull(index)

    private fun setSource(mp: MediaPlayer, src: Any) {
        when (src) {
            is String -> {
                val fd = context.assets.openFd(src)
                mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            is Uri -> mp.setDataSource(context, src)
        }
    }

    private fun startPlayback(surface: SurfaceTexture) {
        stopPlayback()
        if (sources.isEmpty()) return
        index = 0
        try {
            player = MediaPlayer().apply {
                setSource(this, sources[0])
                setSurface(Surface(surface))
                isLooping = false
                setVolume(0f, 0f)   // 静音
                setOnPreparedListener {
                    it.start()
                    fitCenterCrop()
                }
                // 播放完 → 自动切下一个视频, 循环轮播 + 淡入衔接
                setOnCompletionListener { mp ->
                    index = (index + 1) % sources.size
                    try {
                        mp.reset()
                        setSource(mp, sources[index])
                        mp.prepareAsync()
                        texture.alpha = 0f
                        texture.animate().alpha(1f).setDuration(200).start()
                    } catch (e: Exception) {
                        mp.start()
                    }
                }
                setOnErrorListener { _, _, _ -> true }
                prepareAsync()
            }
        } catch (e: Exception) {
            player = null   // 无视频 → 保持纯色遮罩
        }
    }

    // center-crop 适配: 按容器比例缩放视频纹理, 避免拉伸变形
    private fun fitCenterCrop() {
        val p = player ?: return
        val vw = p.videoWidth
        val vh = p.videoHeight
        if (vw <= 0 || vh <= 0) return
        post {
            val cw = width
            val ch = height
            if (cw <= 0 || ch <= 0) return@post
            val scale = maxOf(cw.toFloat() / vw, ch.toFloat() / vh)
            texture.layoutParams = LayoutParams((vw * scale).toInt(), (vh * scale).toInt(), Gravity.CENTER)
            texture.requestLayout()
        }
    }

    private fun stopPlayback() {
        player?.apply {
            stop()
            release()
        }
        player = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopPlayback()
    }
}

// ═══════════════════════════════════════════════
//  图片背景 (centerCrop + 白色 55% 遮罩)
//  支持: Uri=自定义图片 / String=assets 内置图片
// ═══════════════════════════════════════════════
class Md3ImageBackground(context: Context, uri: Uri) : FrameLayout(context) {
    constructor(context: Context, assetPath: String) : this(context, Uri.EMPTY) {
        try {
            val img = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }
            addView(img)
            addView(View(context).apply {
                setBackgroundColor(0x59FFFFFF.toInt())
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            })
            context.assets.open(assetPath).use {
                img.setImageBitmap(BitmapFactory.decodeStream(it))
            }
        } catch (e: Exception) {
            // 读取失败 → 纯色遮罩
        }
    }

    init {
        val img = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(img)
        try {
            context.contentResolver.openInputStream(uri)?.use {
                img.setImageBitmap(BitmapFactory.decodeStream(it))
            }
        } catch (e: Exception) {
            // 读取失败 → 纯色遮罩
        }
    }
}

// ═══════════════════════════════════════════════
//  折叠卡 (高度 + 透明度动画, chevron 旋转)
// ═══════════════════════════════════════════════
class Md3Fold(
    private val context: Context,
    private val title: String,
    private val content: View,
    private val onHeaderClick: (() -> Unit)? = null
) {
    val root: LinearLayout
    private val chevron: TextView
    private val contentHolder = LinearLayout(context)
    private var expanded = false

    init {
        root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        root.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val header = LinearLayout(context)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Md3.dp(context, 48f))
        header.setPadding(Md3.dp(context, 12f), 0, Md3.dp(context, 12f), 0)
        val shape = Md3.rounded(android.graphics.Color.TRANSPARENT, 12f)
        header.background = Md3.ripple(shape)

        val titleText = Md3.text(context, title, 15f, Md3.onSurface, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(titleText)

        chevron = Md3.text(context, "▾", 18f, Md3.onSurfaceVariant, true)
        val chevronWrap = FrameLayout(context)
        chevronWrap.layoutParams = LinearLayout.LayoutParams(Md3.dp(context, 24f), Md3.dp(context, 24f))
        chevronWrap.addView(chevron)
        header.addView(chevronWrap)

        header.setOnClickListener { toggle() }
        root.addView(header)

        contentHolder.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
        contentHolder.isClickable = true
        contentHolder.visibility = View.GONE
        contentHolder.addView(content)
        root.addView(contentHolder)
    }

    fun toggle() {
        expanded = !expanded
        if (expanded) {
            contentHolder.visibility = View.VISIBLE
            contentHolder.measure(
                View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val target = contentHolder.measuredHeight
            ValueAnimator.ofInt(0, target).apply {
                duration = 320
                interpolator = Md3Motion.decelEmphasized
                addUpdateListener { a ->
                    contentHolder.layoutParams.height = a.animatedValue as Int
                    contentHolder.requestLayout()
                    chevron.rotation = 180f * (a.animatedValue as Int).toFloat() / target
                }
                start()
            }
        } else {
            val from = contentHolder.layoutParams.height
            ValueAnimator.ofInt(from, 0).apply {
                duration = 260
                interpolator = Md3Motion.accelEmphasized
                addUpdateListener { a ->
                    contentHolder.layoutParams.height = a.animatedValue as Int
                    contentHolder.requestLayout()
                    chevron.rotation = 180f * (a.animatedValue as Int).toFloat() / from
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        contentHolder.visibility = View.GONE
                    }
                })
                start()
            }
        }
        onHeaderClick?.invoke()
    }
}

// 渠道列表 (与 jiangxiaosui GameChannel 一致)
// 渠道 (与梦之喵羽完全对齐: 迷你世界 minitech 系, 顺序 官方服/233/4399/九游/小米/OPPO/vivo)
val CHANNEL_NAMES = arrayOf(
    "官方服", "233渠道", "4399渠道", "九游渠道", "小米渠道",
    "OPPO渠道", "vivo渠道"
)

val CHANNEL_PACKAGES = arrayOf(
    "com.minitech.miniworld",                  // 官方服
    "com.minitech.miniworld.meta",             // 233
    "com.minitech.miniworld.m4399",            // 4399
    "com.minitech.miniworld.uc",               // 九游
    "com.minitech.miniworld.TMobile.mi",       // 小米
    "com.minitech.miniworld.nearme.gamecenter",// OPPO
    "com.minitech.miniworld.vivo",             // vivo
)

// ── 通用 UI 工具 (原 MainActivity 私有方法) ──

// 状态栏高度 (edge-to-edge 布局避让)
fun Context.statusBarHeight(): Int {
    val id = resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (id > 0) resources.getDimensionPixelSize(id) else 0
}

// 1px 分割线
fun Context.dividerView(): View = View(this).apply {
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Md3.dp(this@dividerView, 1f))
    setBackgroundColor(Md3.outlineVariant)
}

// 梦之初风格弹窗: 白底/微透明白 28dp 大圆角 (非白底背景模式用微透明白)
fun styleBgDialog(context: Context, d: AlertDialog) {
    val glass = (SPUtils.getParam(context.applicationContext, "bg_mode", 0) as Int) != 0
    d.window?.setBackgroundDrawable(
        Md3.rounded(if (glass) 0xCCFFFFFF.toInt() else Md3.surface, Md3.dp(context, 28f).toFloat()))
}

// ═══════════════════════════════════════════════
//  MD3 浅色弹窗/Toast 工具 (纯代码, 替代平台默认 AlertDialog/Toast 样式)
// ═══════════════════════════════════════════════
object Md3Dialog {
    // 统一弹窗窗口: 透明底 + 屏幕 85% 宽 (与 ModeSelectDialog 一致)
    private fun styleWindow(d: AlertDialog) {
        d.window?.let { w ->
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            val lp = w.attributes
            lp.width = (d.context.resources.displayMetrics.widthPixels * 0.85).toInt()
            w.attributes = lp
        }
    }

    // 浅色圆角容器 (surface 白底 28dp, 无描边)
    private fun base(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = Md3.rounded(Md3.surface, Md3.dp(context, 28f).toFloat())
        setPadding(Md3.dp(context, 24f), Md3.dp(context, 24f), Md3.dp(context, 24f), Md3.dp(context, 18f))
    }

    // 标题 + 正文 (仅文本弹窗共用)
    private fun addTitleAndBody(root: LinearLayout, context: Context, title: String, body: String) {
        root.addView(Md3.text(context, title, 20f, Md3.onSurface, true).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        val bodyView = Md3.text(context, body, 14.5f, Md3.onSurfaceVariant).apply {
            setLineSpacing(0f, 1.2f)
        }
        // 先赋 LayoutParams 再 addView: 新建 View 未 attach 时 layoutParams 为 null
        bodyView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Md3.dp(context, 10f)
        }
        root.addView(bodyView)
    }

    // MD3 文字按钮 (右侧操作栏, 透明底 + 水波, 浅色简洁)
    private fun textAction(context: Context, label: String, color: Int): TextView = TextView(context).apply {
        this.text = label
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(color)
        gravity = Gravity.CENTER
        minimumHeight = Md3.dp(context, 40f)
        setPadding(Md3.dp(context, 16f), 0, Md3.dp(context, 16f), 0)
        background = Md3.ripple(Md3.rounded(Color.TRANSPARENT, Md3.dp(context, 14f).toFloat()))
        isClickable = true
        isFocusable = true
    }

    // MD3 确认弹窗: 标题 + 正文 + 右侧 取消/确定 文字按钮
    // 浅色 surface 圆角容器; negativeText=null 时只显示确定按钮
    fun confirm(
        context: Context,
        title: String,
        message: String,
        positiveText: String = "确定",
        negativeText: String? = "取消",
        cancelable: Boolean = true,
        onPositive: Runnable? = null,
        onNegative: Runnable? = null
    ): AlertDialog {
        val root = base(context)
        addTitleAndBody(root, context, title, message)
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val d: AlertDialog
        val negativeBtn: TextView?
        val positiveBtn: TextView
        if (negativeText != null && cancelable) {
            negativeBtn = textAction(context, negativeText, Md3.onSurfaceVariant)
            actions.addView(negativeBtn)
        } else {
            negativeBtn = null
        }
        positiveBtn = textAction(context, positiveText, Md3.primary)
        actions.addView(positiveBtn)
        root.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Md3.dp(context, 16f)
        })
        d = AlertDialog.Builder(context)
            .setCancelable(cancelable)
            .setView(root)
            .create()
        negativeBtn?.setOnClickListener {
            d.dismiss()
            onNegative?.run()
        }
        positiveBtn.setOnClickListener {
            d.dismiss()
            onPositive?.run()
        }
        d.show()
        styleWindow(d)
        return d
    }

    // MD3 列表选择弹窗 (单层选项, 选中项高亮主色 + 尾部 ✓)
    // selected=-1 时无选中高亮; 点选后自动关闭并回调 onSelect
    fun optionList(
        context: Context,
        title: String,
        options: Array<String>,
        selected: Int = -1,
        onSelect: (Int) -> Unit
    ): AlertDialog {
        val root = base(context)
        root.addView(Md3.text(context, title, 20f, Md3.onSurface, true).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, 0, Md3.dp(context, 8f))
        })
        val dialogRef = arrayOfNulls<AlertDialog>(1)
        for ((i, name) in options.withIndex()) {
            val active = i == selected
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Md3.dp(context, 46f))
                isClickable = true
                isFocusable = true
                background = Md3.ripple(Md3.rounded(Color.TRANSPARENT, Md3.dp(context, 12f).toFloat()))
                setPadding(Md3.dp(context, 10f), 0, Md3.dp(context, 10f), 0)
            }
            row.addView(Md3.text(context, name, 15f, if (active) Md3.primary else Md3.onSurface, active).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (active) {
                row.addView(Md3.text(context, "✓", 17f, Md3.primary, true).apply {
                    setPadding(Md3.dp(context, 6f), 0, 0, 0)
                })
            }
            row.setOnClickListener {
                dialogRef[0]?.dismiss()
                onSelect(i)
            }
            root.addView(row)
        }
        val d = AlertDialog.Builder(context).setCancelable(true).setView(root).create()
        dialogRef[0] = d
        d.show()
        styleWindow(d)
        return d
    }
}

// MD3 深色药丸 Toast (浅色主题下经典 inverse surface 视觉; 替换系统默认样式)
fun md3Toast(context: Context, msg: String, long: Boolean = false) {
    val app = context.applicationContext
    val tv = TextView(app).apply {
        text = msg
        textSize = 14f
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.CENTER
        setPadding(Md3.dp(app, 20f), Md3.dp(app, 12f), Md3.dp(app, 20f), Md3.dp(app, 12f))
        background = Md3.rounded(0xE6313036.toInt(), Md3.dp(app, 24f).toFloat())
    }
    val t = Toast.makeText(app, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT)
    t.view = tv
    t.setGravity(Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM, 0, Md3.dp(app, 88f))
    t.show()
}

// ═══════════════════════════════════════════════
//  MD3 运动曲线 (Material Motion 规范, PathInterpolator = cubic-bezier)
// ═══════════════════════════════════════════════
object Md3Motion {
    val standard = PathInterpolator(0.2f, 0f, 0f, 1f)               // standard 300ms: 通用/透明/颜色
    val decelEmphasized = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)   // 强调减速: 进入/展开
    val accelEmphasized = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)   // 强调加速: 退出/收起
}

// 自绘居中文本 (绕开个别设备 TextView 渲染异常导致尾部缺字; 用于启动检测页标题/说明)
class Md3CenteredText(
    context: Context,
    private val str: String,
    spSize: Float,
    color: Int,
    bold: Boolean = false
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = spSize * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        this.color = color
        if (bold) typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.12f   // 字距放宽: 视觉更透气, 避免"挤/缺"观感
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val needW = Math.ceil(paint.measureText(str).toDouble()).toInt()
        val needH = (paint.textSize * 1.5f).toInt()
        setMeasuredDimension(
            resolveSize(needW, widthMeasureSpec),
            resolveSize(needH, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val fm = paint.fontMetrics
        val y = height / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(str, width / 2f, y, paint)
    }
}

// 自绘加载转圈 (仿 MD3 CircularProgressIndicator, 无外部依赖)
class Md3Spinner(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Md3.dp(context, 2.5f).toFloat()
        strokeCap = Paint.Cap.ROUND
        color = Md3.primary
    }
    private var animator: ValueAnimator? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = Md3.dp(context, 26f)
        setMeasuredDimension(resolveSize(d, widthMeasureSpec), resolveSize(d, heightMeasureSpec))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        val r = (width / 2f) - paint.strokeWidth / 2f
        val t = (System.currentTimeMillis() % 1100L) / 1100f * 360f
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        // 断弧旋转: 效果同 M3 转圈
        canvas.drawArc(oval, t, 300f, false, paint)
    }
}

// ═══════════════════════════════════════════════
