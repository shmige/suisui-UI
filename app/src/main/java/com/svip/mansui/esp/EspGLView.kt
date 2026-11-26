package com.svip.mansui.esp

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import com.svip.mansui.MansuiNative

// ═══════════════════════════════════════════════
//  ESP 渲染窗口 (移植自 梦之喵羽 GLEspView)
//  TextureView 而非 SurfaceView: SurfaceView 在 SurfaceFlinger 有独立 surface 层,
//  部分 OEM (MIUI/ColorOS) InputDispatcher 会按 surface 层路由触摸,
//  即使 FLAG_NOT_TOUCHABLE 也拦截触摸; TextureView 内容属于 window 自身层级,
//  FLAG_NOT_TOUCHABLE 才能真正穿透。
//  surface 就绪 → native EGL+ImGui 渲染循环 (esp/SurfaceCreate)
// ═══════════════════════════════════════════════
class EspGLView(context: Context) : TextureView(context) {

    init {
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        isEnabled = false
        isOpaque = false

        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                try {
                    st.setDefaultBufferSize(width, height)
                    MansuiNative.surfaceCreate(Surface(st))
                    // 数据侧以实际渲染窗口尺寸为准 (纯自适应, 防机型/横竖屏差异)
                    EspCtl.syncOverlaySize(width, height)
                } catch (_: Exception) {
                }
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                try {
                    MansuiNative.surfaceChanged(width, height)
                    EspCtl.syncOverlaySize(width, height)
                } catch (_: Exception) {
                }
            }

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                try {
                    MansuiNative.surfaceDestroy()
                } catch (_: Exception) {
                }
                return true
            }

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
            }
        }
    }
}
