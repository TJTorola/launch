package dev.torola.launch

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Activity for managing widgets on the home screen
 * Allows users to add, view, and remove widgets
 */
class WidgetManagementActivity : AppCompatActivity() {
    
    private lateinit var widgetsRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var addWidgetButton: Button
    private lateinit var widgetHost: LauncherAppWidgetHost
    private lateinit var widgetManager: AppWidgetManager
    private lateinit var widgetManagerHelper: WidgetManagerHelper
    
    private var pendingWidgetId: Int = -1
    private var pendingWidgetProviderInfo: AppWidgetProviderInfo? = null
    
    // Activity result launcher for widget configuration
    private val widgetConfigLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Widget configured successfully, save it
            if (pendingWidgetId != -1) {
                saveWidget(pendingWidgetId)
                enableEditMode()
                Toast.makeText(this, "Widget added. Edit mode enabled.", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Configuration cancelled, clean up widget ID
            if (pendingWidgetId != -1) {
                widgetHost.deleteAppWidgetId(pendingWidgetId)
            }
        }
        pendingWidgetId = -1
        pendingWidgetProviderInfo = null
        loadWidgets()
    }
    
    // Activity result launcher for widget binding permission
    private val widgetBindLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingWidgetProviderInfo != null) {
            // Permission granted, try binding again
            configureWidget(pendingWidgetId, pendingWidgetProviderInfo!!)
        } else {
            // Permission denied, clean up
            if (pendingWidgetId != -1) {
                widgetHost.deleteAppWidgetId(pendingWidgetId)
            }
            pendingWidgetId = -1
            pendingWidgetProviderInfo = null
        }
    }
    
    // Activity result launcher for widget picker
    private val widgetPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val componentName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data!!.getParcelableExtra("widget_provider", android.content.ComponentName::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data!!.getParcelableExtra("widget_provider")
            }
            
            if (componentName != null) {
                // Find the provider info and add the widget
                val providers = widgetManagerHelper.getAllProviders()
                val providerInfo = providers.find { it.provider == componentName }
                if (providerInfo != null) {
                    addWidget(providerInfo)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_management)
        
        widgetHost = LauncherAppWidgetHost(this, WidgetManagerHelper.APPWIDGET_HOST_ID)
        widgetManager = AppWidgetManager.getInstance(this)
        widgetManagerHelper = WidgetManagerHelper(this)
        
        widgetsRecyclerView = findViewById(R.id.widgetsRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        addWidgetButton = findViewById(R.id.addWidgetButton)
        
        widgetsRecyclerView.layoutManager = LinearLayoutManager(this)
        
        addWidgetButton.setOnClickListener {
            openWidgetPicker()
        }
        
        // Handle back button press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
        
        loadWidgets()
    }
    
    override fun onStart() {
        super.onStart()
        widgetHost.startListeningIfResumed()
    }
    
    override fun onStop() {
        super.onStop()
        widgetHost.stopListeningIfNeeded()
    }
    
    private fun openWidgetPicker() {
        val intent = Intent(this, WidgetPickerActivity::class.java)
        widgetPickerLauncher.launch(intent)
    }
    
    private fun loadWidgets() {
        val prefs = getSharedPreferences("widgets", Context.MODE_PRIVATE)
        val widgetIds = prefs.getStringSet("widget_list", emptySet()) ?: emptySet()
        
        val widgets = widgetIds.mapNotNull { idString ->
            val id = idString.toIntOrNull() ?: return@mapNotNull null
            val providerInfo = widgetManagerHelper.getWidgetInfo(id) ?: return@mapNotNull null
            
            WidgetItem(
                providerInfo = providerInfo,
                label = providerInfo.loadLabel(packageManager),
                icon = providerInfo.loadIcon(this, resources.displayMetrics.densityDpi),
                widgetId = id
            )
        }.sortedBy { it.label.lowercase() }
        
        if (widgets.isEmpty()) {
            widgetsRecyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
        } else {
            widgetsRecyclerView.visibility = View.VISIBLE
            emptyStateText.visibility = View.GONE
            
            val adapter = WidgetsAdapter(widgets, "Remove") { widget ->
                showDeleteConfirmation(widget)
            }
            widgetsRecyclerView.adapter = adapter
        }
    }
    
    private fun showDeleteConfirmation(widget: WidgetItem) {
        AlertDialog.Builder(this)
            .setTitle("Remove Widget")
            .setMessage("Do you want to remove \"${widget.label}\" from your home screen?")
            .setPositiveButton("Remove") { _, _ ->
                deleteWidget(widget.widgetId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteWidget(widgetId: Int) {
        // Remove from SharedPreferences
        val prefs = getSharedPreferences("widgets", Context.MODE_PRIVATE)
        val widgetIds = prefs.getStringSet("widget_list", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        widgetIds.remove(widgetId.toString())
        
        prefs.edit().apply {
            putStringSet("widget_list", widgetIds)
            apply()
        }
        
        // Delete from AppWidgetHost
        widgetHost.deleteAppWidgetId(widgetId)
        
        // Reload the list
        loadWidgets()
        
        Toast.makeText(this, "Widget removed", Toast.LENGTH_SHORT).show()
    }
    
    private fun configureWidget(widgetId: Int, providerInfo: AppWidgetProviderInfo) {
        if (providerInfo.configure != null) {
            // Widget requires configuration
            try {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = providerInfo.configure
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                widgetConfigLauncher.launch(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to configure widget: ${e.message}", Toast.LENGTH_LONG).show()
                widgetHost.deleteAppWidgetId(widgetId)
                pendingWidgetId = -1
                pendingWidgetProviderInfo = null
            }
        } else {
            // No configuration needed, save directly
            saveWidget(widgetId)
            enableEditMode()
            Toast.makeText(this, "Widget added. Edit mode enabled.", Toast.LENGTH_SHORT).show()
            loadWidgets()
        }
    }
    
    private fun saveWidget(widgetId: Int) {
        val prefs = getSharedPreferences("widgets", Context.MODE_PRIVATE)
        val widgetIds = prefs.getStringSet("widget_list", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        widgetIds.add(widgetId.toString())
        
        prefs.edit().apply {
            putStringSet("widget_list", widgetIds)
            apply()
        }
    }
    
    /**
     * Called by WidgetPickerActivity when a widget is selected
     */
    fun addWidget(providerInfo: AppWidgetProviderInfo) {
        // Allocate a widget ID
        val widgetId = widgetHost.allocateAppWidgetId()
        pendingWidgetId = widgetId
        pendingWidgetProviderInfo = providerInfo
        
        // Try to bind the widget
        val bound = widgetManagerHelper.bindAppWidgetIdIfAllowed(widgetId, providerInfo)
        
        if (bound) {
            // Binding successful, configure if needed
            configureWidget(widgetId, providerInfo)
        } else {
            // Need permission to bind widgets
            try {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
                }
                widgetBindLauncher.launch(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to bind widget: ${e.message}", Toast.LENGTH_LONG).show()
                widgetHost.deleteAppWidgetId(widgetId)
                pendingWidgetId = -1
                pendingWidgetProviderInfo = null
            }
        }
    }
    
    private fun enableEditMode() {
        // Enable widget edit mode in settings
        val settingsPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        settingsPrefs.edit().apply {
            putBoolean("widget_edit_mode", true)
            apply()
        }
    }
}
