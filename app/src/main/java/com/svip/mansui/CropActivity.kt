package com.svip.mansui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream

// 自定义背景裁剪 (移植自 G:\jiangxiaosui CropActivity, 2026-09-01)
// 固定比例裁剪窗 (9:16), 支持缩放/拖动, 暗角 + 三分线, 输出 PNG
// 原版用 androidx appcompat, 本工程无依赖 → 纯 Activity + 自定义顶部栏
class CropActivity : Activity() {

    private var aspectX: Int = 1
    private var aspectY: Int = 1
    private var outputPath: String = ""

    private lateinit var imageView: ImageView
    private lateinit var overlayView: View
    private val imageMatrix = Matrix()

    private var sourceBitmap: Bitmap? = null
    private var viewW: Int = 0
    private var viewH: Int = 0

    private var cropLeft: Float = 0f
    private var cropTop: Float = 0f
    private var cropRight: Float = 0f
    private var cropBottom: Float = 0f

    private var minScale: Float = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 裁剪页为纯黑沉浸界面: 系统栏同黑, 与 CropOverlay 暗角背景连贯 (主题已统一为浅色 NoActionBar)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val imagePath = intent.getStringExtra("imagePath") ?: ""
        aspectX = intent.getIntExtra("aspectX", 1)
        aspectY = intent.getIntExtra("aspectY", 1)
        outputPath = intent.getStringExtra("outputPath") ?: ""
        val title = intent.getStringExtra("title") ?: "裁剪图片"

        sourceBitmap = loadBitmap(imagePath)
        if (sourceBitmap == null) {
            finish()
            return
        }

        imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.MATRIX
            setImageBitmap(sourceBitmap)
        }

        overlayView = CropOverlay(this)

        val container = FrameLayout(this).apply {
            addView(imageView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(overlayView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        // 完成按钮: 浮在图片右上角 (标题栏位置), 节省整条顶栏空间
        val doneBtn = TextView(this).apply {
            text = "完成"
            textSize = 15f
            setTextColor(Md3.primary)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = Md3.rounded(0xCCFFFFFF.toInt(), Md3.dp(this@CropActivity, 10f).toFloat())
            elevation = Md3.dp(this@CropActivity, 4f).toFloat()
            setPadding(Md3.dp(this@CropActivity, 18f), Md3.dp(this@CropActivity, 8f), Md3.dp(this@CropActivity, 18f), Md3.dp(this@CropActivity, 8f))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END)
            (layoutParams as FrameLayout.LayoutParams).setMargins(
                0, Md3.dp(this@CropActivity, 12f), Md3.dp(this@CropActivity, 12f), 0)
            setOnClickListener { onCropDone() }
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        root.addView(container, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(doneBtn)
        setContentView(root)

        val scaleDetector = ScaleGestureDetector(this, ScaleListener())
        val panDetector = GestureDetector(this, PanListener())

        overlayView.setOnTouchListener { _, event ->
            val sd = scaleDetector.onTouchEvent(event)
            val pd = panDetector.onTouchEvent(event)
            sd || pd
        }

        container.viewTreeObserver.addOnGlobalLayoutListener(
            object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (viewW > 0 && viewH > 0) return
                    val w = container.width
                    val h = container.height
                    if (w > 0 && h > 0) {
                        viewW = w
                        viewH = h
                        setupBaseMatrix()
                        container.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                }
            })
    }

    private fun loadBitmap(path: String): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, opts)
            val maxDim = 2048
            var sample = 1
            while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) {
                sample *= 2
            }
            BitmapFactory.Options().apply {
                inSampleSize = sample
            }.let { BitmapFactory.decodeFile(path, it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun setupBaseMatrix() {
        val bmp = sourceBitmap ?: return
        if (viewW <= 0 || viewH <= 0) return

        val targetAspect = aspectX.toFloat() / aspectY.toFloat()
        val viewAspect = viewW.toFloat() / viewH

        val cropW: Float
        val cropH: Float
        if (viewAspect > targetAspect) {
            cropH = viewH.toFloat()
            cropW = viewH.toFloat() * targetAspect
        } else {
            cropW = viewW.toFloat()
            cropH = viewW.toFloat() / targetAspect
        }
        cropLeft = (viewW - cropW) / 2f
        cropTop = (viewH - cropH) / 2f
        cropRight = cropLeft + cropW
        cropBottom = cropTop + cropH

        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        minScale = maxOf(cropW / bw, cropH / bh)

        val tx = cropLeft + (cropW - bw * minScale) / 2f
        val ty = cropTop + (cropH - bh * minScale) / 2f

        imageMatrix.setScale(minScale, minScale)
        imageMatrix.postTranslate(tx, ty)
        imageView.imageMatrix = imageMatrix
        overlayView.invalidate()
    }

    private fun applyMatrix() {
        imageView.imageMatrix = imageMatrix
        overlayView.invalidate()
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val m = FloatArray(9)
            imageMatrix.getValues(m)
            val curScale = m[Matrix.MSCALE_X]
            val newScale = curScale * detector.scaleFactor
            val clampedFactor = if (newScale < minScale) minScale / curScale else detector.scaleFactor

            imageMatrix.postScale(clampedFactor, clampedFactor, detector.focusX, detector.focusY)
            clampImagePosition()
            applyMatrix()
            return true
        }
    }

    private inner class PanListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?, e2: MotionEvent,
            dx: Float, dy: Float
        ): Boolean {
            imageMatrix.postTranslate(-dx, -dy)
            clampImagePosition()
            applyMatrix()
            return true
        }
    }

    private fun clampImagePosition() {
        val m = FloatArray(9)
        imageMatrix.getValues(m)
        val scale = m[Matrix.MSCALE_X]
        val tx = m[Matrix.MTRANS_X]
        val ty = m[Matrix.MTRANS_Y]

        val bmp = sourceBitmap ?: return
        val imgW = bmp.width * scale
        val imgH = bmp.height * scale

        var newTx = tx
        var newTy = ty

        if (imgW < cropRight - cropLeft) {
            newTx = cropLeft + (cropRight - cropLeft - imgW) / 2f
        } else {
            if (newTx > cropLeft) newTx = cropLeft
            if (newTx + imgW < cropRight) newTx = cropRight - imgW
        }

        if (imgH < cropBottom - cropTop) {
            newTy = cropTop + (cropBottom - cropTop - imgH) / 2f
        } else {
            if (newTy > cropTop) newTy = cropTop
            if (newTy + imgH < cropBottom) newTy = cropBottom - imgH
        }

        if (newTx != tx || newTy != ty) {
            imageMatrix.postTranslate(newTx - tx, newTy - ty)
        }
    }

    private fun onCropDone() {
        val bmp = sourceBitmap ?: return
        try {
            val inv = Matrix()
            imageMatrix.invert(inv)

            val src = floatArrayOf(cropLeft, cropTop, cropRight, cropBottom)
            val dst = floatArrayOf(0f, 0f, 0f, 0f)
            inv.mapPoints(dst, src)

            val x = dst[0].toInt().coerceIn(0, bmp.width)
            val y = dst[1].toInt().coerceIn(0, bmp.height)
            val w = (dst[2] - dst[0]).toInt().coerceAtMost(bmp.width - x)
            val h = (dst[3] - dst[1]).toInt().coerceAtMost(bmp.height - y)

            if (w <= 0 || h <= 0) {
                setResult(RESULT_CANCELED)
                finish()
                return
            }

            val cropped = Bitmap.createBitmap(bmp, x, y, w, h)
            val file = File(outputPath)
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cropped.recycle()

            intent.putExtra("outputPath", outputPath)
            setResult(RESULT_OK, intent)
        } catch (_: Exception) {
            setResult(RESULT_CANCELED)
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        sourceBitmap?.recycle()
        sourceBitmap = null
    }

    private inner class CropOverlay(context: android.content.Context) : View(context) {
        private val dimPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        private val borderPaint = Paint().apply {
            color = Color.argb(210, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        private val guidePaint = Paint().apply {
            color = Color.argb(80, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            val cl = cropLeft
            val ct = cropTop
            val cr = cropRight
            val cb = cropBottom
            if (cr <= cl || cb <= ct) return

            canvas.drawRect(0f, 0f, cl, h, dimPaint)
            canvas.drawRect(cr, 0f, w, h, dimPaint)
            canvas.drawRect(cl, 0f, cr, ct, dimPaint)
            canvas.drawRect(cl, cb, cr, h, dimPaint)

            canvas.drawRect(cl, ct, cr, cb, borderPaint)

            for (i in 1..2) {
                val gx = cl + (cr - cl) * i / 3f
                canvas.drawLine(gx, ct, gx, cb, guidePaint)
            }
            for (i in 1..2) {
                val gy = ct + (cb - ct) * i / 3f
                canvas.drawLine(cl, gy, cr, gy, guidePaint)
            }
        }
    }
}
