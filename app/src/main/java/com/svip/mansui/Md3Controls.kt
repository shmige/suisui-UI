package com.svip.mansui
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

// ═══════════════════════════════════════════════
//  MD3 控件库 2 (KSU/M3 风格, 横屏小巧尺寸): 拖动条/折叠布局/复选框/输入框/多选按钮/配色动画
//  全部带震动反馈; 图标用矢量绘制 (无文字符号)
// ═══════════════════════════════════════════════

// 轻震动反馈 (调整控件时使用)
fun haptic(v: View) {
    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}

// 颜色线性插值 (配色动画基础)
fun lerpColor(from: Int, to: Int, t: Float): Int {
    val a = Color.alpha(from) + ((Color.alpha(to) - Color.alpha(from)) * t).toInt()
    val r = Color.red(from) + ((Color.red(to) - Color.red(from)) * t).toInt()
    val g = Color.green(from) + ((Color.green(to) - Color.green(from)) * t).toInt()
    val b = Color.blue(from) + ((Color.blue(to) - Color.blue(from)) * t).toInt()
    return Color.argb(a, r, g, b)
}

fun animateColor(from: Int, to: Int, duration: Long = 240, onUpdate: (Int) -> Unit) {
    ValueAnimator.ofFloat(0f, 1f).apply {
        this.duration = duration
        interpolator = Md3Motion.standard
        addUpdateListener { onUpdate(lerpColor(from, to, it.animatedValue as Float)) }
        start()
    }
}

// 折叠箭头 (矢量绘制, 旋转动画由外部驱动)
class ChevronView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Md3.dp(context, 1.8f).toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Md3.onSurfaceVariant
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        setMeasuredDimension(resolveSize((22f * d).toInt(), widthMeasureSpec), resolveSize((22f * d).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val s = width / 6f
        canvas.drawLine(cx - s, cy - s / 2f, cx, cy + s / 2f, paint)
        canvas.drawLine(cx, cy + s / 2f, cx + s, cy - s / 2f, paint)
    }
}

// ── 拖动条 (小巧: 30dp 高, 8dp 滑块, 拖动数值气泡 + 震动反馈) ──
class Md3Slider(context: Context) : View(context) {

    var value = 50f   // 0..100
        private set
    var onValueChange: ((Float) -> Unit)? = null
    // 气泡显示真实值 (默认 null = 显示原始 0..100 整数; 业务滑块必须接换算, 否则
    // 气泡"19"和实际"0.30"对不上)
    var valueLabel: ((Float) -> String)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val trackRect = RectF()
    private var dragging = false
    private var lastTick = -1
    private var target = 50f        // 手指目标值 (displayed 缓动追赶)
    private var chase: ValueAnimator? = null   // 持续追赶循环 (无重启, 永不断流)
    private var glide: ValueAnimator? = null   // 松手后的"滑行尾"
    private var bubbleA = 0f                   // 气泡可见度 0..1 (动画)

    init {
        setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    haptic(v)
                    glide?.cancel()
                    target = pointerOf(ev.x)
                    animateBubble(1f)
                    ensureChase()
                }
                MotionEvent.ACTION_MOVE -> if (dragging) {
                    // 手指移动: 只更新目标, 追赶持续不打断 (慢追 → 手停后才滑完)
                    target = pointerOf(ev.x)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    // 松手: 停止追赶循环, 启动 320ms 减速滑行尾 (可被打断)
                    chase?.cancel()
                    chase = null
                    startGlide()
                }
            }
            true
        }
    }

    private fun startGlide() {
        glide?.cancel()
        if (Math.abs(target - value) < 0.05f) {
            animateBubble(0f)
            return
        }
        glide = ValueAnimator.ofFloat(value, target).apply {
            duration = 320
            interpolator = DecelerateInterpolator()
            addUpdateListener { applyValue(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!dragging) animateBubble(0f)
                }
            })
            start()
        }
    }

    private fun animateBubble(to: Float) {
        bubbleAnim?.cancel()
        if (to > bubbleA) {
            bubbleA = to
        }
        val from = bubbleA
        ValueAnimator.ofFloat(from, to).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                bubbleA = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
    private var bubbleAnim: ValueAnimator? = null

    private fun pointerOf(x: Float): Float {
        val d = resources.displayMetrics.density
        val pad = 8f * d
        val w = width - pad * 2f
        if (w <= 0f) return target
        return ((x - pad) / w).coerceIn(0f, 1f) * 100f
    }

    // 单次追赶循环: 每帧向目标逼近 25% 剩余距离 (跟手 ~150ms, 数值与手指同步,
    // 过慢会造成"拖动值与实际值不一致"的观感), 松手时由 glide 滑行尾接管平滑收尾
    private fun ensureChase() {
        if (chase != null) return
        chase = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2147483647L   // 长跑循环
            addUpdateListener {
                val diff = target - value
                if (dragging) {
                    // 按住期间: 快速逼近 (即便几乎到达也不停, 直到松手)
                    applyValue(value + diff * 0.25f)
                } else if (Math.abs(diff) < 0.05f) {
                    chase?.cancel()
                    chase = null
                }
            }
            start()
        }
    }

    fun setValue(v: Float) {
        chase?.cancel()
        target = v.coerceIn(0f, 100f)
        value = target
        invalidate()
    }

    private fun withAlpha(color: Int, a: Float): Int {
        val alpha = (a.coerceIn(0f, 1f) * 255f).toInt()
        return (alpha shl 24) or (color and 0xFFFFFF)
    }

    private fun applyValue(v: Float) {
        value = v.coerceIn(0f, 100f)
        val tick = value.toInt() / 10
        if (tick != lastTick) {
            lastTick = tick
            haptic(this)
        }
        onValueChange?.invoke(value)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        setMeasuredDimension(
            resolveSize((200f * d).toInt(), widthMeasureSpec),
            resolveSize((74f * d).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val d = resources.displayMetrics.density
        val pad = 8f * d
        val trackH = 6f * d
        val thumbR = 7f * d
        // 轨道基准下移: 上方为值气泡预留完整空间 (气泡 20 + 间隙 5 + 拇指 7 + 边距)
        val cy = 33f * d
        val ratio = value / 100f
        val thumbX = pad + (w - pad * 2f) * ratio
        // ── 外围背景 (MD3 tonal chip): 圆角更小、横向比轨道宽 (两边流出一点) ──
        chipPaint.color = withAlpha(Md3.primaryContainer, 0.55f)
        canvas.drawRoundRect(RectF(0f, cy - 16f * d, w, cy + 16f * d),
                             8f * d, 8f * d, chipPaint)
        trackPaint.color = Md3.surfaceVariant
        trackRect.set(pad, cy - trackH / 2f, w - pad, cy + trackH / 2f)
        canvas.drawRoundRect(trackRect, trackH / 2f, trackH / 2f, trackPaint)
        fillPaint.color = Md3.primary
        trackRect.set(pad, cy - trackH / 2f, thumbX, cy + trackH / 2f)
        if (thumbX > pad) canvas.drawRoundRect(trackRect, trackH / 2f, trackH / 2f, fillPaint)
        if (dragging) {
            thumbPaint.color = 0x33415C8E.toInt()
            canvas.drawCircle(thumbX, cy, thumbR + 3.5f * d, thumbPaint)
        }
        thumbPaint.color = Md3.primary
        canvas.drawCircle(thumbX, cy, thumbR, thumbPaint)
        thumbPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(thumbX, cy, thumbR - 2f * d, thumbPaint)
        if (bubbleA > 0.01f) {
            // ── MD3 值气泡 (重设计): 胶囊 + 柔和投影 + 小三角, 随拖动淡入淡出 ──
            val sc = 0.88f + 0.12f * bubbleA
            val rise = (1f - bubbleA) * 6f * d
            textPaint.textSize = 13f * d
            val txt = valueLabel?.invoke(value) ?: value.toInt().toString()
            val tw = textPaint.measureText(txt)
            val bw = Math.max(34f * d, tw + 20f * d)
            val bh = 24f * d
            val bx0 = thumbX - bw / 2f
            val bx = bx0.coerceIn(2f * d, w - bw - 2f * d)
            val by = cy - thumbR - bh - 1f * d - rise
            // 柔和投影 (两圈渐隐)
            bubblePaint.color = 0x33000000.toInt()
            canvas.drawRoundRect(RectF(bx + 1.5f * d, by + 2f * d, bx + bw + 1.5f * d, by + bh + 2f * d),
                                 8f * d, 8f * d, bubblePaint)
            bubblePaint.color = 0x1A000000.toInt()
            canvas.drawRoundRect(RectF(bx + 0.5f * d, by + 3.5f * d, bx + bw + 0.5f * d, by + bh + 3.5f * d),
                                 8f * d, 8f * d, bubblePaint)
            // 主体胶囊
            canvas.save()
            canvas.scale(sc, sc, thumbX, by + bh * 0.6f)
            bubblePaint.color = 0xFF313033.toInt()   // M3 inverseSurface
            canvas.drawRoundRect(RectF(bx, by, bx + bw, by + bh), 9f * d, 9f * d, bubblePaint)
            canvas.restore()
            // 小三角 (朝拇指)
            val tri = android.graphics.Path().apply {
                moveTo(thumbX - 5f * d, by + bh)
                lineTo(thumbX + 5f * d, by + bh)
                lineTo(thumbX, by + bh + 5f * d)
                close()
            }
            bubblePaint.color = 0xFF313033.toInt()
            canvas.drawPath(tri, bubblePaint)
            // 数值 (白色粗体)
            textPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawText(txt, thumbX, by + bh / 2f + 4.5f * d, textPaint)
        }
    }
}



class Md3Collapse(context: Context, title: String) : LinearLayout(context) {

    var expanded = false
        private set

    private val chevron: ChevronView
    private val contentFrame: FrameLayout
    private var contentH = 0

    init {
        orientation = VERTICAL
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Md3.dp(context, 2f), Md3.dp(context, 4f), Md3.dp(context, 2f), Md3.dp(context, 4f))
            isClickable = true
            background = Md3.ripple(Md3.rounded(0x00000000, 10f))
        }
        header.addView(Md3.text(context, title, 13f, Md3.onSurface, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        chevron = ChevronView(context)
        header.addView(chevron)
        header.setOnClickListener {
            toggle()
            haptic(header)
        }
        addView(header)
        contentFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
            clipChildren = true
        }
        addView(contentFrame)
    }

    fun setContent(view: View) {
        contentFrame.removeAllViews()
        contentFrame.addView(view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        contentFrame.measure(
            MeasureSpec.makeMeasureSpec(width.takeIf { it > 0 } ?: Md3.dp(context, 200f), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        contentH = contentFrame.measuredHeight
        (contentFrame.layoutParams as LinearLayout.LayoutParams).height = if (expanded) contentH else 0
    }

    fun toggle() {
        expanded = !expanded
        chevron.animate().rotation(if (expanded) 180f else 0f).setDuration(180)
            .setInterpolator(if (expanded) Md3Motion.decelEmphasized else Md3Motion.accelEmphasized).start()
        val from = (contentFrame.layoutParams as LinearLayout.LayoutParams).height
        val to = if (expanded) contentH else 0
        ValueAnimator.ofInt(from, to).apply {
            duration = if (expanded) 320 else 240
            interpolator = if (expanded) Md3Motion.decelEmphasized else Md3Motion.accelEmphasized
            addUpdateListener {
                (contentFrame.layoutParams as LinearLayout.LayoutParams).height = it.animatedValue as Int
                contentFrame.requestLayout()
            }
            start()
        }
    }
}

// ── 复选框 (小巧 30dp: 描边方框 + 主色填充 + 矢量对勾缩放动画 + 震动) ──
class Md3Checkbox(context: Context) : View(context) {

    var checked = false
        private set
    var onCheckedChange: ((Boolean) -> Unit)? = null

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFFFFFFFF.toInt()
    }
    private var progress = 0f
    private var anim: ValueAnimator? = null

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            checked = !checked
            haptic(this)
            anim?.cancel()
            anim = ValueAnimator.ofFloat(progress, if (checked) 1f else 0f).apply {
                duration = 160
                interpolator = Md3Motion.standard
                addUpdateListener { progress = it.animatedValue as Float; invalidate() }
                start()
            }
            onCheckedChange?.invoke(checked)
        }
    }

    fun setChecked(value: Boolean) {
        checked = value
        progress = if (value) 1f else 0f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        setMeasuredDimension(resolveSize((30f * d).toInt(), widthMeasureSpec), resolveSize((30f * d).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val box = 16f * d
        val cx = width / 2f
        val cy = height / 2f
        val rect = RectF(cx - box / 2f, cy - box / 2f, cx + box / 2f, cy + box / 2f)
        val radius = 2f * d
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeWidth = 1.8f * d
        boxPaint.color = lerpColor(Md3.onSurfaceVariant, Md3.primary, progress)
        canvas.drawRoundRect(rect, radius, radius, boxPaint)
        boxPaint.style = Paint.Style.FILL
        boxPaint.color = lerpColor(0x00000000.toInt(), Md3.primary, progress)
        canvas.drawRoundRect(rect, radius, radius, boxPaint)
        // 矢量对勾
        checkPaint.strokeWidth = 1.8f * d
        checkPaint.alpha = (progress * 255).toInt()
        val scale = 0.5f + 0.5f * progress
        canvas.save()
        canvas.scale(scale, scale, cx, cy)
        canvas.drawLine(cx - 4.2f * d, cy, cx - 1.2f * d, cy + 3f * d, checkPaint)
        canvas.drawLine(cx - 1.2f * d, cy + 3f * d, cx + 4.4f * d, cy - 3.2f * d, checkPaint)
        canvas.restore()
    }
}

// ── 输入框 (MD3 Outlined: 圆角描边 + 聚焦变主色动画) ──
class Md3EditField(context: Context, hint: String) : LinearLayout(context) {

    val edit: EditText
    private val bg: GradientDrawable
    private var colorAnim: ValueAnimator? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        bg = GradientDrawable().apply {
            cornerRadius = Md3.dp(context, 8f).toFloat()
            setStroke(Md3.dp(context, 1.5f), Md3.outline)
            setColor(0x0AFFFFFF.toInt())
        }
        background = bg
        edit = EditText(context).apply {
            this.hint = hint
            setHintTextColor(Md3.onSurfaceVariant)
            setTextColor(Md3.onSurface)
            textSize = 13f
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            background = null
            setPadding(Md3.dp(context, 10f), Md3.dp(context, 7f), Md3.dp(context, 10f), Md3.dp(context, 7f))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        addView(edit)
        edit.setOnFocusChangeListener { _, has ->
            colorAnim?.cancel()
            val fromStroke = if (has) Md3.outline else Md3.primary
            val toStroke = if (has) Md3.primary else Md3.outline
            colorAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 180
                interpolator = Md3Motion.standard
                addUpdateListener {
                    val t = it.animatedValue as Float
                    bg.setStroke(
                        Md3.dp(context, if (has) 1.5f + t * 0.5f else 2f - t * 0.5f),
                        lerpColor(fromStroke, toStroke, t))
                }
                start()
            }
        }
    }

    val text: String get() = edit.text.toString()
}

// ── 多选按钮 (MD3 FilterChip 流式布局, 可多选 + 震动) ──
class Md3Chips(context: Context) : FlowLayout(context) {

    private val selected = mutableListOf<String>()

    fun addChip(text: String): TextView {
        val chip = TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(Md3.onSurfaceVariant)
            gravity = Gravity.CENTER
            setPadding(Md3.dp(context, 10f), Md3.dp(context, 5f), Md3.dp(context, 10f), Md3.dp(context, 5f))
            background = GradientDrawable().apply {
                cornerRadius = Md3.dp(context, 8f).toFloat()
                setStroke(Md3.dp(context, 1f), Md3.outline)
                setColor(0x00000000)
            }
            isClickable = true
            setOnClickListener {
                haptic(this)
                if (selected.contains(text)) {
                    selected.remove(text)
                    setTextColor(Md3.onSurfaceVariant)
                    background = GradientDrawable().apply {
                        cornerRadius = Md3.dp(this@Md3Chips.context, 8f).toFloat()
                        setStroke(Md3.dp(this@Md3Chips.context, 1f), Md3.outline)
                    }
                } else {
                    selected.add(text)
                    setTextColor(Md3.onPrimaryContainer)
                    background = GradientDrawable().apply {
                        cornerRadius = Md3.dp(this@Md3Chips.context, 8f).toFloat()
                        setStroke(Md3.dp(this@Md3Chips.context, 1f), Md3.primary)
                        setColor(Md3.primaryContainer)
                    }
                }
            }
        }
        addView(chip, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return chip
    }

    fun selected(): List<String> = selected.toList()
}

// 简易流式布局 (自动换行)
open class FlowLayout(context: Context) : ViewGroup(context) {

    class LayoutParams(w: Int, h: Int) : ViewGroup.LayoutParams(w, h) {
        var x = 0
        var y = 0
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        var x = 0
        var y = 0
        var rowH = 0
        var maxW = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            measureChild(c, widthMeasureSpec, heightMeasureSpec)
            val cw = c.measuredWidth
            val ch = c.measuredHeight
            if (x + cw > w && x > 0) {
                x = 0
                y += rowH
                rowH = 0
            }
            (c.layoutParams as? LayoutParams)?.let { it.x = x; it.y = y }
            x += cw
            rowH = Math.max(rowH, ch)
            maxW = Math.max(maxW, x)
        }
        setMeasuredDimension(w, resolveSize(y + rowH, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val lp = c.layoutParams as? LayoutParams ?: continue
            c.layout(lp.x, lp.y, lp.x + c.measuredWidth, lp.y + c.measuredHeight)
        }
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
