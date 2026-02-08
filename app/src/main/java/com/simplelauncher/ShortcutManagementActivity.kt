package com.simplelauncher

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ShortcutManagementActivity : AppCompatActivity() {

    private lateinit var shortcutsRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var shortcutsAdapter: ShortcutsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shortcut_management)

        shortcutsRecyclerView = findViewById(R.id.shortcutsRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)

        shortcutsRecyclerView.layoutManager = LinearLayoutManager(this)

        // Handle back button press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        loadShortcuts()
    }

    override fun onResume() {
        super.onResume()
        loadShortcuts()
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
            shortcutsRecyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
        } else {
            shortcutsRecyclerView.visibility = View.VISIBLE
            emptyStateText.visibility = View.GONE

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
