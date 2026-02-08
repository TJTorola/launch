package com.simplelauncher

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val matrix = Matrix()
    private val savedMatrix = Matrix()
    
    private var mode = NONE
    private val start = PointF()
    private val mid = PointF()
    
    private var minScale = 1f
    private var maxScale = 4f
    private var currentScale = 1f
    
    private val scaleDetector: ScaleGestureDetector
    
    init {
        scaleType = ScaleType.MATRIX
        imageMatrix = matrix
        
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        
        val point = PointF(event.x, event.y)
        
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(matrix)
                start.set(point)
                mode = DRAG
            }
            
            MotionEvent.ACTION_POINTER_DOWN -> {
                savedMatrix.set(matrix)
                midPoint(mid, event)
                mode = ZOOM
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG) {
                    matrix.set(savedMatrix)
                    val dx = point.x - start.x
                    val dy = point.y - start.y
                    matrix.postTranslate(dx, dy)
                    fixTranslation()
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
            }
        }
        
        imageMatrix = matrix
        return true
    }
    
    private fun midPoint(point: PointF, event: MotionEvent) {
        val x = event.getX(0) + event.getX(1)
        val y = event.getY(0) + event.getY(1)
        point.set(x / 2, y / 2)
    }
    
    private fun fixTranslation() {
        val values = FloatArray(9)
        matrix.getValues(values)
        
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]
        
        val fixTransX = getFixTranslation(transX, width.toFloat(), getImageWidth())
        val fixTransY = getFixTranslation(transY, height.toFloat(), getImageHeight())
        
        if (fixTransX != 0f || fixTransY != 0f) {
            matrix.postTranslate(fixTransX, fixTransY)
        }
    }
    
    private fun getFixTranslation(trans: Float, viewSize: Float, contentSize: Float): Float {
        val minTrans: Float
        val maxTrans: Float
        
        if (contentSize <= viewSize) {
            minTrans = 0f
            maxTrans = viewSize - contentSize
        } else {
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }
        
        if (trans < minTrans) {
            return -trans + minTrans
        }
        if (trans > maxTrans) {
            return -trans + maxTrans
        }
        return 0f
    }
    
    private fun getImageWidth(): Float {
        return drawable?.intrinsicWidth?.toFloat()?.times(currentScale) ?: 0f
    }
    
    private fun getImageHeight(): Float {
        return drawable?.intrinsicHeight?.toFloat()?.times(currentScale) ?: 0f
    }
    
    fun resetScale() {
        currentScale = 1f
        matrix.reset()
        imageMatrix = matrix
        invalidate()
    }
    
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            var scaleFactor = detector.scaleFactor
            val newScale = currentScale * scaleFactor
            
            if (newScale > maxScale) {
                scaleFactor = maxScale / currentScale
            } else if (newScale < minScale) {
                scaleFactor = minScale / currentScale
            }
            
            currentScale *= scaleFactor
            matrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
            fixTranslation()
            
            return true
        }
    }
    
    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
}
