package com.simplelauncher

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Container view that wraps an AppWidgetHostView and adds drag/resize functionality
 */
class ResizableWidgetView(
    context: Context,
    private val widgetId: Int,
    private val onPositionChanged: (Int, Int, Int, Int, Int) -> Unit // widgetId, x, y, width, height
) : FrameLayout(context) {

    private val widgetView: AppWidgetHostView
    private val resizeHandleSize = 80f // Size of corner resize handle in pixels
    private val borderPaint = Paint().apply {
        color = 0x80FFFFFF.toInt() // Semi-transparent white
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val handlePaint = Paint().apply {
        color = 0xFFFFFFFF.toInt() // Solid white
        style = Paint.Style.FILL
    }
    
    private var isDragging = false
    private var isResizing = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isEditMode = false
    
    init {
        // Create empty container for widget view (will be set later)
        widgetView = LauncherAppWidgetHostView(context)
        addView(widgetView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        
        // Enable drawing
        setWillNotDraw(false)
        
        // Long press to enter edit mode
        setOnLongClickListener {
            isEditMode = !isEditMode
            invalidate()
            true
        }
    }
    
    fun setWidgetView(view: AppWidgetHostView) {
        removeAllViews()
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (isEditMode) {
            // Draw border
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)
            
            // Draw resize handle at bottom-right corner
            val handleLeft = width - resizeHandleSize
            val handleTop = height - resizeHandleSize
            canvas.drawRect(
                handleLeft,
                handleTop,
                width.toFloat(),
                height.toFloat(),
                handlePaint
            )
        }
    }
    
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // In edit mode, intercept touches to handle drag/resize
        return isEditMode
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEditMode) {
            return super.onTouchEvent(event)
        }
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                
                // Check if touch is on resize handle
                val touchX = event.x
                val touchY = event.y
                isResizing = touchX >= width - resizeHandleSize && touchY >= height - resizeHandleSize
                isDragging = !isResizing
                
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - lastTouchX
                val deltaY = event.rawY - lastTouchY
                
                if (isDragging) {
                    // Move the widget
                    val newX = (x + deltaX).toInt().coerceAtLeast(0)
                    val newY = (y + deltaY).toInt().coerceAtLeast(0)
                    
                    x = newX.toFloat()
                    y = newY.toFloat()
                } else if (isResizing) {
                    // Resize the widget
                    val newWidth = (width + deltaX.toInt()).coerceAtLeast(100)
                    val newHeight = (height + deltaY.toInt()).coerceAtLeast(100)
                    
                    layoutParams = layoutParams.apply {
                        this.width = newWidth
                        this.height = newHeight
                    }
                    requestLayout()
                }
                
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                return true
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging || isResizing) {
                    // Save position and size
                    onPositionChanged(
                        widgetId,
                        x.toInt(),
                        y.toInt(),
                        width,
                        height
                    )
                }
                
                isDragging = false
                isResizing = false
                return true
            }
        }
        
        return super.onTouchEvent(event)
    }
    
    fun setEditMode(enabled: Boolean) {
        isEditMode = enabled
        invalidate()
    }
    
    fun isWidgetEditMode(): Boolean = isEditMode
}
