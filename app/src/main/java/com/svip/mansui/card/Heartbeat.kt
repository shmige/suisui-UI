package com.svip.mansui.card

import android.content.Context

/** 纯 UI 版本：心跳/在线校验已删除（空实现）。 */
object Heartbeat {
    fun start(context: Context, onInvalid: (String) -> Unit) {}
    fun stop() {}
}