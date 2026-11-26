package com.svip.mansui.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/** 媒体背景(视频/图片)模式下: 页面容器透明, 让背景透出 (背景系统适配) */
val LocalGlassMode = staticCompositionLocalOf { false }

/**
 * 主页/设置页"控件不透明度" (0.1~1.0)。
 * 背景图/视频模式下控件底衬按此值半透明, 让背景透出来 (背景设置 → 主页控件透明度)。
 * 默认 1f = 完全不透明 (仅在没有媒体背景时也走这个默认值)。
 */
val LocalControlAlpha = staticCompositionLocalOf { 1f }
