package dev.torola.launch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ShortcutReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.android.launcher.action.INSTALL_SHORTCUT") {
            // Handle legacy shortcut request
            val shortcutIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("android.intent.extra.shortcut.INTENT", Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>("android.intent.extra.shortcut.INTENT")
            }
            val shortcutName = intent.getStringExtra("android.intent.extra.shortcut.NAME")
            
            if (shortcutIntent != null && shortcutName != null) {
                addShortcutToLauncher(context, shortcutName, shortcutIntent)
            }
        }
    }
    
    private fun addShortcutToLauncher(context: Context, name: String, intent: Intent) {
        val prefs = context.getSharedPreferences("shortcuts", Context.MODE_PRIVATE)
        val shortcuts = prefs.getStringSet("shortcut_list", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        
        val id = "shortcut_${System.currentTimeMillis()}"
        shortcuts.add(id)
        
        prefs.edit().apply {
            putStringSet("shortcut_list", shortcuts)
            putString("${id}_name", name)
            putString("${id}_intent", intent.toUri(Intent.URI_INTENT_SCHEME))
            apply()
        }
        
        // Notify MainActivity to reload apps
        val reloadIntent = Intent(context, MainActivity::class.java).apply {
            action = "dev.torola.launch.RELOAD_APPS"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(reloadIntent)
    }
}
