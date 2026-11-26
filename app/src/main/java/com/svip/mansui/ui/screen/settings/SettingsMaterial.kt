package com.svip.mansui.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.svip.mansui.R
import com.svip.mansui.ui.component.material.SegmentedColumn
import com.svip.mansui.ui.component.material.SegmentedListItem
import com.svip.mansui.ui.component.material.SegmentedSwitchItem
import com.svip.mansui.ui.component.material.SmallOutlineButton
import com.svip.mansui.ui.theme.LocalEnableBlur
import com.svip.mansui.ui.theme.LocalGlassMode
import com.svip.mansui.ui.util.BlurredBar
import com.svip.mansui.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop

/**
 * 设置页 (Material) — 结构照搬 KernelSU-Style-UI-Kit SettingsMaterial
 * 顺序: 检查更新 / 当前环境(重置) / 主题设置 / 背景设置 / 悬浮窗设置 / 灵动岛 / 关于
 * (2026-09-30: 删"加入我们"; "当前环境"上移到检查更新下面; 顶部大标题进入即收起, 不再压得太低)
 */

/** 「灵动岛」图标: 自绘胶囊 —— Material 里没有贴切的, 原来的 SmartToy(机器人头)不符合 */
private val IslandVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "DynamicIsland",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(8f, 7f)
            horizontalLineTo(16f)
            curveTo(19.31f, 7f, 22f, 9.24f, 22f, 12f)
            curveTo(22f, 14.76f, 19.31f, 17f, 16f, 17f)
            horizontalLineTo(8f)
            curveTo(4.69f, 17f, 2f, 14.76f, 2f, 12f)
            curveTo(2f, 9.24f, 4.69f, 7f, 8f, 7f)
            close()
        }
    }.build()
}
@Composable
fun SettingPagerMaterial(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val glass = LocalGlassMode.current
    val enableBlur = LocalEnableBlur.current
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val scrollState = rememberScrollState()
    // 滚动到顶时不加模糊/不铺底色 (修"开启模糊后手机顶部一大块白色区域")
    val scrolled = scrollState.value > 0
    val barColor = if (glass || (blurBackdrop != null && scrolled)) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surface
    }

    // 标题上移: 与"主题设置"页一致, 进入即收起大标题
    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    Scaffold(
        topBar = {
            BlurredBar(blurBackdrop, blurActive = scrolled) {
                TopBar(scrollBehavior = scrollBehavior, barColor = barColor)
            }
        },
        containerColor = if (glass) Color.Transparent else MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { paddingValues ->
        Box(modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier) {
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ① 检查更新 + 当前环境(重置)
                SegmentedColumn(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    content = listOf(
                        {
                            SegmentedSwitchItem(
                                icon = Icons.Filled.Update,
                                title = stringResource(id = R.string.settings_check_update),
                                summary = stringResource(id = R.string.settings_check_update_summary),
                                checked = uiState.checkUpdate,
                                onCheckedChange = actions.onSetCheckUpdate
                            )
                        },
                        {
                            SegmentedListItem(
                                // 整行不可点: 只有右侧「重置」按钮生效, 防止点到布局就重置
                                headlineContent = { Text(stringResource(id = R.string.settings_env)) },
                                supportingContent = { Text(uiState.envName) },
                                leadingContent = {
                                    Icon(Icons.Filled.Memory, stringResource(id = R.string.settings_env))
                                },
                                trailingContent = {
                                    SmallOutlineButton(
                                        text = stringResource(id = R.string.settings_env_reset),
                                        onClick = actions.onResetEnv,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            )
                        }
                    )
                )

                // ② 主题设置 / 背景设置 / 悬浮窗设置
                SegmentedColumn(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    content = listOf(
                        {
                            SegmentedListItem(
                                onClick = actions.onOpenTheme,
                                headlineContent = { Text(stringResource(id = R.string.settings_theme)) },
                                supportingContent = { Text(stringResource(id = R.string.settings_theme_summary)) },
                                leadingContent = {
                                    Icon(Icons.Filled.Palette, stringResource(id = R.string.settings_theme))
                                },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                }
                            )
                        },
                        {
                            SegmentedListItem(
                                onClick = actions.onOpenBackground,
                                headlineContent = { Text(stringResource(id = R.string.settings_background)) },
                                supportingContent = {
                                    Text(
                                        uiState.backgroundName.ifEmpty {
                                            stringResource(id = R.string.settings_background_summary)
                                        }
                                    )
                                },
                                leadingContent = {
                                    Icon(Icons.Filled.Image, stringResource(id = R.string.settings_background))
                                },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                }
                            )
                        },
                        {
                            SegmentedListItem(
                                onClick = actions.onOpenFloatWindow,
                                headlineContent = { Text(stringResource(id = R.string.settings_float_window)) },
                                supportingContent = {
                                    Text(stringResource(id = R.string.settings_float_window_summary))
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Filled.Widgets,
                                        stringResource(id = R.string.settings_float_window)
                                    )
                                },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                }
                            )
                        }
                    )
                )

                // ③ 灵动岛
                SegmentedColumn(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    content = listOf {
                        SegmentedSwitchItem(
                            icon = IslandVector,
                            title = stringResource(id = R.string.settings_island),
                            summary = stringResource(id = R.string.settings_island_summary),
                            checked = uiState.island,
                            onCheckedChange = actions.onSetIsland
                        )
                    }
                )

                // ④ 关于 / 卸载 (卸载入口照搬 KernelSU settings_uninstall)
                SegmentedColumn(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    content = listOf(
                        {
                            SegmentedListItem(
                                onClick = actions.onOpenAbout,
                                headlineContent = { Text(stringResource(id = R.string.about)) },
                                supportingContent = { Text(stringResource(id = R.string.about_source_link)) },
                                leadingContent = {
                                    Icon(Icons.Filled.ContactPage, stringResource(id = R.string.about))
                                },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                }
                            )
                        },
                        {
                            SegmentedListItem(
                                onClick = actions.onUninstall,
                                headlineContent = {
                                    Text(
                                        text = stringResource(id = R.string.settings_uninstall),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                supportingContent = { Text(stringResource(id = R.string.settings_uninstall_summary)) },
                                leadingContent = {
                                    Icon(
                                        Icons.Filled.DeleteForever,
                                        stringResource(id = R.string.settings_uninstall),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                }
                            )
                        }
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                Spacer(modifier = Modifier.height(bottomInnerPadding))
            }
        }
    }
}

@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null,
    barColor: Color = MaterialTheme.colorScheme.surface,
) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = barColor,
            scrolledContainerColor = barColor
        ),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}
