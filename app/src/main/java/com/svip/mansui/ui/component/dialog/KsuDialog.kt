package com.svip.mansui.ui.component.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 全局 MD3 / KSU 风格弹窗 (Compose)。
 *
 * 设计对齐 KernelSU-Style-UI-Kit 的 Dialog: surfaceContainerHigh + 28dp 大圆角 +
 * headlineSmall 标题 + bodyMedium 正文 + 右下文字按钮 (primary / onSurfaceVariant)。
 * 跟随当前主题 (深浅色 / 主题色) 与页面缩放。
 */
@Immutable
data class KsuDialogSpec(
    val title: String,
    val message: String? = null,
    /** 非空则渲染单选列表 (渠道 / 选项) */
    val items: List<String> = emptyList(),
    val selectedIndex: Int = -1,
    val positiveText: String? = null,
    val negativeText: String? = null,
    val cancelable: Boolean = true,
    val onPositive: (() -> Unit)? = null,
    val onNegative: (() -> Unit)? = null,
    val onItemSelected: ((Int) -> Unit)? = null,
)

/** 单例控制器: 任意位置 (含 Java) 都能弹窗, 由 MainActivity 的 KsuDialogHost 渲染 */
object KsuDialogController {
    var current by mutableStateOf<KsuDialogSpec?>(null)
        private set

    fun show(spec: KsuDialogSpec) {
        current = spec
    }

    fun dismiss() {
        current = null
    }

    val isShowing: Boolean get() = current != null
}

@Composable
fun KsuDialogHost() {
    val spec = KsuDialogController.current ?: return
    Dialog(
        onDismissRequest = { if (spec.cancelable) KsuDialogController.dismiss() },
        properties = DialogProperties(
            dismissOnBackPress = spec.cancelable,
            dismissOnClickOutside = spec.cancelable,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(vertical = 24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 14.dp)) {
                Text(
                    text = spec.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                spec.message?.takeIf { it.isNotBlank() }?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                if (spec.items.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        spec.items.forEachIndexed { index, label ->
                            val selected = index == spec.selectedIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val cb = spec.onItemSelected
                                        KsuDialogController.dismiss()
                                        cb?.invoke(index)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = {
                                        val cb = spec.onItemSelected
                                        KsuDialogController.dismiss()
                                        cb?.invoke(index)
                                    },
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                val hasNegative = spec.negativeText != null
                val hasPositive = spec.positiveText != null
                if (hasNegative || hasPositive) {
                    Spacer(Modifier.size(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (hasNegative) {
                            TextButton(onClick = {
                                val cb = spec.onNegative
                                KsuDialogController.dismiss()
                                cb?.invoke()
                            }) {
                                Text(
                                    text = spec.negativeText!!,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (hasPositive) {
                            TextButton(onClick = {
                                val cb = spec.onPositive
                                KsuDialogController.dismiss()
                                cb?.invoke()
                            }) {
                                Text(text = spec.positiveText!!)
                            }
                        }
                    }
                }
            }
        }
    }
}
