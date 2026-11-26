package com.svip.mansui.ui.util

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import com.svip.mansui.ui.theme.LocalControlAlpha
import com.svip.mansui.ui.theme.LocalGlassMode
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported

/**
 * 顶栏模糊采样底。
 *
 * ⚠️ 2026-10-01 修复: 原来无条件先 `drawRect(surfaceColor)` 垫一块不透明 surface,
 * 顶栏 `textureBlur` 采样到这块白底 → **手机顶部会出现一大块白色区域** (主页的大标题栏尤其明显)。
 * 现在: 媒体背景模式(glass)下不垫白底, 直接采样真实背景(视频/图片), 顶栏才是真正的"磨砂透背景";
 * 白底模式下才垫一层 surface, 否则内容透明时模糊出来是空的。
 */
@Composable
fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
    val glass = LocalGlassMode.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    return rememberLayerBackdrop {
        if (!glass) drawRect(surfaceColor)
        drawContent()
    }
}

@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurActive: Boolean = true,
    content: @Composable () -> Unit,
) {
    // 磨砂色调: 原 0.87 近乎不透明, 视觉上就是一条白块 → 降到 0.55 且跟随控件透明度
    val tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f * LocalControlAlpha.current)
    Box(
        modifier = if (blurActive && backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f,
                colors = BlurColors(
                    blendColors = listOf(BlendColorEntry(color = tint)),
                ),
            )
        } else {
            Modifier
        },
    ) {
        content()
    }
}

/** 无模糊时顶栏的底色 (滚动到顶时不铺任何色, 让背景透出来) */
@Composable
fun transparentBarColor(glassOrBlur: Boolean): Color =
    if (glassOrBlur) Color.Transparent else MaterialTheme.colorScheme.surface
