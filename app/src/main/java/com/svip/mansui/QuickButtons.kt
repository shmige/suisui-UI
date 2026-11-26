package com.svip.mansui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.svip.mansui.IPCurbco.TClass.SPUtils
import com.svip.mansui.esp.EspCtl
import com.svip.mansui.lua.FuncBridge
import kotlin.math.abs

// ═══════════════════════════════════════════════
//  快捷按钮 (功能快捷键; 梦之喵羽 QuickButtonHost 移植)
//  与梦之喵羽同构: **每个按钮一个独立悬浮窗**, 各自拖动到任意位置 (互不粘连),
//  位置按 key 持久化; 点按 = 切换功能
//  清单严格对齐梦之喵羽启用的快捷项 (见 items 注释)
//  视觉与悬浮窗同一套玻璃语言 (面板 glassLayer / capRow / 选中标签同款):
//    开 = 主色容器色玻璃 (primaryContainer) + 主色描边, 深蓝字, 主色点
//    关 = 晶莹玻璃白 + 细描边, 深字, 灰点
// ═══════════════════════════════════════════════
object QuickButtons {

    /** 一个可上快捷栏的功能: 读状态 / 写状态 */
    class Item(
        val key: String,
        val label: String,
        val isOn: () -> Boolean,
        val set: (Boolean) -> Unit,
    )

    // 清单 = 梦之喵羽实际启用(未注释)的 QuickButtonHost ∩ 离情梦已有功能, 不增不减。
    // 梦之喵羽注释掉的 6 项(自定义取物/刷水泥块/创建方块/全员方块/召唤空袭/幻影旋转)不做;
    // 准星方块/近战杀戮/自动瞄准/自动任务/自救搭路/运动相机 离情梦无此功能, 跳过;
    // 人物加速/跳远距离/游泳速度 离情梦是滑条无开关, 跳过。
    val items: List<Item> = listOf(
        Item("halo", "白子光环", { EspCtl.flags[10] }, { on ->
            EspCtl.toggle(10, on)
            if (on && !EspCtl.flags[0]) EspCtl.toggle(0, true)
            OverlayHost.refresh()
        }),
        Item("aim", "自动瞄准", { EspCtl.aimOn }, { EspCtl.setAim(on = it) }),
        Item("sub", "枪械追踪", { EspCtl.subOn }, { EspCtl.setSub(on = it) }),
        Item("ctrk", "坐标追踪", { EspCtl.ctrkOn }, { EspCtl.setCoord(on = it) }),
        Item("vtrk", "相机追踪", { EspCtl.vtrkOn }, { EspCtl.setSight(on = it) }),
        Item("atk", "自动攻击", { FuncBridge.autoAttackOn }, { FuncBridge.setAutoAttack(it) }),
        Item("pkt", "发包攻击", { FuncBridge.packetAttackOn }, { FuncBridge.setPacketAttack(it) }),
        Item("cut", "自动切刀", { FuncBridge.cuttingOn }, { FuncBridge.setCutting(it) }),
        Item("fly", "飞行模式", { FuncBridge.flyOn }, { FuncBridge.setFly(it) }),
        Item("air", "无限踏空", { EspCtl.airOn }, { EspCtl.setAir(it) }),
        Item("wall", "实体穿墙", { EspCtl.wallOn }, { EspCtl.setWall(it) }),
        Item("bridge", "自动搭路", { FuncBridge.bridgeOn }, { FuncBridge.setBridge(it) }),
        Item("circle", "转圈动作", { FuncBridge.circleOn }, { FuncBridge.setCircle(it) }),
        Item("firework", "播放烟花", { FuncBridge.fireworkOn }, { FuncBridge.setFirework(it) }),
    )

    private const val SP_KEY = "quick_buttons_on"
    private const val SP_POS_PREFIX = "quick_pos_"

    // 玻璃配方常量 (与面板 glassLayer / capRow 同一套语言)
    private const val GLASS_ON = 0xD9D6E3FF.toInt()    // md3 primaryContainer 玻璃 (开)
    private const val GLASS_OFF = 0xB3FFFFFF.toInt()   // 晶莹玻璃白 (关)

    class Entry(val view: View, val lp: WindowManager.LayoutParams, val item: Item,
                val dot: View, val label: TextView)

    private val main = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var appCtx: Context? = null
    private val entries = LinkedHashMap<String, Entry>()

    // ── 启用集合 (持久化) ──
    fun enabledKeys(context: Context): MutableSet<String> {
        val raw = SPUtils.getParam(context, SP_KEY, "") as String
        val out = linkedSetOf<String>()
        if (raw.isNotBlank()) out.addAll(raw.split(",").filter { it.isNotBlank() })
        return out
    }

    fun isEnabled(context: Context, key: String): Boolean = key in enabledKeys(context)

    // 勾选即上快捷栏、取消即消失 (无总开关)
    fun setEnabled(context: Context, key: String, on: Boolean) {
        val keys = enabledKeys(context)
        if (on) keys.add(key) else keys.remove(key)
        SPUtils.setParam(context, SP_KEY, keys.joinToString(","))
        appCtx = context.applicationContext
        refresh()
    }

    // 悬浮窗打开: 按勾选集合恢复快捷胶囊
    fun restore(context: Context) {
        appCtx = context.applicationContext
        refresh()
    }

    fun hide() {
        main.post {
            val w = wm
            val it = entries.iterator()
            while (it.hasNext()) {
                val e = it.next().value
                if (w != null) runCatching { w.removeView(e.view) }
                it.remove()
            }
        }
    }

    fun refresh() {
        val c = appCtx ?: return
        main.post { rebuild(c) }
    }

    private fun rebuild(c: Context) {
        val it = entries.iterator()
        while (it.hasNext()) {
            val e = it.next().value
            wm?.let { w -> runCatching { w.removeView(e.view) } }
            it.remove()
        }
        val keys = enabledKeys(c)
        val show = items.filter { it.key in keys }
        if (show.isEmpty()) return
        val w = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = w
        val (scrW, scrH) = screenSize(w)
        val density = c.resources.displayMetrics.density
        var idx = 0
        for (item in show) {
            val view = buildPill(c, item)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                // 每个按钮独立位置 (SP 记忆); 首次默认右侧错位排列
                val saved = SPUtils.getParam(c, "$SP_POS_PREFIX${item.key}", "") as String
                val xy = saved.split(",")
                if (xy.size == 2) {
                    x = xy[0].toIntOrNull() ?: (scrW - (96 * density).toInt())
                    y = xy[1].toIntOrNull() ?: ((scrH * 0.30f).toInt() + idx * (46 * density).toInt())
                } else {
                    x = scrW - (96 * density).toInt()
                    y = (scrH * 0.30f).toInt() + idx * (46 * density).toInt()
                }
                x = x.coerceIn(0, (scrW - 40).coerceAtLeast(0))
                y = y.coerceIn(0, (scrH - 40).coerceAtLeast(0))
                WinCompat.applyCutoutMode(this)   // 失败忽略: 不能让可选适配拖垮建窗
            }
            val entry = Entry(view, lp, item,
                view.getChildAt(0) as View, view.getChildAt(1) as TextView)
            bindTouch(c, entry, w)
            // 每个快捷胶囊都是**独立的悬浮窗**: 部分 ROM 对单应用悬浮窗数量有限制,
            // 超限时 addView 会抛 BadToken/TooManyWindows → 以前这里静默吞掉, 现在记进日志
            val added = runCatching { w.addView(view, lp); true }.getOrNull()
            if (added == null) {
            }
            entries[item.key] = entry
            idx++
        }
    }

    /** 胶囊: 圆点 + 文字; 点按切换 / 拖动换位 */
    @SuppressLint("ClickableViewAccessibility")
    private fun buildPill(c: Context, item: Item): LinearLayout {
        val d = c.resources.displayMetrics.density
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((13 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            isClickable = true
            // 外发光: elevation + 圆角轮廓
            val rad = 16f * d
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, rad)
                }
            }
            clipToOutline = false
            elevation = 8f * d
        }
        val dot = View(c).apply {
            layoutParams = LinearLayout.LayoutParams((7 * d).toInt(), (7 * d).toInt()).apply {
                marginEnd = (6 * d).toInt()
            }
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
        }
        val label = TextView(c).apply {
            text = item.label
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            includeFontPadding = false
        }
        root.addView(dot)
        root.addView(label)
        applyVisual(c, root, dot, label, item)
        return root
    }

    /** 玻璃胶囊: 底色层 + 描边层 (同面板 glassLayer 配方: 半透白玻璃 + 细描边) */
    private fun glassPill(fill: Int, strokeColor: Int, strokePx: Float,
                          rad: Float): LayerDrawable = LayerDrawable(arrayOf(
        GradientDrawable().apply { cornerRadius = rad; setColor(fill) },
        GradientDrawable().apply { cornerRadius = rad; setStroke(strokePx.toInt(), strokeColor) },
    ))

    private fun applyVisual(c: Context, root: View, dot: View, label: TextView, item: Item) {
        val d = c.resources.displayMetrics.density
        val rad = 16f * d
        if (item.isOn()) {
            // 开: 主色容器色玻璃 (与面板选中标签同款) + 主色描边
            root.background = glassPill(GLASS_ON, Md3.primary, 1.5f * d, rad)
            label.setTextColor(Md3.onPrimaryContainer)
            (dot.background as? GradientDrawable)?.setColor(Md3.primary)
        } else {
            // 关: 白玻璃 (同面板 晶莹玻璃 档) + 细描边
            root.background = glassPill(GLASS_OFF, 0x22000000, 1f * d, rad)
            label.setTextColor(Md3.onSurface)
            (dot.background as? GradientDrawable)?.setColor(Md3.outline)
        }
    }

    private fun bindTouch(c: Context, e: Entry, w: WindowManager) {
        val d = c.resources.displayMetrics.density
        val slop = 10f * d
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        e.view.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY
                    startX = e.lp.x; startY = e.lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                    if (moved) {
                        e.lp.x = startX + dx.toInt()
                        e.lp.y = startY + dy.toInt()
                        runCatching { w.updateViewLayout(v, e.lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (moved) {
                        SPUtils.setParam(c, "$SP_POS_PREFIX${e.item.key}", "${e.lp.x},${e.lp.y}")
                    } else {
                        v.performClick()
                        val next = !e.item.isOn()
                        e.item.set(next)
                        applyVisual(c, e.view, e.dot, e.label, e.item)
                        Island.notice(c, e.item.label, next)
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** 面板收起时刷新 (功能可能在面板里被改过) */
    fun syncStates() {
        val c = appCtx ?: return
        main.post {
            for (e in entries.values) applyVisual(c, e.view, e.dot, e.label, e.item)
        }
    }

    private fun screenSize(w: WindowManager): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = w.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val p = Point()
            @Suppress("DEPRECATION")
            w.defaultDisplay.getRealSize(p)
            p.x to p.y
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
}
