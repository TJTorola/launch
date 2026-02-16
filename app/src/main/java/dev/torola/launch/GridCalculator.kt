package dev.torola.launch

import android.content.Context
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

private fun Float.dpToPx(): Int = (this * 72f / 96f).toInt()

data class GridBounds(
    val startX: Float,
    val endX: Float,
    val startY: Float,
    val endY: Float
)

data class CellDimensions(
    val dx: Float,  // Horizontal cell size in pixels
    val dy: Float   // Vertical cell size in pixels
)

object GridCalculator {
    
    const val PADDING_DP = 32f
    const val CELL_COUNT_X = 5
    const val CELL_COUNT_Y = 10
    
    fun calculateGridBounds(view: View): GridBounds {
        val insets = ViewCompat.getRootWindowInsets(view) ?: return GridBounds(0f, 0f, 0f, 0f)
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        
        val paddingPx = PADDING_DP.dpToPx().toFloat()
        val startX = (systemBars.left + paddingPx).toFloat()
        val endX = (view.width - systemBars.right - paddingPx).toFloat()
        val startY = (systemBars.top + paddingPx).toFloat()
        val endY = (view.height - systemBars.bottom - paddingPx).toFloat()
        
        return GridBounds(startX, endX, startY, endY)
    }
    
    fun calculateCellDimensions(view: View): CellDimensions {
        val bounds = calculateGridBounds(view)
        val dx = (bounds.endX - bounds.startX) / CELL_COUNT_X
        val dy = (bounds.endY - bounds.startY) / CELL_COUNT_Y
        
        return CellDimensions(dx.coerceAtLeast(1f), dy.coerceAtLeast(1f))
    }
    
    fun snapToGridX(value: Float, bounds: GridBounds, cellDimensions: CellDimensions): Float {
        val startX = bounds.startX
        val dx = cellDimensions.dx
        val index = ((value - startX) / dx).toInt().coerceAtLeast(0)
        return startX + index * dx
    }
    
    fun snapToGridY(value: Float, bounds: GridBounds, cellDimensions: CellDimensions): Float {
        val startY = bounds.startY
        val dy = cellDimensions.dy
        val index = ((value - startY) / dy).toInt().coerceAtLeast(0)
        return startY + index * dy
    }
    
    fun snapToGridWidth(value: Float, cellDimensions: CellDimensions): Float {
        val cells = ((value + cellDimensions.dx / 2) / cellDimensions.dx).toInt().coerceAtLeast(1)
        return cells * cellDimensions.dx
    }
    
    fun snapToGridHeight(value: Float, cellDimensions: CellDimensions): Float {
        val cells = ((value + cellDimensions.dy / 2) / cellDimensions.dy).toInt().coerceAtLeast(1)
        return cells * cellDimensions.dy
    }
    
    fun pixelsToCellsX(pixels: Float, bounds: GridBounds, cellDimensions: CellDimensions): Int {
        val startX = bounds.startX
        val dx = cellDimensions.dx
        val index = ((pixels - startX) / dx).toInt().coerceAtLeast(0)
        return index
    }
    
    fun pixelsToCellsY(pixels: Float, bounds: GridBounds, cellDimensions: CellDimensions): Int {
        val startY = bounds.startY
        val dy = cellDimensions.dy
        val index = ((pixels - startY) / dy).toInt().coerceAtLeast(0)
        return index
    }
    
    fun cellsToPixelsX(cellIndex: Int, bounds: GridBounds, cellDimensions: CellDimensions): Float {
        val startX = bounds.startX
        return startX + cellIndex * cellDimensions.dx
    }
    
    fun cellsToPixelsY(cellIndex: Int, bounds: GridBounds, cellDimensions: CellDimensions): Float {
        val startY = bounds.startY
        return startY + cellIndex * cellDimensions.dy
    }
    
    fun cellsToPixelsWidth(cellCount: Int, cellDimensions: CellDimensions): Float {
        return cellCount * cellDimensions.dx
    }
    
    fun cellsToPixelsHeight(cellCount: Int, cellDimensions: CellDimensions): Float {
        return cellCount * cellDimensions.dy
    }
}