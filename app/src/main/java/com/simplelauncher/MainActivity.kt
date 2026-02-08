package com.simplelauncher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ResolveInfoFlags
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
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
    private lateinit var wallpaperImageView: ImageView
    private var isAppDrawerVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wallpaperImageView = findViewById(R.id.wallpaperImageView)
        appDrawerLayout = findViewById(R.id.appDrawerLayout)
        searchInput = findViewById(R.id.searchInput)
        appsRecyclerView = findViewById(R.id.appsRecyclerView)
        appsRecyclerView.layoutManager = LinearLayoutManager(this)
        
        loadApps()
        setupSearchInput()
        loadWallpaper()
        
        gestureDetector = GestureDetectorCompat(this, SwipeGestureListener())
        
        // Handle shortcut installation requests
        handleShortcutIntent(intent)
    }
    
    override fun onResume() {
        super.onResume()
        // Reload wallpaper when returning to launcher (e.g., after changing it in settings)
        loadWallpaper()
    }
    
    private fun loadWallpaper() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        
        // First try to load from internal storage (adjusted wallpaper)
        val wallpaperPath = prefs.getString("wallpaper_path", null)
        if (wallpaperPath != null) {
            try {
                val file = java.io.File(wallpaperPath)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(wallpaperPath)
                    wallpaperImageView.setImageBitmap(bitmap)
                    wallpaperImageView.visibility = View.VISIBLE
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Fall back to URI-based wallpaper (for backward compatibility)
        val wallpaperUriString = prefs.getString("wallpaper_uri", null)
        if (wallpaperUriString != null) {
            try {
                val uri = Uri.parse(wallpaperUriString)
                wallpaperImageView.setImageURI(uri)
                wallpaperImageView.visibility = View.VISIBLE
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // No wallpaper set, hide the ImageView (black background will show)
        wallpaperImageView.visibility = View.GONE
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
    }
    
    private fun handleShortcutIntent(intent: Intent?) {
        when (intent?.action) {
            "com.simplelauncher.ADD_SHORTCUT" -> {
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
            "com.simplelauncher.RELOAD_APPS" -> {
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
        
        // Add Launch Settings as a special item
        apps.add(AppInfo(
            label = "Launch Settings",
            packageName = "com.simplelauncher.SETTINGS",
            className = "",
            icon = packageManager.defaultActivityIcon,
            isSettings = true
        ))
        
        // Sort all apps and shortcuts alphabetically
        apps.sortBy { it.label.lowercase() }
        
        appsAdapter = AppsAdapter(apps) { appInfo ->
            launchApp(appInfo)
        }
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
            // Check if this is the settings item
            if (appInfo.isSettings) {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                hideAppDrawer()
                return
            }
            
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
}
