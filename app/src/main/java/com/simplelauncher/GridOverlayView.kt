package com.simplelauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/**
 * Overlay view that displays grid dots to show the widget positioning grid
 */
class GridOverlayView(context: Context) : View(context) {
    
    private val dotPaint = Paint().apply {
        color = 0x60FFFFFF.toInt() // Semi-transparent white dots
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val dotRadius = 6f // Radius of each grid dot in pixels
    
    init {
        // Allow touch events to pass through this view
        setWillNotDraw(false)
        // Make view non-clickable so touches pass through
        isClickable = false
        isFocusable = false
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw dots at each grid cell corner (where snapping happens)
        val gridSize = ResizableWidgetView.GRID_CELL_SIZE
        
        // Draw dots at 0, gridSize, 2*gridSize, etc.
        var y = 0
        while (y <= height) {
            var x = 0
            while (x <= width) {
                canvas.drawCircle(x.toFloat(), y.toFloat(), dotRadius, dotPaint)
                x += gridSize
            }
            y += gridSize
        }
    }
}
