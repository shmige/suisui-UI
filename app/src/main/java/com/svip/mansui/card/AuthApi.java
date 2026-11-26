package com.svip.mansui.card;

import android.content.Context;

/**
 * 纯 UI 版本：卡密网络验证已删除。
 * 保留同名接口，永远返回成功，方便直接进入 UI 预览。
 */
public class AuthApi {

    public interface LoginListener {
        void onSuccess(String expiryDate);
        void onFailure(String error);
        void onStoreFailed(String expiryDate);
    }

    public interface UnbindListener {
        void onSuccess(String remainingTimes);
        void onFailure(String error);
    }

    public static void login(final String kami, final Context context, final LoginListener listener) {
        if (listener == null) return;
        listener.onSuccess("本地模式");
    }

    public static void unbind(final String kami, final Context context, final UnbindListener listener) {
        if (listener == null) return;
        listener.onSuccess("本地模式");
    }
}
