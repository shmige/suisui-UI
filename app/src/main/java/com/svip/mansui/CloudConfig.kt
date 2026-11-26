package com.svip.mansui

/**
 * 纯 UI 版本：云端配置已删除。
 * 保留同名对象与空 map，仅用于 UI 层读取（永远为空）。
 */
object CloudConfig {

    val lastConfig: Map<String, String> = emptyMap()

    fun fetch(onDone: (Map<String, String>) -> Unit = {}) {
        onDone(emptyMap())
    }
}
