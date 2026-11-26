package com.svip.mansui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import java.io.File

/**
 * 大厅背景「相册选择图片」的代理 Activity（移植自 梦之喵羽 PickLobbyBgActivity）。
 *
 * 为什么必须单独开一个 Activity：
 *   悬浮窗面板跑在 Service 里，拿不到 ActivityResultRegistry —— 直接用
 *   rememberLauncherForActivityResult / startActivityForResult 会拿不到结果甚至闪退。
 *   这里借一个真正的 Activity 拉系统相册，选完再交给 CropActivity 裁剪。
 *
 * 流程：系统相册选图 → 复制到 app 私有目录（避开 URI 权限）→ CropActivity 4:3 裁剪
 *        → 输出 PNG 到 /storage/emulated/0/离情梦/大厅背景.png
 * 裁剪完成后本 Activity 直接 finish()，悬浮窗侧靠「文件是否存在」判断，无需回传结果。
 */
class PickLobbyBgActivity : Activity() {

    companion object {
        /** 输出路径：游戏内 Lua 的 applyLobbyBg 读的就是这里 */
        const val OUT_PATH = "/storage/emulated/0/离情梦/大厅背景.png"
        private const val REQ_PICK = 0x4101
        private const val ASPECT_X = 4
        private const val ASPECT_Y = 3
        private const val TITLE = "裁剪大厅背景"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 这个 Activity 没有界面，只借它拉系统选择器；重建时不重复弹
        if (savedInstanceState != null) {
            finish()
            return
        }
        try {
            val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(pick, "选择大厅背景"), REQ_PICK)
        } catch (e: Exception) {
            toast("无法打开相册：${e.message ?: "未知错误"}")
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK) return
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            finish()
            return
        }
        try {
            // 先复制到私有目录，避免把相册 URI 直接交给 CropActivity 时的权限问题
            val tmp = File(filesDir, "lobby_bg_crop_input")
            tmp.parentFile?.mkdirs()
            contentResolver.openInputStream(uri)?.use { ins ->
                tmp.outputStream().use { outs -> ins.copyTo(outs) }
            }

            val dst = File(OUT_PATH)
            dst.parentFile?.mkdirs()

            startActivity(Intent(this, CropActivity::class.java).apply {
                putExtra("imagePath", tmp.absolutePath)
                putExtra("aspectX", ASPECT_X)
                putExtra("aspectY", ASPECT_Y)
                putExtra("outputPath", dst.absolutePath)
                putExtra("title", TITLE)
            })
            // CropActivity 负责写盘；这里不等待结果，直接退场
            finish()
        } catch (e: Exception) {
            toast("打开裁剪失败：${e.message ?: "未知错误"}")
            finish()
        }
    }

    private fun toast(msg: String) {
        try {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }
}
