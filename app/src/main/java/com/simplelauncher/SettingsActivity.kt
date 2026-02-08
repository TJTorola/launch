package com.simplelauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var shortcutsRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var shortcutsAdapter: ShortcutsAdapter
    private lateinit var selectWallpaperButton: Button
    private lateinit var clearWallpaperButton: Button
    private lateinit var manageWidgetsButton: Button
    private lateinit var manageHiddenAppsButton: Button
    private lateinit var widgetEditModeSwitch: androidx.appcompat.widget.SwitchCompat
    
    // Activity result launcher for image picker
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                openWallpaperAdjustment(uri)
            }
        }
    }
    
    // Activity result launcher for wallpaper adjustment
    private val wallpaperAdjustLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Wallpaper updated", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        shortcutsRecyclerView = findViewById(R.id.shortcutsRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        selectWallpaperButton = findViewById(R.id.selectWallpaperButton)
        clearWallpaperButton = findViewById(R.id.clearWallpaperButton)
        manageWidgetsButton = findViewById(R.id.manageWidgetsButton)
        manageHiddenAppsButton = findViewById(R.id.manageHiddenAppsButton)
        widgetEditModeSwitch = findViewById(R.id.widgetEditModeSwitch)
        
        shortcutsRecyclerView.layoutManager = LinearLayoutManager(this)
        
        // Load widget edit mode state
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        widgetEditModeSwitch.isChecked = prefs.getBoolean("widget_edit_mode", false)
        
        // Setup wallpaper selection button
        selectWallpaperButton.setOnClickListener {
            openImagePicker()
        }
        
        // Setup clear wallpaper button
        clearWallpaperButton.setOnClickListener {
            removeWallpaper()
        }
        
        // Setup manage widgets button
        manageWidgetsButton.setOnClickListener {
            openWidgetManagement()
        }

        // Setup manage hidden apps button
        manageHiddenAppsButton.setOnClickListener {
            showHiddenApps()
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
        
        // Handle back button press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
        
        loadShortcuts()
    }
    
    private fun openImagePicker() {
        // Use ACTION_OPEN_DOCUMENT for proper persistent permission support
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // Request persistent permission to access the image
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
        }
        imagePickerLauncher.launch(intent)
    }
    
    private fun openWallpaperAdjustment(uri: Uri) {
        try {
            // Take persistent permission to access the image
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    android.util.Log.w("SettingsActivity", "Could not take persistent permission", e)
                }
            }
            
            // Launch wallpaper adjustment activity
            val intent = Intent(this, WallpaperAdjustActivity::class.java).apply {
                putExtra("imageUri", uri)
            }
            wallpaperAdjustLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open image: ${e.message}", Toast.LENGTH_LONG).show()
            android.util.Log.e("SettingsActivity", "Failed to open wallpaper adjustment", e)
        }
    }
    
    private fun removeWallpaper() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove("wallpaper_uri")
            remove("wallpaper_path")
            apply()
        }
        
        // Delete the wallpaper file from internal storage
        try {
            val file = java.io.File(filesDir, "wallpaper.png")
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "Failed to delete wallpaper file", e)
        }
        
        Toast.makeText(this, "Wallpaper cleared", Toast.LENGTH_SHORT).show()
    }
    
    private fun loadShortcuts() {
        val prefs = getSharedPreferences("shortcuts", Context.MODE_PRIVATE)
        val shortcutIds = prefs.getStringSet("shortcut_list", emptySet()) ?: emptySet()
        
        val shortcuts = shortcutIds.mapNotNull { id ->
            val name = prefs.getString("${id}_name", null)
            if (name != null) {
                ShortcutItem(id, name)
            } else {
                null
            }
        }.sortedBy { it.name.lowercase() }
        
        if (shortcuts.isEmpty()) {
            shortcutsRecyclerView.visibility = android.view.View.GONE
            emptyStateText.visibility = android.view.View.VISIBLE
        } else {
            shortcutsRecyclerView.visibility = android.view.View.VISIBLE
            emptyStateText.visibility = android.view.View.GONE
            
            shortcutsAdapter = ShortcutsAdapter(shortcuts) { shortcut ->
                showDeleteConfirmation(shortcut)
            }
            shortcutsRecyclerView.adapter = shortcutsAdapter
        }
    }
    
    private fun showDeleteConfirmation(shortcut: ShortcutItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Shortcut")
            .setMessage("Do you want to remove \"${shortcut.name}\" from your app list?")
            .setPositiveButton("Delete") { _, _ ->
                deleteShortcut(shortcut)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteShortcut(shortcut: ShortcutItem) {
        val prefs = getSharedPreferences("shortcuts", Context.MODE_PRIVATE)
        val shortcuts = prefs.getStringSet("shortcut_list", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        
        shortcuts.remove(shortcut.id)
        
        prefs.edit().apply {
            putStringSet("shortcut_list", shortcuts)
            remove("${shortcut.id}_name")
            remove("${shortcut.id}_intent")
            remove("${shortcut.id}_shortcut_id")
            remove("${shortcut.id}_package")
            apply()
        }
        
        // Reload the list
        loadShortcuts()
    }
    
    private fun openWidgetManagement() {
        val intent = Intent(this, WidgetManagementActivity::class.java)
        startActivity(intent)
    }

    private fun showHiddenApps() {
        val prefs = getSharedPreferences("hidden_apps", Context.MODE_PRIVATE)
        val hiddenPackages = prefs.getStringSet("hidden_list", emptySet())?.toList() ?: emptyList()

        if (hiddenPackages.isEmpty()) {
            Toast.makeText(this, "No hidden apps", Toast.LENGTH_SHORT).show()
            return
        }

        // Get app labels for the hidden packages
        val packageManager = packageManager
        val hiddenApps = hiddenPackages.mapNotNull { packageName ->
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val label = packageManager.getApplicationLabel(appInfo).toString()
                Pair(packageName, label)
            } catch (e: Exception) {
                // App is no longer installed, should be cleaned up
                null
            }
        }.sortedBy { it.second }

        if (hiddenApps.isEmpty()) {
            // Clean up invalid entries
            prefs.edit().putStringSet("hidden_list", emptySet()).apply()
            Toast.makeText(this, "No hidden apps", Toast.LENGTH_SHORT).show()
            return
        }

        val appLabels = hiddenApps.map { it.second }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Hidden Apps")
            .setItems(appLabels) { _, which ->
                val selectedApp = hiddenApps[which]
                showUnhideConfirmation(selectedApp.first, selectedApp.second)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showUnhideConfirmation(packageName: String, appLabel: String) {
        AlertDialog.Builder(this)
            .setTitle("Unhide App")
            .setMessage("Do you want to show \"$appLabel\" in the app list again?")
            .setPositiveButton("Unhide") { _, _ ->
                unhideApp(packageName, appLabel)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun unhideApp(packageName: String, appLabel: String) {
        val prefs = getSharedPreferences("hidden_apps", Context.MODE_PRIVATE)
        val hiddenApps = prefs.getStringSet("hidden_list", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        hiddenApps.remove(packageName)

        prefs.edit().apply {
            putStringSet("hidden_list", hiddenApps)
            apply()
        }

        Toast.makeText(this, "\"$appLabel\" unhidden", Toast.LENGTH_SHORT).show()
    }
}

data class ShortcutItem(
    val id: String,
    val name: String
)
