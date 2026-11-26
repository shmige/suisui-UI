package com.svip.mansui.ui.screen.floatwindow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.svip.mansui.ui.component.material.SegmentedColumn
import com.svip.mansui.ui.component.material.SegmentedListItem
import com.svip.mansui.ui.component.material.SegmentedRadioItem

/**
 * 悬浮窗设置页 (Material) — 布局与"主题设置"完全一致。
 * 悬浮窗权限入口已从主页挪到这里。
 */
@Composable
fun FloatWindowScreenMaterial(
    state: FloatWindowUiState,
    actions: FloatWindowScreenActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                title = { Text("悬浮窗设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ① 悬浮窗权限 (原主页权限卡搬到这里)
                SegmentedColumn(
                    content = listOf(
                        {
                            SegmentedListItem(
                                onClick = actions.onRequestOverlay,
                                headlineContent = { Text("悬浮窗权限") },
                                supportingContent = {
                                    Text(
                                        if (state.overlayGranted) "已具备悬浮窗权限"
                                        else "缺少悬浮窗权限，无法显示悬浮窗"
                                    )
                                },
                                leadingContent = { Icon(Icons.Filled.Widgets, null) },
                                trailingContent = {
                                    AssistChip(
                                        onClick = actions.onRequestOverlay,
                                        label = {
                                            Text(if (state.overlayGranted) "已授权" else "去授权")
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (state.overlayGranted) {
                                                    Icons.Filled.CheckCircle
                                                } else {
                                                    Icons.Filled.ErrorOutline
                                                },
                                                contentDescription = null,
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(),
                                    )
                                },
                            )
                        }
                    )
                )

                // ② 悬浮球模式
                SegmentedColumn(
                    title = "悬浮球模式",
                    content = listOf(
                        {
                            SegmentedRadioItem(
                                title = "吸附边缘",
                                summary = "拖放手后自动贴边",
                                selected = state.ballMode == 0,
                                onClick = { actions.onSetBallMode(0) },
                            )
                        },
                        {
                            SegmentedRadioItem(
                                title = "独立位置",
                                summary = "自由摆放, 不自动贴边",
                                selected = state.ballMode == 1,
                                onClick = { actions.onSetBallMode(1) },
                            )
                        }
                    )
                )

                // ③ 悬浮球头像
                SegmentedColumn(
                    title = "悬浮球头像",
                    content = listOf(
                        {
                            SegmentedRadioItem(
                                title = "默认满穗",
                                selected = !state.ballCustom,
                                onClick = actions.onResetBallImage,
                            )
                        },
                        {
                            SegmentedRadioItem(
                                title = "自定义图片",
                                summary = "从相册选择并裁成 1:1",
                                selected = state.ballCustom,
                                onClick = actions.onPickBallImage,
                            )
                        }
                    )
                )

                // ④ 悬浮窗背景
                SegmentedColumn(
                    title = "悬浮窗背景",
                    content = listOf(
                        {
                            SegmentedRadioItem(
                                title = "实白磨砂",
                                selected = state.bgMode == 0,
                                onClick = { actions.onSetBgMode(0) },
                            )
                        },
                        {
                            SegmentedRadioItem(
                                title = "晶莹玻璃",
                                selected = state.bgMode == 1,
                                onClick = { actions.onSetBgMode(1) },
                            )
                        },
                        {
                            SegmentedRadioItem(
                                title = "官方背景",
                                summary = "内置插画素材",
                                selected = state.bgMode == 3,
                                onClick = { actions.onSetBgMode(3) },
                            )
                        },
                        {
                            SegmentedRadioItem(
                                title = "自定义图片",
                                summary = "从相册选择",
                                selected = state.bgMode == 2,
                                onClick = actions.onPickPanelImage,
                            )
                        }
                    )
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
