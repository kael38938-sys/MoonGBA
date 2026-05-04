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

    private val paint = Paint().apply { 
        isFilterBitmap = false
    }
    
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)
    private var core: EmulatorCoreInterface? = null
    private var frameBitmap: Bitmap? = null
    private var frameCount = 0
    private var lastLogTime = System.currentTimeMillis()

    init {
        holder.addCallback(this)
        setBackgroundColor(Color.BLACK)
        Log.d("EmulatorView", "EmulatorView initialized")
    }

    fun setEmulatorCore(core: EmulatorCoreInterface) {
        this.core = core
        Log.d("EmulatorView", "Core set")
    }

    fun start() {
        if (running.get() || core == null) {
            Log.d("EmulatorView", "Cannot start: running=${running.get()}, core=${core != null}")
            return
        }
        Log.d("EmulatorView", "Starting emulator thread")        running.set(true)
        thread = Thread(this, "GBARender").apply { 
            priority = Thread.MAX_PRIORITY
            start() 
        }
    }

    fun stop() {
        Log.d("EmulatorView", "Stopping emulator thread")
        running.set(false)
        thread?.join(1000)
        thread = null
    }

    fun pause() {
        Log.d("EmulatorView", "Pausing")
    }

    fun resume() {
        Log.d("EmulatorView", "Resuming")
    }

    val isRunning: Boolean
        get() = running.get()

    override fun run() {
        Log.d("EmulatorView", "Render thread started")
        while (running.get()) {
            try {
                val holder = holder
                if (!holder.surface.isValid) {
                    Thread.sleep(16)
                    continue
                }

                // Step the emulator
                val frameData = core?.stepFrame()
                if (frameData != null) {
                    renderFrame(frameData)
                    frameCount++
                    
                    // Log every 60 frames (1 second)
                    val now = System.currentTimeMillis()
                    if (now - lastLogTime > 1000) {
                        Log.d("EmulatorView", "FPS: $frameCount, Frame data size: ${frameData.size}")
                        frameCount = 0
                        lastLogTime = now
                    }
                }
                Thread.sleep(16)
            } catch (e: Exception) {
                Log.e("EmulatorView", "Error in render loop", e)
            }
        }
        Log.d("EmulatorView", "Render thread stopped")
    }

    private fun renderFrame(frameData: ByteArray) {
        val holder = holder
        if (!holder.surface.isValid) return

        var canvas: Canvas? = null
        try {
            canvas = holder.lockCanvas()
            if (canvas == null) {
                Log.e("EmulatorView", "Canvas is null!")
                return
            }

            // Create or reuse bitmap
            if (frameBitmap == null || frameBitmap!!.width != 240 || frameBitmap!!.height != 160) {
                Log.d("EmulatorView", "Creating new bitmap: 240x160")
                frameBitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
            }

            // Copy RGBA data to bitmap
            frameBitmap?.let { bitmap ->
                val pixels = IntArray(240 * 160)
                
                // Convert byte array to int array (RGBA format)
                for (i in 0 until pixels.size) {
                    val offset = i * 4
                    // Ensure we don't go out of bounds
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
                
                bitmap.setPixels(pixels, 0, 240, 0, 0, 240, 160)
            }

            // Clear canvas
            canvas.drawColor(Color.BLACK)
            // Scale to fit screen while maintaining aspect ratio
            val scaleX = canvas.width.toFloat() / 240f
            val scaleY = canvas.height.toFloat() / 160f
            val scale = minOf(scaleX, scaleY)
            val destWidth = (240 * scale).toInt()
            val destHeight = (160 * scale).toInt()
            val left = (canvas.width - destWidth) / 2
            val top = (canvas.height - destHeight) / 2

            // Draw the bitmap
            frameBitmap?.let { bitmap ->
                canvas.drawBitmap(
                    bitmap, 
                    null, 
                    android.graphics.Rect(left, top, left + destWidth, top + destHeight), 
                    paint
                )
            }
            
        } catch (e: Exception) {
            Log.e("EmulatorView", "Error rendering frame", e)
        } finally {
            if (canvas != null) {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (e: Exception) {
                    Log.e("EmulatorView", "Error unlocking canvas", e)
                }
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("EmulatorView", "Surface created: ${holder.surface.isValid}")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("EmulatorView", "Surface changed: ${width}x${height}")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("EmulatorView", "Surface destroyed")
        stop()
    }
}
