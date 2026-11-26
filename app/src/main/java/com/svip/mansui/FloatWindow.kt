package com.svip.mansui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.svip.mansui.IPCurbco.TClass.FloatTool
import com.svip.mansui.IPCurbco.TClass.SPUtils
import com.svip.mansui.esp.EspCtl
import com.svip.mansui.esp.EspGLView
import com.svip.mansui.lua.FuncBridge
import java.io.File

// ═══════════════════════════════════════════════
//  MD3 悬浮球 + 横屏多页悬浮窗 (KSU 风格)
//  左侧功能页侧边栏 (图片图标 + 方形气泡弹簧指示) | 右侧当前功能内容
//  球窗合一: 开面板隐藏球, 关面板球回归; 控件小巧 + 震动反馈
// ═══════════════════════════════════════════════

object FloatWindow {

    private const val OFFICIAL_PANEL_BG = "官方背景.jpg"   // 悬浮窗"官方背景"档的内置素材
    private const val BALL_SIZE = 52f    // dp
    private const val PANEL_W = 396f     // dp (收窄, 高度不变)
    private const val PANEL_H = 260f     // dp
    private const val SIDE_W = 80f       // dp 侧边栏宽
    private const val ITEM_H = 21f       // dp 侧边栏项高 (10 项全部放下)
    private const val BUBBLE_H = 19f     // dp 方形气泡高

    // 页签: 名称 + 素材库图标 (assets/图标/)
    private val PAGES = arrayOf(
        "首页" to "主页.png",
        "绘制" to "画笔.png",
        "瞄准" to "瞄准.png",
        "追踪" to "追踪.png",
        "战斗" to "战斗.png",
        "娱乐" to "娱乐.png",
        "物品" to "物品.png",
        "特效" to "调试.png",
        "属性" to "人物.png",
        "其他" to "其他.png"
    )

    // ── 单窗口架构 (直接照搬 梦之喵羽 FloatingRootView):
    //   悬浮球 + 面板都是"同一个 overlay 窗口"里的子视图; 窗口永不移动/不增删,
    //   开/关 = 窗口内 球隐藏→面板展开 / 面板收起→球浮现 → 无闪烁/无瞬移/无位移动画
    private var wm: WindowManager? = null
    private var rootView: FrameLayout? = null      // 唯一悬浮窗口 (WRAP_CONTENT)
    private var rootParams: WindowManager.LayoutParams? = null
    private var ballView: FrameLayout? = null      // 球子视图
    private var panelView: FrameLayout? = null     // 面板子视图 (1×1 隐藏 ↔ 展开)
    private var panelAnim: ValueAnimator? = null
    private var preloadedOnly = false              // 只预热还没揭示 (进入页期间), hide 时用于丢弃
    private var ballAnim = false                   // 球进出场动画进行中 (防连点)
    private var tabBubble: View? = null
    private var sideBar: LinearLayout? = null
    private var sideDragActive = false
    private var sideDragBase = 0f
    private var sideDragBaseDy = 0f
    private var pageBox: FrameLayout? = null
    private var pages = arrayListOf<View>()
    private var currentPage = 0
    private var panelOpen = false
    private var ctx: Context? = null

    // 齿轮图标: 首次解码后复用 (每张卡片都现场解码 assets 是开面板卡顿主因)
    private var gearIconCache: android.graphics.Bitmap? = null
    private fun gearIcon(): android.graphics.Bitmap? {
        if (gearIconCache == null) {
            try {
                gearIconCache = BitmapFactory.decodeStream(ctx?.assets?.open("图标/主页设置.png"))
            } catch (_: Exception) {}
        }
        return gearIconCache
    }

    // 物品/装扮数据库: 首次解析后缓存 (每次进页重解析上万行也会造成切页/开面板卡顿)
    private var itemDbCache: List<Pair<String, String>>? = null
    private fun itemDb(): List<Pair<String, String>> {
        if (itemDbCache == null) {
            try {
                val lines = try {
                    val f = File("/storage/emulated/0/离情梦/物品ID.txt")
                    if (f.exists()) f.readLines()
                    else ctx?.assets?.open("物品ID.txt")?.bufferedReader()?.readLines() ?: emptyList()
                } catch (_: Exception) { emptyList() }
                val out = ArrayList<Pair<String, String>>(lines.size)
                for (line in lines) {
                    val s = line.trim()
                    if (s.isEmpty()) continue
                    val sp = s.indexOf(' ')
                    if (sp <= 0) continue
                    out.add(s.substring(0, sp) to s.substring(sp + 1).trim())
                }
                itemDbCache = out
            } catch (_: Exception) {
                itemDbCache = emptyList()
            }
        }
        return itemDbCache ?: emptyList()
    }
    // 音效库: 离情梦/音效ID.txt (cat|name|id|path) 优先, 否则 assets 内置 152 条
    private var soundDbCache: List<Triple<String, String, String>>? = null
    private fun soundDb(): List<Triple<String, String, String>> {
        if (soundDbCache == null) {
            try {
                val lines = try {
                    val f = File("/storage/emulated/0/离情梦/音效ID.txt")
                    if (f.exists()) f.readLines()
                    else ctx?.assets?.open("音效ID.txt")?.bufferedReader()?.readLines() ?: emptyList()
                } catch (_: Exception) { emptyList() }
                val out = ArrayList<Triple<String, String, String>>(lines.size)
                for (line in lines) {
                    val s = line.trim()
                    if (s.isEmpty()) continue
                    val p = s.split('|')
                    if (p.size < 4) continue
                    out.add(Triple(p[0].trim(), p[1].trim(), p[3].trim()))
                }
                soundDbCache = out
            } catch (_: Exception) {
                soundDbCache = emptyList()
            }
        }
        return soundDbCache ?: emptyList()
    }

    private var outfitDbCache: List<Pair<String, String>>? = null
    private fun outfitDb(): List<Pair<String, String>> {
        if (outfitDbCache == null) {
            try {
                val lines = try {
                    val f = File("/storage/emulated/0/离情梦/uuxx.lua")
                    if (f.exists()) f.readLines()
                    else ctx?.assets?.open("uuxx.lua")?.bufferedReader()?.readLines() ?: emptyList()
                } catch (_: Exception) { emptyList() }
                val out = ArrayList<Pair<String, String>>(lines.size)
                for (line in lines) {
                    val s = line.trim()
                    if (s.isEmpty()) continue
                    val sp = s.indexOf(',')
                    if (sp <= 0) continue
                    val id = s.substring(0, sp).trim()
                    val name = s.substring(sp + 1).trim().removePrefix("\"").removeSuffix("\"").trim()
                    if (id.isNotEmpty() && name.isNotEmpty()) out.add(id to name)
                }
                outfitDbCache = out
            } catch (_: Exception) {
                outfitDbCache = emptyList()
            }
        }
        return outfitDbCache ?: emptyList()
    }
    private var lastToggleMs = 0L
    private var clipCx = 0f       // 面板圆形裁剪圆心 (悬浮球中心)
    private var clipCy = 0f
    private var clipR = 0f        // 当前裁剪半径
    private var transGen = 0      // 过渡代际: 旧动画取消后不得执行收尾
    // 同一位开关跨页实例 (如 过滤队友: 绘制页/瞄准页 双向同步)
    private val espSyncRows = arrayListOf<Pair<Int, Md3Switch>>()

    /**
     * 预热 (2026-10-01): 提前把悬浮窗整套视图树建好 + 布局好并挂上窗口, 但**球和面板都隐藏**、
     * 窗口带 FLAG_NOT_TOUCHABLE (不挡下面进入页的点击)。
     * 这样随后 show() 只剩"显隐 + 展开动画", 点「进入首页」/启动悬浮球都不会再卡一下。
     */
    fun preload(context: Context): Boolean {
        if (rootView != null) {
            return true
        }
        if (!Settings.canDrawOverlays(context)) {
            return false
        }
        val app = context.applicationContext
        ctx = app
        wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addFloatingRoot(prepareForPanel = true, hiddenBall = true)
        // ⚠️ 预热期不做任何"启动悬浮窗"的可见副作用 (快捷胶囊/心跳/渲染层都留到 revealWindow)
        preloadedOnly = true
        return true
    }

    /** 进入页被 ✕/标题关掉 (没进首页): 丢弃预热窗口, 不留隐形窗 */
    fun discardPreload() {
        if (preloadedOnly) {
            hide()
            AppState.floatWindowShown = false
        }
        preloadedOnly = false
    }

    /**
     * 显示悬浮窗。
     * @param expand true = 直接展开面板 (进入页「进入首页」用): 窗口在"面板居中"位
     *   (与进入页同尺寸同位置), 由球锚点圆形展开接住
     */
    fun show(context: Context, expand: Boolean = false): Boolean {
        if (rootView != null) {
            // 已预热: 这里只做显隐 + 展开动画 (零构建/零重排 → 不卡)
            preloadedOnly = false
            revealWindow(expand)
            return true
        }
        // ⚠️ 不再用 canDrawOverlays() 一票否决: 部分 ROM(MIUI/ColorOS 等) 这个接口不准
        //    —— 刚授权完仍返回 false, 个别的永远 false。而用户报的"启动悬浮窗失败, 请重试"
        //    正是走到下面这个 return false 分支造成的。
        //    现在改成: 记一笔权限状态, 继续尝试真正建窗; 真没权限时 addView 会抛异常并被记录的。
        val overlaysOk = try { Settings.canDrawOverlays(context) } catch (t: Throwable) { false }
        if (!overlaysOk) {
        }
        val app = context.applicationContext
        ctx = app
        wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // expand 时也走"先隐藏建好再展开", 保证点按瞬间没有构建开销
        val built = addFloatingRoot(prepareForPanel = expand, hiddenBall = expand)
        if (!built) {
            return false
        }
        preloadedOnly = false
        revealWindow(expand)
        return true
    }

    /** 把预热/新建好的窗口揭示出来 (expand=true 时从球锚点圆形展开面板) */
    private fun revealWindow(expand: Boolean) {
        val root = rootView ?: return
        val lp = rootParams
        val b = ballView
        val c = ctx
        // 去掉预热用的"触摸穿透", 恢复可点击
        if (lp != null) {
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            runCatching { wm?.updateViewLayout(root, lp) }.getOrNull()
            root.post {
                runCatching {
                    root.requestLayout()
                    wm?.updateViewLayout(root, lp)
                }
            }
        }
        // "启动悬浮窗"的副作用统一放这里 (预热期间绝不能发生, 否则进入页还在时快捷胶囊就冒出来了)
        if (c != null) {
            runCatching { QuickButtons.restore(c) }.getOrNull()
            runCatching { startHeartbeat(c) }.getOrNull()
        }
        runCatching { OverlayHost.refresh() }.getOrNull()
        if (expand) {
            // 进入页交接: 面板"已经打开准备完毕" —— 不做从球展开的动画, 也不闪球
            b?.let {
                it.animate().cancel()
                it.visibility = View.GONE
                it.alpha = 1f
                it.scaleX = 1f
                it.scaleY = 1f
            }
            snapOpenPanel(withFade = true)
        } else if (b != null && b.visibility != View.VISIBLE) {
            // 预热态 → 显示球: 过冲入场
            b.visibility = View.VISIBLE
            b.alpha = 1f
            b.scaleX = 0f
            b.scaleY = 0f
            b.animate().scaleX(1f).scaleY(1f).setDuration(280)
                .setInterpolator(OvershootInterpolator(1.4f)).start()
        }
    }

    /** 面板尺寸 (dp) — 进入页与悬浮窗保持完全一致 */
    fun panelSizeDp(): Pair<Float, Float> = PANEL_W to PANEL_H

    /**
     * 面板 / 进入页 **统一窗口矩形** (px): [宽, 高, x, y]。
     *
     * 396×260dp 在 480dpi 上是 1188×780px —— 1200px 屏宽只剩 6px 边距(几乎贴边, 观感就是"超出屏幕"),
     * 所以统一夹进 **屏宽 92% / 屏高 70%** 再居中; 进入页与悬浮窗共用同一矩形 → 尺寸/落点完全一致。
     */
    fun panelRectPx(context: Context): IntArray {
        val dm = context.resources.displayMetrics
        var pw = Md3.dp(context, PANEL_W)
        var ph = Md3.dp(context, PANEL_H)
        val maxW = (dm.widthPixels * 0.92f).toInt()
        val maxH = (dm.heightPixels * 0.70f).toInt()
        // 只压"超出屏幕的那一边", 不做整体等比缩放:
        // 等比缩会连高度一起压掉, 侧边栏 10 项(10×21dp)会被裁掉最后一项。
        if (pw > maxW) pw = maxW
        if (ph > maxH) ph = maxH
        return intArrayOf(pw, ph, (dm.widthPixels - pw) / 2, (dm.heightPixels - ph) / 2)
    }

    private fun panelWpx(c: Context): Int = panelRectPx(c)[0]
    private fun panelHpx(c: Context): Int = panelRectPx(c)[1]

    /** 悬浮窗启动后心跳验证 (卡密失效/到期 → 关闭悬浮窗); 只在真正揭示时启动 */
    private fun startHeartbeat(c: Context) {
        com.svip.mansui.card.Heartbeat.start(c, { msg ->
            md3Toast(c, msg, long = true)
            hide()
            AppState.floatWindowShown = false
        })
    }

    fun hide() {
        wm ?: return
        // 注: 心跳不停 — 悬浮球关闭/隐藏后仍持续, 一旦启动永不关闭 (直到卡密失效自停)
        runCatching { QuickButtons.hide() }
        OverlayHost.destroyAll()   // 悬浮球关闭: 灵动岛/ESP 渲染层一并移除
        rootView?.let { runCatching { wm!!.removeView(it) }.getOrNull() }
        rootView = null
        rootParams = null
        ballView = null
        panelView = null
        panelOpen = false
        pages.clear()
        wm = null
        preloadedOnly = false
    }

    /** 悬浮球窗口是否已显示 (灵动岛等渲染层以此为前置条件) */
    fun isWindowShown(): Boolean = rootView != null

    // ── 构建单窗口: 面板(1×1隐藏) + 球, WRAP_CONTENT, 拖动窗口 = 拖动球 ──
    private fun addFloatingRoot(prepareForPanel: Boolean = false, hiddenBall: Boolean = false): Boolean {
        val c = ctx ?: run {  return false }
        val size = Md3.dp(c, BALL_SIZE)
        val pw = panelWpx(c)
        val ph = panelHpx(c)
        val sw = c.resources.displayMetrics.widthPixels
        val sh = c.resources.displayMetrics.heightPixels

        val root = FrameLayout(c).apply {
            isClickable = false
            isFocusable = false
        }
        // ⚠️ 逐步打点 (2026-10-07): 真机日志显示崩在"构建悬浮球"之后、addView 之前那段,
        //    但原来那一段没有任何日志 → 分不清是哪一小步。现在每一步都记, 一步一步定位。
        // 面板子视图: 先 1×1 隐藏占位, 展开时放大 (窗口自动 wrap 变大)
        val card = try {
            buildPanel()
        } catch (t: Throwable) {
             return false
        }
        panelView = card
        try {
            root.addView(card, FrameLayout.LayoutParams(1, 1))
        } catch (t: Throwable) {
             return false
        }
        card.visibility = View.INVISIBLE
        // 球子视图 (在面板上方)
        val b = try {
            buildBallView(size)
        } catch (t: Throwable) {
             return false
        }
        ballView = b
        try {
            root.addView(b, FrameLayout.LayoutParams(size, size))
        } catch (t: Throwable) {
             return false
        }

        // ══════════════════════════════════════════════════════════════════════════
        //  ★ 适配 NP 反射保护 (2026-10-10): 真机日志证明崩在这一步
        //     (NullPointerException: Method.invoke on null, 栈顶是 np.protect.*)。
        //  对策: 这一整段改成**最朴素的写法**, 不留任何"会被反射包装"的东西:
        //     · winType 内联            → 不再调私有方法 overlayType()
        //     · 边距直接乘 density       → 不再调被 NP 处理过的 Md3.dp()
        //     · 去掉 apply { }           → 不留 lambda / 不产生额外调用
        //     · 用 if 代替 coerceAtLeast → 不用扩展函数(会生成静态调用)
        //     · 每条语句之间打日志        → 万一还崩, 日志直接指出是哪一句
        // ══════════════════════════════════════════════════════════════════════════
        val dm = c.resources.displayMetrics
        val density = dm.density
        val winType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val sideMarginPx = (16f * density).toInt()
        val lp: WindowManager.LayoutParams
        try {
            lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                winType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            )
        } catch (t: Throwable) {
            return false
        }
        try {
            lp.gravity = Gravity.TOP or Gravity.START
            if (prepareForPanel) {
                // 首次即展开: 直接落到"面板居中"位 (与进入页同一矩形), 无缝接住
                lp.x = (sw - pw) / 2
                if (lp.x < 0) lp.x = 0
                lp.y = (sh - ph) / 2
                if (lp.y < 0) lp.y = 0
            } else {
                lp.x = sw - size - sideMarginPx
                lp.y = sh / 3
            }
            // 刘海屏适配走 WinCompat: 任何异常都忽略
            // (部分机型/加固后这句会抛 NPE, 以前放在同一个 try 里 → 直接把建窗判死)
            val cutoutOk = WinCompat.applyCutoutMode(lp)
        } catch (t: Throwable) {
            // 参数设置失败不再直接放弃: 用当前(默认)参数也试着把窗口挂上去,
            // 总比"悬浮窗完全出不来"好; 下面 addView 还有多类型重试与兜底
        }
        // ⚠️ 最容易闪退的一步: 部分机型无悬浮窗权限/权限被 ROM 拦截时 addView 抛
        //    BadTokenException / SecurityException / "permission denied for window type 2038"
        var addedOk = false
        val primaryType = lp.type
        // ── 第 1 次: 主窗口类型 ──
        try {
            val w = wm
            if (w == null) {
            } else {
                w.addView(root, lp)
                addedOk = true
            }
        } catch (t: Throwable) {
        }

        // ── 第 2 次: 换窗口类型重试 ──
        //  不少国产 ROM(尤其 Android 8/9 的 MIUI/EMUI/ColorOS) 对 TYPE_APPLICATION_OVERLAY(2038)
        //  卡得很死(还要额外的"后台弹出界面"权限), 但对老的 TYPE_PHONE(2002) 反而放行。
        if (!addedOk && wm != null) {
            val altType = if (primaryType == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                WindowManager.LayoutParams.TYPE_PHONE else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            lp.type = altType
            try {
                wm?.addView(root, lp)
                addedOk = true
            } catch (t2: Throwable) {
            }
        }

        // ── 第 3 次: ROM 额外限制 → 有 ROOT 就自动放行 AppOps 再重试 ──
        //  MIUI/EMUI/ColorOS 常见: Settings.canDrawOverlays()=true, 但 AppOps 的
        //  SYSTEM_ALERT_WINDOW 仍是 IGNORED(=1) (系统里"后台弹出界面/在其他应用上层显示"没开)。
        //  有 ROOT 时直接 appops 放行(和 root 模式进门时那步同一招), 然后按主类型再建一次。
        if (!addedOk && wm != null) {
            if (tryFixOverlayAppOps(c)) {
                lp.type = primaryType
                try {
                    wm?.addView(root, lp)
                    addedOk = true
                } catch (t3: Throwable) {
                }
            }
        }
        if (!addedOk) {
            rootView = null
            rootParams = null
            ballView = null
            panelView = null
            return false
        }
        rootView = root
        rootParams = lp
        bindBallDrag(b, lp)
        // 悬浮球已显示 → 灵动岛(若开启)/自瞄圈等渲染层随之生效
        // (预热态先不刷: 免得进入页还在时冒出全屏渲染层; 由 revealWindow 补刷)
        if (!hiddenBall) runCatching { OverlayHost.refresh() }.getOrNull()
        // 悬浮窗启动后心跳验证 (参照梦喵 bgSyncWorker: 卡密失效/到期 → 关闭悬浮窗)
        // 预热期不做: 真正"启动"发生在 revealWindow
        if (!hiddenBall) runCatching { startHeartbeat(c) }.getOrNull()
        // 入场
        if (hiddenBall) {
            // 预热态: 球先藏着; 面板直接铺到目标尺寸(INVISIBLE 也参与布局),
            // 让整套视图树的 measure/layout 现在就做完 —— 展开时只剩裁剪动画
            b.visibility = View.INVISIBLE
            b.scaleX = 1f
            b.scaleY = 1f
            card.layoutParams = FrameLayout.LayoutParams(pw, ph)
            card.visibility = View.INVISIBLE
            root.requestLayout()
            // 预热窗口不可触摸 (不挡进入页的按钮), revealWindow 时恢复
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { wm?.updateViewLayout(root, lp) }
        } else if (prepareForPanel) {
            // 直接展开: 不做球的过冲入场 (球马上被面板盖住, 弹一下反而突兀)
            b.scaleX = 1f
            b.scaleY = 1f
        } else {
            b.scaleX = 0f
            b.scaleY = 0f
            b.animate().scaleX(1f).scaleY(1f).setDuration(280)
                .setInterpolator(OvershootInterpolator(1.4f)).start()
        }
        return true
    }

    // 球视图 (重构: 只一个干净的实心圆 — 白→浅蓝白渐变, 无描边/光晕/阴影叠加,
    //  外圈像素全不透明 → 不会再泛黑边; 拖动/按压动画在外层手势里保持)
    private fun buildBallView(size: Int): FrameLayout {
        val c = ctx ?: return FrameLayout(ctx!!)
        val r = size / 2f
        val circle = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFE8F0FA.toInt())
        ).apply {
            cornerRadius = r
        }
        val mask = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(0xFFFFFFFF.toInt())
        }
        return FrameLayout(c).apply {
            background = RippleDrawable(
                ColorStateList.valueOf(Md3.rippleColor), circle, mask)
            elevation = 0f
            addView(ImageView(c).apply {
                val ballImg = floatBallImagePath()
                setImageDrawable(if (ballImg != null) loadPhotoFile(ballImg) else iconPhoto("满穗.png"))
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
                layoutParams = FrameLayout.LayoutParams(Md3.dp(c, 40f), Md3.dp(c, 40f), Gravity.CENTER)
            })
        }
    }

    // 球手势: 拖动 = 拖动整个窗口 (同梦喵); 点击 = 展开面板
    @SuppressLint("ClickableViewAccessibility")
    private fun bindBallDrag(b: FrameLayout, lp: WindowManager.LayoutParams) {
        val c = ctx ?: return
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        val slop = android.view.ViewConfiguration.get(c).scaledTouchSlop
        b.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (!dragging && (Math.abs(dx) > slop || Math.abs(dy) > slop)) dragging = true
                    if (dragging) {
                        lp.x = (startX + dx).toInt()
                        lp.y = (startY + dy).toInt()
                        wm?.updateViewLayout(rootView, lp)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                    if (dragging) {
                        // 吸附模式: 0=贴边; 默认独立
                        val snapMode = (SPUtils.getParam(c, "float_ball_mode", 1) as Int) == 0
                        if (snapMode) {
                            val sw = c.resources.displayMetrics.widthPixels
                            val size = Md3.dp(c, BALL_SIZE)
                            val targetX = if (lp.x + size / 2 < sw / 2) 0 else sw - size
                            ValueAnimator.ofInt(lp.x, targetX).apply {
                                duration = 220
                                interpolator = DecelerateInterpolator()
                                addUpdateListener { a ->
                                    lp.x = a.animatedValue as Int
                                    wm?.updateViewLayout(rootView, lp)
                                }
                            }.start()
                        }
                    } else {
                        val now = System.currentTimeMillis()
                        lastToggleMs = now
                        if (panelOpen) collapsePanel() else expandPanel()
                    }
                }
            }
            true
        }
    }

    // ── 展开: 球退场 → 面板在原窗口位置放大 (球的面板窗口一体, 起点就是球) ──
    // ── 展开: 以悬浮球(左上角锚点)为中心向右下"拉开" ──
    //  面板 pivot=(0,0) 从极小沿对角线放大, 球做"幻影模糊"淡出随拉开一起消失;
    //  窗口锚点(左上)不变 → 球位置全程不动
    // ── 展开/收回 (圆形收拢+高斯, 严格镜像; 高斯=面积越大越清/越小越糊渐变;

    //   打断=从当前半径按剩余路程缩短时长反向续接; 无需重排时立即起播, 跟手) ──

    private fun runTransition(toOpen: Boolean) {

        val c = ctx ?: return

        val card = panelView ?: return

        val b = ballView ?: return

        val root = rootView ?: return

        if (panelOpen == toOpen && panelAnim == null) return

        if (panelOpen == toOpen && panelAnim?.isRunning != true) return

        // 清掉可能还在跑的"进入页交接淡入", 免得面板残留半透明
        card.animate().cancel()
        card.alpha = 1f

        panelOpen = toOpen

        val gen = ++transGen

        panelAnim?.cancel()

        b.animate().cancel()

        ballAnim = true

//         android.util.Log.i("FloatWin", "runTransition open=" + toOpen + " gen=" + gen)

        val pw = panelWpx(c)

        val ph = panelHpx(c)

        val cardRadius = Md3.dp(c, 24f)

        val ballC = Md3.dp(c, BALL_SIZE) / 2f

        clipCx = ballC

        clipCy = ballC

        // 圆覆盖整卡(四个角最远点)所需半径 — 未覆盖完四角前保持纯圆裁剪,
        // 到终点才切圆角矩形: 消除中途四角区域跳变/黑边。
        // 注意: 必须用面板目标尺寸 pw×ph 计算 (展开瞬间卡片还是 1×1,
        // 若读 card.width 会算出 rMax≈球半径 → 动画前段空爬后段瞬铺)
        val rMax = (maxOf(
            Math.hypot(ballC.toDouble(), ballC.toDouble()),
            Math.hypot((pw - ballC).toDouble(), ballC.toDouble()),
            Math.hypot(ballC.toDouble(), (ph - ballC).toDouble()),
            Math.hypot((pw - ballC).toDouble(), (ph - ballC).toDouble())
        )).toFloat() + 8f

        // 动画期间去阴影: 巨大 outline 的投影会在窗口边缘形成黑晕
        card.elevation = 0f

        card.clipToOutline = true

        card.outlineProvider = object : android.view.ViewOutlineProvider() {

            override fun getOutline(view: View, outline: android.graphics.Outline) {

                val vw = view.width

                val vh = view.height

                if (vw <= 0 || vh <= 0 || clipR >= rMax) {

                    outline.setRoundRect(0, 0, vw, vh, cardRadius.toFloat())

                } else {

                    outline.setOval((clipCx - clipR).toInt(), (clipCy - clipR).toInt(),

                                   (clipCx + clipR).toInt(), (clipCy + clipR).toInt())

                }

            }

        }

        var resized = false

        if (toOpen) {

            card.visibility = View.VISIBLE

            val lp = card.layoutParams as FrameLayout.LayoutParams

            if (lp.width < 2) {

                resized = true

                card.layoutParams = FrameLayout.LayoutParams(pw, ph)

                root.requestLayout()

            }

            b.visibility = View.VISIBLE

            b.alpha = 1f

            b.scaleX = 1f

            b.scaleY = 1f

        }


        fun launch(gen: Int) {

            val rMin = ballC

            val startR = if (clipR >= rMin && clipR <= rMax) clipR

                         else if (toOpen) rMin else rMax

            val endR = if (toOpen) rMax else rMin

            // 打断: 按剩余路程比例定长 (保底 90ms, 最满 460ms) — 跟手

            val distF = (Math.abs(endR - startR) / (rMax - rMin)).coerceIn(0.15f, 1f)

            val dur = (90L + (370f * distF).toLong()).coerceAtMost(460L)

            var lastBp = -1f

            clipR = startR

            card.invalidateOutline()

            // 展开: 球在面板开始扩散的**同一瞬间**隐藏 ——
            // 面板圆形裁剪的起始半径就是球的半径(圆心也同一个), 所以隐藏是视觉无缝的;
            // 原来用 90ms 淡出 → 面板已经扩散到比球大很多了球还挂在上层, 看起来就是"球没被盖住"。
            if (toOpen) {
                b.animate().cancel()
                b.visibility = View.GONE
                b.alpha = 1f
            }

            panelAnim = ValueAnimator.ofFloat(0f, 1f).apply {

                duration = dur

                interpolator = LinearInterpolator()

                addUpdateListener { av ->

                    val f = av.animatedValue as Float

                    clipR = startR + (endR - startR) * f

                    card.invalidateOutline()

                    card.invalidate()

                    // 收回(镜像): 面板缩到球尺寸时提前把球复现 (球体盖住剩余收拢内容);
                    // 展开方向的球隐藏已挪到 launch() 起播前, 保证"面板一变足球就没了"
                    if (!toOpen && clipR <= ballC) {
                        if (b.visibility != View.VISIBLE) {
                            b.visibility = View.VISIBLE
                            if (b.alpha < 1f) b.animate().alpha(1f).setDuration(90).start()
                            else b.alpha = 1f
                        } else if (b.alpha == 0f) {
                            b.animate().alpha(1f).setDuration(90).start()
                        }
                    }

                }

                addListener(object : android.animation.AnimatorListenerAdapter() {

                    override fun onAnimationEnd(animation: android.animation.Animator) {

                        if (gen != transGen) return

                        card.clipToOutline = false

                        card.outlineProvider = null

                        card.invalidateOutline()

                        card.elevation = Md3.dp(c, 12f).toFloat()   // 恢复静态阴影


                        if (toOpen) {

                            b.visibility = View.GONE

                            b.alpha = 1f

                            ballAnim = false

                            setPage(currentPage, false)

                        } else {

                            card.visibility = View.INVISIBLE

                            card.layoutParams = FrameLayout.LayoutParams(1, 1)

                            root.requestLayout()

                            b.visibility = View.VISIBLE

                            b.alpha = 1f

                            ballAnim = false

                        }

//                         android.util.Log.i("FloatWin", "runTransition open=" + toOpen + " end")

                    }

                })

            }.apply { start() }.also { panelAnim = it }

        }

        if (!toOpen) {

            launch(gen)

        } else if (!resized) {

            launch(gen)   // 尺寸已在: 立即起播, 最跟手

        } else {

            card.post { if (panelOpen && ballAnim && gen == transGen) launch(gen) }

        }

    }


    private fun expandPanel() = runTransition(true)

    /**
     * 直接跳到"面板已展开"的终态 (无展开动画)。
     * 用于进入页「进入首页」的交接: 进入页本来就是面板尺寸, 这里应当"已经开好",
     * 而不是再从球锚点放一遍圆形展开动画。
     * @param withFade true = 面板淡入 (与进入页淡出组成交叉溶解, 交接不生硬)
     */
    private fun snapOpenPanel(withFade: Boolean = false) {
        val c = ctx ?: return
        val card = panelView ?: return
        val b = ballView ?: return
        val root = rootView ?: return
        panelAnim?.cancel()
        panelAnim = null
        transGen++
        panelOpen = true
        ballAnim = false
        // 直接落到展开终态 (与 runTransition 的 onAnimationEnd 完全一致)
        clipR = Float.MAX_VALUE
        card.clipToOutline = false
        card.outlineProvider = null
        card.invalidateOutline()
        card.elevation = Md3.dp(c, 12f).toFloat()
        val lp = card.layoutParams as? FrameLayout.LayoutParams
        if (lp == null || lp.width < 2 || lp.height < 2) {
            card.layoutParams = FrameLayout.LayoutParams(panelWpx(c), panelHpx(c))
        }
        card.visibility = View.VISIBLE
        root.requestLayout()
        b.animate().cancel()
        b.visibility = View.GONE
        b.alpha = 1f
        b.scaleX = 1f
        b.scaleY = 1f
        // 先把内容页建好再开始淡入, 免得淡入过程中出现"半张面板"
        setPage(currentPage, false)
        if (withFade) {
            // 与进入页的淡出同时进行 → 同矩形交叉溶解
            card.alpha = 0f
            card.animate().alpha(1f).setDuration(240)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        } else {
            card.animate().cancel()
            card.alpha = 1f
        }
    }

    private fun collapsePanel() {
        runTransition(false)
        // 面板里可能改过功能开关: 收起时刷新快捷胶囊高亮
        runCatching { QuickButtons.syncStates() }
    }

    // ── ESP 渲染层 委托 OverlayHost (ESP 与 灵动岛 共用单悬浮层, 见 OverlayHost) ──
    private fun showEspOverlay() { OverlayHost.refresh() }

    private fun hideEspOverlay() { OverlayHost.refresh() }
    // ── 开关行胶囊 (独立风格: 16dp 圆角 6%黑玻璃卡 + 整行可点; 无复选框样式指示物)
    private fun capRow(c: Context, title: String, sw: Md3Switch,
                       onChecked: (Boolean) -> Unit): LinearLayout {
        val cap = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val rad = Md3.dp(c, 16f).toFloat()
            background = LayerDrawable(arrayOf(
                Md3.rounded(0x14000000.toInt(), rad),
                Md3.rounded(0x00000000, rad).apply { setStroke(Md3.dp(c, 1f), 0x2E000000) }
            ))
            setPadding(Md3.dp(c, 12f), Md3.dp(c, 3f), Md3.dp(c, 8f), Md3.dp(c, 3f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Md3.dp(c, 2f)
                bottomMargin = Md3.dp(c, 2f)
            }
            minimumHeight = Md3.dp(c, 34f)
            addView(Md3.text(c, title, 12f, Md3.onSurface).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(sw)
            isClickable = true
            // 整行可点 = 拨动开关 (点开关本身走开关自带点击, 不会二次触发)
            setOnClickListener {
                haptic(this)
                sw.setOn(!sw.isOn, true)
            }
        }
        sw.onCheckedChangeListener = { on ->
            haptic(sw)
            if (!EspCtl.tipMute) Island.notice(c, title, on)
            onChecked(on)
        }
        return cap
    }

    // ESP 开关行 (胶囊卡; 状态回写 EspCtl.flags + native isDraw;
    //  同一位的其它页面实例自动镜像, 不重复弹提示)
    private fun espSwitchRow(c: Context, title: String, flagIdx: Int): LinearLayout {
        val sw = Md3Switch(c).apply { setOn(EspCtl.flags[flagIdx], false) }
        espSyncRows.add(flagIdx to sw)
        return capRow(c, title, sw) { on ->
            EspCtl.toggle(flagIdx, on)
            if (flagIdx == 0) OverlayHost.refresh()
            // 同步其它页同一开关实例 (如过滤队友)
            try {
                EspCtl.tipMute = true
                for ((idx, other) in espSyncRows) {
                    if (idx == flagIdx && other !== sw) other.setOn(on, false)
                }
            } finally {
                EspCtl.tipMute = false
            }
        }
    }

    private fun smoothText(v: Float): String {
        val s = if (v >= 0.1f) "%.2f".format(v) else "%.3f".format(v)
        return s.trimEnd('0').trimEnd('.')
    }

    // 悬浮窗外观设置变更后: 原样重建 (球/面板立即采用新样式; 面板若展开会被收起)
    fun applyStyle() {
        val c = ctx ?: return
        val shown = rootView != null
        hide()
        if (shown) show(c)
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    /**
     * 悬浮窗权限的详细快照 —— 排查"启动悬浮窗失败"用。
     * `Settings.canDrawOverlays()` 说"有权限"但仍 addView 失败, 通常是国产 ROM 的**额外开关**
     * (MIUI「后台弹出界面」/「在其他应用上层显示」) 没开, 这时 AppOps 的 OP_SYSTEM_ALERT_WINDOW 会是 IGNORED。
     * 模式值: 0=允许 1=忽略 2=错误 3=默认
     */
    private fun appOpsSnapshot(c: Context): String {
        val overlay = try { Settings.canDrawOverlays(c) } catch (t: Throwable) { false }
        val mode = overlayAppOpsMode(c)
        return "canDrawOverlays=$overlay AppOps(SYSTEM_ALERT_WINDOW)=$mode"
    }

    /** AppOps 的 SYSTEM_ALERT_WINDOW 模式: 0=允许 1=忽略 2=错误 3=默认 -1=取不到 */
    private fun overlayAppOpsMode(c: Context): Int = try {
        val am = c.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
        am?.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
            android.os.Process.myUid(),
            c.packageName
        ) ?: -1
    } catch (t: Throwable) {
        -1
    }

    /**
     * ROM 额外限制的**自动处理**: 系统里"悬浮窗"开关打开了(`canDrawOverlays()=true`),
     * 但 AppOps 仍是 IGNORED → addView 被拒。有 ROOT 时直接 `appops set` 放行(与 root 模式进门同一招)。
     *
     * @return true = 已成功放行 (调用方可以重试建窗)
     */
    private fun tryFixOverlayAppOps(c: Context): Boolean {
        val before = overlayAppOpsMode(c)
        if (before == 0) return false                       // 本来就是 allowed, 不是这个原因
        val root = try { Util.getRootCache() == true } catch (t: Throwable) { false }
        if (!root) {
            return false
        }
        val ret = runCatching {
            FloatTool.RunShell("appops set --uid ${c.packageName} android:system_alert_window allow", true)
        }.getOrNull()
        val after = overlayAppOpsMode(c)
        return after == 0
    }

    /**
     * 建窗彻底失败时给用户看的话 —— 区分"没授权"和"ROM 额外开关没开", 直接告诉他去点哪里。
     */
    fun overlayFixHint(c: Context): String {
        val has = try { Settings.canDrawOverlays(c) } catch (t: Throwable) { false }
        val mode = overlayAppOpsMode(c)
        return when {
            !has -> "未授予悬浮窗权限：请到 设置→应用→离情梦→权限 里打开「显示悬浮窗」"
            mode == 1 || mode == 2 ->
                "系统拦截：请到 设置→应用→离情梦 打开「显示悬浮窗」和「后台弹出界面」" +
                        "（部分手机叫「在其他应用上层显示」），然后重开本应用"
            else -> "系统暂时拒绝创建悬浮窗，请稍后重试或重启手机"
        }
    }

    // 素材库图标 (assets/图标/*.png), tint 上色
    private fun icon(name: String, tint: Int): BitmapDrawable {
        val c = ctx!!
        val d = runCatching {
            BitmapDrawable(c.resources, BitmapFactory.decodeStream(c.assets.open("图标/$name")))
        }.getOrElse { BitmapDrawable() }
        d.mutate()
        d.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
        return d
    }

    // 照片类头像 (满穗.png: 保留原图白底不抠图, 仅采样缩小防内存, 圆形裁剪贴合玻璃球)
    private fun iconPhoto(name: String): android.graphics.drawable.Drawable {
        val c = ctx ?: return ColorDrawable(0)
        return runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 16 }
            val src = BitmapFactory.decodeStream(c.assets.open("图标/$name"), null, opts)
                ?: return@runCatching icon("主页.png", Md3.primary)
            BitmapDrawable(c.resources, src)
        }.getOrElse { icon("主页.png", Md3.primary) }
    }

    // ── 悬浮窗外观 (悬浮窗设置页写入) ──

    // 自定义悬浮球头像 (相册裁剪的 1:1 图); 无则用默认满穗
    private fun floatBallImagePath(): String? {
        val c = ctx ?: return null
        val p = SPUtils.getParam(c, "float_ball_img", "") as String
        return if (p.isNotBlank() && File(p).exists()) p else null
    }

    // 自定义悬浮窗背景图 (按悬浮窗比例裁剪); 无则 null
    private fun floatPanelImagePath(): String? {
        val c = ctx ?: return null
        val p = SPUtils.getParam(c, "float_panel_img", "") as String
        return if (p.isNotBlank() && File(p).exists()) p else null
    }

    // 悬浮窗背景模式: 0=实白磨砂 1=晶莹玻璃 2=自定义图片 3=官方背景 (旧布尔键自动迁移)
    private fun panelBgMode(): Int {
        val c = ctx
        if (c == null) return 0
        var m = (SPUtils.getParam(c, "float_bg_mode", -1) as Int)
        if (m < 0 || m > FloatSettingsManager.MODE_OFFICIAL) {
            val legacy = (SPUtils.getParam(c, "float_custom_bg", true) as Boolean)
            m = if (legacy) FloatSettingsManager.MODE_SOLID else FloatSettingsManager.MODE_GLASS
            SPUtils.setParam(c, "float_bg_mode", m)
        }
        return m
    }

    // 加载自定义图片文件: 按原图动态采样, 不暴力缩小 (固定 inSampleSize=8 会糊);
    // 裁剪产物长边 ≤2048, 正常情况原样解码, 悬浮窗/球内清晰显示
    private fun loadPhotoFile(path: String): android.graphics.drawable.Drawable {
        val c = ctx ?: return ColorDrawable(0)
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            val maxDim = 2048
            while (sample < 64 &&
                (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim)) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(path, opts) ?: return@runCatching ColorDrawable(0)
            BitmapDrawable(c.resources, bmp)
        }.getOrElse { ColorDrawable(0) }
    }

    // 官方背景素材: 首次解码后复用 (同齿轮图标, 避免每次重建面板都解码大图)
    private var officialBgCache: Bitmap? = null
    private fun officialPanelBg(): android.graphics.drawable.Drawable? {
        val c = ctx ?: return null
        if (officialBgCache == null) {
            officialBgCache = try {
                c.assets.open(OFFICIAL_PANEL_BG).use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) {
                null
            }
        }
        return officialBgCache?.let { BitmapDrawable(c.resources, it) }
    }

    // ── 悬浮球 (磨砂玻璃圆球 + 主页图标) ──
    @SuppressLint("ClickableViewAccessibility")
    // ── 面板主体: 头部 + [侧边栏 | 内容] ──
    // 背景四模式: 0=实白磨砂(默认) 1=晶莹玻璃 2=自定义图片 3=官方背景 (图片档 = 底层图片 + 上层玻璃)
    private fun buildPanel(): FrameLayout {
        val c = ctx ?: return FrameLayout(ctx!!)
        val bgMode = panelBgMode()
        val bgDrawable: android.graphics.drawable.Drawable? = when (bgMode) {
            FloatSettingsManager.MODE_CUSTOM -> floatPanelImagePath()?.let { loadPhotoFile(it) }
            FloatSettingsManager.MODE_OFFICIAL -> officialPanelBg()
            else -> null
        }

        fun glassLayer(alpha: Int): LayerDrawable = LayerDrawable(arrayOf(
            Md3.rounded(alpha, Md3.dp(c, 24f).toFloat()),
            Md3.rounded(0x00000000, Md3.dp(c, 24f).toFloat()).apply {
                setStroke(Md3.dp(c, 1f), 0x22000000)
            }
        ))

        val root = FrameLayout(c).apply {
            // 注: 不加 clipToOutline — 与半透明玻璃叠加时某些设备会产生
            // "比窗口略小的底层白块"离屏缓存; 圆角由各子层自带(背景圆角/图片层 clip)
            elevation = Md3.dp(c, 12f).toFloat()
        }
        if (bgDrawable != null) {
            // 图片档 (官方背景/自定义图片): 底层图片铺满 (按悬浮窗比例裁剪) + 上层轻薄玻璃 (35% 白罩, 太厚会盖成白块)
            root.addView(ImageView(c).apply {
                setImageDrawable(bgDrawable)
                scaleType = ImageView.ScaleType.CENTER_CROP
                // 图片层自带 24dp 圆角 (root 的 clipToOutline 未必裁剪子层, 防四角露出方角)
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, Md3.dp(c, 24f).toFloat())
                    }
                }
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            })
            root.addView(View(c).apply {
                background = glassLayer(0x59FFFFFF.toInt())
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            })
        } else {
            // 晶莹玻璃: 70% 白 (0x99=60% 太透, 背后游戏亮区会透成"白块"; 0xB3 压掉亮块仍是最透档)
            root.background = glassLayer(
                if (bgMode == FloatSettingsManager.MODE_GLASS) 0xB3FFFFFF.toInt() else 0xF7FFFFFF.toInt())
        }
        val content = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(Md3.dp(c, 6f), Md3.dp(c, 4f), Md3.dp(c, 6f), Md3.dp(c, 6f))
        }
        root.addView(content)

        // 头部 (小巧)
        val header = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Md3.dp(c, 12f), 0, Md3.dp(c, 4f), 0)
        }
        header.addView(ImageView(c).apply {
            setImageDrawable(icon("麦穗.png", Md3.primary))
            layoutParams = LinearLayout.LayoutParams(Md3.dp(c, 20f), Md3.dp(c, 20f))
        })
        header.addView(Md3.text(c, "离情梦", 15f, Md3.onSurface, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = Md3.dp(c, 8f)
            }
        })
        header.addView(ImageView(c).apply {
            setImageDrawable(icon("关闭.png", Md3.onSurfaceVariant))
            layoutParams = LinearLayout.LayoutParams(Md3.dp(c, 28f), Md3.dp(c, 28f))
            setPadding(Md3.dp(c, 5f), Md3.dp(c, 5f), Md3.dp(c, 5f), Md3.dp(c, 5f))
            background = Md3.ripple(Md3.rounded(0x00000000, 14f))
            isClickable = true
            setOnClickListener {
                haptic(this)
                collapsePanel()
            }
        })
        // 头部拖动整窗; 收起热区只有 麦穗图标+离情梦 一小块与右侧关闭按钮,
        // 其它区域拖动不收起 (避免整横条误触收起)
        var hdX = 0f
        var hdY = 0f
        var hdStartX = 0
        var hdStartY = 0
        var hdMoved = false
        var hdLocalX = 0f
        // 收起热区右界: header左padding 12 + 麦穗图标 20 + 间距 8 + 四字宽约 66 (粗体15sp)
        val collapseEndPx = Md3.dp(c, 12f) + Md3.dp(c, 20f) + Md3.dp(c, 8f) + Md3.dp(c, 66f)
        header.setOnTouchListener { v, ev ->
            val lp = rootParams ?: return@setOnTouchListener false
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val closeZonePx = Md3.dp(c, 4f) + Md3.dp(c, 28f) + Md3.dp(c, 4f)
                    if (ev.x >= v.width - closeZonePx) return@setOnTouchListener false   // 右侧关闭按钮 → 交子控件处理
                    hdLocalX = ev.x
                    hdX = ev.rawX
                    hdY = ev.rawY
                    hdStartX = lp.x
                    hdStartY = lp.y
                    hdMoved = false
                    haptic(v)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(ev.rawX - hdX)
                    val dy = Math.abs(ev.rawY - hdY)
                    if (dx > 8f || dy > 8f) hdMoved = true
                    val sw = c.resources.displayMetrics.widthPixels
                    val sh = c.resources.displayMetrics.heightPixels
                    val pw = panelWpx(c)
                    val ph = panelHpx(c)
                    lp.x = (hdStartX + (ev.rawX - hdX).toInt()).coerceIn(0, (sw - pw).coerceAtLeast(0))
                    lp.y = (hdStartY + (ev.rawY - hdY).toInt()).coerceIn(0, (sh - ph).coerceAtLeast(0))
                    rootView?.let { wm?.updateViewLayout(it, lp) }
                }
                MotionEvent.ACTION_UP -> {
                    // 仅 麦穗+离情梦 文字区域内的单击收起窗口
                    if (!hdMoved && hdLocalX <= collapseEndPx) collapsePanel()
                }
            }
            true   // 消费手势以持续接收 MOVE
        }
        content.addView(header)
        content.addView(View(c).apply {
            setBackgroundColor(0x14000000)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        })
        // 主体: 左 侧边栏 | 右 内容
        val body = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        body.addView(buildSidebar())
        // 无分隔线: 侧边栏与内容区靠各自底衬+留缝区分, 是两个独立区域
        pageBox = FrameLayout(c).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = Md3.dp(c, 3f)
            }
            background = Md3.rounded(0x08000000, Md3.dp(c, 14f).toFloat())
        }
        body.addView(pageBox)
        content.addView(body)
        // 页面
        espSyncRows.clear()
        // 面板每次重建, 页面实例必须重置: 否则旧面板的页面混进列表,
        // 切换/显示逻辑会操作"已移除的旧页面"导致内容区空白
        pages.clear()
        for (i in PAGES.indices) {
            val p = buildPage(i)
            p.visibility = if (i == 0) View.VISIBLE else View.GONE
            pages.add(p)
            pageBox?.addView(p, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        return root
    }

    // 左侧功能页侧边栏: 图片图标 + 文字 + 方形气泡 (同底栏: 跟随手指拖动, 实时切页)
    private fun buildSidebar(): FrameLayout {
        val c = ctx ?: return FrameLayout(ctx!!)
        val box = DragSidebar(c, ::onSidebarDrag).apply {
            layoutParams = LinearLayout.LayoutParams(Md3.dp(c, SIDE_W), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                marginEnd = Md3.dp(c, 3f)
            }
            // 圆角底衬: 与面板圆角呼应, 底部不突兀
            background = Md3.rounded(0x0F000000, Md3.dp(c, 16f).toFloat())
        }
        tabBubble = View(c).apply {
            // 浅蓝半透明 50% (加深, 明显可见)
            background = Md3.rounded(0x80D6E3FF.toInt(), Md3.dp(c, 9f).toFloat())
            layoutParams = FrameLayout.LayoutParams(
                Md3.dp(c, SIDE_W) - Md3.dp(c, 8f), Md3.dp(c, BUBBLE_H),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        }
        box.addView(tabBubble)
        sideBar = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Md3.dp(c, 3f), Md3.dp(c, 3f), 0, 0)
        }
        for (i in PAGES.indices) {
            val item = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Md3.dp(c, 10f), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Md3.dp(c, ITEM_H))
                isClickable = true
                setOnClickListener {
                    haptic(this)
                    setPage(i, true)
                }
            }
            item.addView(ImageView(c).apply {
                setImageDrawable(icon(PAGES[i].second, Md3.onSurfaceVariant))
                layoutParams = LinearLayout.LayoutParams(Md3.dp(c, 13f), Md3.dp(c, 13f))
                tag = i
            })
            item.addView(Md3.text(c, PAGES[i].first, 10f, Md3.onSurfaceVariant).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = Md3.dp(c, 5f)
                }
            })
            sideBar?.addView(item)
        }
        box.addView(sideBar)
        return box
    }

    // 侧边栏手势: 0=按下 1=拖动(dy=相对按下位移) 2=释放 (同底栏逻辑: 气泡跟手+实时切页+橡皮筋)
    private fun onSidebarDrag(phase: Int, dy: Float) {
        val c = ctx ?: return
        val bubble = tabBubble ?: return
        val itemH = Md3.dp(c, ITEM_H)
        val pad = Md3.dp(c, 3f)
        val firstTop = pad + (itemH - Md3.dp(c, BUBBLE_H)) / 2
        val lastTop = firstTop + (PAGES.size - 1) * itemH
        when (phase) {
            0 -> {   // 按下
                haptic(bubble)
            }
            1 -> {   // 拖动
                if (!sideDragActive) {
                    sideDragActive = true
                    sideDragBase = (bubble.layoutParams as FrameLayout.LayoutParams).topMargin.toFloat()
                    sideDragBaseDy = dy
                }
                // 硬限制在首/末项之间: 气泡不允许拖出悬浮窗 (原 30% 橡皮筋残留越界)
                val v = (sideDragBase + (dy - sideDragBaseDy)).coerceIn(firstTop.toFloat(), lastTop.toFloat())
                (bubble.layoutParams as FrameLayout.LayoutParams).topMargin = v.toInt()
                bubble.layoutParams = bubble.layoutParams
                // 实时切页: 越过项中线即切换内容与高亮
                val nearest = ((v - firstTop + itemH / 2f) / itemH).toInt().coerceIn(0, PAGES.size - 1)
                if (nearest != currentPage) switchContentLive(nearest)
            }
            else -> {   // 释放: 就近吸附
                if (!sideDragActive) return
                sideDragActive = false
                val v = (bubble.layoutParams as FrameLayout.LayoutParams).topMargin.toFloat()
                val nearest = ((v - firstTop + itemH / 2f) / itemH).toInt().coerceIn(0, PAGES.size - 1)
                setPage(nearest, true)
            }
        }
    }

    // 拖动中实时切换页面内容 (不依赖淡出回调: 立即只保留目标页可见, 防快速拖动叠层切页失败)
    private fun switchContentLive(index: Int) {
        if (index !in pages.indices) return
        tabBubble?.let { haptic(it) }
        switchPageInternal(index, animate = false, moveBubble = false)
    }

    // 切页: 方形气泡弹簧滑动 + 页面淡入滑入
    private fun setPage(index: Int, animate: Boolean) {
        switchPageInternal(index, animate, moveBubble = true)
    }

    // 统一切页: 立刻把非目标页全部 GONE(任何动画被打断都不会留下半透明叠层挡住新页)
    private fun switchPageInternal(index: Int, animate: Boolean, moveBubble: Boolean) {
        val c = ctx ?: return
        if (index !in pages.indices) return
        val changed = index != currentPage
        currentPage = index
        for (i in pages.indices) {
            val p = pages[i]
            p.animate().cancel()
            p.alpha = 1f
            p.translationX = 0f
            p.visibility = if (i == index) View.VISIBLE else View.GONE
        }
        val target = pages[index]
        if (animate && changed) {
            target.alpha = 0f
            target.animate().alpha(1f).setDuration(170).start()
        } else {
            target.alpha = 1f
        }
        if (moveBubble) layoutBubble(index, animate)
        updateSideColors(index)
    }

    // 方形气泡垂直滑到 index 项 (弹簧感)
    private fun layoutBubble(index: Int, animate: Boolean) {
        val bubble = tabBubble ?: return
        val c = ctx ?: return
        val itemH = Md3.dp(c, ITEM_H)
        val lp = bubble.layoutParams as FrameLayout.LayoutParams
        lp.topMargin = Md3.dp(c, 3f) + index * itemH + (itemH - Md3.dp(c, BUBBLE_H)) / 2
        bubble.layoutParams = lp
        if (animate) {
            bubble.scaleY = 0.6f
            bubble.alpha = 0.5f
            bubble.animate().scaleY(1f).alpha(1f).setDuration(220)
                .setInterpolator(OvershootInterpolator(1.2f)).start()
        }
    }

    private fun updateSideColors(index: Int) {
        val bar = sideBar ?: return
        for (i in 0 until bar.childCount) {
            val item = bar.getChildAt(i) as LinearLayout
            val img = item.getChildAt(0) as ImageView
            val txt = item.getChildAt(1) as TextView
            img.setImageDrawable(icon(PAGES[i].second, if (i == index) Md3.primary else Md3.onSurfaceVariant))
            txt.setTextColor(if (i == index) Md3.primary else Md3.onSurfaceVariant)
            txt.setTypeface(txt.typeface, if (i == index) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }

    // ── 10 个功能页 (小巧控件) ──
    // ── 悬浮窗输入法: 编辑时临时可聚焦 + NOT_TOUCH_MODAL (窗外触摸放行, 否则
    //    可聚焦窗口会吞掉全屏点按, 键盘落下后游戏无法操控); 结束立即还原
    private fun overlayInputFocus(edit: EditText, focus: Boolean) {
        val lp = rootParams ?: run {  return }
        val rv = rootView ?: run {  return }
        if (focus) {
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            // 显式保证"允许输入法聚焦本窗口"(这个标志若被设上, 键盘能弹但按键会无效)
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
            // ⚠️ 关键: 悬浮窗是 TYPE_APPLICATION_OVERLAY, 不设 softInputMode 时部分 ROM 会出现
            //    "输入法弹出来了但点不动" (窗口与输入法争焦点/不重排) → 明确声明随输入法调整并强制可见
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        } else {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
        try {
            wm?.updateViewLayout(rv, lp)
        } catch (t: Throwable) {
        }
        if (focus) {
            edit.post {
                edit.requestFocus()
                val imm = ctx?.getSystemService(Context.INPUT_METHOD_SERVICE)
                        as? android.view.inputmethod.InputMethodManager
                // 首调容错: 移除 NOT_FOCUSABLE 后窗口焦点/输入连接是异步生效的,
                // 立即 show 常被忽略 → 200ms 后补一次强制请求 (多点几次才弹出的根因)
                try {
                    val r1 = imm?.showSoftInput(edit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                } catch (t: Throwable) {
                }
                edit.postDelayed({
                    try {
                        if (!edit.hasFocus()) {
                            edit.requestFocus()
                        }
                        val r2 = imm?.showSoftInput(edit, 0)
                    } catch (t: Throwable) {
                    }
                    // ── 排查"键盘弹出后触控无效": 把窗口/输入法的真实状态打出来 ──
                    //  ⚠️ 注意: 悬浮窗(FLAG_LAYOUT_NO_LIMITS + LAYOUT_IN_SCREEN)的
                    //    getWindowVisibleDisplayFrame 不会扣掉输入法, 永远返回全屏 —— 所以**不能**用它判断
                    //    键盘高度(会假报警)。用 WindowInsets.Type.ime() (API 30+) 才准。
                    //  ⚠️⚠️ 更重要的教训(2026-10-03 真机日志): 之前拿"键盘高=0"当异常判据 → 每次输入都误触发
                    //    一次 updateViewLayout + showSoftInput + requestFocus。对悬浮窗重复 updateViewLayout
                    //    会把输入法焦点打掉, 表现就是"键盘弹出来了但按键点不动"(一次输入触发 3 次)。
                    //    现在**只在窗口被系统改回"不可聚焦"时才纠正**。
                    edit.postDelayed({
                        try {
                            val imeVisible: Boolean? =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    rv.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())
                                } else null
                            val focusable = (lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0
                            val altIm = (lp.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM) != 0
                            val loc = IntArray(2)
                            rv.getLocationOnScreen(loc)
                            if (!focusable || altIm) {
                                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
                                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                                runCatching { wm?.updateViewLayout(rv, lp) }
                                edit.requestFocus()
                                runCatching { imm?.showSoftInput(edit, 0) }
                            }
                        } catch (t: Throwable) {
                        }
                    }, 300)
                }, 200)
            }
        } else {
            try {
                val imm = ctx?.getSystemService(Context.INPUT_METHOD_SERVICE)
                        as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(rv.windowToken, 0)
            } catch (t: Throwable) {
            }
        }
    }

    // ── 输入法"完成"键 = 点确定 (默认动作键"下一步"无行为, 键盘上无法完成输入) ──
    // onDone 返回 true=已提交(收起键盘), false=校验未过(键盘保持)
    private fun bindImeDone(edit: EditText, onDone: () -> Boolean) {
        edit.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        edit.setSingleLine(true)
        edit.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                haptic(v)
                if (onDone()) overlayInputFocus(v as EditText, false)
                true
            } else false
        }
    }

    // ── 战斗页注入功能卡: 胶囊(标题 + 齿轮图 + 开关); 齿轮展开设置区 (同自瞄页动画) ──
    private fun addBattleFeature(root: LinearLayout, title: String, init: Boolean,
                                 hasCfg: Boolean, onToggle: (Boolean) -> Unit,
                                 fillSettings: ((LinearLayout, (EditText) -> Unit) -> Unit)?) {
        val c = ctx ?: return
        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val rad = Md3.dp(c, 16f).toFloat()
            background = LayerDrawable(arrayOf(
                Md3.rounded(0x14000000.toInt(), rad),
                Md3.rounded(0x00000000, rad).apply { setStroke(Md3.dp(c, 1f), 0x2E000000) }
            ))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Md3.dp(c, 2f)
                bottomMargin = Md3.dp(c, 2f)
            }
        }
        val sw = Md3Switch(c).apply { setOn(init, false) }
        val gear = ImageView(c).apply {
            val src = runCatching {
                gearIcon()
            }.getOrNull()
            if (src != null) {
                setImageBitmap(src)
                setColorFilter(Md3.primary, PorterDuff.Mode.SRC_IN)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            background = Md3.ripple(Md3.rounded(0x0A000000.toInt(), 10f))
            layoutParams = LinearLayout.LayoutParams(Md3.dp(c, 22f), Md3.dp(c, 22f)).apply {
                marginStart = Md3.dp(c, 6f)
                marginEnd = Md3.dp(c, 2f)
            }
            visibility = if (hasCfg) View.VISIBLE else View.GONE
        }
        card.addView(LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Md3.dp(c, 12f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
            minimumHeight = Md3.dp(c, 34f)
            addView(Md3.text(c, title, 12f, Md3.onSurface).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(gear)
            addView(sw)
        })
        sw.onCheckedChangeListener = { on -> onToggle(on) }
        root.addView(card)
        if (!hasCfg) return
        val divider = View(c).apply {
            background = ColorDrawable(0x1C000000)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Md3.dp(c, 1f)).apply {
                marginStart = Md3.dp(c, 10f)
                marginEnd = Md3.dp(c, 10f)
            }
            visibility = View.GONE
        }
        card.addView(divider)
        val setArea = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Md3.dp(c, 12f), Md3.dp(c, 6f), Md3.dp(c, 8f), Md3.dp(c, 8f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0)
            visibility = View.GONE
        }
        var activeEdit: EditText? = null
        fillSettings?.invoke(setArea) { activeEdit = it }
        card.addView(setArea)
        var setOpen = false
        var natH = 0
        var heightAnim: ValueAnimator? = null
        var openGen = 0
        fun animateHeight(to: Int, gen: Int) {
            heightAnim?.cancel()
            val from = (setArea.layoutParams as LinearLayout.LayoutParams).height
            heightAnim = ValueAnimator.ofInt(from, to).apply {
                duration = 260
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    (setArea.layoutParams as LinearLayout.LayoutParams).height =
                        (it.animatedValue as Int).coerceAtLeast(0)
                    setArea.requestLayout()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (gen == openGen && to == 0) {
                            setArea.visibility = View.GONE
                            activeEdit?.let { overlayInputFocus(it, false) }
                        }
                    }
                })
                start()
            }
        }
        fun openSettings() {
            openGen++
            heightAnim?.cancel()
            setArea.visibility = View.VISIBLE
            gear.animate().rotation(90f).setDuration(260)
                .setInterpolator(DecelerateInterpolator()).start()
            divider.alpha = 0f
            divider.visibility = View.VISIBLE
            divider.animate().alpha(1f).setDuration(220).start()
            if (natH == 0) {
                setArea.post {
                    val wSpec = View.MeasureSpec.makeMeasureSpec(
                        if (card.width > 0) card.width else Md3.dp(c, 300f),
                        View.MeasureSpec.EXACTLY)
                    setArea.measure(wSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    natH = setArea.measuredHeight
                    if (natH <= 0) natH = Md3.dp(c, 120f)
                    (setArea.layoutParams as LinearLayout.LayoutParams).height = 0
                    setArea.requestLayout()
                    if (setOpen) animateHeight(natH, openGen)
                }
            } else {
                animateHeight(natH, openGen)
            }
        }
        fun closeSettings() {
            openGen++
            heightAnim?.cancel()
            gear.animate().rotation(0f).setDuration(260)
                .setInterpolator(DecelerateInterpolator()).start()
            divider.animate().alpha(0f).setDuration(180)
                .withEndAction { divider.visibility = View.GONE }.start()
            activeEdit?.let { overlayInputFocus(it, false) }
            animateHeight(0, openGen)
        }
        gear.setOnClickListener {
            haptic(it)
            setOpen = !setOpen
            if (setOpen) openSettings() else closeSettings()
        }
    }

    // ── 动作胶囊卡 (无开关: 无齿轮=整卡点击直接执行; 有齿轮=展开设置区, 区内放
    //    输入行 + 确定按钮触发 onAction) — 娱乐页按钮类 Lua 功能 (临时改名/喷漆/追踪房间) ──
    private fun addActionCard(root: LinearLayout, title: String,
                              onAction: () -> Unit,
                              fillSettings: ((LinearLayout, (EditText) -> Unit) -> Unit)?) {
        val c = ctx ?: return
        // ── 无齿轮 = 按钮卡: 主色填充 + 白字居中 (与开关/齿轮胶囊视觉区分) ──
        if (fillSettings == null) {
            root.addView(TextView(c).apply {
                text = title
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
                setPadding(0, Md3.dp(c, 10f), 0, Md3.dp(c, 10f))
                background = Md3.ripple(Md3.rounded(Md3.primary, Md3.dp(c, 16f).toFloat()))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = Md3.dp(c, 2f)
                    bottomMargin = Md3.dp(c, 2f)
                }
                setOnClickListener { haptic(it); onAction() }
            })
            return
        }
        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val rad = Md3.dp(c, 16f).toFloat()
            background = LayerDrawable(arrayOf(
                Md3.rounded(0x14000000.toInt(), rad),
                Md3.rounded(0x00000000, rad).apply { setStroke(Md3.dp(c, 1f), 0x2E000000) }
            ))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Md3.dp(c, 2f)
                bottomMargin = Md3.dp(c, 2f)
            }
        }
        val gear = ImageView(c).apply {
            val src = runCatching {
                gearIcon()
            }.getOrNull()
            if (src != null) {
                setImageBitmap(src)
                setColorFilter(Md3.primary, PorterDuff.Mode.SRC_IN)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            background = Md3.ripple(Md3.rounded(0x0A000000.toInt(), 10f))
            layoutParams = LinearLayout.LayoutParams(Md3.dp(c, 22f), Md3.dp(c, 22f)).apply {
                marginStart = Md3.dp(c, 6f)
                marginEnd = Md3.dp(c, 2f)
            }
        }
        card.addView(LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Md3.dp(c, 12f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
            minimumHeight = Md3.dp(c, 34f)
            addView(Md3.text(c, title, 12f, Md3.onSurface).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(gear)
        })
        root.addView(card)
        val divider = View(c).apply {
            background = ColorDrawable(0x1C000000)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Md3.dp(c, 1f)).apply {
                marginStart = Md3.dp(c, 10f)
                marginEnd = Md3.dp(c, 10f)
            }
            visibility = View.GONE
        }
        card.addView(divider)
        val setArea = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Md3.dp(c, 12f), Md3.dp(c, 6f), Md3.dp(c, 8f), Md3.dp(c, 8f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0)
            visibility = View.GONE
        }
        var activeEdit: EditText? = null
        fillSettings?.invoke(setArea) { activeEdit = it }
        card.addView(setArea)
        var setOpen = false
        var natH = 0
        var heightAnim: ValueAnimator? = null
        var openGen = 0
        fun animateHeight(to: Int, gen: Int) {
            heightAnim?.cancel()
            val from = (setArea.layoutParams as LinearLayout.LayoutParams).height
            heightAnim = ValueAnimator.ofInt(from, to).apply {
                duration = 260
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    (setArea.layoutParams as LinearLayout.LayoutParams).height =
                        (it.animatedValue as Int).coerceAtLeast(0)
                    setArea.requestLayout()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (gen == openGen && to == 0) {
                            setArea.visibility = View.GONE
                            activeEdit?.let { overlayInputFocus(it, false) }
                        }
                    }
                })
                start()
            }
        }
        fun openSettings() {
            openGen++
            heightAnim?.cancel()
            setArea.visibility = View.VISIBLE
            gear.animate().rotation(90f).setDuration(260)
                .setInterpolator(DecelerateInterpolator()).start()
            divider.alpha = 0f
            divider.visibility = View.VISIBLE
            divider.animate().alpha(1f).setDuration(220).start()
            if (natH == 0) {
                setArea.post {
                    val wSpec = View.MeasureSpec.makeMeasureSpec(
                        if (card.width > 0) card.width else Md3.dp(c, 300f),
                        View.MeasureSpec.EXACTLY)
                    setArea.measure(wSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    natH = setArea.measuredHeight
                    if (natH <= 0) natH = Md3.dp(c, 120f)
                    (setArea.layoutParams as LinearLayout.LayoutParams).height = 0
                    setArea.requestLayout()
                    if (setOpen) animateHeight(natH, openGen)
                }
            } else {
                animateHeight(natH, openGen)
            }
        }
        fun closeSettings() {
            openGen++
            heightAnim?.cancel()
            gear.animate().rotation(0f).setDuration(260)
                .setInterpolator(DecelerateInterpolator()).start()
            divider.animate().alpha(0f).setDuration(180)
                .withEndAction { divider.visibility = View.GONE }.start()
            activeEdit?.let { overlayInputFocus(it, false) }
            animateHeight(0, openGen)
        }
        val toggleOpen: (() -> Unit)? = {
            setOpen = !setOpen
            if (setOpen) openSettings() else closeSettings()
        }
        gear.setOnClickListener {
            haptic(it)
            toggleOpen?.invoke()
        }
        // 标题行点击同齿轮 (展开/收起), 无独立按压触发
        (card.getChildAt(0) as LinearLayout).setOnClickListener {
            haptic(it)
            toggleOpen?.invoke()
        }
    }

    // 追踪功能玻璃卡 (坐标/视角 共用; 横向双页内每页一张; 返回视图由调用方布局)
    private fun trackGlassCard(title: String, tip: String, curOn: Boolean,
                               isSel: (Int) -> Boolean, onMode: (Int) -> Unit,
                               onToggle: (Boolean) -> Unit): LinearLayout {
        val c = ctx ?: return LinearLayout(ctx!!)
        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val rad = Md3.dp(c, 16f).toFloat()
            background = LayerDrawable(arrayOf(
                Md3.rounded(0x14000000.toInt(), rad),
                Md3.rounded(0x00000000, rad).apply { setStroke(Md3.dp(c, 1f), 0x2E000000) }
            ))
        }
        val sw = Md3Switch(c).apply { setOn(curOn, false) }
        card.addView(LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Md3.dp(c, 12f), Md3.dp(c, 5f), Md3.dp(c, 8f), Md3.dp(c, 5f))
            addView(LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(Md3.text(c, title, 12f, Md3.onSurface))
                addView(Md3.text(c, tip, 9f, Md3.onSurfaceVariant).apply {
                    setPadding(0, Md3.dp(c, 1f), 0, 0)
                })
            })
            addView(sw)
        })
        val opts = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Md3.dp(c, 12f), 0, Md3.dp(c, 8f), Md3.dp(c, 8f))
            visibility = if (curOn) View.VISIBLE else View.GONE
        }
        // 目标选择单选 chips: 残血优先 / 距离最近 (就地重绘高亮)
        var a: TextView? = null
        var b: TextView? = null
        fun style(tv: TextView?, m: Int) {
            val sel = isSel(m)
            tv?.setTextColor(if (sel) Md3.onPrimaryContainer else Md3.onSurfaceVariant)
            tv?.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = Md3.dp(c, 8f).toFloat()
                if (sel) {
                    setColor(Md3.primaryContainer)
                    setStroke(Md3.dp(c, 1f), Md3.primary)
                } else {
                    setColor(0x00000000)
                    setStroke(Md3.dp(c, 1f), Md3.outline)
                }
            }
        }
        fun mk(txt: String, m: Int): TextView =
            Md3.text(c, txt, 11f, Md3.onSurfaceVariant).apply {
                setPadding(Md3.dp(c, 10f), Md3.dp(c, 4f), Md3.dp(c, 10f), Md3.dp(c, 4f))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = Md3.dp(c, 4f)
                }
                setOnClickListener {
                    haptic(this)
                    onMode(m)
                    style(a, 0)
                    style(b, 1)
                }
            }
        opts.addView(Md3.text(c, "目标", 11f, Md3.onSurfaceVariant).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val na = mk("残血", 0)
        val nb = mk("最近", 1)
        a = na
        b = nb
        opts.addView(na)
        opts.addView(nb)
        style(a, 0)
        style(b, 1)
        card.addView(opts)
        sw.onCheckedChangeListener = { on ->
            onToggle(on)
            opts.visibility = if (on) View.VISIBLE else View.GONE
        }
        return card
    }

    private fun buildPage(index: Int): View {
        val c = ctx ?: return View(ctx)
        val scroll = ScrollView(c).apply { isVerticalScrollBarEnabled = false }
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Md3.dp(c, 12f), Md3.dp(c, 8f), Md3.dp(c, 12f), Md3.dp(c, 8f))
        }
        when (index) {
            0 -> {   // 首页 (快捷功能: 绕过检测 / 注入引擎)
                // 绕过检测 (safe2 3s 循环, 同梦喵: 启动后进程内常驻)
                val swBypass = Md3Switch(c).apply { setOn(EspCtl.bypassOn, false) }
                root.addView(capRow(c, "绕过检测", swBypass) { on ->
                    EspCtl.setBypass(on)
                    if (on && !EspCtl.flags[0]) EspCtl.ensure()
                })
                // 注入引擎 (点击一次注入一次; 云端 Lua 载荷接入点)
                val luaBtn = Md3.button(c, "注入", Md3.dp(c, 30f), filled = true,
                    corner = Md3.dp(c, 9f).toFloat()).apply {
                    textSize = 11f
                    layoutParams = LinearLayout.LayoutParams(
                        Md3.dp(c, 56f), Md3.dp(c, 30f)).apply { marginStart = Md3.dp(c, 8f) }
                    setOnClickListener {
                        haptic(this)
                        EspCtl.luaInject()
                        Island.action(c, "注入引擎")
                    }
                }
                // 注入引擎 行 (玻璃胶囊同款: 标题 + 注入按钮)
                root.addView(LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val rad = Md3.dp(c, 16f).toFloat()
                    background = LayerDrawable(arrayOf(
                        Md3.rounded(0x14000000.toInt(), rad),
                        Md3.rounded(0x00000000, rad).apply { setStroke(Md3.dp(c, 1f), 0x2E000000) }
                    ))
                    setPadding(Md3.dp(c, 12f), Md3.dp(c, 3f), Md3.dp(c, 8f), Md3.dp(c, 3f))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = Md3.dp(c, 2f)
                        bottomMargin = Md3.dp(c, 2f)
                    }
                    addView(Md3.text(c, "注入引擎", 12f, Md3.onSurface).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(luaBtn)
                })
                // 注入DLL (梦喵同款: 开=先投放 so 到 离情梦/ 再写 注入DLL=1, 游戏内 Lua 拷进
                // 游戏私有目录后 dlopen; 关=写 0。须先注入过引擎 —— so 靠注入进去的 Lua 加载)
                val swDll = Md3Switch(c).apply { setOn(FuncBridge.dllInjectOn, false) }
                root.addView(capRow(c, "注入DLL", swDll) { on ->
                    if (on) FuncBridge.enableDllInject(c) else FuncBridge.sendDllInject(false)
                })
                // ══ 大厅背景 (移植梦之喵羽 PickLobbyBgActivity) ══
                // 相册选图 → 4:3 裁剪 → 离情梦/大厅背景.png；Lua 命令 184 = applyLobbyBg。
                // 隐藏大厅人物写 离情梦/隐藏大厅人物=1/0，Lua 每次实时读，再触发 184 生效。
                addActionCard(root, "大厅背景", {}) { area, _ ->
                    fun toast(msg: String) {
                        try {
                            android.widget.Toast.makeText(c, msg, android.widget.Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                        }
                    }
                    val status = Md3.text(c,
                        if (FuncBridge.lobbyBgReady()) "已就绪: 大厅背景.png" else "未选择图片 (点下方按钮选图)",
                        10f, Md3.onSurfaceVariant)
                    area.addView(status)
                    fun actionBtn(label: String, onTap: () -> Unit): TextView = TextView(c).apply {
                        text = label
                        textSize = 11f
                        gravity = Gravity.CENTER
                        setTextColor(0xFFFFFFFF.toInt())
                        background = Md3.rounded(Md3.primary, Md3.dp(c, 10f).toFloat())
                        setPadding(0, Md3.dp(c, 6f), 0, Md3.dp(c, 6f))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = Md3.dp(c, 3f)
                        }
                        setOnClickListener {
                            haptic(this)
                            onTap()
                        }
                    }
                    area.addView(actionBtn("相册选择图片（裁剪 4:3）") {
                        try {
                            c.startActivity(
                                android.content.Intent(c, PickLobbyBgActivity::class.java)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {
                        }
                    })
                    area.addView(actionBtn("应用大厅背景") {
                        when {
                            !FuncBridge.lobbyBgReady() -> toast("还没有选图")
                            else -> {
                                FuncBridge.applyLobbyBg()
                                status.text = "已就绪: 大厅背景.png"
                                toast("已应用")
                            }
                        }
                    })
                    val hideRow = LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(Md3.dp(c, 8f), Md3.dp(c, 4f), Md3.dp(c, 8f), Md3.dp(c, 4f))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = Md3.dp(c, 4f)
                        }
                    }
                    hideRow.addView(Md3.text(c, "隐藏大厅人物", 11f, Md3.onSurface).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    val swHide = Md3Switch(c).apply { setOn(FuncBridge.hideLobbyCharOn(), false) }
                    swHide.setOnClickListener { v ->
                        haptic(v)
                        val nv = !FuncBridge.hideLobbyCharOn()
                        FuncBridge.setHideLobbyChar(nv)
                        swHide.setOn(nv, true)
                    }
                    hideRow.addView(swHide)
                    area.addView(hideRow)
                }
            }
            1 -> {   // 绘制 (ESP 开关, 位序与梦喵 isDraw 对齐)
                root.addView(espSwitchRow(c, "ESP 总开关", 0))
                root.addView(espSwitchRow(c, "方框透视", 1))
                root.addView(espSwitchRow(c, "绘制射线", 2))
                root.addView(espSwitchRow(c, "显示血量", 3))
                root.addView(espSwitchRow(c, "距离显示", 4))
                root.addView(espSwitchRow(c, "名字显示", 5))
                root.addView(espSwitchRow(c, "人数统计", 6))
                root.addView(espSwitchRow(c, "迷你号", 7))
                root.addView(espSwitchRow(c, "击败数", 8))
                // 白子光环 (梦喵 DrawHalo): 开启时若绘制未开则一并打开 (同梦喵)
                val swHalo = Md3Switch(c).apply { setOn(EspCtl.flags[10], false) }
                root.addView(capRow(c, "白子光环", swHalo) { on ->
                    EspCtl.toggle(10, on)
                    if (on && !EspCtl.flags[0]) {
                        EspCtl.toggle(0, true)
                        EspCtl.ensure()
                        for ((idx, other) in espSyncRows) if (idx == 0) other.setOn(true, false)
                    }
                    OverlayHost.refresh()
                })
                root.addView(espSwitchRow(c, "过滤队友", 9))
            }
            2 -> {   // 瞄准 (自瞄开关 + 连体展开菜单, 梦喵式)

                val wrap = LinearLayout(c).apply {

                    orientation = LinearLayout.VERTICAL

                    val rad = Md3.dp(c, 16f).toFloat()

                    background = LayerDrawable(arrayOf(

                        Md3.rounded(0x14000000.toInt(), rad),

                        Md3.rounded(0x00000000, rad).apply { setStroke(Md3.dp(c, 1f), 0x2E000000) }

                    ))

                    layoutParams = LinearLayout.LayoutParams(

                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

                }

                val swAim = Md3Switch(c)

                // 设置齿轮图标: 图片随主题色 tint (跟随 UI 主色)

                val gear = ImageView(c).apply {

                    val src = runCatching {

                        gearIcon()

                    }.getOrNull()

                    if (src != null) {

                        setImageBitmap(src)

                        setColorFilter(Md3.primary, PorterDuff.Mode.SRC_IN)

                        scaleType = ImageView.ScaleType.FIT_CENTER

                    }

                    background = Md3.ripple(Md3.rounded(0x0A000000.toInt(), 11f))

                    layoutParams = LinearLayout.LayoutParams(Md3.dp(c, 22f), Md3.dp(c, 22f)).apply {

                        marginStart = Md3.dp(c, 8f)

                        marginEnd = Md3.dp(c, 14f)

                    }

                    visibility = View.GONE

                }

                // 顶行: 标题 + 齿轮 + 开关 (连体卡片头部)

                val topRow = LinearLayout(c).apply {

                    orientation = LinearLayout.HORIZONTAL

                    gravity = Gravity.CENTER_VERTICAL

                    setPadding(Md3.dp(c, 12f), Md3.dp(c, 4f), Md3.dp(c, 8f), Md3.dp(c, 4f))

                    addView(Md3.text(c, "自动瞄准", 12f, Md3.onSurface).apply {

                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                    })

                    addView(gear)

                    addView(swAim)

                }

                wrap.addView(topRow)

                // 分隔线 (展开时淡入, 与设置区同宽留边, 视觉上与胶囊连体)

                val divider = View(c).apply {

                    background = ColorDrawable(0x1C000000)

                    layoutParams = LinearLayout.LayoutParams(

                        ViewGroup.LayoutParams.MATCH_PARENT, Md3.dp(c, 1f)).apply {

                        marginStart = Md3.dp(c, 10f)

                        marginEnd = Md3.dp(c, 10f)

                    }

                    visibility = View.GONE

                }

                wrap.addView(divider)

                // 设置区 (与胶囊一体, 高度动画展开/收回)

                val setArea = LinearLayout(c).apply {

                    orientation = LinearLayout.VERTICAL

                    setPadding(Md3.dp(c, 12f), Md3.dp(c, 6f), Md3.dp(c, 8f), Md3.dp(c, 8f))

                    layoutParams = LinearLayout.LayoutParams(

                        ViewGroup.LayoutParams.MATCH_PARENT, 0)

                    visibility = View.GONE

                }

                // 自动瞄准追线 (屏心连到锁定敌人头部; 与 ESP 总开关独立显示; capRow 自带提示)
                val swAimLine = Md3Switch(c).apply { setOn(EspCtl.aimLine, false) }
                setArea.addView(capRow(c, "自动瞄准追线", swAimLine) { on ->
                    EspCtl.setAim(line = on)
                })

                // 参数组: 标签+滑块 圈入同一深色背景卡 (比拖动条 chip 更深)
                fun paramGroup(title: String, value: TextView, slider: Md3Slider) {
                    val group = LinearLayout(c).apply {
                        orientation = LinearLayout.VERTICAL
                        background = LayerDrawable(arrayOf(
                            Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                            Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                                setStroke(Md3.dp(c, 1f), 0x38000000)
                            }
                        ))
                        setPadding(Md3.dp(c, 6f), Md3.dp(c, 2f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = Md3.dp(c, 2f)
                        }
                    }
                    val titleRow = LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(Md3.text(c, title, 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        addView(value)
                    }
                    group.addView(titleRow)
                    group.addView(slider)
                    setArea.addView(group)
                }
                // 1.0 = 秒锁 (默认); 往左拉降低吸速
                val smoothLbl = { v: Float -> if (v >= 0.995f) "秒锁" else smoothText(v) }
                val smoothVal = Md3.text(c, smoothLbl(EspCtl.aimSmooth), 11f, Md3.primary)
                val sSlider = Md3Slider(c).apply {
                    setValue(((EspCtl.aimSmooth - 0.05f) / 0.95f * 100f).coerceIn(0f, 100f))
                    onValueChange = { v ->
                        val sm = 0.05f + 0.95f * v / 100f
                        EspCtl.setAim(smooth = sm)
                        smoothVal.text = smoothLbl(sm)
                    }
                    valueLabel = { v -> smoothLbl(0.05f + 0.95f * v / 100f) }
                }
                paramGroup("锁定速度", smoothVal, sSlider)
                val rangeVal = Md3.text(c, "${EspCtl.aimRange.toInt()}px", 11f, Md3.primary)
                val rSlider = Md3Slider(c).apply {
                    setValue(((EspCtl.aimRange - 60f) / 640f * 100f).coerceIn(0f, 100f))
                    onValueChange = { v ->
                        val r = 60f + 640f * v / 100f
                        EspCtl.setAim(range = r)
                        rangeVal.text = "${r.toInt()}px"
                    }
                    valueLabel = { v -> ((60f + 640f * v / 100f).toInt()).toString() }
                }
                paramGroup("瞄准范围", rangeVal, rSlider)
                wrap.addView(setArea)



                var setOpen = false

                var natH = 0          // 设置区自然高度 (首次展开测量缓存)

                var heightAnim: ValueAnimator? = null

                var openGen = 0       // 展开代际: 打断旧动画的迟到收尾

                fun animateHeight(to: Int, gen: Int) {

                    heightAnim?.cancel()

                    val from = (setArea.layoutParams as LinearLayout.LayoutParams).height

                    heightAnim = ValueAnimator.ofInt(from, to).apply {

                        duration = 300

                        interpolator = DecelerateInterpolator()

                        addUpdateListener {

                            (setArea.layoutParams as LinearLayout.LayoutParams).height =

                                (it.animatedValue as Int).coerceAtLeast(0)

                            setArea.requestLayout()

                        }

                        addListener(object : android.animation.AnimatorListenerAdapter() {

                            override fun onAnimationEnd(animation: android.animation.Animator) {

                                // 仅当前代际收尾才 GONE (被打断的旧动画不误隐藏面板)
                                if (gen == openGen && to == 0) setArea.visibility = View.GONE
                            }

                        })

                        start()

                    }

                }

                fun openSettings() {

                    openGen++

                    heightAnim?.cancel()

                    setArea.visibility = View.VISIBLE   // 每次展开必可见 (修复偶发"展不开")
                    gear.animate().rotation(90f).setDuration(300)

                        .setInterpolator(DecelerateInterpolator()).start()

                    divider.alpha = 0f

                    divider.visibility = View.VISIBLE

                    divider.animate().alpha(1f).setDuration(260).start()

                    if (natH == 0) {

                        // 首次: post 后测量 (保证已挂载有宽度), 兜底高度
                        setArea.post {
                            val wSpec = View.MeasureSpec.makeMeasureSpec(
                                if (wrap.width > 0) wrap.width else Md3.dp(c, 300f),
                                View.MeasureSpec.EXACTLY)
                            setArea.measure(wSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                            natH = setArea.measuredHeight
                            if (natH <= 0) natH = Md3.dp(c, 190f)
                            (setArea.layoutParams as LinearLayout.LayoutParams).height = 0
                            setArea.requestLayout()
                            if (setOpen) animateHeight(natH, openGen)
                        }
                    } else {
                        animateHeight(natH, openGen)
                    }

                }

                fun closeSettings() {

                    openGen++

                    heightAnim?.cancel()

                    gear.animate().rotation(0f).setDuration(300)

                        .setInterpolator(DecelerateInterpolator()).start()

                    divider.animate().alpha(0f).setDuration(200)

                        .withEndAction { divider.visibility = View.GONE }.start()

                    animateHeight(0, openGen)

                }



                swAim.onCheckedChangeListener = { on ->

                    EspCtl.setAim(on = on)

                    Island.notice(c, "自动瞄准", on)

                    // 圈/追线与 ESP 总开关独立: 无总开关也要装配数据源并保活渲染层
                    if (on && !EspCtl.flags[0]) EspCtl.ensure()

                    OverlayHost.refresh()

                    if (on) {

                        gear.visibility = View.VISIBLE

                        gear.rotation = 0f

                    } else {

                        gear.visibility = View.GONE

                        setOpen = false

                        closeSettings()

                    }

                }

                gear.setOnClickListener {

                    haptic(it)

                    setOpen = !setOpen

                    if (setOpen) openSettings() else closeSettings()

                }

                root.addView(wrap)

                // 过滤队友 (与绘制页同步, flags[9])

                root.addView(espSwitchRow(c, "过滤队友", 9))

            }

            3 -> {   // 追踪: 双页 (枪械追踪 | 枪械属性), 仅标题栏切页
                val tabsT = arrayOf("枪械追踪", "枪械属性")
                val tabViewsT = arrayListOf<TextView>()
                val tabRowT = LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = Md3.rounded(0x0F000000, Md3.dp(c, 10f).toFloat())
                    setPadding(Md3.dp(c, 3f), Md3.dp(c, 2f), Md3.dp(c, 3f), Md3.dp(c, 2f))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = Md3.dp(c, 4f)
                    }
                }
                fun styleTabT(tv: TextView, sel: Boolean) {
                    tv.setTextColor(if (sel) Md3.onPrimaryContainer else Md3.onSurfaceVariant)
                    tv.background = if (sel)
                        Md3.rounded(Md3.primaryContainer, Md3.dp(c, 8f).toFloat())
                    else null
                }
                val panesT = arrayListOf<LinearLayout>()
                fun paneT(vis: Boolean): LinearLayout {
                    val p = LinearLayout(c).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = Md3.dp(c, 2f)
                        }
                        visibility = if (vis) View.VISIBLE else View.GONE
                    }
                    panesT.add(p)
                    return p
                }
                val pA = paneT(true)
                val pB = paneT(false)
                fun goPaneT(i: Int) {
                    if (i !in panesT.indices) return
                    for (t in tabViewsT.indices) styleTabT(tabViewsT[t], t == i)
                    for (pi in panesT.indices) {
                        val pv = panesT[pi]
                        pv.animate().cancel()
                        pv.alpha = 1f
                        pv.visibility = if (pi == i) View.VISIBLE else View.GONE
                    }
                    panesT[i].alpha = 0f
                    panesT[i].animate().alpha(1f).setDuration(150).start()
                }
                for (ti in tabsT.indices) {
                    val tv = Md3.text(c, tabsT[ti], 11f, Md3.onSurfaceVariant).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, Md3.dp(c, 5f), 0, Md3.dp(c, 5f))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener {
                            haptic(this)
                            goPaneT(ti)
                        }
                    }
                    tabViewsT.add(tv)
                    tabRowT.addView(tv)
                }
                root.addView(tabRowT)
                val wrap = LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    val rad = Md3.dp(c, 16f).toFloat()
                    background = LayerDrawable(arrayOf(
                        Md3.rounded(0x14000000.toInt(), rad),
                        Md3.rounded(0x00000000, rad).apply { setStroke(Md3.dp(c, 1f), 0x2E000000) }
                    ))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }

                val swSub = Md3Switch(c).apply { setOn(EspCtl.subOn, false) }
                val gear = ImageView(c).apply {
                    val src = runCatching {
                        gearIcon()
                    }.getOrNull()
                    if (src != null) {
                        setImageBitmap(src)
                        setColorFilter(Md3.primary, PorterDuff.Mode.SRC_IN)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                    background = Md3.ripple(Md3.rounded(0x0A000000.toInt(), 11f))
                    layoutParams = LinearLayout.LayoutParams(Md3.dp(c, 22f), Md3.dp(c, 22f)).apply {
                        marginStart = Md3.dp(c, 8f)
                        marginEnd = Md3.dp(c, 14f)
                    }
                    visibility = View.GONE
                }

                val topRow = LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(Md3.dp(c, 12f), Md3.dp(c, 4f), Md3.dp(c, 8f), Md3.dp(c, 4f))
                    addView(Md3.text(c, "枪械追踪", 12f, Md3.onSurface).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(gear)
                    addView(swSub)
                }
                wrap.addView(topRow)

                val divider = View(c).apply {
                    background = ColorDrawable(0x1C000000)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Md3.dp(c, 1f)).apply {
                        marginStart = Md3.dp(c, 10f)
                        marginEnd = Md3.dp(c, 10f)
                    }
                    visibility = View.GONE
                }
                wrap.addView(divider)

                val setArea = LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(Md3.dp(c, 12f), Md3.dp(c, 6f), Md3.dp(c, 8f), Md3.dp(c, 8f))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0)
                    visibility = View.GONE
                }

                // 枪械追踪线 (屏心连到圈内锁定敌人头部; 与 ESP 总开关独立显示)
                val swSubLine = Md3Switch(c).apply { setOn(EspCtl.subLine, false) }
                setArea.addView(capRow(c, "枪械追踪线", swSubLine) { on ->
                    EspCtl.setSub(line = on)
                })

                // 枪械类型 (单选; 0=普通枪械 LDR补丁 1=模组枪械 hook; 点击就地重绘高亮)
                var chipN: TextView? = null
                var chipM: TextView? = null
                fun styleChip(tv: TextView?, type: Int) {
                    val sel = EspCtl.subType == type
                    tv?.setTextColor(if (sel) Md3.onPrimaryContainer else Md3.onSurfaceVariant)
                    tv?.background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = Md3.dp(c, 8f).toFloat()
                        if (sel) {
                            setColor(Md3.primaryContainer)
                            setStroke(Md3.dp(c, 1f), Md3.primary)
                        } else {
                            setColor(0x00000000)
                            setStroke(Md3.dp(c, 1f), Md3.outline)
                        }
                    }
                }
                fun typeChip(txt: String, type: Int): TextView =
                    Md3.text(c, txt, 11f, Md3.onSurfaceVariant).apply {
                        setPadding(Md3.dp(c, 10f), Md3.dp(c, 4f), Md3.dp(c, 10f), Md3.dp(c, 4f))
                        background = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = Md3.dp(c, 8f).toFloat()
                            setStroke(Md3.dp(c, 1f), Md3.outline)
                            setColor(0x00000000)
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = Md3.dp(c, 4f)
                        }
                        setOnClickListener {
                            haptic(this)
                            EspCtl.setSub(type = type)
                            styleChip(chipN, 0)
                            styleChip(chipM, 1)
                            Island.action(c, if (type == 1) "模组枪械" else "普通枪械")
                        }
                    }
                val typeGroup = LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = LayerDrawable(arrayOf(
                        Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                        Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                            setStroke(Md3.dp(c, 1f), 0x38000000)
                        }
                    ))
                    setPadding(Md3.dp(c, 6f), Md3.dp(c, 2f), Md3.dp(c, 6f), Md3.dp(c, 2f))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = Md3.dp(c, 2f)
                    }
                    addView(Md3.text(c, "枪械类型", 11f, Md3.onSurfaceVariant).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    val n = typeChip("普通", 0)
                    val m = typeChip("模组【穿墙】", 1)
                    chipN = n
                    chipM = m
                    addView(n)
                    addView(m)
                }
                styleChip(chipN, 0)
                styleChip(chipM, 1)
                setArea.addView(typeGroup)

                // 参数组: 标签+滑块 圈入深色背景卡 (同自瞄页样式)
                fun paramGroup3(title: String, value: TextView, slider: Md3Slider) {
                    val group = LinearLayout(c).apply {
                        orientation = LinearLayout.VERTICAL
                        background = LayerDrawable(arrayOf(
                            Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                            Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                                setStroke(Md3.dp(c, 1f), 0x38000000)
                            }
                        ))
                        setPadding(Md3.dp(c, 6f), Md3.dp(c, 2f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = Md3.dp(c, 2f)
                        }
                    }
                    val titleRow = LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(Md3.text(c, title, 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        addView(value)
                    }
                    group.addView(titleRow)
                    group.addView(slider)
                    setArea.addView(group)
                }
                val rangeVal = Md3.text(c, "${EspCtl.subRadius.toInt()}px", 11f, Md3.primary)
                val rSlider = Md3Slider(c).apply {
                    setValue(((EspCtl.subRadius - 120f) / 680f * 100f).coerceIn(0f, 100f))
                    onValueChange = { v ->
                        val r = 120f + 680f * v / 100f
                        EspCtl.setSub(radiusPx = r)
                        rangeVal.text = "${r.toInt()}px"
                    }
                    valueLabel = { v -> ((120f + 680f * v / 100f).toInt()).toString() }
                }
                paramGroup3("追踪范围", rangeVal, rSlider)
                wrap.addView(setArea)

                var setOpen = false
                var natH = 0
                var heightAnim: ValueAnimator? = null
                var openGen = 0

                fun animateHeight(to: Int, gen: Int) {
                    heightAnim?.cancel()
                    val from = (setArea.layoutParams as LinearLayout.LayoutParams).height
                    heightAnim = ValueAnimator.ofInt(from, to).apply {
                        duration = 300
                        interpolator = DecelerateInterpolator()
                        addUpdateListener {
                            (setArea.layoutParams as LinearLayout.LayoutParams).height =
                                (it.animatedValue as Int).coerceAtLeast(0)
                            setArea.requestLayout()
                        }
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                if (gen == openGen && to == 0) setArea.visibility = View.GONE
                            }
                        })
                        start()
                    }
                }

                fun openSettings() {
                    openGen++
                    heightAnim?.cancel()
                    setArea.visibility = View.VISIBLE
                    gear.animate().rotation(90f).setDuration(300)
                        .setInterpolator(DecelerateInterpolator()).start()
                    divider.alpha = 0f
                    divider.visibility = View.VISIBLE
                    divider.animate().alpha(1f).setDuration(260).start()
                    if (natH == 0) {
                        setArea.post {
                            val wSpec = View.MeasureSpec.makeMeasureSpec(
                                if (wrap.width > 0) wrap.width else Md3.dp(c, 300f),
                                View.MeasureSpec.EXACTLY)
                            setArea.measure(wSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                            natH = setArea.measuredHeight
                            if (natH <= 0) natH = Md3.dp(c, 190f)
                            (setArea.layoutParams as LinearLayout.LayoutParams).height = 0
                            setArea.requestLayout()
                            if (setOpen) animateHeight(natH, openGen)
                        }
                    } else {
                        animateHeight(natH, openGen)
                    }
                }

                fun closeSettings() {
                    openGen++
                    heightAnim?.cancel()
                    gear.animate().rotation(0f).setDuration(300)
                        .setInterpolator(DecelerateInterpolator()).start()
                    divider.animate().alpha(0f).setDuration(200)
                        .withEndAction { divider.visibility = View.GONE }.start()
                    animateHeight(0, openGen)
                }

                swSub.onCheckedChangeListener = { on ->
                    EspCtl.setSub(on = on)
                    Island.notice(c, "枪械追踪", on)
                    // 圈/追线与 ESP 总开关独立: 无总开关也要装配数据源并保活渲染层
                    if (on && !EspCtl.flags[0]) EspCtl.ensure()
                    OverlayHost.refresh()
                    if (on) {
                        gear.visibility = View.VISIBLE
                        gear.rotation = 0f
                    } else {
                        gear.visibility = View.GONE
                        setOpen = false
                        closeSettings()
                    }
                }
                gear.setOnClickListener {
                    haptic(it)
                    setOpen = !setOpen
                    if (setOpen) openSettings() else closeSettings()
                }

                pA.addView(wrap)

                // 过滤队友 (与绘制/瞄准页同步, flags[9])
                pA.addView(espSwitchRow(c, "过滤队友", 9))
                root.addView(pA)
                // 页2 枪械属性: 自动换弹/无限弹药(Lua) + 自动开火(native)
                addBattleFeature(pB, "自动换弹", FuncBridge.autoReloadOn, false,
                    { on -> FuncBridge.setAutoReload(on); Island.notice(c, "自动换弹", on) }, null)
                addBattleFeature(pB, "无限弹药", FuncBridge.unlimitedAmmoOn, false,
                    { on -> FuncBridge.setUnlimitedAmmo(on); Island.notice(c, "无限弹药", on) }, null)
                // 2026-11-05 按用户要求注释: 子弹穿墙 (模组子追的模组档自带穿墙)
                /*
                addBattleFeature(pB, "子弹穿墙", EspCtl.pierceOn, false,
                    { on -> EspCtl.setPierce(on); Island.notice(c, "子弹穿墙", on) }, null)
                */
                // 模组枪械五功能 (同梦喵 main.cpp 偏移原样)
                // 2026-11-08 按用户要求暂时注释: 原版枪械增强 (enh)
                /*
                addBattleFeature(pB, "原版枪械增强", EspCtl.gunEnhOn, false,
                    { on -> EspCtl.setGunMod("enh", on); Island.notice(c, "原版枪械增强", on) }, null)
                */
                addBattleFeature(pB, "模组枪械无后", EspCtl.gunNoRecoilOn, false,
                    { on -> EspCtl.setGunMod("noRecoil", on); Island.notice(c, "模组枪械无后", on) }, null)
                addBattleFeature(pB, "模组枪械聚点", EspCtl.gunFocusOn, false,
                    { on -> EspCtl.setGunMod("focus", on); Island.notice(c, "模组枪械聚点", on) }, null)
                addBattleFeature(pB, "模组枪械快速换弹", EspCtl.gunReloadOn, false,
                    { on -> EspCtl.setGunMod("reload", on); Island.notice(c, "模组枪械快速换弹", on) }, null)
                addBattleFeature(pB, "模组枪械全自动", EspCtl.gunAutoOn, false,
                    { on -> EspCtl.setGunMod("auto", on); Island.notice(c, "模组枪械全自动", on) }, null)
                root.addView(pB)
                styleTabT(tabViewsT[0], true)
                styleTabT(tabViewsT[1], false)
            }

            4 -> {   // 战斗: 三页 (战斗区域 | 坐标追踪 | 相机追踪), 仅标题栏切页, 无横滑
                val tabs = arrayOf("战斗区域", "坐标追踪", "相机追踪")
                val tabViews = arrayListOf<TextView>()
                val tabRow = LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = Md3.rounded(0x0F000000, Md3.dp(c, 10f).toFloat())
                    setPadding(Md3.dp(c, 3f), Md3.dp(c, 2f), Md3.dp(c, 3f), Md3.dp(c, 2f))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = Md3.dp(c, 4f)
                    }
                }
                fun styleTab(tv: TextView, sel: Boolean) {
                    tv.setTextColor(if (sel) Md3.onPrimaryContainer else Md3.onSurfaceVariant)
                    tv.background = if (sel)
                        Md3.rounded(Md3.primaryContainer, Md3.dp(c, 8f).toFloat())
                    else null
                }
                // 三页内容 (宽度 = 内容区全宽, 自适应不裁剪; 每次只显示一页)
                val panes = arrayListOf<LinearLayout>()
                fun pane(vis: Boolean): LinearLayout {
                    val p = LinearLayout(c).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = Md3.dp(c, 2f)
                        }
                        visibility = if (vis) View.VISIBLE else View.GONE
                    }
                    panes.add(p)
                    return p
                }
                val p0 = pane(true)
                val p1 = pane(false)
                val p2 = pane(false)

                fun goPane(i: Int) {
                    if (i !in panes.indices) return
                    for (t in tabViews.indices) styleTab(tabViews[t], t == i)
                    for (pi in panes.indices) {
                        val pv = panes[pi]
                        pv.animate().cancel()
                        pv.alpha = 1f
                        pv.visibility = if (pi == i) View.VISIBLE else View.GONE
                    }
                    panes[i].alpha = 0f
                    panes[i].animate().alpha(1f).setDuration(150).start()
                }
                for (ti in tabs.indices) {
                    val tv = Md3.text(c, tabs[ti], 11f, Md3.onSurfaceVariant).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, Md3.dp(c, 5f), 0, Md3.dp(c, 5f))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener {
                            haptic(this)
                            goPane(ti)
                        }
                    }
                    tabViews.add(tv)
                    tabRow.addView(tv)
                }
                root.addView(tabRow)

                // 页1 战斗区域 (注入引擎 Lua 五功能; 梦喵对接: 功能文件+参数文件)
                // 自动攻击 (主用: libDLL 注入库, 离情梦目录开关 + 速度/范围参数文件;
                // 备用: 远程调用攻击函数, 不依赖注入, 固定 5m 半径)
                // 速度/范围语义同梦喵: 自动攻击速度=秒/次(0.01~1.5 默认 0.3),
                // 自动攻击范围=米(1~5, 文件写 ×100 整数 100~500); 备用速度=秒/次(1.0~2.0)
                addBattleFeature(p0, "自动攻击", FuncBridge.autoAttackOn, true,
                    { on -> FuncBridge.setAutoAttack(on); Island.notice(c, "自动攻击", on) }) { area, _ ->
                    // 主用/备用 分段切换 (单按钮对半, 选中侧高亮; 梦喵 SubTabRow 语义)
                    val modeRow = LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        background = Md3.rounded(0x16000000, Md3.dp(c, 14f).toFloat())
                        setPadding(Md3.dp(c, 2f), Md3.dp(c, 2f), Md3.dp(c, 2f), Md3.dp(c, 2f))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, Md3.dp(c, 30f)).apply {
                            topMargin = Md3.dp(c, 8f)
                            bottomMargin = Md3.dp(c, 2f)
                        }
                    }
                    // 主用/备用内容容器 (按模式显隐)
                    val mainCfg = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
                    val bkCfg = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
                    lateinit var segMain: TextView
                    lateinit var segBk: TextView
                    fun refreshMode(m: Int) {
                        segMain.background = if (m == 0) Md3.rounded(Md3.primary, Md3.dp(c, 12f).toFloat()) else null
                        segBk.background = if (m == 1) Md3.rounded(Md3.primary, Md3.dp(c, 12f).toFloat()) else null
                        segMain.setTextColor(if (m == 0) Color.WHITE else Md3.onSurfaceVariant)
                        segBk.setTextColor(if (m == 1) Color.WHITE else Md3.onSurfaceVariant)
                        mainCfg.visibility = if (m == 0) View.VISIBLE else View.GONE
                        bkCfg.visibility = if (m == 1) View.VISIBLE else View.GONE
                    }
                    fun mkSeg(label: String, m: Int): TextView {
                        val tv = Md3.text(c, label, 12f,
                            if (FuncBridge.autoAttackMode == m) Color.WHITE else Md3.onSurfaceVariant)
                        tv.gravity = Gravity.CENTER
                        tv.background = if (FuncBridge.autoAttackMode == m)
                            Md3.rounded(Md3.primary, Md3.dp(c, 12f).toFloat()) else null
                        tv.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                        tv.setOnClickListener {
                            FuncBridge.setAutoAttackMode(m)
                            refreshMode(m)
                        }
                        return tv
                    }
                    segMain = mkSeg("主用", 0)
                    segBk = mkSeg("备用", 1)
                    modeRow.addView(segMain)
                    modeRow.addView(segBk)
                    area.addView(modeRow)
                    refreshMode(FuncBridge.autoAttackMode)
                    // 速度 (秒/次, 最快 0.01, 默认 0.3)
                    // 注意: 文本引用必须在构造时持有 (动态 getChildAt(childCount-2) 会取到
                    // 后添加的范围行 → 拖速度会改范围显示)
                    var speedVal: TextView? = null
                    mainCfg.addView(LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = LayerDrawable(arrayOf(
                            Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                            Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                                setStroke(Md3.dp(c, 1f), 0x38000000)
                            }
                        ))
                        setPadding(Md3.dp(c, 6f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                        addView(Md3.text(c, "攻击速度(秒/次)", 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        speedVal = Md3.text(c,
                            String.format(java.util.Locale.US, "%.2f", FuncBridge.attackSpeed),
                            11f, Md3.primary)
                        addView(speedVal)
                    })
                    val sSpeed = Md3Slider(c).apply {
                        setValue(((FuncBridge.attackSpeed - 0.01f) / 1.49f * 100f).coerceIn(0f, 100f))
                        onValueChange = { v ->
                            val spd = 0.01f + 1.49f * v / 100f
                            FuncBridge.setAutoAttackSpeed(spd)
                            speedVal?.text = String.format(java.util.Locale.US, "%.2f", spd)
                        }
                        valueLabel = { v ->
                            String.format(java.util.Locale.US, "%.2f", 0.01f + 1.49f * v / 100f)
                        }
                    }
                    mainCfg.addView(sSpeed)
                    // 范围 (米)
                    var rangeVal: TextView? = null
                    mainCfg.addView(LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = LayerDrawable(arrayOf(
                            Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                            Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                                setStroke(Md3.dp(c, 1f), 0x38000000)
                            }
                        ))
                        setPadding(Md3.dp(c, 6f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                        addView(Md3.text(c, "攻击范围(米)", 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        rangeVal = Md3.text(c,
                            String.format(java.util.Locale.US, "%.0f", FuncBridge.attackRangeM),
                            11f, Md3.primary)
                        addView(rangeVal)
                    })
                    val sRange = Md3Slider(c).apply {
                        setValue(((FuncBridge.attackRangeM - 1f) / 4f * 100f).coerceIn(0f, 100f))
                        onValueChange = { v ->
                            val m = 1f + 4f * v / 100f
                            FuncBridge.setAutoAttackRange(m)
                            rangeVal?.text = String.format(java.util.Locale.US, "%.0f", m)
                        }
                        valueLabel = { v ->
                            String.format(java.util.Locale.US, "%.0f", 1f + 4f * v / 100f)
                        }
                    }
                    mainCfg.addView(sRange)
                    area.addView(mainCfg)
                    // 备用: 速度 (秒/次, 1.0~2.0; 攻击半径固定 5m)
                    var bkSpeedVal: TextView? = null
                    bkCfg.addView(LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = LayerDrawable(arrayOf(
                            Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                            Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                                setStroke(Md3.dp(c, 1f), 0x38000000)
                            }
                        ))
                        setPadding(Md3.dp(c, 6f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                        addView(Md3.text(c, "攻击速度(秒/次)", 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        bkSpeedVal = Md3.text(c,
                            String.format(java.util.Locale.US, "%.1f", FuncBridge.autoAttackBkSpeed),
                            11f, Md3.primary)
                        addView(bkSpeedVal)
                    })
                    val sBkSpeed = Md3Slider(c).apply {
                        setValue(((FuncBridge.autoAttackBkSpeed - 1f) * 100f).coerceIn(0f, 100f))
                        onValueChange = { v ->
                            val spd = 1f + v / 100f
                            FuncBridge.setAutoAttackBkSpeed(spd)
                            bkSpeedVal?.text = String.format(java.util.Locale.US, "%.1f", spd)
                        }
                        valueLabel = { v ->
                            String.format(java.util.Locale.US, "%.1f", 1f + v / 100f)
                        }
                    }
                    bkCfg.addView(sBkSpeed)
                    // 备用: 距离 (米, 1~5)
                    var bkRangeVal: TextView? = null
                    bkCfg.addView(LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = LayerDrawable(arrayOf(
                            Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                            Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                                setStroke(Md3.dp(c, 1f), 0x38000000)
                            }
                        ))
                        setPadding(Md3.dp(c, 6f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                        addView(Md3.text(c, "攻击距离(米)", 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        bkRangeVal = Md3.text(c,
                            String.format(java.util.Locale.US, "%.0f", FuncBridge.autoAttackBkRangeM),
                            11f, Md3.primary)
                        addView(bkRangeVal)
                    })
                    val sBkRange = Md3Slider(c).apply {
                        setValue(((FuncBridge.autoAttackBkRangeM - 1f) / 4f * 100f).coerceIn(0f, 100f))
                        onValueChange = { v ->
                            val m = 1f + 4f * v / 100f
                            FuncBridge.setAutoAttackBkRange(m)
                            bkRangeVal?.text = String.format(java.util.Locale.US, "%.0f", m)
                        }
                        valueLabel = { v ->
                            String.format(java.util.Locale.US, "%.0f", 1f + 4f * v / 100f)
                        }
                    }
                    bkCfg.addView(sBkRange)
                    area.addView(bkCfg)
                }
                // 发包攻击 (梦喵同款: libDLL 读 新版自动攻击/新版自动攻击速度/目标 三个文件;
                // 速度 0.001~1 秒/次, 锁定迷你号留空 = 自动打血量最低)
                addBattleFeature(p0, "发包攻击", FuncBridge.packetAttackOn, true,
                    { on -> FuncBridge.setPacketAttack(on); Island.notice(c, "发包攻击", on) }) { area, reg ->
                    var pkSpeedVal: TextView? = null
                    area.addView(LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = LayerDrawable(arrayOf(
                            Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                            Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                                setStroke(Md3.dp(c, 1f), 0x38000000)
                            }
                        ))
                        setPadding(Md3.dp(c, 6f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                        addView(Md3.text(c, "发包速度(秒/次)", 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        pkSpeedVal = Md3.text(c,
                            String.format(java.util.Locale.US, "%.3f", FuncBridge.packetSpeed),
                            11f, Md3.primary)
                        addView(pkSpeedVal)
                    })
                    val sPkSpeed = Md3Slider(c).apply {
                        setValue(((FuncBridge.packetSpeed - 0.001f) / 0.999f * 100f).coerceIn(0f, 100f))
                        onValueChange = { v ->
                            val spd = 0.001f + 0.999f * v / 100f
                            FuncBridge.setPacketAttackSpeed(spd)
                            pkSpeedVal?.text = String.format(java.util.Locale.US, "%.3f", spd)
                        }
                        valueLabel = { v ->
                            String.format(java.util.Locale.US, "%.3f", 0.001f + 0.999f * v / 100f)
                        }
                    }
                    area.addView(sPkSpeed)
                    // 锁定迷你号 (留空 = 自动打血量最低)
                    val box = LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = LayerDrawable(arrayOf(
                            Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                            Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                                setStroke(Md3.dp(c, 1f), 0x38000000)
                            }
                        ))
                        setPadding(Md3.dp(c, 6f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                        addView(Md3.text(c, "锁定迷你号", 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                    }
                    val edit = EditText(c).apply {
                        setText(FuncBridge.packetTarget)
                        setTextColor(Md3.onSurface)
                        textSize = 12f
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        minEms = 16
                        gravity = Gravity.CENTER_VERTICAL
                        setHorizontallyScrolling(false)
                        hint = "留空 = 打血量最低"
                        setHintTextColor(0x66000000)
                        background = Md3.rounded(0x14000000, Md3.dp(c, 8f).toFloat())
                        setPadding(Md3.dp(c, 8f), Md3.dp(c, 4f), Md3.dp(c, 8f), Md3.dp(c, 4f))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = Md3.dp(c, 6f)
                        }
                        setOnClickListener {
                            haptic(this)
                            overlayInputFocus(this, true)
                        }
                        setOnFocusChangeListener { v, has ->
                            if (!has) overlayInputFocus(v as EditText, false)
                        }
                    }
                    val ok = TextView(c).apply {
                        text = "确定"
                        textSize = 11f
                        gravity = Gravity.CENTER
                        setTextColor(0xFFFFFFFF.toInt())
                        background = Md3.rounded(Md3.primary, Md3.dp(c, 8f).toFloat())
                        setPadding(Md3.dp(c, 10f), Md3.dp(c, 4f), Md3.dp(c, 10f), Md3.dp(c, 4f))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = Md3.dp(c, 6f)
                        }
                        setOnClickListener {
                            haptic(this)
                            FuncBridge.setPacketAttackTarget(edit.text.toString())
                            overlayInputFocus(edit, false)
                        }
                        bindImeDone(edit) { FuncBridge.setPacketAttackTarget(edit.text.toString()); true }
                    }
                    reg(edit)
                    box.addView(edit)
                    box.addView(ok)
                    area.addView(box)
                }
                // 自动切刀: 齿轮展开 切刀延迟拖动条 (10~500ms, 实时写文件)
                addBattleFeature(p0, "自动切刀", FuncBridge.cuttingOn, true,
                    { on -> FuncBridge.setCutting(on); Island.notice(c, "自动切刀", on) }) { area, _ ->
                    area.addView(LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = LayerDrawable(arrayOf(
                            Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                            Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                                setStroke(Md3.dp(c, 1f), 0x38000000)
                            }
                        ))
                        setPadding(Md3.dp(c, 6f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                        addView(Md3.text(c, "切刀延迟", 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        val valTxt = Md3.text(c, "${FuncBridge.cuttingDelayMs.toIntOrNull() ?: 60}ms",
                            11f, Md3.primary)
                        addView(valTxt)
                    })
                    val cur = (FuncBridge.cuttingDelayMs.toIntOrNull() ?: 60).coerceIn(10, 500)
                    val sCut = Md3Slider(c).apply {
                        setValue((cur - 10) / 490f * 100f)
                        onValueChange = { v ->
                            val ms = 10 + (490 * v / 100f).toInt()
                            FuncBridge.setCuttingDelay(ms.toString())
                            (area.getChildAt(0) as LinearLayout).let { row ->
                                (row.getChildAt(1) as TextView).text = "${ms}ms"
                            }
                        }
                        valueLabel = { v -> (10 + (490 * v / 100f).toInt()).toString() }
                    }
                    area.addView(sCut)
                }
                // 传送光环
                addBattleFeature(p0, "传送光环", FuncBridge.teleAuraOn, false,
                    { on -> FuncBridge.setTeleAura(on); Island.notice(c, "传送光环", on) }, null)
                // 指定附身: 齿轮展开 迷你号输入框 (内联唤起输入法)
                addBattleFeature(p0, "指定附身", FuncBridge.possessOn, true,
                    { on -> FuncBridge.setPossess(on); Island.notice(c, "指定附身", on) }) { area, reg ->
                    val box = LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = LayerDrawable(arrayOf(
                            Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                            Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                                setStroke(Md3.dp(c, 1f), 0x38000000)
                            }
                        ))
                        setPadding(Md3.dp(c, 6f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                        addView(Md3.text(c, "附身迷你号", 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                    }
                    val edit = EditText(c).apply {
                        setText(FuncBridge.possessUin)
                        setTextColor(Md3.onSurface)
                        textSize = 12f
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        // 预留 12 位迷你号空间: 满 12 位仍完整显示不挤压
                        minEms = 22
                        gravity = Gravity.CENTER_VERTICAL
                        setHorizontallyScrolling(false)
                        hint = "12 位迷你号"
                        setHintTextColor(0x66000000)
                        background = Md3.rounded(0x14000000, Md3.dp(c, 8f).toFloat())
                        setPadding(Md3.dp(c, 8f), Md3.dp(c, 4f), Md3.dp(c, 8f), Md3.dp(c, 4f))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = Md3.dp(c, 6f)
                        }
                        // 点击唤起输入法 (临时去 NOT_FOCUSABLE + NOT_TOUCH_MODAL)
                        setOnClickListener {
                            haptic(this)
                            overlayInputFocus(this, true)
                        }
                        // 失焦即释放窗口焦点, 防止吞屏
                        setOnFocusChangeListener { v, has ->
                            if (!has) overlayInputFocus(v as EditText, false)
                        }
                    }
                    val ok = TextView(c).apply {
                        text = "确定"
                        textSize = 11f
                        gravity = Gravity.CENTER
                        setTextColor(0xFFFFFFFF.toInt())
                        background = Md3.rounded(Md3.primary, Md3.dp(c, 8f).toFloat())
                        setPadding(Md3.dp(c, 10f), Md3.dp(c, 4f), Md3.dp(c, 10f), Md3.dp(c, 4f))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = Md3.dp(c, 6f)
                        }
                        setOnClickListener {
                            haptic(this)
                            val v = edit.text.toString()
                            if (v.toLongOrNull() != null) {
                                FuncBridge.setPossessUin(v)
                                overlayInputFocus(edit, false)
                            }
                        }
                        bindImeDone(edit) {
                            val v = edit.text.toString()
                            if (v.toLongOrNull() != null) { FuncBridge.setPossessUin(v); true } else false
                        }
                    }
                    reg(edit)
                    box.addView(edit)
                    box.addView(ok)
                    area.addView(box)
                }

                // 页2 坐标追踪                p0.addView(rPs)

                // 页2 坐标追踪 (传送自身到目标头顶)
                p1.addView(trackGlassCard("坐标追踪", "传送自身到目标头顶 · 目标死亡自动换", EspCtl.ctrkOn,
                    { EspCtl.ctrkMode == it }, { EspCtl.setCoord(mode = it) }) { on ->
                    EspCtl.setCoord(on = on)
                    Island.notice(c, "坐标追踪", on)
                    if (on && !EspCtl.flags[0]) EspCtl.ensure()
                })

                // 页3 相机追踪 (视角/相机锁定跟随目标)
                p2.addView(trackGlassCard("相机追踪", "视角锁定跟随目标 · 关闭还原", EspCtl.vtrkOn,
                    { EspCtl.vtrkMode == it }, { EspCtl.setSight(mode = it) }) { on ->
                    EspCtl.setSight(on = on)
                    Island.notice(c, "相机追踪", on)
                    if (on && !EspCtl.flags[0]) EspCtl.ensure()
                })

                root.addView(p0)
                root.addView(p1)
                root.addView(p2)
                styleTab(tabViews[0], true)
                styleTab(tabViews[1], false)
                styleTab(tabViews[2], false)
            }
            5 -> {   // 娱乐 (Lua 引擎: 16 解锁全喷漆 / 20·120 自动发言 / 37·137 绿色发言 /
                //          44 临时改名 / 78 追踪房间 — mansui.lua v0.2)
                // 齿轮输入行: 标签 + 输入框 + 确定 (确定触发动作并收起键盘)
                fun inputOkRow(area: LinearLayout, label: String, hint: String, initial: String,
                               numeric: Boolean, reg: (EditText) -> Unit,
                               onOk: (String) -> Unit) {
                    val box = LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = LayerDrawable(arrayOf(
                            Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                            Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                                setStroke(Md3.dp(c, 1f), 0x38000000)
                            }
                        ))
                        setPadding(Md3.dp(c, 6f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                        addView(Md3.text(c, label, 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                marginEnd = Md3.dp(c, 6f)
                            }
                        })
                    }
                    val edit = EditText(c).apply {
                        setText(initial)
                        setTextColor(Md3.onSurface)
                        textSize = 12f
                        if (numeric) {
                            inputType = android.text.InputType.TYPE_CLASS_NUMBER
                            // 12 位迷你号完整显示不挤压 (同战斗页附身输入框)
                            minEms = 22
                        }
                        gravity = Gravity.CENTER_VERTICAL
                        setHint(hint)
                        setHintTextColor(0x66000000)
                        background = Md3.rounded(0x14000000, Md3.dp(c, 8f).toFloat())
                        setPadding(Md3.dp(c, 8f), Md3.dp(c, 4f), Md3.dp(c, 8f), Md3.dp(c, 4f))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener {
                            haptic(this)
                            overlayInputFocus(this, true)
                        }
                        setOnFocusChangeListener { v, has ->
                            if (!has) overlayInputFocus(v as EditText, false)
                        }
                    }
                    reg(edit)
                    val ok = TextView(c).apply {
                        text = "确定"
                        textSize = 11f
                        gravity = Gravity.CENTER
                        setTextColor(0xFFFFFFFF.toInt())
                        background = Md3.rounded(Md3.primary, Md3.dp(c, 8f).toFloat())
                        setPadding(Md3.dp(c, 10f), Md3.dp(c, 4f), Md3.dp(c, 10f), Md3.dp(c, 4f))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = Md3.dp(c, 6f)
                        }
                        setOnClickListener {
                            haptic(this)
                            overlayInputFocus(edit, false)
                            onOk(edit.text.toString())
                        }
                        bindImeDone(edit) { onOk(edit.text.toString()); true }
                    }
                    box.addView(edit)
                    box.addView(ok)
                    area.addView(box)
                }
                // 自动发言 (20/120): 开关 + 齿轮填内容; 开着时齿轮改内容点确定 = 以新内容重启循环
                // ══ 发包音效: 齿轮展开 → 搜索 + 列表, 点行即全房播放 (Lua 193) ══
                val soundList = soundDb()
                addActionCard(root, "发包音效", {}) { area, _ ->
                    var doSoundSearch: (() -> Unit)? = null
                    val sEdit = EditText(c).apply {
                        textSize = 11f
                        gravity = Gravity.CENTER_VERTICAL
                        setTextColor(Md3.onSurface)
                        setHint("搜索音效名称或分类")
                        setHintTextColor(0x66000000)
                        background = Md3.rounded(0x16000000, Md3.dp(c, 10f).toFloat())
                        setPadding(Md3.dp(c, 10f), Md3.dp(c, 5f), Md3.dp(c, 10f), Md3.dp(c, 5f))
                        layoutParams = LinearLayout.LayoutParams(0, Md3.dp(c, 30f), 1f)
                        setOnClickListener {
                            haptic(this)
                            overlayInputFocus(this, true)
                        }
                        setOnFocusChangeListener { v, has ->
                            if (!has) overlayInputFocus(v as EditText, false)
                        }
                    }
                    bindImeDone(sEdit) { doSoundSearch?.invoke(); true }
                    area.addView(LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, 0, 0, Md3.dp(c, 3f))
                        addView(sEdit)
                        addView(TextView(c).apply {
                            text = "搜索"
                            textSize = 11f
                            gravity = Gravity.CENTER
                            setTextColor(0xFFFFFFFF.toInt())
                            background = Md3.ripple(Md3.rounded(Md3.primary, Md3.dp(c, 10f).toFloat()))
                            setPadding(Md3.dp(c, 12f), 0, Md3.dp(c, 12f), 0)
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, Md3.dp(c, 30f)).apply {
                                marginStart = Md3.dp(c, 4f)
                            }
                            setOnClickListener {
                                haptic(this)
                                overlayInputFocus(sEdit, false)
                                doSoundSearch?.invoke()
                            }
                        })
                    })
                    val sList = LinearLayout(c).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    }
                    area.addView(sList)
                    val sEmpty = Md3.text(c, "未找到音效", 11f, Md3.onSurfaceVariant)
                    area.addView(sEmpty)
                    sEmpty.visibility = View.GONE
                    fun renderSounds() {
                        val kw = sEdit.text.toString().trim()
                        val hit = ArrayList<Triple<String, String, String>>(80)
                        for (it in soundList) {
                            if (kw.isEmpty() || it.first.contains(kw) || it.second.contains(kw)) {
                                hit.add(it)
                                if (hit.size >= 80) break
                            }
                        }
                        sList.removeAllViews()
                        if (hit.isEmpty()) {
                            sEmpty.visibility = View.VISIBLE
                            return
                        }
                        sEmpty.visibility = View.GONE
                        for ((cat, name, path) in hit) {
                            val rowV = LinearLayout(c).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                background = Md3.rounded(0x0F000000, Md3.dp(c, 10f).toFloat())
                                setPadding(Md3.dp(c, 8f), Md3.dp(c, 2f), Md3.dp(c, 6f), Md3.dp(c, 2f))
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                    topMargin = Md3.dp(c, 1f)
                                }
                            }
                            rowV.addView(Md3.text(c, name, 11f, Md3.onSurface).apply {
                                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                                maxLines = 1
                                ellipsize = android.text.TextUtils.TruncateAt.END
                            })
                            rowV.addView(Md3.text(c, cat, 9f, Md3.onSurfaceVariant).apply {
                                setPadding(0, 0, Md3.dp(c, 6f), 0)
                            })
                            rowV.addView(TextView(c).apply {
                                text = "播放"
                                textSize = 10f
                                gravity = Gravity.CENTER
                                setTextColor(0xFFFFFFFF.toInt())
                                background = Md3.ripple(Md3.rounded(Md3.primary, Md3.dp(c, 8f).toFloat()))
                                setPadding(Md3.dp(c, 10f), 0, Md3.dp(c, 10f), 0)
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT, Md3.dp(c, 24f))
                                setOnClickListener {
                                    haptic(this)
                                    FuncBridge.sendSound(path)
                                    Island.action(c, "发包音效")
                                }
                            })
                            sList.addView(rowV)
                        }
                    }
                    doSoundSearch = {
                        overlayInputFocus(sEdit, false)
                        renderSounds()
                    }
                    renderSounds()
                }
                addBattleFeature(root, "自动发言", FuncBridge.autoSpeakOn, true,
                    { on ->
                        FuncBridge.setAutoSpeak(on, FuncBridge.speakText)
                        Island.notice(c, "自动发言", on)
                    }) { area, reg ->
                    inputOkRow(area, "发言内容", "输入发言文字", FuncBridge.speakText, false, reg) { v ->
                        FuncBridge.updateSpeakText(v)
                    }
                }
                // 绿色发言 (37/137): 纯开关
                addBattleFeature(root, "绿色发言", FuncBridge.greenChatOn, false,
                    { on ->
                        FuncBridge.setGreenChat(on)
                        Island.notice(c, "绿色发言", on)
                    }, null)
                // 临时改名 (44): 齿轮输入新名字 → 确定触发
                addActionCard(root, "临时改名",
                    { FuncBridge.renameTo(FuncBridge.renameText) }) { area, reg ->
                    inputOkRow(area, "新名字", "输入新名字", FuncBridge.renameText, false, reg) { v ->
                        FuncBridge.renameTo(v)
                    }
                }
                // 解锁全喷漆 (16): 整卡点击直接执行
                addActionCard(root, "解锁全喷漆", { FuncBridge.unlockSprays() }, null)
                // 追踪房间 (78): 齿轮输入目标迷你号 → 确定触发 (Lua 侧仅大厅生效)
                addActionCard(root, "追踪房间",
                    { FuncBridge.followRoom(FuncBridge.followUin) }) { area, reg ->
                    inputOkRow(area, "追踪迷你号", "输入目标迷你号", FuncBridge.followUin, true, reg) { v ->
                        FuncBridge.followRoom(v)
                    }
                }
            }
            6 -> {   // 物品 (四标题页: 联机取物 / 其他 / 解锁道具 / 物品查询)
                // ── 标题栏 (同战斗区样式) ──
                val itemTabs = arrayOf("其他", "解锁道具", "物品查询")
                val itemTabViews = arrayListOf<TextView>()
                val itemTabRow = LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = Md3.rounded(0x0F000000, Md3.dp(c, 10f).toFloat())
                    setPadding(Md3.dp(c, 3f), Md3.dp(c, 2f), Md3.dp(c, 3f), Md3.dp(c, 2f))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = Md3.dp(c, 4f)
                    }
                }
                fun styleItemTab(tv: TextView, sel: Boolean) {
                    tv.setTextColor(if (sel) Md3.onPrimaryContainer else Md3.onSurfaceVariant)
                    tv.background = if (sel)
                        Md3.rounded(Md3.primaryContainer, Md3.dp(c, 8f).toFloat())
                    else null
                }
                val itemPanes = arrayListOf<LinearLayout>()
                fun itemPane(vis: Boolean): LinearLayout {
                    val p = LinearLayout(c).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = Md3.dp(c, 2f)
                        }
                        visibility = if (vis) View.VISIBLE else View.GONE
                    }
                    itemPanes.add(p)
                    return p
                }

                val ipA = itemPane(true)    // 其他 (第一页)
                val ipB = itemPane(false)   // 解锁道具
                val ip1 = itemPane(false)
                fun goItemPane(i: Int) {
                    if (i !in itemPanes.indices) return
                    for (t in itemTabViews.indices) styleItemTab(itemTabViews[t], t == i)
                    for (pi in itemPanes.indices) {
                        val pv = itemPanes[pi]
                        pv.animate().cancel()
                        pv.alpha = 1f
                        pv.visibility = if (pi == i) View.VISIBLE else View.GONE
                    }
                    itemPanes[i].alpha = 0f
                    itemPanes[i].animate().alpha(1f).setDuration(150).start()
                }
                for (ti in itemTabs.indices) {
                    val tv = Md3.text(c, itemTabs[ti], 11f, Md3.onSurfaceVariant).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, Md3.dp(c, 5f), 0, Md3.dp(c, 5f))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener {
                            haptic(this)
                            goItemPane(ti)
                        }
                    }
                    itemTabViews.add(tv)
                    itemTabRow.addView(tv)
                }
                root.addView(itemTabRow)
                root.addView(ipA)
                root.addView(ipB)
                root.addView(ip1)
                for (ti in itemTabViews.indices) styleItemTab(itemTabViews[ti], ti == 0)

                // ══ 页1 联机取物 (Lua: 182 云服取物(填ID) / 185 刷取手持物品 / 12 面板取物 / 6 丢弃 / 13 编辑器; 编辑器填充 = native) ══
                // 联机取物 (182): 自定义物品ID (数量固定 1, 同梦之喵羽原版); 主按钮=立即取物, 齿轮=填 ID
                // ⚠ 2026-10-11: 主按钮必须先提交齿轮里当前输入的ID —— 否则用户"填了ID没点确定就点卡片"
                //   会把内存里的旧值(常常是空) 写回 云服取物ID 文件, lua 读到空 → 功能表现为"无效"
                // 2026-11-05 按用户要求注释停用: 联机取物 (Lua 182 云服取物)
                /*
                var cloudIdEdit: EditText? = null
                addActionCard(ipA, "联机取物", {
                    cloudIdEdit?.text?.toString()?.let { FuncBridge.setCloudTake(it) }
                    FuncBridge.cloudTake()
                }) { area, reg ->
                    fun fieldRow(label: String, init: String, hintTxt: String): EditText {
                        val row = LinearLayout(c).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            background = LayerDrawable(arrayOf(
                                Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                                Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                                    setStroke(Md3.dp(c, 1f), 0x38000000)
                                }
                            ))
                            setPadding(Md3.dp(c, 6f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                bottomMargin = Md3.dp(c, 4f)
                            }
                            addView(Md3.text(c, label, 11f, Md3.onSurfaceVariant).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                    marginEnd = Md3.dp(c, 6f)
                                }
                            })
                        }
                        val edit = EditText(c).apply {
                            setText(init)
                            setTextColor(Md3.onSurface)
                            textSize = 12f
                            inputType = android.text.InputType.TYPE_CLASS_NUMBER
                            minEms = 6
                            gravity = Gravity.CENTER_VERTICAL
                            setHint(hintTxt)
                            setHintTextColor(0x66000000)
                            background = Md3.rounded(0x14000000, Md3.dp(c, 8f).toFloat())
                            setPadding(Md3.dp(c, 8f), Md3.dp(c, 4f), Md3.dp(c, 8f), Md3.dp(c, 4f))
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                                marginStart = Md3.dp(c, 6f)
                            }
                            setOnClickListener {
                                haptic(this)
                                overlayInputFocus(this, true)
                            }
                            setOnFocusChangeListener { v, has ->
                                if (!has) overlayInputFocus(v as EditText, false)
                            }
                        }
                        reg(edit)
                        row.addView(edit)
                        area.addView(row)
                        return edit
                    }
                    val cidEdit = fieldRow("物品ID", FuncBridge.cloudItemId, "如 15056")
                    cloudIdEdit = cidEdit
                    fun commitCloud() {
                        FuncBridge.setCloudTake(cidEdit.text.toString())
                    }
                    // ⚠ 2026-10-11 修复「联机取物无效」:
                    //   addActionCard 带齿轮时, 标题行点击被写死为「同齿轮(展开/收起), 无独立按压触发」,
                    //   点卡片主体根本不会调用 onAction —— 所以动作必须落在设置区按钮上。
                    //   这也正是「联机踢人 / 武器皮肤」的既有约定: 确定 = 提交参数 + 立即执行。
                    val cOk = TextView(c).apply {
                        text = "取物"
                        textSize = 11f
                        gravity = Gravity.CENTER
                        setTextColor(0xFFFFFFFF.toInt())
                        background = Md3.rounded(Md3.primary, Md3.dp(c, 10f).toFloat())
                        setPadding(0, Md3.dp(c, 5f), 0, Md3.dp(c, 5f))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = Md3.dp(c, 2f)
                        }
                        setOnClickListener {
                            haptic(this)
                            overlayInputFocus(cidEdit, false)
                            commitCloud()
                            FuncBridge.cloudTake()
                        }
                    }
                    area.addView(cOk)
                    bindImeDone(cidEdit) {
                        commitCloud()
                        true
                    }
                }
                */
                // 刷取手持物品 (185): 现读手里那个物品ID, 面板不用填
                addActionCard(ipA, "刷取手持物品", { FuncBridge.spawnHeldItem() }, null)
                // 2026-11-05 按用户要求注释: 面板取物 (12) 与联机取物同组
                /*
                addActionCard(ipA, "面板取物", { FuncBridge.panelTake() }, null)
                */
                addActionCard(ipA, "丢弃物品", { FuncBridge.discardItems() }, null)
                // 编辑器填充 (native 补丁): 齿轮填 填充物ID
                addBattleFeature(ipA, "编辑器填充", EspCtl.edFillOn, true,
                    { on ->
                        EspCtl.setEdFill(on)
                        Island.notice(c, "编辑器填充", on)
                    }) { area, reg ->
                    fun fillRow(store: (String) -> Unit) {
                        val box = LinearLayout(c).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            background = LayerDrawable(arrayOf(
                                Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                                Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                                    setStroke(Md3.dp(c, 1f), 0x38000000)
                                }
                            ))
                            setPadding(Md3.dp(c, 6f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                            addView(Md3.text(c, "填充物ID", 11f, Md3.onSurfaceVariant).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                    marginEnd = Md3.dp(c, 6f)
                                }
                            })
                        }
                        val edit = EditText(c).apply {
                            setText(EspCtl.edFillVal.toString())
                            setTextColor(Md3.onSurface)
                            textSize = 12f
                            inputType = android.text.InputType.TYPE_CLASS_NUMBER
                            gravity = Gravity.CENTER_VERTICAL
                            setHint("默认 5")
                            setHintTextColor(0x66000000)
                            background = Md3.rounded(0x14000000, Md3.dp(c, 8f).toFloat())
                            setPadding(Md3.dp(c, 8f), Md3.dp(c, 4f), Md3.dp(c, 8f), Md3.dp(c, 4f))
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                                marginStart = Md3.dp(c, 6f)
                            }
                            setOnClickListener {
                                haptic(this)
                                overlayInputFocus(this, true)
                            }
                            setOnFocusChangeListener { v, has ->
                                if (!has) overlayInputFocus(v as EditText, false)
                            }
                        }
                        reg(edit)
                        val ok = TextView(c).apply {
                            text = "确定"
                            textSize = 11f
                            gravity = Gravity.CENTER
                            setTextColor(0xFFFFFFFF.toInt())
                            background = Md3.rounded(Md3.primary, Md3.dp(c, 8f).toFloat())
                            setPadding(Md3.dp(c, 10f), Md3.dp(c, 4f), Md3.dp(c, 10f), Md3.dp(c, 4f))
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                marginStart = Md3.dp(c, 6f)
                            }
                            setOnClickListener {
                                haptic(this)
                                overlayInputFocus(edit, false)
                                store(edit.text.toString())
                            }
                            bindImeDone(edit) { store(edit.text.toString()); true }
                        }
                        box.addView(edit)
                        box.addView(ok)
                        area.addView(box)
                    }
                    fillRow { v ->
                        EspCtl.setEdFill(EspCtl.edFillOn, v.toIntOrNull() ?: 5)
                    }
                }
                // 2026-11-05 按用户要求注释: 强制开启编辑器 (13) 与联机取物同组
                /*
                addActionCard(ipA, "强制开启编辑器", { FuncBridge.forceEditor() }, null)
                */

                // ══ 页2 其他 (游商子页 / Lua 14 添加琵琶 / 66 打开工匠台) ══
                addActionCard(ipA, "游商", {}) { area, _ ->
                    fun tBtn(label: String, tap: () -> Unit): TextView = TextView(c).apply {
                        text = label
                        textSize = 11f
                        gravity = Gravity.CENTER
                        setTextColor(0xFFFFFFFF.toInt())
                        background = Md3.rounded(Md3.primary, Md3.dp(c, 10f).toFloat())
                        setPadding(0, Md3.dp(c, 6f), 0, Md3.dp(c, 6f))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = Md3.dp(c, 3f)
                        }
                        setOnClickListener { haptic(this); tap() }
                    }
                    area.addView(tBtn("游商 0 元购") {
                        FuncBridge.buyTravelingTrader()
                        Island.action(c, "游商0元购")
                    })
                    area.addView(tBtn("查看库存") {
                        FuncBridge.traderStock()
                        Island.action(c, "游商库存")
                    })
                    val loopRow = LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(Md3.dp(c, 8f), Md3.dp(c, 4f), Md3.dp(c, 8f), Md3.dp(c, 4f))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = Md3.dp(c, 4f)
                        }
                    }
                    loopRow.addView(Md3.text(c, "循环游商", 11f, Md3.onSurface).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    val swLoop = Md3Switch(c).apply { setOn(FuncBridge.loopTraderOn, false) }
                    swLoop.setOnClickListener { v ->
                        haptic(v)
                        val nv = !FuncBridge.loopTraderOn
                        FuncBridge.setLoopTrader(nv)
                        swLoop.setOn(nv, true)
                        Island.notice(c, "循环游商", nv)
                    }
                    loopRow.addView(swLoop)
                    area.addView(loopRow)
                }
                addActionCard(ipA, "添加琵琶", { FuncBridge.addPipa() }, null)
                addActionCard(ipA, "打开工匠台", { FuncBridge.openToolbox() }, null)

                // ══ 页3 解锁道具 (Lua: 4 皮肤解锁 / 2 临时会员 / 3 免广告 / 9 武器皮肤) ══
                addActionCard(ipB, "皮肤获取解锁", { FuncBridge.unlockRoleSkins() }, null)
                addActionCard(ipB, "临时获取会员", { FuncBridge.takeMembership() }, null)
                addActionCard(ipB, "商城免广告", { FuncBridge.enableAdFree() }, null)
                // 武器皮肤: 齿轮展开 17 款列表, 点行即设置 (同梦喵 FoldSelect)
                addActionCard(ipB, "武器皮肤", { FuncBridge.setWeaponSkin(FuncBridge.weaponSkinId) }) { area, _ ->
                    val skins = arrayOf(
                        "浪花长枪" to "194", "炫光缤纷太刀" to "73", "流年刻度剑" to "158",
                        "银霜利刃" to "200", "莲花疾风剑" to "146", "炫光缤纷大刀" to "75",
                        "几把在燃烧" to "166", "赤龙渊鸣剑" to "174", "星空法阵剑" to "178",
                        "幻灵剑" to "177", "破碎炎魂剑" to "239", "光剑" to "240",
                        "粒子光刃" to "241", "神射之辉" to "248", "数学之剑" to "259",
                        "天秤裁决剑" to "261", "凛冬女皇剑" to "275")
                    val rowViews = arrayListOf<TextView>()
                    fun restyle() {
                        for ((i, tv) in rowViews.withIndex()) {
                            val sel = skins[i].second == FuncBridge.weaponSkinId
                            tv.setTextColor(if (sel) Md3.primary else Md3.onSurface)
                            tv.background = if (sel)
                                Md3.rounded(0x1A415C8E.toInt(), Md3.dp(c, 10f).toFloat())
                            else Md3.rounded(0x0F000000, Md3.dp(c, 10f).toFloat())
                        }
                    }
                    for ((name, id) in skins) {
                        val tv = Md3.text(c, name, 11f, Md3.onSurface).apply {
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(Md3.dp(c, 10f), Md3.dp(c, 6f), Md3.dp(c, 10f), Md3.dp(c, 6f))
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                topMargin = Md3.dp(c, 2f)
                            }
                            setOnClickListener {
                                haptic(this)
                                FuncBridge.setWeaponSkin(id)
                                restyle()
                            }
                        }
                        rowViews.add(tv)
                        area.addView(tv)
                    }
                    restyle()
                }

                // ══ 页4 物品查询 (搜索 物品ID.txt: ID 或名称; 点行展开, 长按复制 ID) ══
                val itemList = itemDb()
                val qEdit = EditText(c).apply {
                    textSize = 11f
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(Md3.onSurface)
                    setHint("输入物品ID或名称")
                    setHintTextColor(0x66000000)
                    background = Md3.rounded(0x16000000, Md3.dp(c, 10f).toFloat())
                    setPadding(Md3.dp(c, 10f), Md3.dp(c, 5f), Md3.dp(c, 10f), Md3.dp(c, 5f))
                    layoutParams = LinearLayout.LayoutParams(0, Md3.dp(c, 30f), 1f)
                    setOnClickListener {
                        haptic(this)
                        overlayInputFocus(this, true)
                    }
                    setOnFocusChangeListener { v, has ->
                        if (!has) overlayInputFocus(v as EditText, false)
                    }
                }
                // 搜索按钮/列表渲染在 renderQuery 声明后赋值 (Kotlin 局部函数不可前向引用)
                var doQuery: (() -> Unit)? = null
                bindImeDone(qEdit) { doQuery?.invoke(); true }
                ip1.addView(LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, 0, Md3.dp(c, 3f))
                    addView(qEdit)
                    addView(TextView(c).apply {
                        text = "搜索"
                        textSize = 11f
                        gravity = Gravity.CENTER
                        setTextColor(0xFFFFFFFF.toInt())
                        background = Md3.ripple(Md3.rounded(Md3.primary, Md3.dp(c, 10f).toFloat()))
                        setPadding(Md3.dp(c, 12f), 0, Md3.dp(c, 12f), 0)
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, Md3.dp(c, 30f)).apply {
                            marginStart = Md3.dp(c, 4f)
                        }
                        setOnClickListener {
                            haptic(this)
                            overlayInputFocus(qEdit, false)
                            doQuery?.invoke()
                        }
                    })
                })
                val qList = LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                ip1.addView(qList)
                val qEmpty = Md3.text(c, "未找到该物品", 11f, Md3.onSurfaceVariant)
                val expandedRows = HashSet<String>()
                fun renderQuery() {
                    val kw = qEdit.text.toString().trim()
                    val hit = ArrayList<Pair<String, String>>(128)
                    if (kw.isEmpty()) {
                        val n = Math.min(itemList.size, 200)
                        for (i in 0 until n) hit.add(itemList[i])
                    } else {
                        for (it in itemList) {
                            if (it.first.contains(kw) || it.second.contains(kw)) {
                                hit.add(it)
                                if (hit.size >= 200) break
                            }
                        }
                    }
                    qList.removeAllViews()
                    if (hit.isEmpty()) {
                        qEmpty.visibility = View.VISIBLE
                        return
                    }
                    qEmpty.visibility = View.GONE
                    val clip = c.getSystemService(Context.CLIPBOARD_SERVICE)
                            as? android.content.ClipboardManager
                    for ((id, name) in hit) {
                        val expanded = expandedRows.contains(id)
                        qList.addView(LinearLayout(c).apply {
                            orientation = LinearLayout.VERTICAL
                            background = if (expanded)
                                Md3.rounded(0x1A415C8E.toInt(), Md3.dp(c, 10f).toFloat())
                            else Md3.rounded(0x0F000000, Md3.dp(c, 10f).toFloat())
                            setPadding(Md3.dp(c, 8f), Md3.dp(c, 2f), Md3.dp(c, 8f), Md3.dp(c, 2f))
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                topMargin = Md3.dp(c, 1f)
                            }
                            setOnClickListener {
                                haptic(this)
                                if (expandedRows.contains(id)) expandedRows.remove(id)
                                else expandedRows.add(id)
                                renderQuery()
                            }
                            setOnLongClickListener {
                                clip?.setPrimaryClip(android.content.ClipData.newPlainText("id", id))
                                try {
                                    android.widget.Toast.makeText(c, "已复制ID: $id", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {}
                                true
                            }
                            addView(LinearLayout(c).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                addView(Md3.text(c, id, 11f, Md3.primary, true).apply {
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                        marginEnd = Md3.dp(c, 6f)
                                    }
                                })
                                addView(Md3.text(c, name, 11f, Md3.onSurface, false).apply {
                                    layoutParams = LinearLayout.LayoutParams(
                                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                                    maxLines = 1
                                    ellipsize = android.text.TextUtils.TruncateAt.END
                                })
                            })
                            if (expanded) {
                                addView(View(c).apply {
                                    background = ColorDrawable(0x1C000000)
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT, Md3.dp(c, 1f)).apply {
                                        topMargin = Md3.dp(c, 2f)
                                        bottomMargin = Md3.dp(c, 2f)
                                    }
                                })
                                addView(Md3.text(c, "ID: $id\n$name\n长按复制ID", 10f, Md3.onSurfaceVariant))
                            }
                        })
                    }
                }
                doQuery = {
                    overlayInputFocus(qEdit, false)
                    renderQuery()
                }
                // 仅点击"搜索"才过滤 (不实时刷新, 避免输入过程列表跳动)
                ip1.addView(qEmpty)
                qEmpty.visibility = View.GONE
                renderQuery()
            }
            7 -> {   // 特效 (四标题页: 世界特效 / 自身特效 / 定制装扮 / 动作)
                val fxTabs = arrayOf("世界特效", "自身特效", "定制装扮", "动作")
                val fxTabViews = arrayListOf<TextView>()
                val fxTabRow = LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = Md3.rounded(0x0F000000, Md3.dp(c, 10f).toFloat())
                    setPadding(Md3.dp(c, 3f), Md3.dp(c, 2f), Md3.dp(c, 3f), Md3.dp(c, 2f))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = Md3.dp(c, 4f)
                    }
                }
                fun styleFxTab(tv: TextView, sel: Boolean) {
                    tv.setTextColor(if (sel) Md3.onPrimaryContainer else Md3.onSurfaceVariant)
                    tv.background = if (sel)
                        Md3.rounded(Md3.primaryContainer, Md3.dp(c, 8f).toFloat())
                    else null
                }
                val fxPanes = arrayListOf<LinearLayout>()
                fun fxPane(vis: Boolean): LinearLayout {
                    val p = LinearLayout(c).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = Md3.dp(c, 2f)
                        }
                        visibility = if (vis) View.VISIBLE else View.GONE
                    }
                    fxPanes.add(p)
                    return p
                }
                val fxp0 = fxPane(true)
                val fxp1 = fxPane(false)
                val fxp2 = fxPane(false)
                val fxp3 = fxPane(false)
                root.addView(fxTabRow)
                root.addView(fxp0)
                root.addView(fxp1)
                root.addView(fxp2)
                root.addView(fxp3)
                for (ti in fxTabs.indices) {
                    val tv = Md3.text(c, fxTabs[ti], 11f, Md3.onSurfaceVariant).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, Md3.dp(c, 5f), 0, Md3.dp(c, 5f))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener {
                            haptic(this)
                            for (k in fxTabViews.indices) styleFxTab(fxTabViews[k], k == ti)
                            for (pi in fxPanes.indices) {
                                val pv = fxPanes[pi]
                                pv.visibility = if (pi == ti) View.VISIBLE else View.GONE
                            }
                        }
                    }
                    fxTabViews.add(tv)
                    fxTabRow.addView(tv)
                }
                for (k in fxTabViews.indices) styleFxTab(fxTabViews[k], k == 0)

                // ══ 世界特效: 全员提示子页 + 聊天气泡(native) / 进房播报 / 天空盒 ══
                addActionCard(fxp0, "全员提示", {}) { area, _ ->
                    fun fBtn(label: String, tap: () -> Unit): TextView = TextView(c).apply {
                        text = label
                        textSize = 11f
                        gravity = Gravity.CENTER
                        setTextColor(0xFFFFFFFF.toInt())
                        background = Md3.rounded(Md3.primary, Md3.dp(c, 10f).toFloat())
                        setPadding(0, Md3.dp(c, 6f), 0, Md3.dp(c, 6f))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = Md3.dp(c, 3f)
                        }
                        setOnClickListener { haptic(this); tap() }
                    }
                    area.addView(fBtn("发送一条") {
                        FuncBridge.sendOneTip()
                        Island.action(c, "全员提示")
                    })
                    val loopRow = LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(Md3.dp(c, 8f), Md3.dp(c, 4f), Md3.dp(c, 8f), Md3.dp(c, 4f))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = Md3.dp(c, 4f)
                        }
                    }
                    loopRow.addView(Md3.text(c, "循环全员提示", 11f, Md3.onSurface).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    val swLoop = Md3Switch(c).apply { setOn(FuncBridge.allTipsOn, false) }
                    swLoop.setOnClickListener { v ->
                        haptic(v)
                        val nv = !FuncBridge.allTipsOn
                        FuncBridge.setAllTips(nv)
                        swLoop.setOn(nv, true)
                        Island.notice(c, "循环全员提示", nv)
                    }
                    loopRow.addView(swLoop)
                    area.addView(loopRow)
                }
                // ══ 世界特效: 聊天气泡(native) / 进房播报(无作者) / 召唤天空盒 /
                //              移除天空盒 / 送礼播报(无房间检测) ══
                // 聊天气泡: 齿轮内 17 项单选即设置 (native BubbleSlot, 同梦喵气泡列表)
                addActionCard(fxp0, "聊天气泡",
                    { if (EspCtl.bubbleSel > 0) EspCtl.setBubble(EspCtl.bubbleSel) }) { area, _ ->
                    val bubbleList = listOf(
                        "紫曜之冠", "雀灵魅影", "幻想童话", "缤纷彩虹", "最佳拍档",
                        "流光圣灵", "梦幻之约", "化蝶入梦", "变形金刚", "彩虹旋风",
                        "瑶光玄灵", "花蔓四季", "喜羊羊与灰太狼", "古韵山水", "青白之息",
                        "机器时代", "日月同辉",
                    )
                    var sel = EspCtl.bubbleSel - 1
                    val rows = ArrayList<TextView>(bubbleList.size)
                    fun rebuild() {
                        for (i in rows.indices) {
                            rows[i].setTextColor(if (i == sel) Md3.primary else Md3.onSurface)
                            rows[i].background = if (i == sel)
                                Md3.rounded(0x1A415C8E.toInt(), Md3.dp(c, 8f).toFloat())
                            else Md3.rounded(0x0F000000, Md3.dp(c, 8f).toFloat())
                        }
                    }
                    for ((i, name) in bubbleList.withIndex()) {
                        val tv = Md3.text(c, name, 11f, Md3.onSurface)
                        tv.setPadding(Md3.dp(c, 8f), Md3.dp(c, 5f), Md3.dp(c, 8f), Md3.dp(c, 5f))
                        tv.layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        tv.setOnClickListener {
                            haptic(it)
                            sel = i
                            EspCtl.setBubble(i + 1)
                            rebuild()
                        }
                        rows.add(tv)
                        area.addView(tv)
                    }
                    rebuild()
                }
                addActionCard(fxp0, "进房播报",
                    { FuncBridge.sendBroadcast(FuncBridge.broadcastUin) }) { area, reg ->
                    // 迷你号输入行 + 确定 (留空 = 自己迷你号)
                    val box = LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = LayerDrawable(arrayOf(
                            Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                            Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                                setStroke(Md3.dp(c, 1f), 0x38000000)
                            }
                        ))
                        setPadding(Md3.dp(c, 6f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                        addView(Md3.text(c, "播报迷你号", 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                marginEnd = Md3.dp(c, 6f)
                            }
                        })
                    }
                    val edit = EditText(c).apply {
                        setText(FuncBridge.broadcastUin)
                        setTextColor(Md3.onSurface)
                        textSize = 12f
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        minEms = 18
                        gravity = Gravity.CENTER_VERTICAL
                        setHint("留空=自己")
                        setHintTextColor(0x66000000)
                        background = Md3.rounded(0x14000000, Md3.dp(c, 8f).toFloat())
                        setPadding(Md3.dp(c, 8f), Md3.dp(c, 4f), Md3.dp(c, 8f), Md3.dp(c, 4f))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = Md3.dp(c, 6f)
                        }
                        setOnClickListener {
                            haptic(this)
                            overlayInputFocus(this, true)
                        }
                        setOnFocusChangeListener { v, has ->
                            if (!has) overlayInputFocus(v as EditText, false)
                        }
                    }
                    reg(edit)
                    val ok = TextView(c).apply {
                        text = "确定"
                        textSize = 11f
                        gravity = Gravity.CENTER
                        setTextColor(0xFFFFFFFF.toInt())
                        background = Md3.rounded(Md3.primary, Md3.dp(c, 8f).toFloat())
                        setPadding(Md3.dp(c, 10f), Md3.dp(c, 4f), Md3.dp(c, 10f), Md3.dp(c, 4f))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = Md3.dp(c, 6f)
                        }
                        setOnClickListener {
                            haptic(this)
                            overlayInputFocus(edit, false)
                            FuncBridge.sendBroadcast(edit.text.toString())
                        }
                        bindImeDone(edit) { FuncBridge.sendBroadcast(edit.text.toString()); true }
                    }
                    box.addView(edit)
                    box.addView(ok)
                    area.addView(box)
                }
                // 召唤天空盒: 齿轮内 6 选项单选即时召唤
                addActionCard(fxp0, "召唤天空盒",
                    { FuncBridge.sendSkybox("default_skybox_snowplain_cfg") }) { area, _ ->
                    val skyList = listOf(
                        "default_skybox_snowplain_cfg" to "雪山群雁",
                        "default_skybox_palace_cfg" to "天上宫阙",
                        "default_skybox_sunset_cfg" to "日落时分",
                        "default_ornament_cfg" to "浩瀚星海",
                        "default_skybox_PalaceTowers_cfg" to "神临人间",
                        "default_skybox_aurora_cfg" to "极光之冕",
                    )
                    var sel = FuncBridge.skyboxSel
                    val rows = ArrayList<TextView>(skyList.size)
                    fun rebuild() {
                        for (i in rows.indices) {
                            rows[i].setTextColor(if (i == sel) Md3.primary else Md3.onSurface)
                            rows[i].background = if (i == sel)
                                Md3.rounded(0x1A415C8E.toInt(), Md3.dp(c, 8f).toFloat())
                            else Md3.rounded(0x0F000000, Md3.dp(c, 8f).toFloat())
                        }
                    }
                    for ((i, kv) in skyList.withIndex()) {
                        val tv = Md3.text(c, kv.second, 11f, Md3.onSurface)
                        tv.setPadding(Md3.dp(c, 8f), Md3.dp(c, 5f), Md3.dp(c, 8f), Md3.dp(c, 5f))
                        tv.layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        tv.setOnClickListener {
                            haptic(it)
                            sel = i
                            FuncBridge.setSkyboxSel(i)
                            FuncBridge.sendSkybox(skyList[i].first)
                            rebuild()
                        }
                        rows.add(tv)
                        area.addView(tv)
                    }
                    rebuild()
                }
                addActionCard(fxp0, "移除天空盒", { FuncBridge.removeSkybox() }, null)
                addActionCard(fxp0, "送礼播报", { FuncBridge.giftBroadcast() }, null)

                // ══ 自身特效: 转圈动作 / 音符特效 / 播放烟花 (齿轮: ID + 随机) ══
                addBattleFeature(fxp1, "转圈动作", FuncBridge.circleOn, false,
                    { on -> FuncBridge.setCircle(on); Island.notice(c, "转圈动作", on) }, null)
                addBattleFeature(fxp1, "音符特效", FuncBridge.noteFxOn, false,
                    { on -> FuncBridge.setNoteFx(on); Island.notice(c, "音符特效", on) }, null)
                addBattleFeature(fxp1, "播放烟花", FuncBridge.fireworkOn, true,
                    { on -> FuncBridge.setFirework(on); Island.notice(c, "播放烟花", on) }) { area, reg ->
                    fun fwRow() {
                        val box = LinearLayout(c).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            background = LayerDrawable(arrayOf(
                                Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                                Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                                    setStroke(Md3.dp(c, 1f), 0x38000000)
                                }
                            ))
                            setPadding(Md3.dp(c, 6f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                            addView(Md3.text(c, "烟花ID", 11f, Md3.onSurfaceVariant).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                    marginEnd = Md3.dp(c, 6f)
                                }
                            })
                        }
                        val edit = EditText(c).apply {
                            setText(if (FuncBridge.fireworkRandom) "" else FuncBridge.fireworkId)
                            setTextColor(Md3.onSurface)
                            textSize = 12f
                            inputType = android.text.InputType.TYPE_CLASS_NUMBER
                            minEms = 12
                            gravity = Gravity.CENTER_VERTICAL
                            setHint(if (FuncBridge.fireworkRandom) "随机模式" else "默认 13")
                            setHintTextColor(0x66000000)
                            background = Md3.rounded(0x14000000, Md3.dp(c, 8f).toFloat())
                            setPadding(Md3.dp(c, 8f), Md3.dp(c, 4f), Md3.dp(c, 8f), Md3.dp(c, 4f))
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                                marginStart = Md3.dp(c, 6f)
                            }
                            setOnClickListener {
                                haptic(this)
                                overlayInputFocus(this, true)
                            }
                            setOnFocusChangeListener { v, has ->
                                if (!has) overlayInputFocus(v as EditText, false)
                            }
                        }
                        reg(edit)
                        val ok = TextView(c).apply {
                            text = "确定"
                            textSize = 11f
                            gravity = Gravity.CENTER
                            setTextColor(0xFFFFFFFF.toInt())
                            background = Md3.rounded(Md3.primary, Md3.dp(c, 8f).toFloat())
                            setPadding(Md3.dp(c, 10f), Md3.dp(c, 4f), Md3.dp(c, 10f), Md3.dp(c, 4f))
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                marginStart = Md3.dp(c, 6f)
                            }
                            setOnClickListener {
                                haptic(this)
                                overlayInputFocus(edit, false)
                                val v = edit.text.toString()
                                if (v.toIntOrNull() != null) FuncBridge.setFireworkId(v)
                                else FuncBridge.setFireworkRandom(true)
                            }
                            bindImeDone(edit) {
                                val v = edit.text.toString()
                                if (v.toIntOrNull() != null) FuncBridge.setFireworkId(v)
                                else FuncBridge.setFireworkRandom(true)
                                true
                            }
                        }
                        box.addView(edit)
                        box.addView(ok)
                        area.addView(box)
                    }
                    fwRow()
                    val sw = Md3Switch(c).apply { setOn(FuncBridge.fireworkRandom, false) }
                    area.addView(LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = LayerDrawable(arrayOf(
                            Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                            Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                                setStroke(Md3.dp(c, 1f), 0x38000000)
                            }
                        ))
                        setPadding(Md3.dp(c, 6f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                        addView(Md3.text(c, "随机烟花", 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        sw.setOnClickListener {
                            haptic(it)
                            FuncBridge.setFireworkRandom(!FuncBridge.fireworkRandom)
                            sw.setOn(FuncBridge.fireworkRandom, true)
                        }
                        addView(sw)
                    })
                }
                addBattleFeature(fxp1, "幻影旋转", FuncBridge.phantomOn, false,
                    { on -> FuncBridge.setPhantomSpin(on); Island.notice(c, "幻影旋转", on) }, null)
                // 人物特效: 齿轮展开 41 款特效列表, 点行即播放一次 (native 远端 shellcode,
                // 目标 = 云 effectTargetChain; 需先注入引擎? 不需要, 走 native 通道)
                addActionCard(fxp1, "人物特效", { EspCtl.setEffect(EspCtl.effectSel) }) { area, _ ->
                    val effects = arrayOf(
                        "紫色法阵" to "magic119", "背后光环" to "horse_4662_3", "背后雪花" to "xuli",
                        "雪花法阵" to "ice_fazhen_gongji", "头顶眩晕" to "bossxuanyun", "传送门" to "portal_1",
                        "火光龙卷风" to "aotu_07_xuanfeng", "紫色黑洞" to "140246_1", "小型箭雨" to "140340_1",
                        "蓝色星光" to "140335_1", "暗黑风高" to "140228_1", "血月时刻" to "140330_1",
                        "淋雨特效" to "linyu", "攻击特效131" to "attack131", "彩带" to "ribbon",
                        "音符特效1" to "music01", "音符特效2" to "music02", "混沌雕像" to "chaosstatue",
                        "云雾151" to "cloud151", "能量吸收" to "nengliang_xishou", "怪物特效hq1" to "mob_100068_hq1",
                        "怪物特效hq2" to "mob_100068_hq2", "怪物特效hq3" to "mob_100068_hq3", "怪物特效hq4" to "mob_100068_hq4",
                        "怪物特效hq5" to "mob_100068_hq5", "动画100180" to "anim_100180", "蓝色传送门" to "portal_blue",
                        "海啸1" to "haixiaoy_1", "物品特效xs" to "item_100068_xs", "BOSS护盾" to "bossskill_3514_sheild",
                        "喷气63" to "jet_63", "橙色传送门" to "portal_orange", "冲锋特效" to "chongfeng",
                        "BOSS展示" to "boss_10069_zs", "彩带1" to "ribbon_1", "大魂环-金" to "dl_golden",
                        "大魂环-红" to "dl_gules", "大魂环-黑" to "dl_black", "大魂环-紫" to "dl_purple",
                        "大魂环-白" to "dl_white", "大魂环-黄" to "dl_yellow")
                    val rowViews = arrayListOf<TextView>()
                    fun restyle() {
                        for ((i, tv) in rowViews.withIndex()) {
                            val sel = effects[i].second == EspCtl.effectSel
                            tv.setTextColor(if (sel) Md3.primary else Md3.onSurface)
                            tv.background = if (sel)
                                Md3.rounded(0x1A415C8E.toInt(), Md3.dp(c, 10f).toFloat())
                            else Md3.rounded(0x0F000000, Md3.dp(c, 10f).toFloat())
                        }
                    }
                    for ((name, id) in effects) {
                        val tv = Md3.text(c, name, 11f, Md3.onSurface).apply {
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(Md3.dp(c, 10f), Md3.dp(c, 6f), Md3.dp(c, 10f), Md3.dp(c, 6f))
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                topMargin = Md3.dp(c, 2f)
                            }
                            setOnClickListener {
                                haptic(this)
                                EspCtl.setEffect(id)
                                Island.action(c, "特效已播放")
                                restyle()
                            }
                        }
                        rowViews.add(tv)
                        area.addView(tv)
                    }
                    restyle()
                }

                // ══ 定制装扮: 搜索 uuxx.lua (XOR90 装扮表), 行内"装扮"按钮上身 ══
                val outfitList = outfitDb()
                var doOutfitSearch: (() -> Unit)? = null
                val oEdit = EditText(c).apply {
                    textSize = 11f
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(Md3.onSurface)
                    setHint("输入装扮名称")
                    setHintTextColor(0x66000000)
                    background = Md3.rounded(0x16000000, Md3.dp(c, 10f).toFloat())
                    setPadding(Md3.dp(c, 10f), Md3.dp(c, 5f), Md3.dp(c, 10f), Md3.dp(c, 5f))
                    layoutParams = LinearLayout.LayoutParams(0, Md3.dp(c, 30f), 1f)
                    setOnClickListener {
                        haptic(this)
                        overlayInputFocus(this, true)
                    }
                    setOnFocusChangeListener { v, has ->
                        if (!has) overlayInputFocus(v as EditText, false)
                    }
                }
                bindImeDone(oEdit) { doOutfitSearch?.invoke(); true }
                fxp2.addView(LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, 0, Md3.dp(c, 3f))
                    addView(oEdit)
                    addView(TextView(c).apply {
                        text = "搜索"
                        textSize = 11f
                        gravity = Gravity.CENTER
                        setTextColor(0xFFFFFFFF.toInt())
                        background = Md3.ripple(Md3.rounded(Md3.primary, Md3.dp(c, 10f).toFloat()))
                        setPadding(Md3.dp(c, 12f), 0, Md3.dp(c, 12f), 0)
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, Md3.dp(c, 30f)).apply {
                            marginStart = Md3.dp(c, 4f)
                        }
                        setOnClickListener {
                            haptic(this)
                            overlayInputFocus(oEdit, false)
                            doOutfitSearch?.invoke()
                        }
                    })
                })
                val oList = LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                fxp2.addView(oList)
                val oEmpty = Md3.text(c, "未找到装扮", 11f, Md3.onSurfaceVariant)
                fxp2.addView(oEmpty)
                oEmpty.visibility = View.GONE
                fun renderOutfit() {
                    val kw = oEdit.text.toString().trim()
                    val hit = ArrayList<Pair<String, String>>(100)
                    if (kw.isEmpty()) {
                        val n = Math.min(outfitList.size, 100)
                        for (i in 0 until n) hit.add(outfitList[i])
                    } else {
                        for (it in outfitList) {
                            if (it.second.contains(kw)) {
                                hit.add(it)
                                if (hit.size >= 100) break
                            }
                        }
                    }
                    oList.removeAllViews()
                    if (hit.isEmpty()) {
                        oEmpty.visibility = View.VISIBLE
                        return
                    }
                    oEmpty.visibility = View.GONE
                    for ((xorId, name) in hit) {
                        val rowV = LinearLayout(c).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            background = Md3.rounded(0x0F000000, Md3.dp(c, 10f).toFloat())
                            setPadding(Md3.dp(c, 8f), Md3.dp(c, 2f), Md3.dp(c, 6f), Md3.dp(c, 2f))
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                topMargin = Md3.dp(c, 1f)
                            }
                        }
                        rowV.addView(Md3.text(c, name, 11f, Md3.onSurface).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                        })
                        rowV.addView(TextView(c).apply {
                            text = "装扮"
                            textSize = 10f
                            gravity = Gravity.CENTER
                            setTextColor(0xFFFFFFFF.toInt())
                            background = Md3.ripple(Md3.rounded(Md3.primary, Md3.dp(c, 8f).toFloat()))
                            setPadding(Md3.dp(c, 10f), 0, Md3.dp(c, 10f), 0)
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, Md3.dp(c, 24f))
                            setOnClickListener {
                                haptic(this)
                                FuncBridge.sendOutfit(xorId, name)
                            }
                        })
                        oList.addView(rowV)
                    }
                }
                doOutfitSearch = {
                    overlayInputFocus(oEdit, false)
                    renderOutfit()
                }
                renderOutfit()

                // ══ 动作页(第 4 页): 解锁全部动作 ══
                addActionCard(fxp3, "解锁全部动作", { FuncBridge.unlockAllActions() }, null)
            }
            8 -> {   // 属性 (双标题页: 基础联机 / 属性区域)
                val atTabs = arrayOf("基础联机", "属性区域")
                val atTabViews = arrayListOf<TextView>()
                val atTabRow = LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = Md3.rounded(0x0F000000, Md3.dp(c, 10f).toFloat())
                    setPadding(Md3.dp(c, 3f), Md3.dp(c, 2f), Md3.dp(c, 3f), Md3.dp(c, 2f))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = Md3.dp(c, 4f)
                    }
                }
                fun styleAtTab(tv: TextView, sel: Boolean) {
                    tv.setTextColor(if (sel) Md3.onPrimaryContainer else Md3.onSurfaceVariant)
                    tv.background = if (sel)
                        Md3.rounded(Md3.primaryContainer, Md3.dp(c, 8f).toFloat())
                    else null
                }
                val atPanes = arrayListOf<LinearLayout>()
                fun atPane(vis: Boolean): LinearLayout {
                    val p = LinearLayout(c).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = Md3.dp(c, 2f)
                        }
                        visibility = if (vis) View.VISIBLE else View.GONE
                    }
                    atPanes.add(p)
                    return p
                }
                val atp0 = atPane(true)
                val atp1 = atPane(false)
                root.addView(atTabRow)
                root.addView(atp0)
                root.addView(atp1)
                for (ti in atTabs.indices) {
                    val tv = Md3.text(c, atTabs[ti], 11f, Md3.onSurfaceVariant).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, Md3.dp(c, 5f), 0, Md3.dp(c, 5f))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener {
                            haptic(this)
                            for (t in atTabViews.indices) styleAtTab(atTabViews[t], t == ti)
                            for (pi in atPanes.indices) atPanes[pi].visibility =
                                if (pi == ti) View.VISIBLE else View.GONE
                        }
                    }
                    atTabViews.add(tv)
                    atTabRow.addView(tv)
                }
                styleAtTab(atTabViews[0], true)
                styleAtTab(atTabViews[1], false)

                // ══ 页1 基础联机 (Lua: 22 面条回血 / 23 自动重生 / 29 花环无敌 /
                //          59 屏蔽死亡 / 19 飞行模式 / 49 自动搭路) ══
                addBattleFeature(atp0, "面条回血", FuncBridge.noodleOn, false,
                    { on -> FuncBridge.setNoodleHeal(on); Island.notice(c, "面条回血", on) }, null)
                addBattleFeature(atp0, "花环无敌", FuncBridge.flowerOn, false,
                    { on -> FuncBridge.setFlower(on); Island.notice(c, "花环无敌", on) }, null)
                addBattleFeature(atp0, "自动重生", FuncBridge.reviveOn, false,
                    { on -> FuncBridge.setAutoRevive(on); Island.notice(c, "自动重生", on) }, null)
                addBattleFeature(atp0, "屏蔽死亡", FuncBridge.deathBlockOn, false,
                    { on -> FuncBridge.setDeathBlock(on); Island.notice(c, "屏蔽死亡", on) }, null)
                addBattleFeature(atp0, "飞行模式", FuncBridge.flyOn, false,
                    { on -> FuncBridge.setFly(on); Island.notice(c, "飞行模式", on) }, null)
                addBattleFeature(atp0, "无限踏空", EspCtl.airOn, false,
                    { on -> EspCtl.setAir(on); Island.notice(c, "无限踏空", on) }, null)
                // 人物夜视: 无开关, 齿轮入口 → 竖排选项 (同游戏模式/人物视角)
                addActionCard(atp0, "人物夜视", {}, { area, _ ->
                    val opts = listOf("开启" to true, "关闭" to false)
                    fun render() {
                        area.removeAllViews()
                        for ((name, on) in opts) {
                            val sel = if (on) EspCtl.nightOn else !EspCtl.nightOn
                            area.addView(TextView(c).apply {
                                text = name
                                textSize = 11f
                                gravity = Gravity.CENTER_VERTICAL
                                setPadding(Md3.dp(c, 10f), Md3.dp(c, 7f), Md3.dp(c, 10f), Md3.dp(c, 7f))
                                background = if (sel)
                                    Md3.rounded(0x1A415C8E.toInt(), Md3.dp(c, 10f).toFloat())
                                else Md3.rounded(0x0F000000, Md3.dp(c, 10f).toFloat())
                                setTextColor(if (sel) Md3.primary else Md3.onSurface)
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                    topMargin = Md3.dp(c, 2f)
                                }
                                setOnClickListener {
                                    haptic(it)
                                    EspCtl.setNight(on)
                                    render()
                                }
                            })
                        }
                    }
                    render()
                })
                addBattleFeature(atp0, "实体穿墙", EspCtl.wallOn, false,
                    { on -> EspCtl.setWall(on); Island.notice(c, "实体穿墙", on) }, null)
                addBattleFeature(atp0, "快速投掷", EspCtl.throwOn, false,
                    { on -> EspCtl.setThrow(on); Island.notice(c, "快速投掷", on) }, null)
                // 云服秒吃 (native: hook 吃东西函数) / 秒吃食物 (Lua 30/130 回退) / 人物爬行 (Lua 36/136)
                addBattleFeature(atp0, "云服秒吃", EspCtl.cloudEatOn, false,
                    { on -> EspCtl.setCloudEat(on); Island.notice(c, "云服秒吃", on) }, null)
                addBattleFeature(atp0, "秒吃食物", FuncBridge.autoEatOn, true,
                    { on -> FuncBridge.setAutoEat(on); Island.notice(c, "秒吃食物", on) }) { area, reg ->
                    val box = LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = LayerDrawable(arrayOf(
                            Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                            Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                                setStroke(Md3.dp(c, 1f), 0x38000000)
                            }
                        ))
                        setPadding(Md3.dp(c, 6f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                        addView(Md3.text(c, "间隔 (秒)", 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                marginEnd = Md3.dp(c, 6f)
                            }
                        })
                    }
                    val edit = EditText(c).apply {
                        setText(FuncBridge.eatSpeed)
                        setTextColor(Md3.onSurface)
                        textSize = 12f
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                        minEms = 6
                        gravity = Gravity.CENTER_VERTICAL
                        setHint("0.1")
                        setHintTextColor(0x66000000)
                        background = Md3.rounded(0x14000000, Md3.dp(c, 8f).toFloat())
                        setPadding(Md3.dp(c, 8f), Md3.dp(c, 4f), Md3.dp(c, 8f), Md3.dp(c, 4f))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = Md3.dp(c, 6f)
                        }
                        setOnClickListener {
                            haptic(this)
                            overlayInputFocus(this, true)
                        }
                        setOnFocusChangeListener { v, has ->
                            if (!has) overlayInputFocus(v as EditText, false)
                        }
                    }
                    reg(edit)
                    val ok = TextView(c).apply {
                        text = "确定"
                        textSize = 11f
                        gravity = Gravity.CENTER
                        setTextColor(0xFFFFFFFF.toInt())
                        background = Md3.rounded(Md3.primary, Md3.dp(c, 8f).toFloat())
                        setPadding(Md3.dp(c, 10f), Md3.dp(c, 4f), Md3.dp(c, 10f), Md3.dp(c, 4f))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = Md3.dp(c, 6f)
                        }
                        setOnClickListener {
                            haptic(this)
                            overlayInputFocus(edit, false)
                            FuncBridge.setEatSpeed(edit.text.toString())
                        }
                        bindImeDone(edit) { FuncBridge.setEatSpeed(edit.text.toString()); true }
                    }
                    box.addView(edit)
                    box.addView(ok)
                    area.addView(box)
                }
                addBattleFeature(atp0, "人物爬行", FuncBridge.crawlOn, false,
                    { on -> FuncBridge.setCrawl(on); Island.notice(c, "人物爬行", on) }, null)
                // 自动搭路: 齿轮只留 方块ID(物品ID) 自定义; 触发栏位固定 1 (第2格, 游戏0起始)
                addBattleFeature(atp0, "自动搭路", FuncBridge.bridgeOn, true,
                    { on -> FuncBridge.setBridge(on); Island.notice(c, "自动搭路", on) }) { area, reg ->
                    val box = LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = LayerDrawable(arrayOf(
                            Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                            Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                                setStroke(Md3.dp(c, 1f), 0x38000000)
                            }
                        ))
                        setPadding(Md3.dp(c, 6f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                        addView(Md3.text(c, "方块ID", 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                marginEnd = Md3.dp(c, 6f)
                            }
                        })
                    }
                    val edit = EditText(c).apply {
                        setText(FuncBridge.bridgeBlockId)
                        setTextColor(Md3.onSurface)
                        textSize = 12f
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        minEms = 8
                        gravity = Gravity.CENTER_VERTICAL
                        setHint("默认 600")
                        setHintTextColor(0x66000000)
                        background = Md3.rounded(0x14000000, Md3.dp(c, 8f).toFloat())
                        setPadding(Md3.dp(c, 8f), Md3.dp(c, 4f), Md3.dp(c, 8f), Md3.dp(c, 4f))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = Md3.dp(c, 6f)
                        }
                        setOnClickListener {
                            haptic(this)
                            overlayInputFocus(this, true)
                        }
                        setOnFocusChangeListener { v, has ->
                            if (!has) overlayInputFocus(v as EditText, false)
                        }
                    }
                    reg(edit)
                    val ok = TextView(c).apply {
                        text = "确定"
                        textSize = 11f
                        gravity = Gravity.CENTER
                        setTextColor(0xFFFFFFFF.toInt())
                        background = Md3.rounded(Md3.primary, Md3.dp(c, 8f).toFloat())
                        setPadding(Md3.dp(c, 10f), Md3.dp(c, 4f), Md3.dp(c, 10f), Md3.dp(c, 4f))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = Md3.dp(c, 6f)
                        }
                        setOnClickListener {
                            haptic(this)
                            overlayInputFocus(edit, false)
                            FuncBridge.setBridgeBlockId(edit.text.toString())
                        }
                        bindImeDone(edit) { FuncBridge.setBridgeBlockId(edit.text.toString()); true }
                    }
                    box.addView(edit)
                    box.addView(ok)
                    area.addView(box)
                }
                atp0.addView(Md3.text(c, "提示：请手持 2 号物品栏(栏位从0起=1)进行自动搭路", 10f,
                    Md3.onSurfaceVariant).apply {
                    setPadding(Md3.dp(c, 14f), 0, Md3.dp(c, 8f), Md3.dp(c, 4f))
                })

                // ══ 页2 属性区域 (native, 偏移=云端下发; 移速 1~10 / 跳远 2~50 /
                //          游戏模式 / 人物视角 / 头部大小) + 无限踏空/夜视/穿墙/快速投掷 ══
                // 人物移速: 无开关, 齿轮展开拖动条即时生效 (1=还原游戏原速度)
                addActionCard(atp1, "人物移速", {}, { area, _ ->
                    val valTxt = Md3.text(c,
                        if (EspCtl.speedVal <= 1) "原速" else "${EspCtl.speedVal}", 11f, Md3.primary)
                    area.addView(LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(Md3.text(c, "速度档位 (1~10)", 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        addView(valTxt)
                    })
                    area.addView(Md3Slider(c).apply {
                        setValue((Math.max(EspCtl.speedVal, 1) - 1) / 9f * 100f)
                        onValueChange = { v ->
                            val sv = 1 + (9 * v / 100f).toInt()
                            EspCtl.setSpeed(true, sv)   // 拖动即生效; 1 = 还原原速
                            valTxt.text = if (sv <= 1) "原速" else "$sv"
                        }
                        valueLabel = { v ->
                            val sv = 1 + (9 * v / 100f).toInt()
                            if (sv <= 1) "原速" else sv.toString()
                        }
                    })
                })
                // 跳远距离: 无开关, 齿轮展开拖动条即时生效
                addActionCard(atp1, "跳远距离", {}, { area, _ ->
                    val valTxt = Md3.text(c, "%.0f".format(EspCtl.jumpVal), 11f, Md3.primary)
                    area.addView(LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(Md3.text(c, "距离 (2~50)", 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        addView(valTxt)
                    })
                    area.addView(Md3Slider(c).apply {
                        setValue((EspCtl.jumpVal - 2f) / 48f * 100f)
                        onValueChange = { v ->
                            val jv = 2f + 48f * v / 100f
                            EspCtl.setJump(true, jv)    // 拖动即生效并保持
                            valTxt.text = "%.0f".format(jv)
                        }
                        valueLabel = { v -> "%.0f".format(2f + 48f * v / 100f) }
                    })
                })
                // 游戏模式: 无开关, 齿轮入口 → 竖排选项 (不显示ID)
                addActionCard(atp1, "游戏模式", {}, { area, _ ->
                    val opts = listOf("玩法模式" to 5, "编辑模式" to 4)
                    fun render() {
                        area.removeAllViews()
                        for ((name, v) in opts) {
                            val sel = EspCtl.modeOn && EspCtl.modeVal == v
                            area.addView(TextView(c).apply {
                                text = name
                                textSize = 11f
                                gravity = Gravity.CENTER_VERTICAL
                                setPadding(Md3.dp(c, 10f), Md3.dp(c, 7f), Md3.dp(c, 10f), Md3.dp(c, 7f))
                                background = if (sel)
                                    Md3.rounded(0x1A415C8E.toInt(), Md3.dp(c, 10f).toFloat())
                                else Md3.rounded(0x0F000000, Md3.dp(c, 10f).toFloat())
                                setTextColor(if (sel) Md3.primary else Md3.onSurface)
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                    topMargin = Md3.dp(c, 2f)
                                }
                                setOnClickListener {
                                    haptic(it)
                                    EspCtl.setMode(true, v)
                                    render()
                                }
                            })
                        }
                    }
                    render()
                })
                // 人物视角: 无开关, 齿轮入口 → 竖排选项 (不显示ID)
                addActionCard(atp1, "人物视角", {}, { area, _ ->
                    val opts = listOf("主视角" to 0, "背视角" to 1, "正视角" to 2)
                    fun render() {
                        area.removeAllViews()
                        for ((name, v) in opts) {
                            val sel = EspCtl.viewOn && EspCtl.viewVal == v
                            area.addView(TextView(c).apply {
                                text = name
                                textSize = 11f
                                gravity = Gravity.CENTER_VERTICAL
                                setPadding(Md3.dp(c, 10f), Md3.dp(c, 7f), Md3.dp(c, 10f), Md3.dp(c, 7f))
                                background = if (sel)
                                    Md3.rounded(0x1A415C8E.toInt(), Md3.dp(c, 10f).toFloat())
                                else Md3.rounded(0x0F000000, Md3.dp(c, 10f).toFloat())
                                setTextColor(if (sel) Md3.primary else Md3.onSurface)
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                    topMargin = Md3.dp(c, 2f)
                                }
                                setOnClickListener {
                                    haptic(it)
                                    EspCtl.setView(true, v)
                                    render()
                                }
                            })
                        }
                    }
                    render()
                })

                // 头部大小: 齿轮滑块 1~50
                addBattleFeature(atp1, "头部大小", EspCtl.headOn, true,
                    { on -> EspCtl.setHead(on); Island.notice(c, "头部大小", on) }) { area, _ ->
                    val valTxt = Md3.text(c, "${EspCtl.headVal}", 11f, Md3.primary)
                    area.addView(LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(Md3.text(c, "大小 (1~50)", 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        addView(valTxt)
                    })
                    area.addView(Md3Slider(c).apply {
                        setValue((EspCtl.headVal - 1) / 49f * 100f)
                        onValueChange = { v ->
                            val hv = 1 + (49 * v / 100f).toInt()
                            EspCtl.setHead(EspCtl.headOn, hv)
                            valTxt.text = "$hv"
                        }
                        valueLabel = { v -> (1 + (49 * v / 100f).toInt()).toString() }
                    })
                }
                // 挖掘距离: 齿轮滑块 5~100 (米); 链 = 云 digCh4/digAdd
                addBattleFeature(atp1, "挖掘距离", EspCtl.digOn, true,
                    { on -> EspCtl.setDig(on); Island.notice(c, "挖掘距离", on) }) { area, _ ->
                    val valTxt = Md3.text(c, "%.0f".format(EspCtl.digVal), 11f, Md3.primary)
                    area.addView(LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(Md3.text(c, "距离 (5~100)", 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        addView(valTxt)
                    })
                    area.addView(Md3Slider(c).apply {
                        setValue((EspCtl.digVal - 5f) / 95f * 100f)
                        onValueChange = { v ->
                            val dv = 5f + 95f * v / 100f
                            EspCtl.setDig(true, dv)
                            valTxt.text = "%.0f".format(dv)
                        }
                        valueLabel = { v -> "%.0f".format(5f + 95f * v / 100f) }
                    })
                }
                // 手持物品模型大小: 齿轮滑块 1~5 (本地静态补丁)
                addBattleFeature(atp1, "手持物品模型大小", EspCtl.handOn, true,
                    { on -> EspCtl.setHand(on); Island.notice(c, "手持物品模型大小", on) }) { area, _ ->
                    val valTxt = Md3.text(c, "${EspCtl.handVal}", 11f, Md3.primary)
                    area.addView(LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(Md3.text(c, "大小 (1~5)", 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        addView(valTxt)
                    })
                    area.addView(Md3Slider(c).apply {
                        setValue((EspCtl.handVal - 1) / 4f * 100f)
                        onValueChange = { v ->
                            val hv = 1 + (4 * v / 100f).toInt()
                            EspCtl.setHand(EspCtl.handOn, hv)
                            valTxt.text = "$hv"
                        }
                        valueLabel = { v -> (1 + (4 * v / 100f).toInt()).toString() }
                    })
                }
                // 子弹模型大小: 齿轮滑块 1~50 (本地静态补丁)
                addBattleFeature(atp1, "子弹模型大小", EspCtl.bulletOn, true,
                    { on -> EspCtl.setBullet(on); Island.notice(c, "子弹模型大小", on) }) { area, _ ->
                    val valTxt = Md3.text(c, "${EspCtl.bulletVal}", 11f, Md3.primary)
                    area.addView(LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(Md3.text(c, "大小 (1~50)", 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        addView(valTxt)
                    })
                    area.addView(Md3Slider(c).apply {
                        setValue((EspCtl.bulletVal - 1) / 49f * 100f)
                        onValueChange = { v ->
                            val bv = 1 + (49 * v / 100f).toInt()
                            EspCtl.setBullet(EspCtl.bulletOn, bv)
                            valTxt.text = "$bv"
                        }
                        valueLabel = { v -> (1 + (49 * v / 100f).toInt()).toString() }
                    })
                }
            }
            else -> {   // 其他
                // 地图UI选择 (11/15) / 联机踢人 (72) / 回重生点 (17) — Lua
                addBattleFeature(root, "地图UI选择", FuncBridge.mapUiOn, false,
                    { on -> FuncBridge.setMapUi(on); Island.notice(c, "地图UI选择", on) }, null)
                // 快捷按钮: 逐项勾选上快捷栏 (勾选即显示, 取消即消失; 右侧胶囊列, 可拖动)
                addActionCard(root, "开启快捷键", {}) { area, _ ->
                    area.addView(Md3.text(c, "勾选要放到快捷栏的功能 (每个按钮可单独拖到任意位置)",
                        10f, Md3.onSurfaceVariant).apply {
                        setPadding(Md3.dp(c, 6f), Md3.dp(c, 2f), Md3.dp(c, 6f), Md3.dp(c, 2f))
                    })
                    for (item in QuickButtons.items) {
                        val row = LinearLayout(c).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(Md3.dp(c, 8f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                topMargin = Md3.dp(c, 1f)
                            }
                            addView(Md3.text(c, item.label, 11f, Md3.onSurface).apply {
                                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                            })
                        }
                        val sw = Md3Switch(c).apply { setOn(QuickButtons.isEnabled(c, item.key), false) }
                        sw.setOnClickListener {
                            haptic(it)
                            val on = !QuickButtons.isEnabled(c, item.key)
                            QuickButtons.setEnabled(c, item.key, on)
                            sw.setOn(on, true)
                        }
                        row.addView(sw)
                        area.addView(row)
                    }
                }
                addActionCard(root, "联机踢人",
                    { FuncBridge.kickPlayer(FuncBridge.kickUin) }) { area, reg ->
                    val box = LinearLayout(c).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = LayerDrawable(arrayOf(
                            Md3.rounded(0x18000000, Md3.dp(c, 12f).toFloat()),
                            Md3.rounded(0x00000000, Md3.dp(c, 12f).toFloat()).apply {
                                setStroke(Md3.dp(c, 1f), 0x38000000)
                            }
                        ))
                        setPadding(Md3.dp(c, 6f), Md3.dp(c, 3f), Md3.dp(c, 6f), Md3.dp(c, 3f))
                        addView(Md3.text(c, "踢人迷你号", 11f, Md3.onSurfaceVariant).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                marginEnd = Md3.dp(c, 6f)
                            }
                        })
                    }
                    val edit = EditText(c).apply {
                        setText(FuncBridge.kickUin)
                        setTextColor(Md3.onSurface)
                        textSize = 12f
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        minEms = 22
                        gravity = Gravity.CENTER_VERTICAL
                        setHint("12 位迷你号")
                        setHintTextColor(0x66000000)
                        background = Md3.rounded(0x14000000, Md3.dp(c, 8f).toFloat())
                        setPadding(Md3.dp(c, 8f), Md3.dp(c, 4f), Md3.dp(c, 8f), Md3.dp(c, 4f))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = Md3.dp(c, 6f)
                        }
                        setOnClickListener {
                            haptic(this)
                            overlayInputFocus(this, true)
                        }
                        setOnFocusChangeListener { v, has ->
                            if (!has) overlayInputFocus(v as EditText, false)
                        }
                    }
                    reg(edit)
                    val ok = TextView(c).apply {
                        text = "确定"
                        textSize = 11f
                        gravity = Gravity.CENTER
                        setTextColor(0xFFFFFFFF.toInt())
                        background = Md3.rounded(Md3.primary, Md3.dp(c, 8f).toFloat())
                        setPadding(Md3.dp(c, 10f), Md3.dp(c, 4f), Md3.dp(c, 10f), Md3.dp(c, 4f))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = Md3.dp(c, 6f)
                        }
                        setOnClickListener {
                            haptic(this)
                            overlayInputFocus(edit, false)
                            FuncBridge.kickPlayer(edit.text.toString())
                        }
                        bindImeDone(edit) { FuncBridge.kickPlayer(edit.text.toString()); true }
                    }
                    box.addView(edit)
                    box.addView(ok)
                    area.addView(box)
                }
                addActionCard(root, "回重生点", { FuncBridge.teleportHome() }, null)
            }
        }
        scroll.addView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return scroll
    }

    // 小巧按钮 (32dp 高 + 震动)
    private fun btn(c: Context, text: String, filled: Boolean): TextView {
        val b = Md3.button(c, text, Md3.dp(c, 32f), filled = filled, corner = Md3.dp(c, 10f).toFloat())
        b.textSize = 12f
        b.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Md3.dp(c, 32f)).apply {
            topMargin = Md3.dp(c, 8f)
        }
        b.setOnClickListener { haptic(b) }
        return b
    }

    private fun row(c: Context, title: String, right: View): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Md3.dp(c, 3f), 0, Md3.dp(c, 3f))
            addView(Md3.text(c, title, 12f, Md3.onSurface, true).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(right)
        }

    private fun switchRow(c: Context, title: String, sw: Md3Switch): LinearLayout =
        capRow(c, title, sw) { }

    // 复选框行 (保持简单行样式, 不套胶囊 — 胶囊只给开关用)
    private fun checkRow(c: Context, title: String, cb: Md3Checkbox): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Md3.dp(c, 3f), 0, Md3.dp(c, 3f))
            addView(Md3.text(c, title, 12f, Md3.onSurface).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            cb.onCheckedChange = { on ->
                haptic(cb)
                Island.notice(c, title, on)
            }
            addView(cb)
        }
}

// 侧边栏手势拦截: 单击穿透给子项(切页), 纵向拖动则拦截 → 气泡跟随手指 (同底栏手势)
// phase: 0=按下 1=拖动(dy=相对按下位移) 2=释放
class DragSidebar(context: Context, private val onGesture: (phase: Int, dy: Float) -> Unit) : FrameLayout(context) {

    private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private var dragging = false
    private var startY = 0f
    private var lastY = 0f

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = false
                startY = ev.y
                lastY = ev.y
                onGesture(0, 0f)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && Math.abs(ev.y - lastY) > slop) {
                    dragging = true
                    onGesture(1, ev.y - startY)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) onGesture(2, ev.y - startY)
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (dragging) onGesture(1, ev.y - startY)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    onGesture(2, ev.y - startY)
                    return true
                }
            }
        }
        return dragging || super.onTouchEvent(ev)
    }
}
