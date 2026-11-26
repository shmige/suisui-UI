package com.svip.mansui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.svip.mansui.ui.component.dialog.KsuDialogController
import com.svip.mansui.ui.component.dialog.KsuDialogSpec

/**
 * 更新弹窗 —— 与公告弹窗**同一套** KSU/MD3 弹窗（都走 KsuDialogController → KsuDialogHost），
 * 所以两者风格天然一致。
 * 触发点：① 启动时公告弹窗点「确定」之后（仅当开着"检查更新"）② 主页「应用版本」行的「更新」按钮。
 */
object UpdateDialog {

    @JvmStatic
    fun show(context: Context) {
        if (!AppUpdate.hasUpdate) return
        val msg = AppUpdate.updateMessage.ifBlank { "发现新版本，建议更新后再使用。" }
        KsuDialogController.show(
            KsuDialogSpec(
                title = "有新版本",
                message = msg + "\n\n最新版：v" + AppUpdate.latestVersion,
                positiveText = "立即更新",
                negativeText = if (AppUpdate.updateMust) null else "稍后",
                cancelable = !AppUpdate.updateMust,
                onPositive = { openUrl(context) },
            )
        )
    }

    private fun openUrl(context: Context) {
        val url = AppUpdate.updateUrl
        if (url.isEmpty()) {
            md3Toast(context, "未获取到下载地址", long = true)
            return
        }
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { md3Toast(context, "无法打开浏览器，请手动访问：$url", long = true) }
    }
}
