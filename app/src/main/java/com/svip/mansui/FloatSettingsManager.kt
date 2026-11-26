package com.svip.mansui

import android.content.Intent
import android.net.Uri
import com.svip.mansui.IPCurbco.TClass.SPUtils
import java.io.File
import java.io.FileOutputStream

// 悬浮窗设置: 悬浮球模式 / 悬浮球头像 / 悬浮窗背景 (+ 悬浮窗权限入口)
// 2026-09-30: 设置子页改为 Compose (ui/screen/floatwindow), 本类只保留数据与动作逻辑
class FloatSettingsManager(private val activity: MainActivity) {

    companion object {
        // 背景档位: 弹窗显示顺序 ≠ 存储编号 (旧版 2=自定义图片 保持不变, 官方背景 取新编号 3)
        private val BG_OPTIONS = arrayOf("实白磨砂", "晶莹玻璃", "官方背景", "自定义图片")
        private val BG_MODES = intArrayOf(0, 1, 3, 2)
        const val MODE_GLASS = 1        // 晶莹玻璃
        const val MODE_OFFICIAL = 3     // 官方背景
        const val MODE_CUSTOM = 2       // 自定义图片
        const val MODE_SOLID = 0        // 实白磨砂
    }

    private var pickTarget = 0          // 1=悬浮球(1:1) 2=悬浮窗背景(43:26 ≈ 430×260)

    private fun ballImgFile(): File = File(activity.filesDir, "江/float_ball.png")
    private fun panelImgFile(): File = File(activity.filesDir, "江/float_panel.png")

    // ── 状态查询 (Compose 侧读) ──

    fun ballMode(): Int {
        var m = (SPUtils.getParam(activity.applicationContext, "float_ball_mode", 1) as Int)
        if (m != 0 && m != 1) {
            m = 1
            SPUtils.setParam(activity.applicationContext, "float_ball_mode", 1)
        }
        return m
    }

    fun ballModeDesc(): String = if (ballMode() == 0) "吸附边缘" else "独立位置"

    fun hasCustomBallImage(): Boolean = ballImgFile().exists()

    fun ballDesc(): String = if (hasCustomBallImage()) "自定义图片" else "默认满穗"

    fun bgMode(): Int {
        var m = (SPUtils.getParam(activity.applicationContext, "float_bg_mode", -1) as Int)
        if (m < 0 || m > MODE_OFFICIAL) {
            val legacy = (SPUtils.getParam(activity.applicationContext, "float_custom_bg", true) as Boolean)
            m = if (legacy) MODE_SOLID else MODE_GLASS
            SPUtils.setParam(activity.applicationContext, "float_bg_mode", m)
        }
        return m
    }

    fun bgModeDesc(): String = when (bgMode()) {
        MODE_CUSTOM -> "自定义图片"
        MODE_OFFICIAL -> "官方背景"
        MODE_GLASS -> "晶莹玻璃"
        else -> "实白磨砂"
    }

    // ── 动作 (Compose 侧调) ──

    fun setBallMode(mode: Int) {
        val m = if (mode == 0) 0 else 1
        SPUtils.setParam(activity.applicationContext, "float_ball_mode", m)
        md3Toast(activity, if (m == 0) "已切换：吸附边缘（拖放手自动贴边）" else "已切换：独立位置（自由摆放）")
    }

    /** 恢复默认满穗头像 */
    fun resetBallImage() {
        SPUtils.setParam(activity.applicationContext, "float_ball_img", "")
        ballImgFile().delete()
        FloatWindow.applyStyle()
        md3Toast(activity, "已恢复默认满穗头像")
    }

    fun setBgMode(mode: Int) {
        SPUtils.setParam(activity.applicationContext, "float_bg_mode", mode)
        FloatWindow.applyStyle()
        md3Toast(activity, "已切换：" + bgModeDesc())
    }

    fun pickBallImage() = startPick(1)

    fun pickPanelImage() = startPick(2)

    // 相册/裁剪结果 (requestCode 6665/6666; 与背景系统的 5555/5556 分开)
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        when (requestCode) {
            6665 -> {
                if (resultCode == android.app.Activity.RESULT_OK && data?.data != null) {
                    launchCrop(data.data!!)
                    return true
                }
            }
            6666 -> {
                if (resultCode == android.app.Activity.RESULT_OK) {
                    val path = data?.getStringExtra("outputPath")
                    if (path != null && File(path).exists()) {
                        applyPickedImage(path)
                    } else {
                        md3Toast(activity, "图片处理失败，请重试")
                    }
                    return true
                }
            }
        }
        return false
    }

    // 打开系统相册 (Android 13+ 为 Photo Picker)
    private fun startPick(target: Int) {
        pickTarget = target
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            activity.startActivityForResult(intent, 6665)
        } catch (e: Exception) {
            md3Toast(activity, "没有可用的相册选择器")
        }
    }

    // 复制到临时文件 → CropActivity 按目标比例裁剪 (球 1:1 / 窗背景 43:26)
    private fun launchCrop(uri: Uri) {
        try {
            val tmp = File(activity.filesDir, "江/float_crop_input.png")
            tmp.parentFile?.mkdirs()
            activity.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            }
            if (tmp.length() <= 0) throw Exception("empty")
            val dest = if (pickTarget == 1) ballImgFile() else panelImgFile()
            dest.parentFile?.mkdirs()
            val cropIntent = Intent(activity, CropActivity::class.java).apply {
                putExtra("imagePath", tmp.absolutePath)
                putExtra("aspectX", if (pickTarget == 1) 1 else 43)
                putExtra("aspectY", if (pickTarget == 1) 1 else 26)
                putExtra("outputPath", dest.absolutePath)
                putExtra("title", if (pickTarget == 1) "裁剪悬浮球头像 (1:1)" else "裁剪悬浮窗背景")
            }
            activity.startActivityForResult(cropIntent, 6666)
        } catch (e: Exception) {
            md3Toast(activity, "图片读取失败，请重试")
        }
    }

    // 裁剪完成 → 记录路径并立即生效
    private fun applyPickedImage(path: String) {
        val key = if (pickTarget == 1) "float_ball_img" else "float_panel_img"
        if (pickTarget == 2) SPUtils.setParam(activity.applicationContext, "float_bg_mode", MODE_CUSTOM)
        SPUtils.setParam(activity.applicationContext, key, path)
        FloatWindow.applyStyle()
        activity.resumeTick.intValue++   // 刷新 Compose 侧状态文字
        md3Toast(activity, if (pickTarget == 1) "悬浮球头像已更新" else "悬浮窗背景已更新")
    }
}
