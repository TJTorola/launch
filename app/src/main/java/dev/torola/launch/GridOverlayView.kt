package dev.torola.launch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.View

/**
 * Overlay view that displays grid dots to show the widget positioning grid
 */
class GridOverlayView(context: Context) : View(context) {
    
    private val dotPaint = Paint().apply {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(
            com.google.android.material.R.attr.colorOnSurface,
            typedValue,
            true
        )
        color = typedValue.data
        alpha = (255 * 0.38).toInt() // 38% opacity per Material guidelines
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
