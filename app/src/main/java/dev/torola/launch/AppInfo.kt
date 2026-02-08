package dev.torola.launch

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val className: String,
    val icon: Drawable,
    val isSettings: Boolean = false
)
