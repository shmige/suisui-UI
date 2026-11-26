package com.svip.mansui.ui.screen.background

import androidx.compose.runtime.Immutable

/** 背景设置 (设置 → 背景设置 子页) */
@Immutable
data class BackgroundUiState(
    /** 0=默认背景 1=官方动态 2=官方图库 3=自定义 */
    val mode: Int = 0,
    /** 官方动态: 0=全部轮播 1=离情梦 2=待良归来 */
    val dynamicSel: Int = 0,
    /** 官方图库选中的图片名 */
    val officialImage: String = "",
    /** 自定义背景当前描述 */
    val customLabel: String = "",
    /** 官方图库候选 (与 BackgroundManager.OFFICIAL_BG_IMAGES 一致) */
    val officialImages: List<String> = emptyList(),
    /** 主页/设置页控件不透明度 0.1~1.0 (背景图/视频模式下让控件半透明, 背景透出来) */
    val controlAlpha: Float = 0.85f,
)

@Immutable
data class BackgroundScreenActions(
    val onBack: () -> Unit,
    val onSetMode: (Int) -> Unit,
    val onSetDynamicSel: (Int) -> Unit,
    val onSetOfficialImage: (String) -> Unit,
    val onPickImage: () -> Unit,
    val onPickVideo: () -> Unit,
    val onSetControlAlpha: (Float) -> Unit,
)
