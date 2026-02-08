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
    private lateinit var editWallpaperButton: Button
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
        editWallpaperButton = findViewById(R.id.editWallpaperButton)
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

        // Setup edit wallpaper button
        editWallpaperButton.setOnClickListener {
            editExistingWallpaper()
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

    private fun editExistingWallpaper() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val wallpaperOriginalFile = java.io.File(filesDir, "wallpaper_original.png")

        if (!wallpaperOriginalFile.exists()) {
            Toast.makeText(this, "No wallpaper set", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = Uri.fromFile(wallpaperOriginalFile)
            val scale = prefs.getFloat("wallpaper_scale", 1f)
            val translateX = prefs.getFloat("wallpaper_translate_x", 0f)
            val translateY = prefs.getFloat("wallpaper_translate_y", 0f)

            // Launch wallpaper adjustment activity with restore state
            val intent = Intent(this, WallpaperAdjustActivity::class.java).apply {
                putExtra("imageUri", uri)
                putExtra("restoreState", true)
                putExtra("scale", scale)
                putExtra("translateX", translateX)
                putExtra("translateY", translateY)
            }
            wallpaperAdjustLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open wallpaper: ${e.message}", Toast.LENGTH_LONG).show()
            android.util.Log.e("SettingsActivity", "Failed to edit wallpaper", e)
        }
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

            // Save the original URI for re-editing
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("wallpaper_uri", uri.toString())
                apply()
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
            remove("wallpaper_scale")
            remove("wallpaper_translate_x")
            remove("wallpaper_translate_y")
            apply()
        }

        // Delete the wallpaper files from internal storage
        try {
            val wallpaperFile = java.io.File(filesDir, "wallpaper.png")
            if (wallpaperFile.exists()) {
                wallpaperFile.delete()
            }
            val originalFile = java.io.File(filesDir, "wallpaper_original.png")
            if (originalFile.exists()) {
                originalFile.delete()
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "Failed to delete wallpaper files", e)
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
