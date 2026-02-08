package dev.torola.launch

import android.app.Application
import com.google.android.material.color.DynamicColors

/**
 * Application class for Launch launcher
 * Enables Material You dynamic colors to adapt to user's wallpaper
 */
class LaunchApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Apply Material You dynamic colors to all activities
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}