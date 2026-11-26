package com.svip.mansui.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * 安全读取系统"动态取色"(Material You / monet)。
 *
 * ⚠️ 2026-10-01 修闪退 (实测机型 OPPO PCEM00 / ColorOS / **Android 11 API 30**):
 *
 * ```
 * android.content.res.Resources$NotFoundException: Resource ID #0x106002b
 *   at android.content.res.Resources.getColor
 *   at androidx.compose.material3.DynamicTonalPaletteKt.dynamicDarkColorScheme
 *   at com.svip.mansui.ui.theme.MaterialThemeKt.MaterialTemplateTheme
 * ```
 *
 * `0x106002b` 是 **`android:` 包的系统色资源** (`@android:color/system_accent1_*` 那一套),
 * **只有 Android 12(API 31)+ 才提供**。所以这些 `dynamicXxxColorScheme()` 必须做版本判断,
 * 否则 Android 11 及以下一进软件就闪退 —— 这就是"部分机型给完悬浮窗权限进软件就闪退"的真因
 * (和悬浮窗权限、和 so 层都没有关系, 只是那些机器系统版本低)。
 *
 * 另外部分定制 ROM 即使 API 31+ 也可能缺个别资源, 所以再套一层 runCatching 兜底。
 *
 * @return 系统动态色板; 当前系统不支持时返回 null, 调用方用 [fallbackScheme] 兜底。
 */
fun safeDynamicColorScheme(context: Context, dark: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return runCatching {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }.getOrNull()
}

/** 动态取色不可用时的兜底色板: Material3 基线 (浅色/深色) */
fun fallbackScheme(dark: Boolean): ColorScheme =
    if (dark) darkColorScheme() else lightColorScheme()
