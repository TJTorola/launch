package com.simplelauncher

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

@RequiresApi(Build.VERSION_CODES.O)
class PinShortcutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("PinShortcutActivity", "onCreate called")

        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val request = launcherApps.getPinItemRequest(intent)

        Log.d("PinShortcutActivity", "Request: $request")

        if (request == null) {
            Log.e("PinShortcutActivity", "Request is null")
            finish()
            return
        }

        Log.d("PinShortcutActivity", "Request type: ${request.requestType}")

        if (request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            Log.e("PinShortcutActivity", "Not a shortcut request")
            finish()
            return
        }

        val shortcutInfo = request.shortcutInfo
        if (shortcutInfo == null) {
            Log.e("PinShortcutActivity", "ShortcutInfo is null")
            finish()
            return
        }

        val shortcutName = shortcutInfo.shortLabel?.toString() 
            ?: shortcutInfo.longLabel?.toString() 
            ?: "Shortcut"

        Log.d("PinShortcutActivity", "Shortcut name: $shortcutName")
        Log.d("PinShortcutActivity", "Shortcut ID: ${shortcutInfo.id}")
        Log.d("PinShortcutActivity", "Shortcut package: ${shortcutInfo.`package`}")

        // Show confirmation dialog
        AlertDialog.Builder(this)
            .setTitle("Add Shortcut")
            .setMessage("Do you want to add \"$shortcutName\" to your app list?")
            .setPositiveButton("Add") { _, _ ->
                Log.d("PinShortcutActivity", "User accepted shortcut")
                // Accept the pin request
                if (request.accept()) {
                    Log.d("PinShortcutActivity", "Request accepted successfully")
                    // Save the shortcut ID and package
                    addShortcutToLauncher(shortcutName, shortcutInfo.id, shortcutInfo.`package`)
                } else {
                    Log.e("PinShortcutActivity", "Failed to accept request")
                }
                finish()
            }
            .setNegativeButton("Cancel") { _, _ ->
                Log.d("PinShortcutActivity", "User cancelled shortcut")
                finish()
            }
            .setOnDismissListener {
                finish()
            }
            .show()
    }

    private fun addShortcutToLauncher(name: String, shortcutId: String, packageName: String) {
        val prefs = getSharedPreferences("shortcuts", Context.MODE_PRIVATE)
        val shortcuts = prefs.getStringSet("shortcut_list", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        
        val id = "shortcut_${System.currentTimeMillis()}"
        shortcuts.add(id)
        
        prefs.edit().apply {
            putStringSet("shortcut_list", shortcuts)
            putString("${id}_name", name)
            putString("${id}_shortcut_id", shortcutId)
            putString("${id}_package", packageName)
            apply()
        }
        
        // Notify MainActivity to reload
        val reloadIntent = android.content.Intent(this, MainActivity::class.java).apply {
            action = "com.simplelauncher.RELOAD_APPS"
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(reloadIntent)
    }
}
