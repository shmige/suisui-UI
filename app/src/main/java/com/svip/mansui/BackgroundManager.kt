package com.svip.mansui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.svip.mansui.IPCurbco.TClass.SPUtils
import java.io.File
import java.io.FileOutputStream

// 背景系统: 默认白底 / 官方动态视频 / 官方图库 / 自定义视频图片 (含 9:16 裁剪页跳转)
// 2026-09-30: 设置子页改为 Compose (ui/screen/background), 本类只保留数据与动作逻辑
class BackgroundManager(private val activity: MainActivity) {

    companion object {
        // 官方图库 (名字按原图)
        val OFFICIAL_BG_IMAGES = arrayOf("最佳拍档", "标签满穗", "河畔满穗", "良田满穗", "麦穗满穗")
    }

    private var homeBg: View? = null

    // ── 状态查询 (Compose 侧读) ──

    fun mode(): Int = (SPUtils.getParam(activity.applicationContext, "bg_mode", 0) as Int).coerceIn(0, 3)

    /**
     * 当前背景是不是"视频"。
     * ⚠️ 必须按**模式**判断, 不能用 `isVideoBackground()`（那个读的是上一次构建出来的视图,
     * 切换背景的那一帧还是旧值 → 会把视频误当静态图挂进 layerBackdrop → 底栏采到黑底）。
     */
    fun isVideoMode(): Boolean = when (mode()) {
        1 -> true   // 官方动态
        3 -> (SPUtils.getParam(activity.applicationContext, "bg_custom_mime", "") as String)
            .startsWith("video")
        else -> false
    }

    fun dynamicSel(): Int =
        (SPUtils.getParam(activity.applicationContext, "bg_dynamic_sel", 0) as Int).coerceIn(0, 2)

    fun officialImage(): String =
        (SPUtils.getParam(activity.applicationContext, "bg_official_image", OFFICIAL_BG_IMAGES[0]) as String)
            .takeIf { it in OFFICIAL_BG_IMAGES } ?: OFFICIAL_BG_IMAGES[0]

    /** 自定义背景描述 (视频 / 图片 / 未选择) */
    fun customLabel(): String {
        val uriStr = SPUtils.getParam(activity.applicationContext, "bg_custom_uri", "") as String
        if (uriStr.isEmpty()) return ""
        val mime = SPUtils.getParam(activity.applicationContext, "bg_custom_mime", "") as String
        return if (mime.startsWith("video")) "已选择：自定义视频" else "已选择：自定义图片"
    }

    fun bgModeName(mode: Int): String = when (mode) {
        1 -> "官方动态"
        2 -> "官方图库"
        3 -> "自定义"
        else -> "默认背景"
    }

    /** 设置页副标题用: 官方图库带上图片名 */
    fun currentModeDesc(): String {
        val m = mode()
        return if (m == 2) "官方背景图 · ${officialImage()}" else bgModeName(m)
    }

    fun isVideoBackground(): Boolean = homeBg is Md3VideoBackground

    fun hasBackground(): Boolean = homeBg != null

    fun isBackgroundReady(): Boolean = (homeBg?.width ?: 0) > 0 && (homeBg?.height ?: 0) > 0

    fun videoBackground(): Md3VideoBackground? = homeBg as? Md3VideoBackground

    // 液态玻璃底栏模糊层: 图片背景直接画到 canvas
    fun captureBackdropInto(bmp: Bitmap): Boolean {
        val bg = homeBg ?: return false
        if (bg is Md3VideoBackground) return false
        return try {
            val canvas = Canvas(bmp)
            canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            bg.draw(canvas)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ── 动作 (Compose 侧调) ──

    fun setMode(m: Int) {
        SPUtils.setParam(activity.applicationContext, "bg_mode", m.coerceIn(0, 3))
        apply()
    }

    fun setDynamicSel(sel: Int) {
        SPUtils.setParam(activity.applicationContext, "bg_mode", 1)
        SPUtils.setParam(activity.applicationContext, "bg_dynamic_sel", sel.coerceIn(0, 2))
        apply()
    }

    fun setOfficialImage(name: String) {
        SPUtils.setParam(activity.applicationContext, "bg_mode", 2)
        SPUtils.setParam(activity.applicationContext, "bg_official_image", name)
        apply()
    }

    /** 相册入口 (仿梦之喵羽 GetContent): Android 13+ 自动升级为 Photo Picker */
    fun pickCustomImage() = launchMediaPicker("image/*")

    fun pickCustomVideo() = launchMediaPicker("video/*")

    /** 背景变化: 递增版本号 → Compose 重建背景层 + 刷新设置页副标题 */
    fun apply() {
        activity.bgVersion.intValue++
    }

    /** 构建当前背景视图 (无背景模式返回 null); Compose 侧用 AndroidView 渲染 */
    fun backgroundView(): View? {
        val mode = mode()
        homeBg = when (mode) {
            1 -> {
                val sources = when (dynamicSel()) {
                    1 -> listOf("bg.mp4")
                    2 -> listOf("a2.mp4")
                    else -> listOf("bg.mp4", "a2.mp4")
                }
                Md3VideoBackground(activity, sources).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            }
            2 -> Md3ImageBackground(activity, "${officialImage()}.png").apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            3 -> {
                val uriStr = SPUtils.getParam(activity.applicationContext, "bg_custom_uri", "") as String
                val mime = SPUtils.getParam(activity.applicationContext, "bg_custom_mime", "") as String
                if (uriStr.isEmpty()) {
                    null
                } else {
                    val uri = Uri.parse(uriStr)
                    val v: View = if (mime.startsWith("video")) {
                        Md3VideoBackground(activity, uri)
                    } else {
                        Md3ImageBackground(activity, uri)
                    }
                    v.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    v
                }
            }
            else -> null
        }
        return homeBg
    }

    // 返回 true = 已消费 (背景选择/裁剪结果)
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == 5555 && resultCode == android.app.Activity.RESULT_OK && data?.data != null) {
            val uri = data.data!!
            // MIME 兜底: 部分选择器/相册不返回 type, 按扩展名推断, 避免视频被误当图片送去裁剪
            var mime = try { data.type ?: activity.contentResolver.getType(uri) ?: "" } catch (e: Exception) { "" }
            if (!mime.startsWith("image") && !mime.startsWith("video")) {
                mime = guessMimeByExtension(uri)
            }
            try {
                activity.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
            }
            if (mime.startsWith("video")) {
                // 视频: 直接循环播放
                SPUtils.setParam(activity.applicationContext, "bg_mode", 3)
                SPUtils.setParam(activity.applicationContext, "bg_custom_uri", uri.toString())
                SPUtils.setParam(activity.applicationContext, "bg_custom_mime", "video/*")
                apply()
                md3Toast(activity, "已应用自定义视频背景")
            } else {
                // 图片: 复制临时文件 → CropActivity 9:16 裁剪 (异常时兜底直接存原图)
                try {
                    val tmp = File(activity.filesDir, "江/bg_crop_input.png")
                    tmp.parentFile?.mkdirs()
                    activity.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tmp).use { output -> input.copyTo(output) }
                    }
                    if (tmp.length() <= 0) throw Exception("empty")
                    val cropIntent = Intent(activity, CropActivity::class.java).apply {
                        putExtra("imagePath", tmp.absolutePath)
                        putExtra("aspectX", 9)
                        putExtra("aspectY", 16)
                        putExtra("outputPath", File(activity.filesDir, "江/bg_custom.png").absolutePath)
                        putExtra("title", "裁剪背景")
                    }
                    activity.startActivityForResult(cropIntent, 5556)
                } catch (e: Exception) {
                    saveCustomImageFallback(uri)
                }
            }
            return true
        } else if (requestCode == 5556 && resultCode == android.app.Activity.RESULT_OK) {
            val path = data?.getStringExtra("outputPath")
            if (path != null && File(path).exists()) {
                SPUtils.setParam(activity.applicationContext, "bg_mode", 3)
                SPUtils.setParam(activity.applicationContext, "bg_custom_uri", Uri.fromFile(File(path)).toString())
                SPUtils.setParam(activity.applicationContext, "bg_custom_mime", "image/*")
                apply()
                md3Toast(activity, "背景已更新")
            }
            return true
        }
        return false
    }

    // 裁剪流程失败兜底: 直接把原图存为自定义背景
    private fun saveCustomImageFallback(uri: Uri) {
        try {
            val bgFile = File(activity.filesDir, "江/bg_custom.png")
            bgFile.parentFile?.mkdirs()
            activity.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(bgFile).use { output -> input.copyTo(output) }
            }
            if (bgFile.exists() && bgFile.length() > 0) {
                SPUtils.setParam(activity.applicationContext, "bg_mode", 3)
                SPUtils.setParam(activity.applicationContext, "bg_custom_uri", Uri.fromFile(bgFile).toString())
                SPUtils.setParam(activity.applicationContext, "bg_custom_mime", "image/*")
                apply()
                md3Toast(activity, "背景已更新（未裁剪）")
            } else {
                md3Toast(activity, "图片读取失败")
            }
        } catch (e: Exception) {
            md3Toast(activity, "图片读取失败")
        }
    }

    // 无 MIME 时按扩展名推断媒体类型 (默认按图片处理)
    private fun guessMimeByExtension(uri: Uri): String {
        val name = uri.lastPathSegment?.lowercase() ?: return "image/*"
        val videoExt = arrayOf(".mp4", ".webm", ".3gp", ".mkv", ".avi", ".mov", ".ts")
        return if (videoExt.any { name.endsWith(it) }) "video/*" else "image/*"
    }

    private fun launchMediaPicker(mime: String) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = mime
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            activity.startActivityForResult(intent, 5555)
        } catch (e: Exception) {
            md3Toast(activity, "没有可用的相册选择器")
        }
    }
}
