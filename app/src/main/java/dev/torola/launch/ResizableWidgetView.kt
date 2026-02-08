package dev.torola.launch

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
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
        // Grid cell size in pixels (approximately 70dp which is standard Android widget cell size)
        const val GRID_CELL_SIZE = 200 // pixels
        
        /**
         * Snap a value to the nearest grid cell (rounds to nearest, not down)
         */
        fun snapToGrid(value: Int): Int {
            val cells = (value + GRID_CELL_SIZE / 2) / GRID_CELL_SIZE
            return cells * GRID_CELL_SIZE
        }
        
        /**
         * Convert grid cells to pixels
         */
        fun cellsToPixels(cells: Int): Int {
            return cells * GRID_CELL_SIZE
        }
        
        /**
         * Convert pixels to grid cells (rounded to nearest)
         */
        fun pixelsToCells(pixels: Int): Int {
            return (pixels + GRID_CELL_SIZE / 2) / GRID_CELL_SIZE
        }
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
                    val parent = parent as? android.view.ViewGroup
                    val maxWidth = (parent?.width ?: Int.MAX_VALUE) - x.toInt()
                    val maxHeight = (parent?.height ?: Int.MAX_VALUE) - y.toInt()

                    val newWidth = (width + deltaX.toInt()).coerceIn(GRID_CELL_SIZE, maxWidth.coerceAtLeast(GRID_CELL_SIZE))
                    val newHeight = (height + deltaY.toInt()).coerceIn(GRID_CELL_SIZE, maxHeight.coerceAtLeast(GRID_CELL_SIZE))

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
                    val parent = parent as? android.view.ViewGroup
                    val parentWidth = parent?.width ?: width
                    val parentHeight = parent?.height ?: height

                    // Snap to grid on release
                    val snappedX = snapToGrid(x.toInt()).coerceAtLeast(0)
                    val snappedY = snapToGrid(y.toInt()).coerceAtLeast(0)

                    // Constrain width/height to not exceed screen bounds
                    val maxPossibleWidth = parentWidth - snappedX
                    val maxPossibleHeight = parentHeight - snappedY
                    val snappedWidth = snapToGrid(width).coerceIn(GRID_CELL_SIZE, maxPossibleWidth.coerceAtLeast(GRID_CELL_SIZE))
                    val snappedHeight = snapToGrid(height).coerceIn(GRID_CELL_SIZE, maxPossibleHeight.coerceAtLeast(GRID_CELL_SIZE))

                    // Ensure widget doesn't extend beyond screen
                    val maxX = parentWidth - snappedWidth
                    val maxY = parentHeight - snappedHeight

                    val finalX = snappedX.coerceIn(0, maxX.coerceAtLeast(0))
                    val finalY = snappedY.coerceIn(0, maxY.coerceAtLeast(0))
                    
                    // Check for collision with other widgets
                    val wouldCollide = checkCollision(
                        widgetId,
                        finalX,
                        finalY,
                        snappedWidth,
                        snappedHeight
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
                        x = finalX.toFloat()
                        y = finalY.toFloat()
                        layoutParams = layoutParams.apply {
                            this.width = snappedWidth
                            this.height = snappedHeight
                        }
                        requestLayout()
                        
                        // Save position and size (convert to grid cells for storage)
                        onPositionChanged(
                            widgetId,
                            pixelsToCells(finalX),
                            pixelsToCells(finalY),
                            pixelsToCells(snappedWidth),
                            pixelsToCells(snappedHeight)
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
