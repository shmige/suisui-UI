package com.svip.mansui;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.LinearLayout;

// 全局状态持有 (纯 Java): Kotlin UI 与 Root IPC 共用,
// 避免 Java 类引用 Kotlin 类 (AIDE 不解析 .kt 文件会报"未知实体 MainActivity")
public class AppState {
    public static boolean app_init_mode = false;
    // 运行模式: 0=Root 1=框架 2=UID (与 app_Operation_mode_root 同步: root 模式时 true)
    public static int app_Operation_mode = 0;
    public static boolean app_Operation_mode_root = false;
    public static boolean floatWindowShown = false;
    public static Context getContext = null;
    public static AlertDialog alertDialogs = null;
    public static LinearLayout linearLayouts = null;
}
