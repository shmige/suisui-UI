package com.svip.mansui.ui.screen.background

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.svip.mansui.ui.component.material.SegmentedColumn
import com.svip.mansui.ui.component.material.SegmentedRadioItem
import com.svip.mansui.ui.component.material.TonalCard

/**
 * 背景设置页 (Material) — 布局与"主题设置"完全一致:
 * LargeFlexibleTopAppBar(立即收起) + 分段列表分组, 无突兀观感。
 */
@Composable
fun BackgroundScreenMaterial(
    state: BackgroundUiState,
    actions: BackgroundScreenActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    // 与主题设置页一致: 进入即收起顶部大标题, 内容不被压到下方
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
                title = { Text("背景设置") },
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
                // ① 背景类型
                SegmentedColumn(
                    content = listOf(
                        {
                            SegmentedRadioItem(
                                title = "默认背景",
                                summary = "纯白底, 不加载任何媒体",
                                selected = state.mode == 0,
                                onClick = { actions.onSetMode(0) },
                            )
                        },
                        {
                            SegmentedRadioItem(
                                title = "官方动态",
                                summary = "内置视频, 静音循环播放",
                                selected = state.mode == 1,
                                onClick = { actions.onSetMode(1) },
                            )
                        },
                        {
                            SegmentedRadioItem(
                                title = "官方图库",
                                summary = "满穗图库 " + state.officialImages.size + " 张",
                                selected = state.mode == 2,
                                onClick = { actions.onSetMode(2) },
                            )
                        },
                        {
                            SegmentedRadioItem(
                                title = "自定义",
                                summary = state.customLabel.ifBlank { "从相册选择图片或视频" },
                                selected = state.mode == 3,
                                onClick = { actions.onSetMode(3) },
                            )
                        }
                    )
                )

                // ② 官方动态: 选哪一段视频
                if (state.mode == 1) {
                    SegmentedColumn(
                        title = "播放内容",
                        content = listOf(
                            {
                                SegmentedRadioItem(
                                    title = "全部轮播",
                                    summary = "播完自动切换下一段",
                                    selected = state.dynamicSel == 0,
                                    onClick = { actions.onSetDynamicSel(0) },
                                )
                            },
                            {
                                SegmentedRadioItem(
                                    title = "离情梦",
                                    selected = state.dynamicSel == 1,
                                    onClick = { actions.onSetDynamicSel(1) },
                                )
                            },
                            {
                                SegmentedRadioItem(
                                    title = "待良归来",
                                    selected = state.dynamicSel == 2,
                                    onClick = { actions.onSetDynamicSel(2) },
                                )
                            }
                        )
                    )
                }

                // ③ 官方图库: 选哪张图
                if (state.mode == 2) {
                    SegmentedColumn(
                        title = "选择图片",
                        content = state.officialImages.map { name ->
                            {
                                SegmentedRadioItem(
                                    title = name,
                                    selected = state.officialImage == name,
                                    onClick = { actions.onSetOfficialImage(name) },
                                )
                            }
                        }
                    )
                }

                // ④ 自定义: 选图 / 选视频
                if (state.mode == 3) {
                    SegmentedColumn(
                        title = "自定义来源",
                        content = listOf(
                            {
                                SegmentedRadioItem(
                                    title = "选择图片",
                                    summary = "进入 9:16 裁剪页",
                                    selected = false,
                                    onClick = actions.onPickImage,
                                )
                            },
                            {
                                SegmentedRadioItem(
                                    title = "选择视频",
                                    summary = "直接循环播放",
                                    selected = false,
                                    onClick = actions.onPickVideo,
                                )
                            }
                        )
                    )
                }

                // ⑤ 主页/设置页控件透明度 (有背景时才有意义)
                if (state.mode != 0) {
                    TonalCard {
                        var slider by remember(state.controlAlpha) {
                            mutableFloatStateOf(state.controlAlpha)
                        }
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Opacity,
                                    contentDescription = "主页控件透明度",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "主页控件透明度",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "调低可让背景从卡片后透出来（只影响主页/设置页的控件底衬）",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                                Text(
                                    text = "${(slider * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Slider(
                                value = slider,
                                onValueChange = { slider = it },
                                onValueChangeFinished = { actions.onSetControlAlpha(slider) },
                                valueRange = 0.1f..1f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
