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
        
        val bounds = GridCalculator.calculateGridBounds(this)
        val cells = GridCalculator.calculateCellDimensions(this)
        
        // Draw dots at grid intersections
        var y = bounds.startY
        while (y <= bounds.endY) {
            var x = bounds.startX
            while (x <= bounds.endX) {
                canvas.drawCircle(x, y, dotRadius, dotPaint)
                x += cells.dx
            }
            y += cells.dy
        }
    }
}