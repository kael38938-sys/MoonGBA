package com.moonlight.moongba

import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

// Simple enum for scaling modes
enum class ScalingMode {
    ORIGINAL,
    PROPORTIONAL,
    STRETCH,
    X2
}

class EmulatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    companion object {
        const val VIDEO_W = 240
        const val VIDEO_H = 160
    }

    private var emulator: Emulator? = null
    private var scalingMode = ScalingMode.PROPORTIONAL
    private var actualWidth = 0
    private var actualHeight = 0
    private var aspectRatio = 0f

    init {
        val holder = holder
        // 16-bit color for better performance on mobile
        holder.setFormat(PixelFormat.RGB_565) 
        holder.setFixedSize(VIDEO_W, VIDEO_H)
        holder.addCallback(this)
        setFocusableInTouchMode(true)
        requestFocus()
    }

    fun setEmulator(emulator: Emulator) {
        this.emulator = emulator
    }

    fun setScalingMode(mode: ScalingMode) {
        scalingMode = mode
        requestLayout()        updateSurfaceSize()
    }

    /**
     * Called by the Emulator thread when a frame is ready.
     * Expects an IntArray of pixels (ARGB_8888 usually).
     */
    fun onImageUpdate(data: IntArray) {
        val holder = holder
        val canvas: Canvas? = holder.lockCanvas()
        
        if (canvas != null) {
            // Draw the bitmap data to the canvas
            canvas.drawBitmap(data, 0, VIDEO_W, 0f, 0f, VIDEO_W.toFloat(), VIDEO_H.toFloat(), false, null)
            holder.unlockCanvasAndPost(canvas)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // Surface ready
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        emulator?.setRenderSurface(null, 0, 0)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        emulator?.setRenderSurface(this, width, height)
    }

    fun setActualSize(w: Int, h: Int) {
        if (actualWidth != w || actualHeight != h) {
            actualWidth = w
            actualHeight = h
            updateSurfaceSize()
        }
    }

    fun setAspectRatio(ratio: Float) {
        if (aspectRatio != ratio) {
            aspectRatio = ratio
            updateSurfaceSize()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateSurfaceSize()
    }

    private fun updateSurfaceSize() {        var viewWidth = width
        var viewHeight = height
        if (viewWidth == 0 || viewHeight == 0 || actualWidth == 0 || actualHeight == 0) return

        var w = 0
        var h = 0

        if (scalingMode != ScalingMode.STRETCH && aspectRatio != 0f) {
            val ratio = aspectRatio * actualHeight / actualWidth
            viewWidth = (viewWidth / ratio).toInt()
        }

        when (scalingMode) {
            ScalingMode.ORIGINAL -> {
                w = viewWidth
                h = viewHeight
            }
            ScalingMode.X2 -> {
                w = viewWidth / 2
                h = viewHeight / 2
            }
            ScalingMode.STRETCH -> {
                if (viewWidth * actualHeight >= viewHeight * actualWidth) {
                    w = actualWidth
                    h = actualHeight
                }
            }
            else -> {
                // Proportional (default)
                val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()
                val contentRatio = actualWidth.toFloat() / actualHeight.toFloat()
                if (viewRatio > contentRatio) {
                    h = viewHeight
                    w = (h * contentRatio).toInt()
                } else {
                    w = viewWidth
                    h = (w / contentRatio).toInt()
                }
            }
        }

        if (w < actualWidth || h < actualHeight) {
            h = actualHeight
            w = h * viewWidth / viewHeight
            if (w < actualWidth) {
                w = actualWidth
                h = w * viewHeight / viewWidth
            }
        }
        w = (w + 3) and 3.inv() // Align to 4 bytes
        h = (h + 3) and 3.inv()

        holder.setFixedSize(w, h)
    }
}
