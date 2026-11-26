package com.svip.mansui.ui.screen.settings

import androidx.compose.runtime.Immutable
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.svip.mansui.ui.UiMode

@Immutable
data class SettingsUiState(
    val uiMode: String = UiMode.DEFAULT_VALUE,
    val checkUpdate: Boolean = true,
    val themeMode: Int = 0,
    val miuixMonet: Boolean = false,
    val keyColor: Int = 0,
    val colorStyle: String = PaletteStyle.TonalSpot.name,
    val colorSpec: String = ColorSpec.SpecVersion.Default.name,
    val enablePredictiveBack: Boolean = true,
    val enableBlur: Boolean = true,
    val enableFloatingBottomBar: Boolean = true,
    val enableFloatingBottomBarBlur: Boolean = false,
    val pageScale: Float = 1.0f,
    // ── 离情梦业务态 (由 MainActivity 注入, 不持久化在 settings 仓库) ──
    val envName: String = "",
    val backgroundName: String = "",
    val island: Boolean = false,
)

@Immutable
data class SettingsScreenActions(
    val onSetCheckUpdate: (Boolean) -> Unit,
    val onOpenTheme: () -> Unit,
    val onOpenBackground: () -> Unit,
    val onOpenFloatWindow: () -> Unit,
    val onOpenAbout: () -> Unit,
    val onSetIsland: (Boolean) -> Unit,
    val onResetEnv: () -> Unit,
    val onUninstall: () -> Unit,
)
