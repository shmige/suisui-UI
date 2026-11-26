package com.svip.mansui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 版本 / 更新状态（NoticeApi 拉取后写入，主页「应用版本」行与更新弹窗共用）。
 * 用 Compose 可变状态承载 → 拉取完成后主页那一行会自动刷新。
 */
object AppUpdate {

    /** 当前安装版本（App.onCreate 里设为 BuildConfig.VERSION_NAME） */
    @JvmStatic
    var currentVersion: String = ""

    /** 服务端最新版本（空 = 还没取到） */
    @JvmStatic
    var latestVersion by mutableStateOf("")

    /** 更新包地址 */
    @JvmStatic
    var updateUrl: String = ""

    /** 更新说明 */
    @JvmStatic
    var updateMessage: String = ""

    /** 是否强制更新 */
    @JvmStatic
    var updateMust: Boolean = false

    /** 是否有新版本（版本号不同即视为有新版本） */
    @JvmStatic
    val hasUpdate: Boolean
        get() = latestVersion.isNotEmpty() &&
                currentVersion.isNotEmpty() &&
                latestVersion != currentVersion

    /** 已确认是最新版（拉取成功且相同） */
    @JvmStatic
    val isLatest: Boolean
        get() = latestVersion.isNotEmpty() &&
                currentVersion.isNotEmpty() &&
                latestVersion == currentVersion

    /**
     * 是否应该弹更新提示 = 有新版本 且「检查更新」开关为开。
     * 关掉开关即"忽略"，但仍会显示版本对比信息。
     */
    @JvmStatic
    fun shouldPrompt(context: android.content.Context): Boolean {
        if (!hasUpdate) return false
        return context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            .getBoolean("check_update", true)
    }
}
