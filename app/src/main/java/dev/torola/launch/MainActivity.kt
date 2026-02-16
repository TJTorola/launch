package dev.torola.launch

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentManager
import kotlin.math.abs

class MainActivity : AppCompatActivity(),
    AppDrawerFragment.AppDrawerFragmentCallback {

    private lateinit var fragmentManager: FragmentManager
    private var isAppDrawerVisible = false
    private lateinit var gestureDetector: GestureDetectorCompat
    private var appDrawerFragment: AppDrawerFragment? = null
    
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

        fragmentManager = supportFragmentManager
        
        gestureDetector = GestureDetectorCompat(this, SwipeGestureListener())
        
        // Load WidgetFragment initially
        if (savedInstanceState == null) {
            fragmentManager.beginTransaction()
                .add(R.id.fragment_container, WidgetFragment(), "widget_fragment")
                .commit()
        }
        
        handleShortcutIntent(intent)
    }
    
    override fun onResume() {
        super.onResume()
        (fragmentManager.findFragmentByTag("widget_fragment") as? WidgetFragment)?.reloadWidgets()
    }
    
    private fun showAppDrawer() {
        if (isAppDrawerVisible) return
        isAppDrawerVisible = true
        hapticFeedback()
        
        appDrawerFragment = AppDrawerFragment()
        fragmentManager.beginTransaction()
            .replace(R.id.fragment_container, appDrawerFragment!!, "app_drawer_fragment")
            .addToBackStack("app_drawer")
            .commit()
    }
    
    private fun hideAppDrawer() {
        if (!isAppDrawerVisible) return
        isAppDrawerVisible = false
        appDrawerFragment = null
        
        fragmentManager.popBackStack("app_drawer", FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }
    
    private fun hapticFeedback() {
        val vibrator = getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createOneShot(10, 50))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (gestureDetector.onTouchEvent(event)) {
            true
        } else {
            super.onTouchEvent(event)
        }
    }
    
    override fun onBackPressed() {
        if (isAppDrawerVisible) {
            hideAppDrawer()
        }
        // Don't call super - this prevents exiting the launcher
    }

    inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        
        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100
        private val LONG_PRESS_THRESHOLD = 50f
        
        private var initialTouchX: Float = 0f
        private var initialTouchY: Float = 0f
        private var movementExceeded: Boolean = false

        override fun onDown(e: MotionEvent): Boolean {
            initialTouchX = e.x
            initialTouchY = e.y
            movementExceeded = false
            return true
        }
        
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            val diffX = e2.x - initialTouchX
            val diffY = e2.y - initialTouchY
            
            if (kotlin.math.abs(diffX) > LONG_PRESS_THRESHOLD || kotlin.math.abs(diffY) > LONG_PRESS_THRESHOLD) {
                movementExceeded = true
            }
            return false
        }
        
        override fun onLongPress(e: MotionEvent) {
            if (!movementExceeded && !isAppDrawerVisible) {
                val widgetFragment = fragmentManager.findFragmentByTag("widget_fragment") as? WidgetFragment
                widgetFragment?.toggleEditMode()
            }
        }

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
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
    }
    
    private fun handleShortcutIntent(intent: Intent?) {
        if (intent?.action == "dev.torola.launch.ADD_SHORTCUT") {
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
        val prefs = getSharedPreferences("shortcuts", Context.MODE_PRIVATE)
        val shortcuts = prefs.getStringSet("shortcut_list", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        
        val id = "shortcut_${System.currentTimeMillis()}"
        shortcuts.add(id)
        
        prefs.edit().apply {
            putStringSet("shortcut_list", shortcuts)
            putString("${id}_name", name)
            putString("${id}_intent", intent.toUri(Intent.URI_INTENT_SCHEME))
            apply()
        }
        
        appDrawerFragment?.loadApps()
    }
    
    // AppDrawerFragmentCallback
    override fun onAppLaunched(appInfo: AppInfo) {
        hideAppDrawer()
    }
    
    override fun onSettingsClick() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
}