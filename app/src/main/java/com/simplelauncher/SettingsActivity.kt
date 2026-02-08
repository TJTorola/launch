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
        
        shortcutsRecyclerView.layoutManager = LinearLayoutManager(this)
        
        // Setup wallpaper selection button
        selectWallpaperButton.setOnClickListener {
            openImagePicker()
        }
        
        // Setup clear wallpaper button
        clearWallpaperButton.setOnClickListener {
            removeWallpaper()
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
}

data class ShortcutItem(
    val id: String,
    val name: String
)
