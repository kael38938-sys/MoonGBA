package com.moonlight.moongba

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
        fun stepFrame(): IntArray  // Return IntArray directly, not ByteArray
    }

    private var core: EmulatorCoreInterface? = null
    private var running = false
    private var thread: Thread? = null

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
        if (running || core == null) {
            Log.w("EmulatorView", "Start failed: running=$running, core=${core != null}")
            return
        }
        running = true
        thread = Thread({ renderLoop() }, "GBARender")
        thread?.start()
        Log.d("EmulatorView", "Render thread started")
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        Log.d("EmulatorView", "Render thread stopped")
    }

    fun pause() {
        stop()
    }

    fun resume() {
        // Don't auto-start — let user press button
    }

    fun isRunning(): Boolean = running

    private fun renderLoop() {
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
    }

    fun onImageUpdate(pixels: IntArray) {
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
