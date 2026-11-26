package com.svip.mansui

import android.app.Activity
import android.os.Process
import com.svip.mansui.IPCurbco.TClass.SPUtils

// ── 运行环境模式常量 (2026-09-30: 模式选择改为 Compose 页 ui/screen/boot/ModeSelectScreen) ──
val MODE_NAMES = arrayOf("Root模式", "框架模式", "UID模式")
val MODE_DESCS = arrayOf(
    "需要Root权限，功能最完整",
    "无需Root，依赖虚拟机和框架",
    "共享游戏UID，无需Root直连"
)

// ── UID 渠道检测 (顶层共用): 当前渠道包已安装且 applicationInfo.uid == 本应用 uid ──
// 返回 0=通过 1=未安装(或不可见) 2=已安装但 uid 不一致
fun uidChannelCheck(activity: Activity): Int {
    val idx = channelSelIndex(activity)
    return try {
        val info = activity.packageManager.getPackageInfo(CHANNEL_PACKAGES[idx], 0)
        if (info.applicationInfo!!.uid == Process.myUid()) 0 else 2
    } catch (_: Exception) {
        1
    }
}

fun channelSelIndex(activity: Activity): Int =
    (SPUtils.getParam(activity.applicationContext, "channel_sel", 0) as Int)
        .coerceIn(0, CHANNEL_PACKAGES.size - 1)

fun currentChannelName(activity: Activity): String = CHANNEL_NAMES[channelSelIndex(activity)]
