package com.svip.mansui

import android.os.Build
import android.view.WindowManager

/**
 * 窗口 / 悬浮窗兼容工具。
 *
 * ⚠ 2026-10-11 修复「悬浮窗在部分机型无效」:
 *   实测机型 realme RMX2200 (Android 10 / API 29) 上, 只要执行
 *     `WindowManager.LayoutParams.layoutInDisplayCutoutMode = ...`
 *   就会被 NP 加固的反射层拦到并抛:
 *     java.lang.NullPointerException: Attempt to invoke virtual method
 *     'Object java.lang.reflect.Method.invoke(Object, Object[])' on a null object reference
 *     at np.protect.*  →  com.svip.mansui.FloatWindow.addFloatingRoot
 *   而这句话原本只是**可选的刘海屏适配**, 却因为和建窗参数写在同一个 try / apply 块里,
 *   异常直接把整次建窗判死 → 表现成「悬浮窗启动失败，请重试」(FloatWindow),
 *   进入页与全屏渲染层也同样建不出来 (FloatEntry / OverlayHost / QuickButtons)。
 *
 *   统一改走本函数: 任何异常都吞掉, 最多是没做刘海适配, 绝不影响建窗。
 */
object WinCompat {

    /**
     * 刘海屏适配 (可选优化)。
     * @return true=设置成功; false=该机型不支持或被加固拦到, 已忽略
     */
    fun applyCutoutMode(lp: WindowManager.LayoutParams): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            true
        } catch (t: Throwable) {
            false
        }
    }
}
