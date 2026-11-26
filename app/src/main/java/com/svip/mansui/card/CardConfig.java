package com.svip.mansui.card;

/**
 * 卡密/更新服务全局配置 (微验平台)
 */
public final class CardConfig {

    private CardConfig() {}

    /** 与 manifest versionName 一致; 服务端 version 不同则弹更新 */
    public static final String CURRENT_VERSION = "2.5";

    /** 本地卡密存储: /sdcard/离情梦/km 与 /imei (Android 11+ 需"所有文件访问"权限) */
    public static final String STORE_DIR = "离情梦";
    public static final String KAMI_FILE = "km";
    public static final String IMEI_FILE = "imei";

    // 服务端返回的"登录成功"状态码 (其余字段名由服务端混淆命名, 勿改)
    public static final String LOGIN_OK_CODE = "21753";
    public static final String UNBIND_OK_CODE = "200";
}
