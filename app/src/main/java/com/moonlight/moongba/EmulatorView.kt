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

    private val paint = Paint()
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)
    private var core: EmulatorCoreInterface? = null
    private var frameBitmap: Bitmap? = null

    init {
        holder.addCallback(this)
        setBackgroundColor(Color.BLACK)
        paint.isFilterBitmap = false
    }

    fun setEmulatorCore(core: EmulatorCoreInterface) {
        this.core = core
    }

    fun start() {
        if (running.get() || core == null) return
        running.set(true)
        thread = Thread(this, "GBARender")
        thread?.start()
    }

    fun stop() {
        running.set(false)
        try { thread?.join(500) } catch (e: Exception) {}
        thread = null
    }
    fun isRunning(): Boolean = running.get()
    fun pause() {}
    fun resume() {}

    override fun run() {
        while (running.get()) {
            try {
                val holder = holder
                if (!holder.surface.isValid) {
                    Thread.sleep(16)
                    continue
                }
                val frameData = core?.stepFrame()
                if (frameData != null) {
                    renderFrame(frameData)
                }
                Thread.sleep(16)
            } catch (e: Exception) {
                // Ignore errors silently for now
            }
        }
    }

    private fun renderFrame(frameData: ByteArray) {
        val holder = holder
        if (!holder.surface.isValid) return
        
        var canvas: Canvas? = null
        try {
            canvas = holder.lockCanvas()
            if (canvas == null) return

            if (frameBitmap == null || frameBitmap!!.width != 240 || frameBitmap!!.height != 160) {
                frameBitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
            }

            val pixels = IntArray(240 * 160)
            for (i in 0 until pixels.size) {
                val offset = i * 4
                if (offset + 3 < frameData.size) {
                    val r = frameData[offset].toInt() and 0xFF
                    val g = frameData[offset + 1].toInt() and 0xFF
                    val b = frameData[offset + 2].toInt() and 0xFF
                    val a = frameData[offset + 3].toInt() and 0xFF
                    pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                } else {
                    pixels[i] = Color.BLACK
                }
            }
            frameBitmap?.setPixels(pixels, 0, 240, 0, 0, 240, 160)
            canvas.drawColor(Color.BLACK)
            
            val scaleX = canvas.width.toFloat() / 240f
            val scaleY = canvas.height.toFloat() / 160f
            val scale = kotlin.math.min(scaleX, scaleY)
            val destWidth = (240 * scale).toInt()
            val destHeight = (160 * scale).toInt()
            val left = (canvas.width - destWidth) / 2
            val top = (canvas.height - destHeight) / 2

            frameBitmap?.let { 
                canvas.drawBitmap(it, null, android.graphics.Rect(left, top, left + destWidth, top + destHeight), paint)
            }

        } catch (e: Exception) {
            // Ignore
        } finally {
            if (canvas != null) {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) { stop() }
}
