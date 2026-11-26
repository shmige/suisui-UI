package com.svip.mansui.ui.screen.boot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.svip.mansui.MODE_DESCS
import com.svip.mansui.MODE_NAMES
import com.svip.mansui.md3Toast
import com.svip.mansui.ui.component.material.SegmentedColumn
import com.svip.mansui.ui.component.material.SegmentedRadioItem

/**
 * 运行环境选择 (Compose) —— 与"主题设置/背景设置"同一套 KSU 分段列表布局,
 * 不再是独立样式的居中弹窗卡片。
 *
 * 语义与原 ModeSelectDialog 一致:
 *  - 有 root 默认 ROOT; 无 root 默认框架
 *  - ROOT 不可用时置灰但仍可点 → 重新检测
 *  - 已检测到 ROOT 时 UID 置灰不可选
 *  - 环境变更时顶部显示红色 banner
 */
@Composable
fun ModeSelectScreen(
    banner: String?,
    rootOk: Boolean,
    rootUnknown: Boolean,
    rootChecking: Boolean,
    selectedMode: Int,
    onSelect: (Int) -> Unit,
    onRetryRoot: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            Text(
                text = "离情梦",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "选择运行环境",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "选择后不可更改，请谨慎选择",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!banner.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = banner,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(20.dp))

            SegmentedColumn(
                content = (0 until 3).map { index ->
                    {
                        val rootDisabled = index == 0 && !rootOk
                        val uidDisabled = index == 2 && rootOk
                        val dimmed = rootDisabled || uidDisabled

                        Box(modifier = Modifier.alpha(if (dimmed) 0.55f else 1f)) {
                            SegmentedRadioItem(
                                title = MODE_NAMES[index],
                                summary = when {
                                    rootDisabled -> if (rootUnknown) {
                                        "需要Root权限（授权未响应，点此重试）"
                                    } else {
                                        "需要Root权限（设备未检测到 ROOT，点此重试）"
                                    }
                                    uidDisabled -> "已检测到 ROOT，请使用 Root 模式"
                                    else -> MODE_DESCS[index]
                                },
                                selected = selectedMode == index,
                                onClick = {
                                    when {
                                        rootDisabled -> {
                                            if (rootChecking) md3Toast(context, "正在重新检测中…")
                                            else onRetryRoot()
                                        }
                                        uidDisabled ->
                                            md3Toast(context, "检测到 ROOT，Root 模式下不可使用 UID 模式")
                                        else -> onSelect(index)
                                    }
                                },
                            )
                        }
                    }
                }
            )

            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) { Text("取消") }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) { Text("开始部署") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
