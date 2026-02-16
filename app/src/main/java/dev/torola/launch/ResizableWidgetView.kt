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
    
    init {
        widgetView = LauncherAppWidgetHostView(context)
        addView(widgetView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        
        setWillNotDraw(false)
        
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
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)
            
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
                
                originalX = x
                originalY = y
                originalWidth = width
                originalHeight = height
                
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
                    val parent = parent as? android.view.ViewGroup
                    val maxX = (parent?.width ?: Int.MAX_VALUE) - width
                    val maxY = (parent?.height ?: Int.MAX_VALUE) - height

                    val newX = (x + deltaX).toInt().coerceIn(0, maxX.coerceAtLeast(0))
                    val newY = (y + deltaY).toInt().coerceIn(0, maxY.coerceAtLeast(0))

                    x = newX.toFloat()
                    y = newY.toFloat()
                } else if (isResizing) {
                    val parent = parent as? View
                    val cells = if (parent != null) {
                        GridCalculator.calculateCellDimensions(parent)
                    } else {
                        CellDimensions(1f, 1f)
                    }

                    val maxWidth = (parent?.width ?: Int.MAX_VALUE) - x.toInt()
                    val maxHeight = (parent?.height ?: Int.MAX_VALUE) - y.toInt()

                    val newWidth = (width + deltaX.toInt()).coerceIn(cells.dx.toInt(), maxWidth.coerceAtLeast(cells.dx.toInt()))
                    val newHeight = (height + deltaY.toInt()).coerceIn(cells.dy.toInt(), maxHeight.coerceAtLeast(cells.dy.toInt()))

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
                    
                    val bounds = GridCalculator.calculateGridBounds(parent)
                    val cells = GridCalculator.calculateCellDimensions(parent)

                    val snappedX = GridCalculator.snapToGridX(x, bounds, cells).coerceIn(bounds.startX, bounds.endX)
                    val snappedY = GridCalculator.snapToGridY(y, bounds, cells).coerceIn(bounds.startY, bounds.endY)

                    val maxPossibleWidth = parentWidth - snappedX.toFloat()
                    val maxPossibleHeight = parentHeight - snappedY.toFloat()
                    val snappedWidth = GridCalculator.snapToGridWidth(width.toFloat(), cells).coerceIn(cells.dx, maxPossibleWidth.coerceAtLeast(cells.dx))
                    val snappedHeight = GridCalculator.snapToGridHeight(height.toFloat(), cells).coerceIn(cells.dy, maxPossibleHeight.coerceAtLeast(cells.dy))

                    val maxX = parentWidth - snappedWidth
                    val maxY = parentHeight - snappedHeight

                    val finalX = snappedX.coerceAtLeast(bounds.startX)
                    val finalY = snappedY.coerceAtLeast(bounds.startY)
                    val finalWidth = snappedWidth.coerceAtMost(maxX - bounds.startX + cells.dx)
                    val finalHeight = snappedHeight.coerceAtMost(maxY - bounds.startY + cells.dy)
                    
                    val wouldCollide = checkCollision(
                        widgetId,
                        finalX.toInt(),
                        finalY.toInt(),
                        finalWidth.toInt(),
                        finalHeight.toInt()
                    )
                    
                    if (wouldCollide) {
                        x = originalX
                        y = originalY
                        layoutParams = layoutParams.apply {
                            this.width = originalWidth
                            this.height = originalHeight
                        }
                        requestLayout()
                    } else {
                        x = finalX
                        y = finalY
                        layoutParams = layoutParams.apply {
                            this.width = finalWidth.toInt()
                            this.height = finalHeight.toInt()
                        }
                        requestLayout()
                        
                        onPositionChanged(
                            widgetId,
                            GridCalculator.pixelsToCellsX(finalX, bounds, cells),
                            GridCalculator.pixelsToCellsY(finalY, bounds, cells),
                            (snappedWidth / cells.dx).toInt(),
                            (snappedHeight / cells.dy).toInt()
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