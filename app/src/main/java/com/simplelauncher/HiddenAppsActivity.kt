package com.simplelauncher

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HiddenAppsActivity : AppCompatActivity() {

    private lateinit var hiddenAppsRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var hiddenAppsAdapter: HiddenAppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hidden_apps)

        hiddenAppsRecyclerView = findViewById(R.id.hiddenAppsRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)

        hiddenAppsRecyclerView.layoutManager = LinearLayoutManager(this)

        // Handle back button press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        loadHiddenApps()
    }

    override fun onResume() {
        super.onResume()
        loadHiddenApps()
    }

    private fun loadHiddenApps() {
        val prefs = getSharedPreferences("hidden_apps", Context.MODE_PRIVATE)
        val hiddenPackages = prefs.getStringSet("hidden_list", emptySet())?.toList() ?: emptyList()

        if (hiddenPackages.isEmpty()) {
            hiddenAppsRecyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
            return
        }

        // Get app labels for the hidden packages
        val packageManager = packageManager
        val hiddenApps = hiddenPackages.mapNotNull { packageName ->
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val label = packageManager.getApplicationLabel(appInfo).toString()
                HiddenAppItem(packageName, label)
            } catch (e: Exception) {
                // App is no longer installed, should be cleaned up
                null
            }
        }.sortedBy { it.label.lowercase() }

        if (hiddenApps.isEmpty()) {
            // Clean up invalid entries
            prefs.edit().putStringSet("hidden_list", emptySet()).apply()
            hiddenAppsRecyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
        } else {
            hiddenAppsRecyclerView.visibility = View.VISIBLE
            emptyStateText.visibility = View.GONE

            hiddenAppsAdapter = HiddenAppsAdapter(hiddenApps) { hiddenApp ->
                showUnhideConfirmation(hiddenApp)
            }
            hiddenAppsRecyclerView.adapter = hiddenAppsAdapter
        }
    }

    private fun showUnhideConfirmation(hiddenApp: HiddenAppItem) {
        AlertDialog.Builder(this)
            .setTitle("Unhide App")
            .setMessage("Do you want to show \"${hiddenApp.label}\" in the app list again?")
            .setPositiveButton("Unhide") { _, _ ->
                unhideApp(hiddenApp)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun unhideApp(hiddenApp: HiddenAppItem) {
        val prefs = getSharedPreferences("hidden_apps", Context.MODE_PRIVATE)
        val hiddenApps = prefs.getStringSet("hidden_list", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        hiddenApps.remove(hiddenApp.packageName)

        prefs.edit().apply {
            putStringSet("hidden_list", hiddenApps)
            apply()
        }

        Toast.makeText(this, "\"${hiddenApp.label}\" unhidden", Toast.LENGTH_SHORT).show()

        // Reload the list
        loadHiddenApps()
    }
}

data class HiddenAppItem(
    val packageName: String,
    val label: String
)

class HiddenAppsAdapter(
    private val hiddenApps: List<HiddenAppItem>,
    private val onAppClick: (HiddenAppItem) -> Unit
) : RecyclerView.Adapter<HiddenAppsAdapter.HiddenAppViewHolder>() {

    inner class HiddenAppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appLabel: TextView = itemView.findViewById(R.id.shortcutNameLabel)
        val deleteButton: TextView = itemView.findViewById(R.id.deleteButton)

        fun bind(hiddenApp: HiddenAppItem) {
            appLabel.text = hiddenApp.label
            deleteButton.text = "Unhide"
            itemView.setOnClickListener {
                onAppClick(hiddenApp)
            }
            deleteButton.setOnClickListener {
                onAppClick(hiddenApp)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HiddenAppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shortcut, parent, false)
        return HiddenAppViewHolder(view)
    }

    override fun onBindViewHolder(holder: HiddenAppViewHolder, position: Int) {
        holder.bind(hiddenApps[position])
    }

    override fun getItemCount(): Int = hiddenApps.size
}
