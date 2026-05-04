package com.moonlight.moongba

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

class EmulatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    interface EmulatorCoreInterface {
        fun stepFrame(): ByteArray
    }

    private var core: EmulatorCoreInterface? = null
    private var running = false

    init {
        holder.setFormat(PixelFormat.RGB_565)
        holder.setFixedSize(240, 160)
        holder.setKeepScreenOn(true)
        holder.addCallback(this)
        setBackgroundColor(Color.BLACK)
    }

    fun setEmulatorCore(core: EmulatorCoreInterface) {
        this.core = core
    }

    fun start() {
        if (running || core == null) return
        running = true
        renderLoop()
    }

    fun stop() {
        running = false
    }

    fun isRunning(): Boolean = running

    private fun renderLoop() {
        Thread {
            while (running) {
                try {
                    val frameData = core?.stepFrame()
                    if (frameData != null) {
                        onImageUpdate(frameData)
                    }
                    Thread.sleep(16)
                } catch (e: Exception) {
                    Log.e("EmulatorView", "Render loop error", e)
                }
            }
        }.start()
    }

    fun onImageUpdate(frameData: ByteArray) {
        if (!holder.surface.isValid) {
            Log.w("EmulatorView", "Surface invalid")
            return
        }

        val canvas: Canvas? = holder.lockCanvas()
        if (canvas == null) {
            Log.w("EmulatorView", "lockCanvas returned null")
            return
        }

        try {
            // Convert RGBA bytes to IntArray for direct canvas draw
            val pixels = IntArray(240 * 160)
            var i = 0
            var p = 0
            while (i + 3 < frameData.size && p < pixels.size) {
                val r = frameData[i].toInt() and 0xFF
                val g = frameData[i + 1].toInt() and 0xFF
                val b = frameData[i + 2].toInt() and 0xFF
                val a = frameData[i + 3].toInt() and 0xFF
                pixels[p] = (a shl 24) or (r shl 16) or (g shl 8) or b
                i += 4
                p++
            }

            // Draw directly — no Bitmap object needed
            canvas.drawBitmap(pixels, 0, 240, 0f, 0f, 240, 160, false, null)

        } catch (e: Exception) {
            Log.e("EmulatorView", "onImageUpdate error", e)
        } finally {
            holder.unlockCanvasAndPost(canvas)
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
