package com.svip.mansui

import android.os.Handler
import android.os.Looper
import com.svip.mansui.ui.component.dialog.KsuDialogController
import com.svip.mansui.ui.component.dialog.KsuDialogSpec

/**
 * Java 侧可调用的 MD3/KSU 弹窗入口 (NoticeApi 等 Java 类用)。
 * 实际渲染由 MainActivity 的 KsuDialogHost 完成, 跟随当前 Compose 主题。
 */
object AppDialog {

    private val main = Handler(Looper.getMainLooper())

    /** 确认/提示弹窗; negativeText=null 时不显示取消按钮; cancelable=false 时不可点外部关闭 */
    @JvmStatic
    fun alert(
        title: String,
        message: String,
        positiveText: String?,
        negativeText: String?,
        cancelable: Boolean,
        onPositive: Runnable?,
        onNegative: Runnable?,
    ) {
        main.post {
            KsuDialogController.show(
                KsuDialogSpec(
                    title = title,
                    message = message,
                    positiveText = positiveText ?: "确定",
                    negativeText = negativeText,
                    cancelable = cancelable,
                    onPositive = onPositive?.let { r -> { r.run() } },
                    onNegative = onNegative?.let { r -> { r.run() } },
                )
            )
        }
    }

    /** 单选列表弹窗 (渠道版本 / 外观选项等) */
    @JvmStatic
    fun options(title: String, items: Array<String>, selected: Int, onSelect: Runnable?) {
        main.post {
            KsuDialogController.show(
                KsuDialogSpec(
                    title = title,
                    items = items.toList(),
                    selectedIndex = selected,
                    negativeText = "取消",
                    onItemSelected = if (onSelect == null) null else { _ -> onSelect.run() },
                )
            )
        }
    }
}
