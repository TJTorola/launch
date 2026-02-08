package dev.torola.launch

import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable

/**
 * Data class representing a widget item in the picker and management screens
 */
data class WidgetItem(
    val providerInfo: AppWidgetProviderInfo,
    val label: String,
    val icon: Drawable,
    val widgetId: Int = -1 // -1 for picker, actual ID for placed widgets
)
