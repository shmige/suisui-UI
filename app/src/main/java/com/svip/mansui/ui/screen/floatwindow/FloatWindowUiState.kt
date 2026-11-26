package com.svip.mansui.ui.screen.floatwindow

import androidx.compose.runtime.Immutable

/** 悬浮窗设置 (设置 → 悬浮窗设置 子页) */
@Immutable
data class FloatWindowUiState(
    /** 悬浮窗权限是否已授予 */
    val overlayGranted: Boolean = false,
    /** 悬浮球模式: 0=吸附边缘 1=独立位置 */
    val ballMode: Int = 1,
    /** 是否使用自定义悬浮球头像 */
    val ballCustom: Boolean = false,
    /** 悬浮窗背景: 0=实白磨砂 1=晶莹玻璃 3=官方背景 2=自定义图片 */
    val bgMode: Int = 0,
)

@Immutable
data class FloatWindowScreenActions(
    val onBack: () -> Unit,
    val onRequestOverlay: () -> Unit,
    val onSetBallMode: (Int) -> Unit,
    val onPickBallImage: () -> Unit,
    val onResetBallImage: () -> Unit,
    val onPickPanelImage: () -> Unit,
    val onSetBgMode: (Int) -> Unit,
)
