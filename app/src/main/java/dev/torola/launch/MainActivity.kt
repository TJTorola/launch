package dev.torola.launch

import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ResolveInfoFlags
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.InputStream
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var appDrawerLayout: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var appsAdapter: AppsAdapter
    private lateinit var widgetContainer: FrameLayout
    private lateinit var widgetHost: LauncherAppWidgetHost
    private lateinit var widgetManagerHelper: WidgetManagerHelper
    private lateinit var gridOverlay: GridOverlayView
    private lateinit var settingsIcon: ImageView
    private var isAppDrawerVisible = false
    private var isWidgetEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Show system wallpaper behind this window
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        
        // Enable edge-to-edge display (transparent system bars)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = false
        
        setContentView(R.layout.activity_main)

        appDrawerLayout = findViewById(R.id.appDrawerLayout)
        searchInput = findViewById(R.id.searchInput)
        appsRecyclerView = findViewById(R.id.appsRecyclerView)
        widgetContainer = findViewById(R.id.widgetContainer)
        settingsIcon = findViewById(R.id.settingsIcon)
        appsRecyclerView.layoutManager = LinearLayoutManager(this)

        settingsIcon.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        
        // Initialize widget host
        widgetHost = LauncherAppWidgetHost(this, WidgetManagerHelper.APPWIDGET_HOST_ID)
        widgetManagerHelper = WidgetManagerHelper(this)
        
        // Create grid overlay first (so it's behind widgets)
        gridOverlay = GridOverlayView(this)
        gridOverlay.visibility = View.GONE
        val gridLayoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        widgetContainer.addView(gridOverlay, gridLayoutParams)
        
        loadApps()
        setupSearchInput()
        loadWidgets()
        
        gestureDetector = GestureDetectorCompat(this, SwipeGestureListener())
        
        // Handle shortcut installation requests
        handleShortcutIntent(intent)
    }
    
    override fun onResume() {
        super.onResume()
        // Reload apps when returning to launcher (e.g., after uninstalling an app)
        loadApps()
        // Reload widgets in case they were added/removed
        loadWidgets()
        // Update widget edit mode based on settings
        updateWidgetEditMode()
    }
    
    override fun onStart() {
        super.onStart()
        // Start listening for widget updates
        widgetHost.startListeningIfResumed()
    }
    
    override fun onStop() {
        super.onStop()
        // Stop listening for widget updates
        widgetHost.stopListeningIfNeeded()
    }
    
    private fun loadWidgets() {
        // Clear existing widgets but keep the grid overlay
        val childCount = widgetContainer.childCount
        for (i in childCount - 1 downTo 0) {
            val child = widgetContainer.getChildAt(i)
            if (child !is GridOverlayView) {
                widgetContainer.removeView(child)
            }
        }
        
        val prefs = getSharedPreferences("widgets", Context.MODE_PRIVATE)
        val widgetIds = prefs.getStringSet("widget_list", emptySet()) ?: emptySet()
        val failedWidgets = mutableListOf<String>()
        
        for (idString in widgetIds) {
            val widgetId = idString.toIntOrNull() ?: continue
            val widgetInfo = widgetManagerHelper.getWidgetInfo(widgetId)
            
            if (widgetInfo == null) {
                // Widget provider no longer available
                failedWidgets.add(idString)
                continue
            }
            
            try {
                // Create widget view
                val widgetView = widgetHost.createView(
                    applicationContext,
                    widgetId,
                    widgetInfo
                )
                
                // Get stored position and size in grid cells, or use defaults
                val storedCellX = prefs.getInt("${widgetId}_x", -1)
                val storedCellY = prefs.getInt("${widgetId}_y", -1)
                val storedCellWidth = prefs.getInt("${widgetId}_width", -1)
                val storedCellHeight = prefs.getInt("${widgetId}_height", -1)
                
                // Calculate default size in cells if not stored
                val density = resources.displayMetrics.density
                val minWidthPx = (widgetInfo.minWidth * density).toInt()
                val minHeightPx = (widgetInfo.minHeight * density).toInt()
                
                val defaultCellWidth = if (storedCellWidth > 0) {
                    storedCellWidth
                } else {
                    ResizableWidgetView.pixelsToCells(minWidthPx).coerceAtLeast(1)
                }
                val defaultCellHeight = if (storedCellHeight > 0) {
                    storedCellHeight
                } else {
                    ResizableWidgetView.pixelsToCells(minHeightPx).coerceAtLeast(1)
                }
                
                // Calculate default position in cells if not stored (vertically stacked)
                val defaultCellX = if (storedCellX >= 0) storedCellX else 0
                val defaultCellY = if (storedCellY >= 0) {
                    storedCellY
                } else {
                    widgetContainer.childCount * (defaultCellHeight + 1)
                }
                
                // Convert grid cells to pixels
                val widthPx = ResizableWidgetView.cellsToPixels(defaultCellWidth)
                val heightPx = ResizableWidgetView.cellsToPixels(defaultCellHeight)
                val xPx = ResizableWidgetView.cellsToPixels(defaultCellX)
                val yPx = ResizableWidgetView.cellsToPixels(defaultCellY)

                // Constrain widget dimensions to fit within screen bounds
                // Use container dimensions if available, otherwise use unconstrained values
                val containerWidth = if (widgetContainer.width > 0) widgetContainer.width else Int.MAX_VALUE
                val containerHeight = if (widgetContainer.height > 0) widgetContainer.height else Int.MAX_VALUE
                val maxWidth = containerWidth - xPx
                val maxHeight = containerHeight - yPx
                val constrainedWidthPx = if (maxWidth > ResizableWidgetView.GRID_CELL_SIZE) {
                    widthPx.coerceIn(ResizableWidgetView.GRID_CELL_SIZE, maxWidth)
                } else {
                    widthPx
                }
                val constrainedHeightPx = if (maxHeight > ResizableWidgetView.GRID_CELL_SIZE) {
                    heightPx.coerceIn(ResizableWidgetView.GRID_CELL_SIZE, maxHeight)
                } else {
                    heightPx
                }
                
                // Wrap widget in resizable container
                val resizableWidget = ResizableWidgetView(
                    this,
                    widgetId,
                    { id, cellX, cellY, cellWidth, cellHeight ->
                        saveWidgetPosition(id, cellX, cellY, cellWidth, cellHeight)
                    },
                    { id, x, y, w, h ->
                        checkWidgetCollision(id, x, y, w, h)
                    }
                )
                resizableWidget.setWidgetView(widgetView)

                // Create layout params with constrained pixel dimensions
                val layoutParams = FrameLayout.LayoutParams(constrainedWidthPx, constrainedHeightPx)
                resizableWidget.layoutParams = layoutParams
                resizableWidget.x = xPx.toFloat()
                resizableWidget.y = yPx.toFloat()
                
                // Add to container
                widgetContainer.addView(resizableWidget)
            } catch (e: Exception) {
                // Widget failed to load - log and mark for cleanup
                android.util.Log.e("MainActivity", "Failed to load widget $widgetId: ${widgetInfo.provider}", e)
                failedWidgets.add(idString)
            }
        }
        
        // Clean up failed widgets from preferences
        if (failedWidgets.isNotEmpty()) {
            val updatedWidgets = widgetIds.toMutableSet()
            updatedWidgets.removeAll(failedWidgets.toSet())
            prefs.edit().apply {
                putStringSet("widget_list", updatedWidgets)
                apply()
            }
        }
    }
    
    private fun saveWidgetPosition(widgetId: Int, cellX: Int, cellY: Int, cellWidth: Int, cellHeight: Int) {
        // Save widget position and size in grid cells (not pixels)
        val prefs = getSharedPreferences("widgets", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("${widgetId}_x", cellX)
            putInt("${widgetId}_y", cellY)
            putInt("${widgetId}_width", cellWidth)
            putInt("${widgetId}_height", cellHeight)
            apply()
        }
    }
    
    private fun checkWidgetCollision(movingWidgetId: Int, x: Int, y: Int, width: Int, height: Int): Boolean {
        // Check if this position/size would collide with any other widget
        for (i in 0 until widgetContainer.childCount) {
            val child = widgetContainer.getChildAt(i)

            // Skip if not a ResizableWidgetView or if it's the moving widget itself
            if (child !is ResizableWidgetView) continue

            // Skip the widget that's being moved
            if (child.getWidgetId() == movingWidgetId) continue
            
            // Check if rectangles overlap
            val otherLeft = child.x.toInt()
            val otherTop = child.y.toInt()
            val otherRight = otherLeft + child.width
            val otherBottom = otherTop + child.height
            
            val thisLeft = x
            val thisTop = y
            val thisRight = x + width
            val thisBottom = y + height
            
            // Check for overlap
            val overlaps = !(thisRight <= otherLeft ||   // This is to the left of other
                            thisLeft >= otherRight ||    // This is to the right of other
                            thisBottom <= otherTop ||    // This is above other
                            thisTop >= otherBottom)      // This is below other
            
            if (overlaps) {
                return true // Collision detected
            }
        }
        return false // No collision
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
    }
    
    private fun handleShortcutIntent(intent: Intent?) {
        when (intent?.action) {
            "dev.torola.launch.ADD_SHORTCUT" -> {
                val shortcutIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("shortcut_intent", Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra("shortcut_intent")
                }
                val shortcutName = intent.getStringExtra("shortcut_name")

                if (shortcutIntent != null && shortcutName != null) {
                    showShortcutConfirmationDialog(shortcutName, shortcutIntent)
                }
            }
            "dev.torola.launch.RELOAD_APPS" -> {
                // Reload apps when a shortcut was added
                loadApps()
            }
        }
    }
    
    private fun showShortcutConfirmationDialog(name: String, intent: Intent) {
        AlertDialog.Builder(this)
            .setTitle("Add Shortcut")
            .setMessage("Do you want to add \"$name\" to your app list?")
            .setPositiveButton("Add") { _, _ ->
                addShortcut(name, intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun addShortcut(name: String, intent: Intent) {
        // Store the shortcut in SharedPreferences
        val prefs = getSharedPreferences("shortcuts", Context.MODE_PRIVATE)
        val shortcuts = prefs.getStringSet("shortcut_list", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        
        // Create a unique ID for this shortcut
        val id = "shortcut_${System.currentTimeMillis()}"
        shortcuts.add(id)
        
        // Save shortcut details
        prefs.edit().apply {
            putStringSet("shortcut_list", shortcuts)
            putString("${id}_name", name)
            putString("${id}_intent", intent.toUri(Intent.URI_INTENT_SCHEME))
            apply()
        }
        
        // Reload apps to include the new shortcut
        loadApps()
    }

    private fun setupSearchInput() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                appsAdapter.filter(s.toString())
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
        
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                launchFirstApp()
                true
            } else {
                false
            }
        }
    }
    
    private fun launchFirstApp() {
        val firstApp = appsAdapter.getFirstApp()
        if (firstApp != null) {
            launchApp(firstApp)
        }
    }

    private fun loadApps() {
        val packageManager = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                mainIntent,
                ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(mainIntent, 0)
        }
        
        val apps = resolveInfoList
            .map { resolveInfo ->
                AppInfo(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    className = resolveInfo.activityInfo.name,
                    icon = resolveInfo.loadIcon(packageManager)
                )
            }
            .toMutableList()
        
        // Add shortcuts to the list
        val shortcuts = loadShortcuts()
        apps.addAll(shortcuts)

        // Filter out hidden apps
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val hiddenAppsPrefs = getSharedPreferences("hidden_apps", Context.MODE_PRIVATE)
        val hiddenApps = hiddenAppsPrefs.getStringSet("hidden_list", emptySet()) ?: emptySet()
        apps.removeAll { appInfo -> hiddenApps.contains(appInfo.packageName) }

        // Sort all apps and shortcuts alphabetically
        apps.sortBy { it.label.lowercase() }

        // Get show icons setting
        val showIcons = prefs.getBoolean("show_app_icons", false)

        appsAdapter = AppsAdapter(
            apps,
            onAppClick = { appInfo ->
                launchApp(appInfo)
            },
            onAppLongPress = { appInfo ->
                showUninstallDialog(appInfo)
            },
            showIcons = showIcons
        )
        appsRecyclerView.adapter = appsAdapter
    }
    
    private fun loadShortcuts(): List<AppInfo> {
        val prefs = getSharedPreferences("shortcuts", Context.MODE_PRIVATE)
        val shortcutIds = prefs.getStringSet("shortcut_list", emptySet()) ?: emptySet()
        
        return shortcutIds.mapNotNull { id ->
            val name = prefs.getString("${id}_name", null)
            
            // Check if this is a pinned shortcut (has shortcut_id) or legacy shortcut (has intent)
            val shortcutId = prefs.getString("${id}_shortcut_id", null)
            val intentUri = prefs.getString("${id}_intent", null)
            
            if (name != null && (shortcutId != null || intentUri != null)) {
                AppInfo(
                    label = name,
                    packageName = id, // Use our ID as package name
                    className = "",
                    icon = packageManager.defaultActivityIcon
                )
            } else {
                null
            }
        }
    }

    private fun launchApp(appInfo: AppInfo) {
        try {
            // Check if this is a shortcut (packageName starts with "shortcut_")
            if (appInfo.packageName.startsWith("shortcut_")) {
                val prefs = getSharedPreferences("shortcuts", Context.MODE_PRIVATE)
                
                // Check if this is a pinned shortcut (Android 8.0+)
                val shortcutId = prefs.getString("${appInfo.packageName}_shortcut_id", null)
                val packageName = prefs.getString("${appInfo.packageName}_package", null)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && shortcutId != null && packageName != null) {
                    // Launch pinned shortcut using LauncherApps
                    val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
                } else {
                    // Legacy shortcut - use the stored intent
                    val intentUri = prefs.getString("${appInfo.packageName}_intent", null)
                    if (intentUri != null) {
                        val intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    }
                }
            } else {
                // Regular app launch
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setClassName(appInfo.packageName, appInfo.className)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
            hideAppDrawer()
        } catch (e: Exception) {
            // Handle launch errors silently
            e.printStackTrace()
        }
    }

    private fun showUninstallDialog(appInfo: AppInfo) {
        val intent = Intent(this, AppOptionsActivity::class.java).apply {
            putExtra(AppOptionsActivity.EXTRA_PACKAGE_NAME, appInfo.packageName)
            putExtra(AppOptionsActivity.EXTRA_CLASS_NAME, appInfo.className)
            putExtra(AppOptionsActivity.EXTRA_APP_LABEL, appInfo.label)
            putExtra(AppOptionsActivity.EXTRA_IS_SHORTCUT, appInfo.packageName.startsWith("shortcut_"))
        }
        startActivity(intent)
    }



    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (gestureDetector.onTouchEvent(event)) {
            true
        } else {
            super.onTouchEvent(event)
        }
    }

    private fun showAppDrawer() {
        if (isAppDrawerVisible) return
        
        appDrawerLayout.visibility = View.VISIBLE
        isAppDrawerVisible = true
        
        // Start off-screen at the bottom
        appDrawerLayout.translationY = appDrawerLayout.height.toFloat()
        
        // Animate sliding up
        appDrawerLayout.animate()
            .translationY(0f)
            .setDuration(100)
            .withEndAction {
                // Clear search and reset filter after animation
                searchInput.setText("")
                
                // Show keyboard
                searchInput.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
            }
            .start()
    }

    private fun hideAppDrawer() {
        if (!isAppDrawerVisible) return
        
        // Hide keyboard immediately
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
        
        // Animate sliding down
        appDrawerLayout.animate()
            .translationY(appDrawerLayout.height.toFloat())
            .setDuration(100)
            .withEndAction {
                appDrawerLayout.visibility = View.GONE
                isAppDrawerVisible = false
                
                // Clear search
                searchInput.setText("")
            }
            .start()
    }

    override fun onBackPressed() {
        if (isAppDrawerVisible) {
            hideAppDrawer()
        } else {
            // Don't call super - this prevents exiting the launcher
        }
    }

    inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        
        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false
            
            val diffY = e2.y - e1.y
            val diffX = e2.x - e1.x
            
            if (abs(diffY) > abs(diffX)) {
                if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY < 0) {
                        // Swipe up
                        showAppDrawer()
                        return true
                    } else {
                        // Swipe down
                        hideAppDrawer()
                        return true
                    }
                }
            }
            return false
        }
    }
    
    private fun updateWidgetEditMode() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        isWidgetEditMode = prefs.getBoolean("widget_edit_mode", false)
        
        // Show/hide grid overlay
        gridOverlay.visibility = if (isWidgetEditMode) View.VISIBLE else View.GONE
        
        // Update all ResizableWidgetViews
        for (i in 0 until widgetContainer.childCount) {
            val child = widgetContainer.getChildAt(i)
            if (child is ResizableWidgetView) {
                child.setEditMode(isWidgetEditMode)
            }
        }
    }
}
