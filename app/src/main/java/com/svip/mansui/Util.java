package com.svip.mansui;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.icu.text.SimpleDateFormat;
import android.media.MediaPlayer;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.Random;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import android.Manifest;
import android.content.pm.PackageManager;

public class Util {

	
    private static MediaPlayer mMediaPlayer;
	

    // ROOT 检测缓存: 进门检测一次后复用, 避免每次启动都阻塞等 su 授权
    // null = 未知(未测 / 授权超时未响应) — 授权超时不算"无 ROOT", 否则一旦误判就无法重试
    public static volatile Boolean rootCache = null;

    private static boolean rootDetecting = false;
    private static Runnable pendingDone = null;

    public static final int ROOT_OK = 0;
    public static final int ROOT_NO = 1;
    public static final int ROOT_UNKNOWN = 2;

    public static boolean isRootCached() {
        return Boolean.TRUE.equals(rootCache);
    }

    // 供 Kotlin 判三态: true=有root false=确认无root null=未知
    public static Boolean getRootCache() {
        return rootCache;
    }

    // 检测线程是否在跑 (等待 su 授权期间为 true)
    public static boolean isRootDetecting() {
        synchronized (Util.class) {
            return rootDetecting;
        }
    }

    // 三态检测: 授权弹窗 10s 未响应 → 未知 (不写缓存, 保证可重试)
    private static int detectRootOnce() {
        Process process = null;
        OutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = process.getOutputStream();
            os.write("\n".getBytes());
            os.flush();
            os.write("exit".getBytes());
            os.flush();
            os.write("\n".getBytes());
            os.flush();
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroy();
                return ROOT_UNKNOWN;
            }
            return process.exitValue() == 0 ? ROOT_OK : ROOT_NO;
        } catch (Exception e) {
            return ROOT_NO;
        } finally {
            if (os != null) {
                try { os.close(); } catch (Exception ignored) {}
            }
        }
    }

    // 异步检测 ROOT: 每次都真测(重试也走这里), 仅"确认有/无"写缓存, 完成后回调
    public static void detectRootAsync(final Runnable onDone) {
        synchronized (Util.class) {
            if (rootDetecting) {
                pendingDone = onDone;   // 已有检测在跑, 结果回来后统一回调
                return;
            }
            rootDetecting = true;
            pendingDone = onDone;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                int r = detectRootOnce();
                Runnable cb;
                synchronized (Util.class) {
                    if (r != ROOT_UNKNOWN) {
                        rootCache = (r == ROOT_OK);
                    }
                    cb = pendingDone;
                    pendingDone = null;
                    rootDetecting = false;
                }
                if (cb != null) cb.run();
            }
        }).start();
    }

    public static boolean isRoot() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            OutputStream os = process.getOutputStream();
            os.write("\n".getBytes());
            os.flush();
            os.write("exit".getBytes());
            os.flush();
            os.write("\n".getBytes());
            os.flush();
            // 10s 超时: 无授权/无 su 时不再无限阻塞
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroy();
                return false;
            }
            int exitValue = process.exitValue();
            os.close();
            return exitValue == 0;
        } catch (Exception e) {
            return false;
        }
    }

        public static byte[] runShell(String command, boolean isRoot) {
        try {
            Process process = Runtime.getRuntime().exec(isRoot ? "su" : "sh");
            InputStream ins = process.getInputStream();
            InputStream es = process.getErrorStream();
            OutputStream ous = process.getOutputStream();
            ous.write("\n".getBytes());
            ous.flush();
            ous.write(command.getBytes());
            ous.flush();
            ous.write("\n".getBytes());
            ous.flush();
            ous.write("exit".getBytes());
            ous.flush();
            ous.write("\n".getBytes());
            ous.flush();
            byte[] result = readInputStream(ins, false);
            byte[] error = readInputStream(es, false);
            process.waitFor();
            ins.close();
            es.close();
            ous.close();
            if (new String(error).trim().isEmpty()) {
                return result;
            } else {
                return ("Shell Result : \n" + new String(result) + "\n" + "Shell Error : \n" + new String(error) + "\n").getBytes();
            }
        } catch (Throwable th) {
            return ("Application Error : \n" + Log.getStackTraceString(th)).getBytes();
        }
    }

    public static byte[] readInputStream(InputStream ins, boolean close) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int i = -1;
            byte[] buf = new byte[1024];
            while ((i = ins.read(buf)) != -1) {
                bos.write(buf, 0, i);
            }
            if (close) {
                ins.close();
                bos.close();
            }
            return bos.toByteArray();
        } catch (Throwable th) {
            return Log.getStackTraceString(th).getBytes();
        }
    }


    
	
	
	
	
}

