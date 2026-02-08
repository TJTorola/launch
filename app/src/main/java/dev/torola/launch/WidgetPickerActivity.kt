package dev.torola.launch

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Activity for picking a widget from available widget providers
 * Displays all available widgets in a list
 */
class WidgetPickerActivity : AppCompatActivity() {
    
    private lateinit var widgetPickerRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var widgetManagerHelper: WidgetManagerHelper
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_picker)
        
        widgetManagerHelper = WidgetManagerHelper(this)
        
        widgetPickerRecyclerView = findViewById(R.id.widgetPickerRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        
        widgetPickerRecyclerView.layoutManager = LinearLayoutManager(this)
        
        // Handle back button press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        })
        
        loadAvailableWidgets()
    }
    
    private fun loadAvailableWidgets() {
        val providers = widgetManagerHelper.getAllProviders()
        
        if (providers.isEmpty()) {
            widgetPickerRecyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
            return
        }
        
        val widgets = providers.map { providerInfo ->
            WidgetItem(
                providerInfo = providerInfo,
                label = providerInfo.loadLabel(packageManager),
                icon = providerInfo.loadIcon(this, resources.displayMetrics.densityDpi)
            )
        }
        
        widgetPickerRecyclerView.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE
        
        val adapter = WidgetsAdapter(widgets, "Add") { widget ->
            onWidgetSelected(widget)
        }
        widgetPickerRecyclerView.adapter = adapter
    }
    
    private fun onWidgetSelected(widget: WidgetItem) {
        // Pass the selected widget provider component name back to the caller
        val data = android.content.Intent().apply {
            putExtra("widget_provider", widget.providerInfo.provider)
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }
}
