package dev.torola.launch

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SettingsActivity : AppCompatActivity() {

    private lateinit var backIcon: ImageView
    private lateinit var selectWallpaperButton: Button
    private lateinit var clearWallpaperButton: Button
    private lateinit var manageWidgetsButton: Button
    private lateinit var manageShortcutsButton: Button
    private lateinit var manageHiddenAppsButton: Button
    private lateinit var widgetEditModeSwitch: com.google.android.material.materialswitch.MaterialSwitch
    
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
        
        selectWallpaperButton = findViewById(R.id.selectWallpaperButton)
        backIcon = findViewById(R.id.backIcon)
        backIcon.setOnClickListener { finish() }
        clearWallpaperButton = findViewById(R.id.clearWallpaperButton)
        manageWidgetsButton = findViewById(R.id.manageWidgetsButton)
        manageShortcutsButton = findViewById(R.id.manageShortcutsButton)
        manageHiddenAppsButton = findViewById(R.id.manageHiddenAppsButton)
        widgetEditModeSwitch = findViewById(R.id.widgetEditModeSwitch)
        
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
        
        // Handle back button press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
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

data class ShortcutItem(
    val id: String,
    val name: String
)
