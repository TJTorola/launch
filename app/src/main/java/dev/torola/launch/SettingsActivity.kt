package dev.torola.launch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsActivity : AppCompatActivity() {

    private lateinit var backIcon: ImageView
    private lateinit var selectWallpaperButton: Button
    private lateinit var manageWidgetsButton: Button
    private lateinit var manageShortcutsButton: Button
    private lateinit var manageHiddenAppsButton: Button
    private lateinit var widgetEditModeSwitch: MaterialSwitch
    private lateinit var showIconsSwitch: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        selectWallpaperButton = findViewById(R.id.selectWallpaperButton)
        backIcon = findViewById(R.id.backIcon)
        backIcon.setOnClickListener { finish() }
        manageWidgetsButton = findViewById(R.id.manageWidgetsButton)
        manageShortcutsButton = findViewById(R.id.manageShortcutsButton)
        manageHiddenAppsButton = findViewById(R.id.manageHiddenAppsButton)
        widgetEditModeSwitch = findViewById(R.id.widgetEditModeSwitch)
        showIconsSwitch = findViewById(R.id.showIconsSwitch)

        // Load widget edit mode state
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        widgetEditModeSwitch.isChecked = prefs.getBoolean("widget_edit_mode", false)
        showIconsSwitch.isChecked = prefs.getBoolean("show_app_icons", false)

        // Setup wallpaper button to open Wallpaper & Style settings
        selectWallpaperButton.setOnClickListener {
            try {
                val intent = android.content.Intent("android.intent.action.MAIN").apply {
                    addCategory("android.intent.category.LAUNCHER")
                    component = android.content.ComponentName(
                        "com.android.wallpaperaper",
                        "com.android.wallpaperaper.WallpaperPicker"
                    )
                }
                startActivity(intent)
            } catch (e: Exception) {
                val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_SET_WALLPAPER)
                startActivity(android.content.Intent.createChooser(fallbackIntent, "Select Wallpaper"))
            }
        }

        // Setup manage widgets button
        manageWidgetsButton.setOnClickListener {
            openWidgetManagement()
        }

        // Setup manage shortcuts button
        manageShortcutsButton.setOnClickListener {
            openShortcutManagement()
        }

        // Setup manage hidden apps button
        manageHiddenAppsButton.setOnClickListener {
            openHiddenAppsManagement()
        }

        // Setup widget edit mode switch
        widgetEditModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().apply {
                putBoolean("widget_edit_mode", isChecked)
                apply()
            }

            val message = if (isChecked) {
                "Widget edit mode enabled. Long-press widgets to move/resize."
            } else {
                "Widget edit mode disabled"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        // Setup show icons switch
        showIconsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().apply {
                putBoolean("show_app_icons", isChecked)
                apply()
            }
        }

        // Handle back button press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun openWidgetManagement() {
        val intent = Intent(this, WidgetManagementActivity::class.java)
        startActivity(intent)
    }

    private fun openShortcutManagement() {
        val intent = Intent(this, ShortcutManagementActivity::class.java)
        startActivity(intent)
    }

    private fun openHiddenAppsManagement() {
        val intent = Intent(this, HiddenAppsActivity::class.java)
        startActivity(intent)
    }
}