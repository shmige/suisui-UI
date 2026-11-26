package com.svip.mansui.card;

import android.content.Context;
import android.os.Environment;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;

/**
 * 卡密/设备码本地存储。
 * 路径: /sdcard/离情梦/km (卡密)、/imei (设备码)。
 * initPaths() 必须在任何读写前调用 (MainActivity.onCreate 立即调用)。
 */
public final class DeviceStore {

    public static String kamiPath = null;
    public static String imeiPath = null;

    private DeviceStore() {}

    public static void initPaths(Context context) {
        File dir = new File(Environment.getExternalStorageDirectory(), CardConfig.STORE_DIR);
        if (!dir.exists()) dir.mkdirs();
        kamiPath = new File(dir, CardConfig.KAMI_FILE).getAbsolutePath();
        imeiPath = new File(dir, CardConfig.IMEI_FILE).getAbsolutePath();
    }

    /**
     * 获取设备码 (ANDROID_ID; 读本地私有文件缓存, 不存在则生成保存)
     *
     * ⚠️ 这个码是卡密绑定的"设备指纹", 一旦变化服务器就会认为换机
     * (表现为"卡密不存在 / 已绑定其他设备") → 每步都打日志, 便于远程定位。
     */
    public static String getDeviceCode(Context context) {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) dir = context.getFilesDir();
        File cache = new File(dir, "tools");

        if (cache.exists()) {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(new FileInputStream(cache)));
                String code = reader.readLine();
                if (code != null && !code.trim().isEmpty()) {
                    return code.trim();
                }
            } catch (Exception e) {
            } finally {
                closeQuietly(reader);
            }
        } else {
        }

        String code = android.provider.Settings.Secure.getString(
                context.getContentResolver(),
                android.provider.Settings.Secure.ANDROID_ID);
        String source;
        if (code == null || code.isEmpty()) {
            code = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            source = "随机生成(ANDROID_ID 取不到)";
        } else {
            source = "ANDROID_ID";
        }

        FileWriter writer = null;
        boolean savedOk = false;
        try {
            File parent = cache.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            writer = new FileWriter(cache);
            writer.write(code);
            savedOk = true;
        } catch (Exception e) {
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (IOException e) { }
            }
        }
        return code;
    }

    /** 本地卡密文件是否存在 (排查"重置后卡密是不是被删了") */
    public static boolean kamiExists() {
        return kamiPath != null && new File(kamiPath).exists();
    }

    /** 本地设备码文件是否存在 */
    public static boolean imeiExists() {
        return imeiPath != null && new File(imeiPath).exists();
    }

    /** 写卡密文件; 失败返回 false 供上层引导文件访问权限 */
    public static boolean writeKami(String content) {
        boolean ok = writeFile(kamiPath, content);
        verifyWrite("writeKami", kamiPath, content, ok);
        return ok;
    }

    /** 写设备码文件 */
    public static boolean writeImei(String content) {
        boolean ok = writeFile(imeiPath, content);
        verifyWrite("writeImei", imeiPath, content, ok);
        return ok;
    }

    /**
     * 写入后**回读校验** —— 专门排查"输入框 → 文件"这条线:
     * 如果回读出来的内容和写进去的不一致(或含全角/不可见字符), 日志会直接点出来。
     */
    private static void verifyWrite(String tag, String path, String content, boolean ok) {
        try {
            String want = content == null ? "" : content.trim();
            String back = readPath(path).trim();
            boolean same = back.equals(want);
            if (!same) {
            }
        } catch (Throwable t) {
        }
    }

    /** 读取本地保存的卡密; 无则返回空串 */
    public static String readKami() {
        String v = readPath(kamiPath).trim();
        // 2026-10-10 调整: 原逻辑把 <8 位一律判无效。在"免费模式"(服务端不校验长度)下会出现
        //   登录成功 → 写盘(短卡密) → 心跳 readKami 返回空 → Heartbeat -1 → 重试 3 次后 killSelf() 自杀。
        //   现改为: 只告警, 不再判无效; 空串仍视为无卡密。
        //   (线上真卡密是 13 位, 长度只作诊断参考; 日志只给长度+指纹, 不打内容)
        if (!v.isEmpty() && v.length() < 8) {
        }
        return v;
    }

    /** 读取本地保存的设备码 (登录成功时写入 /sdcard/离情梦/imei); 无则返回空串 */
    public static String readImei() {
        return readPath(imeiPath);
    }

    private static String readPath(String path) {
        if (path == null) return "";
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = null;
        try {
            File file = new File(path);
            if (!file.exists()) return "";
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
        } finally {
            closeQuietly(reader);
        }
        return sb.toString();
    }

    /** 解绑成功后删除本地卡密 */
    public static void deleteKami() {
        if (kamiPath != null) {
            boolean ok = new File(kamiPath).delete();
        }
    }

    private static boolean writeFile(String path, String content) {
        if (path == null) return false;
        FileWriter writer = null;
        try {
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            writer = new FileWriter(file);
            writer.write(content);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (IOException e) { }
            }
        }
    }

    private static void closeQuietly(BufferedReader reader) {
        if (reader != null) {
            try { reader.close(); } catch (IOException e) { }
        }
    }
}
