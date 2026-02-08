package com.simplelauncher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle

/**
 * Helper class that wraps AppWidgetManager for cleaner API access
 * Inspired by Lawnchair's WidgetManagerHelper pattern
 */
class WidgetManagerHelper(private val context: Context) {
    
    private val appWidgetManager = AppWidgetManager.getInstance(context)
    
    companion object {
        const val APPWIDGET_HOST_ID = 1024
    }
    
    /**
     * Gets all available widget providers
     */
    fun getAllProviders(): List<AppWidgetProviderInfo> {
        return try {
            appWidgetManager.installedProviders.sortedBy { 
                it.loadLabel(context.packageManager).toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Gets widget provider info for a specific widget ID
     */
    fun getWidgetInfo(widgetId: Int): AppWidgetProviderInfo? {
        return try {
            appWidgetManager.getAppWidgetInfo(widgetId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Binds an app widget ID if allowed
     */
    fun bindAppWidgetIdIfAllowed(
        widgetId: Int,
        info: AppWidgetProviderInfo,
        options: Bundle? = null
    ): Boolean {
        return try {
            appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, info.provider, options)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Checks if binding widgets is allowed
     */
    fun hasBindAppWidgetPermission(): Boolean {
        return try {
            appWidgetManager.bindAppWidgetIdIfAllowed(
                APPWIDGET_HOST_ID,
                null
            )
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Deletes a widget by ID (Note: This is actually done through the AppWidgetHost)
     */
    fun deleteAppWidgetId(widgetId: Int) {
        // Note: Widget deletion should be done through AppWidgetHost.deleteAppWidgetId()
        // This method is kept for API consistency but delegates to the host
        try {
            // The actual deletion is handled by LauncherAppWidgetHost
            // appWidgetManager doesn't have a public deleteAppWidgetId method
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Gets widget options bundle
     */
    fun getAppWidgetOptions(widgetId: Int): Bundle? {
        return try {
            appWidgetManager.getAppWidgetOptions(widgetId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Updates widget options
     */
    fun updateAppWidgetOptions(widgetId: Int, options: Bundle) {
        try {
            appWidgetManager.updateAppWidgetOptions(widgetId, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
