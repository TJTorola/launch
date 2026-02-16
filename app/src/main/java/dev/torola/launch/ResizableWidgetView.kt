package dev.torola.launch

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs

/**
 * Container view that wraps an AppWidgetHostView and adds drag/resize functionality
 * with grid snapping for discrete positioning
 */
class ResizableWidgetView(
    context: Context,
    private val widgetId: Int,
    private val onPositionChanged: (Int, Int, Int, Int, Int) -> Unit, // widgetId, x, y, width, height
    private val checkCollision: (Int, Int, Int, Int, Int) -> Boolean // widgetId, x, y, width, height -> overlaps
) : FrameLayout(context) {

    private val widgetView: AppWidgetHostView
    private val resizeHandleSize = 80f // Size of corner resize handle in pixels
    private val borderPaint = Paint().apply {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(
            com.google.android.material.R.attr.colorPrimary,
            typedValue,
            true
        )
        color = typedValue.data
        alpha = (255 * 0.5).toInt() // 50% opacity per Material guidelines
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val handlePaint = Paint().apply {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(
            com.google.android.material.R.attr.colorPrimary,
            typedValue,
            true
        )
        color = typedValue.data // Full opacity for handle
        style = Paint.Style.FILL
    }
    
    private var isDragging = false
    private var isResizing = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isEditMode = false
    private var originalX = 0f
    private var originalY = 0f
    private var originalWidth = 0
    private var originalHeight = 0
    
    companion object {
        const val PADDING_DP = 32f
        const val CELL_COUNT_X = 5
        const val CELL_COUNT_Y = 10
        
        private fun Float.dpToPx(): Int = (this * 72f / 96f).toInt()
        
        private fun calculateGridMetrics(view: View): Pair<Float, Float> {
            val insets = ViewCompat.getRootWindowInsets(view) ?: return Pair(1f, 1f)
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            val paddingPx = PADDING_DP.dpToPx().toFloat()
            val startX = (systemBars.left + paddingPx).toFloat()
            val endX = (view.width - systemBars.right - paddingPx).toFloat()
            val startY = (systemBars.top + paddingPx).toFloat()
            val endY = (view.height - systemBars.bottom - paddingPx).toFloat()
            
            val dx = (endX - startX) / CELL_COUNT_X
            val dy = (endY - startY) / CELL_COUNT_Y
            
            return Pair(dx.coerceAtLeast(1f), dy.coerceAtLeast(1f))
        }
        
        fun pixelsToCells(view: View, pixels: Int, isHorizontal: Boolean): Int {
            val (dx, dy) = calculateGridMetrics(view)
            val cellSize = if (isHorizontal) dx else dy
            val result = ((pixels.toFloat() + cellSize / 2) / cellSize).toInt()
            return result.coerceAtLeast(1)
        }
        
        fun cellsToPixels(view: View, cells: Int, isHorizontal: Boolean): Int {
            val (dx, dy) = calculateGridMetrics(view)
            val cellSize = if (isHorizontal) dx else dy
            return (cells * cellSize).toInt()
        }
    }
    
    private fun Float.dpToPx(): Int = (this * 72f / 96f).toInt()
    
    private fun calculateGridBounds(parent: View): Triple<FloatArray, Float, Float> {
        val insets = ViewCompat.getRootWindowInsets(parent) ?: return Triple(floatArrayOf(0f, 0f, 0f, 0f), 1f, 1f)
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        
        val paddingPx = PADDING_DP.dpToPx().toFloat()
        val startX = (systemBars.left + paddingPx).toFloat()
        val endX = (parent.width - systemBars.right - paddingPx).toFloat()
        val startY = (systemBars.top + paddingPx).toFloat()
        val endY = (parent.height - systemBars.bottom - paddingPx).toFloat()
        
        val dx = (endX - startX) / CELL_COUNT_X
        val dy = (endY - startY) / CELL_COUNT_Y
        
        return Triple(floatArrayOf(startX, endX, startY, endY), dx.coerceAtLeast(1f), dy.coerceAtLeast(1f))
    }
    
    private fun snapToGridX(value: Float, parent: View): Float {
        val (bounds, dx, _) = calculateGridBounds(parent)
        val startX = bounds[0]
        val index = ((value - startX) / dx).toInt().coerceAtLeast(0)
        return startX + index * dx
    }
    
    private fun snapToGridY(value: Float, parent: View): Float {
        val (bounds, _, dy) = calculateGridBounds(parent)
        val startY = bounds[2]
        val index = ((value - startY) / dy).toInt().coerceAtLeast(0)
        return startY + index * dy
    }
    
    private fun snapToGridWidth(value: Float, dx: Float): Float {
        val cells = ((value + dx / 2) / dx).toInt().coerceAtLeast(1)
        return cells * dx
    }
    
    private fun snapToGridHeight(value: Float, dy: Float): Float {
        val cells = ((value + dy / 2) / dy).toInt().coerceAtLeast(1)
        return cells * dy
    }
    
    private fun pixelsToCellsX(pixels: Float, parent: View): Int {
        val (bounds, dx, _) = calculateGridBounds(parent)
        val startX = bounds[0]
        val index = ((pixels - startX) / dx).toInt().coerceAtLeast(0)
        return index
    }
    
    private fun pixelsToCellsY(pixels: Float, parent: View): Int {
        val (bounds, _, dy) = calculateGridBounds(parent)
        val startY = bounds[2]
        val index = ((pixels - startY) / dy).toInt().coerceAtLeast(0)
        return index
    }
    
    private fun cellsToPixelsX(cellIndex: Int, parent: View): Float {
        val (bounds, dx, _) = calculateGridBounds(parent)
        val startX = bounds[0]
        return startX + cellIndex * dx
    }
    
    private fun cellsToPixelsY(cellIndex: Int, parent: View): Float {
        val (bounds, _, dy) = calculateGridBounds(parent)
        val startY = bounds[2]
        return startY + cellIndex * dy
    }
    
    private fun cellsToPixelsWidth(cellCount: Int, dx: Float): Float {
        return cellCount * dx
    }
    
    private fun cellsToPixelsHeight(cellCount: Int, dy: Float): Float {
        return cellCount * dy
    }
    
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
                
                // Save original position and size in case of collision
                originalX = x
                originalY = y
                originalWidth = width
                originalHeight = height
                
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
                    // Move the widget (no snapping during drag for smooth movement)
                    val parent = parent as? android.view.ViewGroup
                    val maxX = (parent?.width ?: Int.MAX_VALUE) - width
                    val maxY = (parent?.height ?: Int.MAX_VALUE) - height

                    val newX = (x + deltaX).toInt().coerceIn(0, maxX.coerceAtLeast(0))
                    val newY = (y + deltaY).toInt().coerceIn(0, maxY.coerceAtLeast(0))

                    x = newX.toFloat()
                    y = newY.toFloat()
                } else if (isResizing) {
                    // Resize the widget (no snapping during resize for smooth resizing)
                    val parent = parent as? View
                    val (_, dx, dy) = if (parent != null) calculateGridBounds(parent) else Triple(floatArrayOf(), 1f, 1f)
                    val minCellSize = dx.coerceAtLeast(dy)

                    val maxWidth = (parent?.width ?: Int.MAX_VALUE) - x.toInt()
                    val maxHeight = (parent?.height ?: Int.MAX_VALUE) - y.toInt()

                    val newWidth = (width + deltaX.toInt()).coerceIn(dx.toInt(), maxWidth.coerceAtLeast(dx.toInt()))
                    val newHeight = (height + deltaY.toInt()).coerceIn(dy.toInt(), maxHeight.coerceAtLeast(dy.toInt()))

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
                    val parent = parent as? View ?: return@onTouchEvent super.onTouchEvent(event)
                    val parentWidth = parent.width
                    val parentHeight = parent.height
                    
                    val (bounds, dx, dy) = calculateGridBounds(parent)
                    val startX = bounds[0]
                    val endX = bounds[1]
                    val startY = bounds[2]
                    val endY = bounds[3]

                    // Snap to grid on release
                    val snappedX = snapToGridX(x, parent).coerceIn(startX, endX)
                    val snappedY = snapToGridY(y, parent).coerceIn(startY, endY)

                    // Constrain width/height to not exceed screen bounds
                    val maxPossibleWidth = parentWidth.toFloat() - snappedX
                    val maxPossibleHeight = parentHeight.toFloat() - snappedY
                    val snappedWidth = snapToGridWidth(width.toFloat(), dx).coerceIn(dx, maxPossibleWidth.coerceAtLeast(dx))
                    val snappedHeight = snapToGridHeight(height.toFloat(), dy).coerceIn(dy, maxPossibleHeight.coerceAtLeast(dy))

                    // Ensure widget doesn't extend beyond screen
                    val maxX = parentWidth - snappedWidth
                    val maxY = parentHeight - snappedHeight

                    val finalX = snappedX.coerceAtLeast(startX)
                    val finalY = snappedY.coerceAtLeast(startY)
                    val finalWidth = snappedWidth.coerceAtMost(maxX - startX + dx)
                    val finalHeight = snappedHeight.coerceAtMost(maxY - startY + dy)
                    
                    // Check for collision with other widgets
                    val wouldCollide = checkCollision(
                        widgetId,
                        finalX.toInt(),
                        finalY.toInt(),
                        finalWidth.toInt(),
                        finalHeight.toInt()
                    )
                    
                    if (wouldCollide) {
                        // Revert to original position/size
                        x = originalX
                        y = originalY
                        layoutParams = layoutParams.apply {
                            this.width = originalWidth
                            this.height = originalHeight
                        }
                        requestLayout()
                    } else {
                        // Apply snapped values
                        x = finalX
                        y = finalY
                        layoutParams = layoutParams.apply {
                            this.width = finalWidth.toInt()
                            this.height = finalHeight.toInt()
                        }
                        requestLayout()
                        
                        // Save position and size (convert to grid cells for storage)
                        onPositionChanged(
                            widgetId,
                            pixelsToCellsX(finalX, parent),
                            pixelsToCellsY(finalY, parent),
                            (snappedWidth / dx).toInt(),
                            (snappedHeight / dy).toInt()
                        )
                    }
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

    fun getWidgetId(): Int = widgetId
}
