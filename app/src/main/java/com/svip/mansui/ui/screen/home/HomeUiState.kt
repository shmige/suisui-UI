package com.svip.mansui.ui.screen.home

import androidx.compose.runtime.Immutable

/**
 * 主页状态 (离情梦业务: 卡密 / 渠道 / 运行状态)
 * 2026-09-30: 悬浮窗权限卡移到"悬浮窗设置"页, 主页不再持有权限态
 */
@Immutable
data class HomeUiState(
    val appVersion: AppVersion,
    /** 当前运行模式名 (Root模式 / 框架模式 / UID模式) */
    val modeName: String,
    /** 当前渠道版本名 */
    val channelName: String,
    /** 卡密输入内容 */
    val kami: String,
    /** 卡密是否明文显示 */
    val kamiVisible: Boolean,
    /** 悬浮窗是否已显示 */
    val floatShown: Boolean,
    /** 服务端最新版本 (空 = 还没取到) */
    val latestVersion: String = "",
    /** 是否有新版本 (有新版本时 trailing 变成「更新」按钮) */
    val hasUpdate: Boolean = false,
)

@Immutable
data class HomeActions(
    val onKamiChange: (String) -> Unit,
    val onToggleKamiVisible: () -> Unit,
    val onChannelClick: () -> Unit,
    val onStart: () -> Unit,
    val onUnbind: () -> Unit,
    val onBuy: () -> Unit,
    val onUpdate: () -> Unit,
)
