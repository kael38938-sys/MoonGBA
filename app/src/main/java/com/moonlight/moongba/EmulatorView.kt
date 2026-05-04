package com.moonlight.moongba

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.atomic.AtomicBoolean

class EmulatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    interface EmulatorCoreInterface {
        fun stepFrame(): ByteArray
    }

    private val paint = Paint().apply { isFilterBitmap = false }
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)
    private var core: EmulatorCoreInterface? = null
    private var frameBitmap: Bitmap? = null

    init {
        holder.addCallback(this)
        setBackgroundColor(Color.BLACK)
    }

    fun setEmulatorCore(core: EmulatorCoreInterface) {
        this.core = core
    }

    fun start() {
        if (running.get() || core == null) return
        running.set(true)
        thread = Thread(this, "GBARender").apply { start() }
    }

    fun stop() {
        running.set(false)
        thread?.join(1000)
        thread = null
    }

    fun pause() {}
    fun resume() {}    val isRunning: Boolean get() = running.get()

    override fun run() {
        while (running.get()) {
            try {
                val holder = holder
                if (!holder.surface.isValid) continue

                val frameData = core?.stepFrame()
                if (frameData != null) {
                    renderFrame(frameData)
                }
                Thread.sleep(16)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun renderFrame(frameData: ByteArray) {
        val holder = holder
        if (!holder.surface.isValid) return

        val canvas: Canvas? = holder.lockCanvas()
        if (canvas != null) {
            try {
                if (frameBitmap == null || frameBitmap!!.width != 240 || frameBitmap!!.height != 160) {
                    frameBitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
                }

                frameBitmap?.let { bitmap ->
                    val pixels = IntArray(240 * 160)
                    for (i in 0 until pixels.size) {
                        val offset = i * 4
                        pixels[i] = (frameData[offset + 3].toInt() and 0xFF shl 24) or
                                (frameData[offset].toInt() and 0xFF shl 16) or
                                (frameData[offset + 1].toInt() and 0xFF shl 8) or
                                (frameData[offset + 2].toInt() and 0xFF)
                    }
                    bitmap.setPixels(pixels, 0, 240, 0, 0, 240, 160)
                }

                val scaleX = canvas.width.toFloat() / 240f
                val scaleY = canvas.height.toFloat() / 160f
                val scale = minOf(scaleX, scaleY)
                val destWidth = (240 * scale).toInt()
                val destHeight = (160 * scale).toInt()
                val left = (canvas.width - destWidth) / 2
                val top = (canvas.height - destHeight) / 2
                canvas.drawColor(Color.BLACK)
                frameBitmap?.let { bitmap ->
                    canvas.drawBitmap(bitmap, null, android.graphics.Rect(left, top, left + destWidth, top + destHeight), paint)
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stop()
    }
}
