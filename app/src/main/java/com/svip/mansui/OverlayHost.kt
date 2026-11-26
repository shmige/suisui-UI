package com.svip.mansui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import com.svip.mansui.IPCurbco.TClass.SPUtils
import com.svip.mansui.esp.EspCtl
import com.svip.mansui.esp.EspGLView
import java.io.File
import java.io.FileOutputStream

// ═══════════════════════════════════════════════
//  单悬浮渲染层 (ESP 绘制 与 灵动岛 共用同一个全屏透明 TextureView):
//  任一需要显示(ESP总开关 / 灵动岛开关)就创建, 都关闭才移除 —
//  避免两个 native 渲染实例争抢同一套 EGL/ImGui 全局状态
// ═══════════════════════════════════════════════
object OverlayHost {

    private var wm: WindowManager? = null
    private var view: EspGLView? = null
    private var ctx: Context? = null

    private fun islandOn(c: Context): Boolean =
        SPUtils.getParam(c.applicationContext, "dynamic_island", false) as Boolean

    /** 根据 ESP 总开关 / 灵动岛 / 自瞄 / 子追 的当前状态刷新悬浮渲染层
     *  (自瞄圈/子追圈与追线独立于 ESP 总开关显示, 需要渲染层常驻)
     *  灵动岛仅在悬浮球(悬浮窗)已显示时生效 — 球未开不创建全屏渲染层 */
    fun refresh() {
        val c = ctx ?: AppState.getContext ?: run {
            return
        }
        val islandActive = islandOn(c) && FloatWindow.isWindowShown()
        val need = EspCtl.flags[0] || islandActive || EspCtl.aimOn || EspCtl.subOn
        if (need) {
            ensure(c)
        } else {
            teardown()
        }
    }

    private fun ensure(c: Context) {
        if (view != null) return
        if (!Settings2.canOverlay(c)) {
            return
        }
        ctx = c.applicationContext
        if (runCatching { EspCtl.prepare(ctx!!) }.getOrNull() == null) {
        }
        wm = ctx!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val v = runCatching { EspGLView(ctx!!) }.getOrNull() ?: run {
            return
        }
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            WinCompat.applyCutoutMode(this)   // 失败忽略: 不能让可选适配拖垮建窗
        }
        val ok = runCatching { wm?.addView(v, lp); true }.getOrNull()
        if (ok != true) {
            return
        }
        view = v
    }

    fun teardown() {
        if (view == null) return
        view?.let { runCatching { wm?.removeView(it) }.getOrNull() }
        view = null
    }

    fun destroyAll() {
        teardown()
        ctx = null
        wm = null
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    // 轻量包装, 避免在宿主/工具间来回 import android.provider.Settings
    private object Settings2 {
        fun canOverlay(c: Context): Boolean =
            android.provider.Settings.canDrawOverlays(c)
    }
}
