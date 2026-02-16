package dev.torola.launch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

private val Context.dp: Float
    get() = resources.displayMetrics.density

private fun Float.dpToPx(): Int = (this * 72f / 96f).toInt()

/**
 * Overlay view that displays grid dots to show the widget positioning grid
 */
class GridOverlayView(context: Context) : View(context) {
    
    companion object {
        const val PADDING_DP = 32f
        const val CELL_COUNT_X = 5
        const val CELL_COUNT_Y = 10
    }
    
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
        
        // Get the system window insets (status bar height + navigation bar height)
        val insets = ViewCompat.getRootWindowInsets(this) ?: return
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        
        // Calculate drawable bounds within content area
        val paddingPx = PADDING_DP.dpToPx()
        val startX = (systemBars.left + paddingPx)
        val endX = (width - systemBars.right - paddingPx)
        val startY = (systemBars.top + paddingPx)
        val endY = (height - systemBars.bottom - paddingPx)
        
        // Calculate cell sizes within bounds
        val dx = (endX - startX) / CELL_COUNT_X
        val dy = (endY - startY) / CELL_COUNT_Y
        
        // Draw dots at grid intersections
        var y = startY
        while (y <= endY) {
            var x = startX
            while (x <= endX) {
                canvas.drawCircle(x.toFloat(), y.toFloat(), dotRadius, dotPaint)
                x += dx
            }
            y += dy
        }
    }
}
