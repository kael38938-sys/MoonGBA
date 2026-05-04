package com.moonlight.moongba

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
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
    private val isRunningFlag = AtomicBoolean(false)
    private var core: EmulatorCoreInterface? = null
    private var frameBitmap: Bitmap? = null

    init {
        holder.addCallback(this)
        setBackgroundColor(Color.BLACK)
        Log.d("EmulatorView", "Initialized")
    }

    fun setEmulatorCore(core: EmulatorCoreInterface) {
        this.core = core
    }

    fun start() {
        if (isRunningFlag.get() || core == null) return
        Log.d("EmulatorView", "Starting")
        isRunningFlag.set(true)
        thread = Thread(this, "GBARender").apply { 
            priority = Thread.MAX_PRIORITY
            start() 
        }
    }

    fun stop() {
        Log.d("EmulatorView", "Stopping")        isRunningFlag.set(false)
        thread?.join(1000)
        thread = null
    }

    fun isRunning(): Boolean = isRunningFlag.get()

    override fun run() {
        Log.d("EmulatorView", "Thread started")
        while (isRunningFlag.get()) {
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
                Log.e("EmulatorView", "Render error", e)
            }
        }
        Log.d("EmulatorView", "Thread stopped")
    }

    private fun renderFrame(frameData: ByteArray) {
        val holder = holder
        if (!holder.surface.isValid) return

        var canvas: Canvas? = null
        try {
            canvas = holder.lockCanvas()
            if (canvas == null) return

            // Create bitmap if needed
            if (frameBitmap == null || frameBitmap!!.width != 240 || frameBitmap!!.height != 160) {
                frameBitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
            }

            // Convert bytes to pixels
            frameBitmap?.let { bitmap ->
                val pixels = IntArray(240 * 160)
                for (i in 0 until pixels.size) {
                    val offset = i * 4
                    if (offset + 3 < frameData.size) {
                        val r = frameData[offset].toInt() and 0xFF                        val g = frameData[offset + 1].toInt() and 0xFF
                        val b = frameData[offset + 2].toInt() and 0xFF
                        val a = frameData[offset + 3].toInt() and 0xFF
                        pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                    } else {
                        pixels[i] = Color.BLACK
                    }
                }
                bitmap.setPixels(pixels, 0, 240, 0, 0, 240, 160)
            }

            // Draw
            canvas.drawColor(Color.BLACK)
            val scaleX = canvas.width.toFloat() / 240f
            val scaleY = canvas.height.toFloat() / 160f
            val scale = minOf(scaleX, scaleY)
            val destWidth = (240 * scale).toInt()
            val destHeight = (160 * scale).toInt()
            val left = (canvas.width - destWidth) / 2
            val top = (canvas.height - destHeight) / 2

            frameBitmap?.let { bitmap ->
                canvas.drawBitmap(bitmap, null, android.graphics.Rect(left, top, left + destWidth, top + destHeight), paint)
            }

        } catch (e: Exception) {
            Log.e("EmulatorView", "Draw error", e)
        } finally {
            canvas?.let { holder.unlockCanvasAndPost(it) }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("EmulatorView", "Surface created")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("EmulatorView", "Surface changed: ${width}x${height}")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("EmulatorView", "Surface destroyed")
        stop()
    }
}
