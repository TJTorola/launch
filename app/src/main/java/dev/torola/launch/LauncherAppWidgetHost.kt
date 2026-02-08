package dev.torola.launch

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/**
 * Custom AppWidgetHost for the launcher
 * Provides custom widget host view creation and lifecycle management
 */
class LauncherAppWidgetHost(
    context: Context,
    hostId: Int
) : AppWidgetHost(context, hostId) {
    
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return LauncherAppWidgetHostView(context)
    }
    
    /**
     * Starts listening for widget updates
     * Should be called when launcher becomes visible
     */
    fun startListeningIfResumed() {
        try {
            startListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Stops listening for widget updates
     * Should be called when launcher stops
     */
    fun stopListeningIfNeeded() {
        try {
            stopListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Custom AppWidgetHostView for the launcher
 * Provides custom widget rendering and interaction
 */
class LauncherAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    
    init {
        // Set long-click as unhandled so parent can handle drag operations
        setOnLongClickListener { false }
    }
    
    override fun getDescendantFocusability(): Int {
        // Block descendant focus to prevent widgets from hijacking focus
        return FOCUS_BLOCK_DESCENDANTS
    }
    
    override fun updateAppWidget(remoteViews: android.widget.RemoteViews?) {
        try {
            super.updateAppWidget(remoteViews)
        } catch (e: Exception) {
            // Handle errors when inflating RemoteViews
            android.util.Log.e("LauncherAppWidgetHostView", "Error updating widget", e)
            // Show error view instead of crashing
            showErrorView()
        }
    }
    
    private fun showErrorView() {
        try {
            removeAllViews()
            val textView = android.widget.TextView(context).apply {
                text = "Widget couldn't load"
                setTextColor(0xFF888888.toInt())
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(16, 16, 16, 16)
            }
            addView(textView)
        } catch (e: Exception) {
            android.util.Log.e("LauncherAppWidgetHostView", "Error showing error view", e)
        }
    }
}
