package dev.torola.launch

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment

class WidgetFragment : Fragment() {
    
    private lateinit var widgetContainer: FrameLayout
    private lateinit var widgetHost: LauncherAppWidgetHost
    private lateinit var widgetManagerHelper: WidgetManagerHelper
    private lateinit var gridOverlay: GridOverlayView
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_widget, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        widgetContainer = view.findViewById(R.id.widgetContainer)
        
        setupWidgetSystem()
        loadWidgets()
    }
    
    override fun onStart() {
        super.onStart()
        widgetHost.startListeningIfResumed()
    }
    
    override fun onStop() {
        super.onStop()
        widgetHost.stopListeningIfNeeded()
    }
    
    fun reloadWidgets() {
        loadWidgets()
    }
    
    private fun setupWidgetSystem() {
        widgetHost = LauncherAppWidgetHost(requireContext(), WidgetManagerHelper.APPWIDGET_HOST_ID)
        widgetManagerHelper = WidgetManagerHelper(requireContext())
        
        gridOverlay = GridOverlayView(requireContext())
        gridOverlay.visibility = View.GONE
        val gridLayoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        widgetContainer.addView(gridOverlay, gridLayoutParams)
    }
    
    private fun loadWidgets() {
        val childCount = widgetContainer.childCount
        for (i in childCount - 1 downTo 0) {
            val child = widgetContainer.getChildAt(i)
            if (child !is GridOverlayView) {
                widgetContainer.removeView(child)
            }
        }
        
        val prefs = requireContext().getSharedPreferences("widgets", Context.MODE_PRIVATE)
        val widgetIds = prefs.getStringSet("widget_list", emptySet()) ?: emptySet()
        val failedWidgets = mutableListOf<String>()
        
        for (idString in widgetIds) {
            val widgetId = idString.toIntOrNull() ?: continue
            val widgetInfo = widgetManagerHelper.getWidgetInfo(widgetId)
            
            if (widgetInfo == null) {
                failedWidgets.add(idString)
                continue
            }
            
            try {
                val widgetView = widgetHost.createView(
                    requireContext().applicationContext,
                    widgetId,
                    widgetInfo
                )
                
                val storedCellX = prefs.getInt("${widgetId}_x", -1)
                val storedCellY = prefs.getInt("${widgetId}_y", -1)
                val storedCellWidth = prefs.getInt("${widgetId}_width", -1)
                val storedCellHeight = prefs.getInt("${widgetId}_height", -1)
                
                val density = resources.displayMetrics.density
                val minWidthPx = (widgetInfo.minWidth * density).toInt()
                val minHeightPx = (widgetInfo.minHeight * density).toInt()
                
                val defaultCellWidth = if (storedCellWidth > 0) {
                    storedCellWidth
                } else {
                    ResizableWidgetView.pixelsToCells(minWidthPx).coerceAtLeast(1)
                }
                val defaultCellHeight = if (storedCellHeight > 0) {
                    storedCellHeight
                } else {
                    ResizableWidgetView.pixelsToCells(minHeightPx).coerceAtLeast(1)
                }
                
                val defaultCellX = if (storedCellX >= 0) storedCellX else 0
                val defaultCellY = if (storedCellY >= 0) {
                    storedCellY
                } else {
                    widgetContainer.childCount * (defaultCellHeight + 1)
                }
                
                val widthPx = ResizableWidgetView.cellsToPixels(defaultCellWidth)
                val heightPx = ResizableWidgetView.cellsToPixels(defaultCellHeight)
                val xPx = ResizableWidgetView.cellsToPixels(defaultCellX)
                val yPx = ResizableWidgetView.cellsToPixels(defaultCellY)

                val containerWidth = if (widgetContainer.width > 0) widgetContainer.width else Int.MAX_VALUE
                val containerHeight = if (widgetContainer.height > 0) widgetContainer.height else Int.MAX_VALUE
                val maxWidth = containerWidth - xPx
                val maxHeight = containerHeight - yPx
                val constrainedWidthPx = if (maxWidth > ResizableWidgetView.GRID_CELL_SIZE) {
                    widthPx.coerceIn(ResizableWidgetView.GRID_CELL_SIZE, maxWidth)
                } else {
                    widthPx
                }
                val constrainedHeightPx = if (maxHeight > ResizableWidgetView.GRID_CELL_SIZE) {
                    heightPx.coerceIn(ResizableWidgetView.GRID_CELL_SIZE, maxHeight)
                } else {
                    heightPx
                }
                
                val resizableWidget = ResizableWidgetView(
                    requireContext(),
                    widgetId,
                    { id, cellX, cellY, cellWidth, cellHeight ->
                        saveWidgetPosition(id, cellX, cellY, cellWidth, cellHeight)
                    },
                    { id, x, y, w, h ->
                        checkWidgetCollision(id, x, y, w, h)
                    }
                )
                resizableWidget.setWidgetView(widgetView)

                val layoutParams = FrameLayout.LayoutParams(constrainedWidthPx, constrainedHeightPx)
                resizableWidget.layoutParams = layoutParams
                resizableWidget.x = xPx.toFloat()
                resizableWidget.y = yPx.toFloat()
                
                widgetContainer.addView(resizableWidget)
            } catch (e: Exception) {
                android.util.Log.e("WidgetFragment", "Failed to load widget $widgetId: ${widgetInfo.provider}", e)
                failedWidgets.add(idString)
            }
        }
        
        if (failedWidgets.isNotEmpty()) {
            val updatedWidgets = widgetIds.toMutableSet()
            updatedWidgets.removeAll(failedWidgets.toSet())
            prefs.edit().apply {
                putStringSet("widget_list", updatedWidgets)
                apply()
            }
        }
    }
    
    private fun saveWidgetPosition(widgetId: Int, cellX: Int, cellY: Int, cellWidth: Int, cellHeight: Int) {
        val prefs = requireContext().getSharedPreferences("widgets", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("${widgetId}_x", cellX)
            putInt("${widgetId}_y", cellY)
            putInt("${widgetId}_width", cellWidth)
            putInt("${widgetId}_height", cellHeight)
            apply()
        }
    }
    
    private fun checkWidgetCollision(movingWidgetId: Int, x: Int, y: Int, width: Int, height: Int): Boolean {
        for (i in 0 until widgetContainer.childCount) {
            val child = widgetContainer.getChildAt(i)
            if (child !is ResizableWidgetView) continue
            if (child.getWidgetId() == movingWidgetId) continue
            
            val otherLeft = child.x.toInt()
            val otherTop = child.y.toInt()
            val otherRight = otherLeft + child.width
            val otherBottom = otherTop + child.height
            
            val thisLeft = x
            val thisTop = y
            val thisRight = x + width
            val thisBottom = y + height
            
            val overlaps = !(thisRight <= otherLeft || 
                            thisLeft >= otherRight ||
                            thisBottom <= otherTop || 
                            thisTop >= otherBottom)
            
            if (overlaps) {
                return true
            }
        }
        return false
    }
}