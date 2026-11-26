package com.svip.mansui

import android.content.Context
import com.svip.mansui.IPCurbco.TClass.SPUtils

// ═══════════════════════════════════════════════
//  灵动岛控制 (1:1 移植 G:\bing DynamicIsland 的 native 渲染在 esp/EspRender.cpp):
//   - 开关: 设置页「灵动岛」→ applySetting (持久化 + native 开关 + 渲染层保活)
//   - 提示: 开关切换时 → notice(title, on) 推给 native (岛上弹出 Notice 行)
//   - 渲染层 (OverlayHost) 只在岛开启或 ESP 开启时存在; 岛关闭时提示不生效
// ═══════════════════════════════════════════════
object Island {

    private const val SP_KEY = "dynamic_island"

    private fun spOn(c: Context): Boolean =
        SPUtils.getParam(c.applicationContext, SP_KEY, false) as Boolean

    fun isOn(context: Context): Boolean = spOn(context)

    /** 设置页开关回调 */
    fun applySetting(context: Context, on: Boolean) {
        SPUtils.setParam(context.applicationContext, SP_KEY, on)
        com.svip.mansui.esp.EspCtl.prepare(context.applicationContext)
        MansuiNative.setIslandEnabled(on)
        OverlayHost.refresh()
    }

    /** 应用启动恢复 (已开启则保活渲染层 + native 开关) */
    fun ensure(context: Context) {
        val on = spOn(context)
        if (on) {
            runCatching { MansuiNative.setIslandEnabled(true) }.getOrNull()
            runCatching { OverlayHost.refresh() }.getOrNull()
        }
    }

    /** 功能开关提示 (岛未开启时不生效 — 渲染层不存在且 native 忽略) */
    fun notice(context: Context?, title: String, on: Boolean) {
        val c = context ?: AppState.getContext ?: return
        if (!spOn(c)) return
        OverlayHost.refresh()   // 确保渲染层在
        MansuiNative.islandNotice(title, if (on) "开启" else "关闭", if (on) 0 else 1)
    }

    /** 按钮类动作提示 (非开关, 无开/关概念): 灵动岛显示"已执行" */
    fun action(context: Context?, title: String) {
        val c = context ?: AppState.getContext ?: return
        if (!spOn(c)) return
        OverlayHost.refresh()
        MansuiNative.islandNotice(title, "已执行", 0)
    }

    /** 自定义提示 (type: 0对 1错 2警示 3通知 4提示); 诊断类信息用 */
    fun tip(context: Context?, title: String, content: String, type: Int) {
        val c = context ?: AppState.getContext ?: return
        if (!spOn(c)) return
        OverlayHost.refresh()
        MansuiNative.islandNotice(title, content, type)
    }
}
