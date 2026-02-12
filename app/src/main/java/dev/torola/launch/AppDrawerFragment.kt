package dev.torola.launch

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ResolveInfoFlags
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppDrawerFragment : Fragment() {
    
    private lateinit var searchInput: EditText
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var appsAdapter: AppsAdapter
    private lateinit var settingsIcon: ImageView
    
    private var callback: AppDrawerFragmentCallback? = null
    
    interface AppDrawerFragmentCallback {
        fun onAppLaunched(appInfo: AppInfo)
        fun onSettingsClick()
    }
    
    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = context as? AppDrawerFragmentCallback
    }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_app_drawer, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        searchInput = view.findViewById(R.id.searchInput)
        appsRecyclerView = view.findViewById(R.id.appsRecyclerView)
        settingsIcon = view.findViewById(R.id.settingsIcon)
        appsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        setupSearchInput()
        loadApps()
        
        settingsIcon.setOnClickListener {
            callback?.onSettingsClick()
        }
    }
    
    fun loadApps() {
        val packageManager = requireContext().packageManager
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
        
        val customNamePrefs = requireContext().getSharedPreferences("app_names", Context.MODE_PRIVATE)
        
        val apps = resolveInfoList
            .map { resolveInfo ->
                val pkgName = resolveInfo.activityInfo.packageName
                val appName = resolveInfo.loadLabel(packageManager).toString()
                val customName = customNamePrefs.getString("${pkgName}_custom_name", null)
                
                AppInfo(
                    label = customName ?: appName,
                    packageName = pkgName,
                    className = resolveInfo.activityInfo.name,
                    icon = resolveInfo.loadIcon(packageManager)
                )
            }
            .toMutableList()
        
        val shortcuts = loadShortcuts(customNamePrefs)
        apps.addAll(shortcuts)

        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val hiddenAppsPrefs = requireContext().getSharedPreferences("hidden_apps", Context.MODE_PRIVATE)
        val hiddenApps = hiddenAppsPrefs.getStringSet("hidden_list", emptySet()) ?: emptySet()
        apps.removeAll { appInfo -> hiddenApps.contains(appInfo.packageName) }

        apps.sortBy { it.label.lowercase() }

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
    
    private fun loadShortcuts(customNamePrefs: android.content.SharedPreferences): List<AppInfo> {
        val prefs = requireContext().getSharedPreferences("shortcuts", Context.MODE_PRIVATE)
        val shortcutIds = prefs.getStringSet("shortcut_list", emptySet()) ?: emptySet()
        
        return shortcutIds.mapNotNull { id ->
            val name = prefs.getString("${id}_name", null)
            val customName = customNamePrefs.getString("${id}_custom_name", null)
            
            val shortcutId = prefs.getString("${id}_shortcut_id", null)
            val intentUri = prefs.getString("${id}_intent", null)
            
            if (name != null && (shortcutId != null || intentUri != null)) {
                AppInfo(
                    label = customName ?: name,
                    packageName = id,
                    className = "",
                    icon = requireContext().packageManager.defaultActivityIcon
                )
            } else {
                null
            }
        }
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
        
        searchInput.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
    }
    
    private fun launchFirstApp() {
        val firstApp = appsAdapter.getFirstApp()
        if (firstApp != null) {
            launchApp(firstApp)
        }
    }

    private fun launchApp(appInfo: AppInfo) {
        try {
            callback?.onAppLaunched(appInfo)
            
            if (appInfo.packageName.startsWith("shortcut_")) {
                val prefs = requireContext().getSharedPreferences("shortcuts", Context.MODE_PRIVATE)
                
                val shortcutId = prefs.getString("${appInfo.packageName}_shortcut_id", null)
                val packageName = prefs.getString("${appInfo.packageName}_package", null)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && shortcutId != null && packageName != null) {
                    val launcherApps = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
                } else {
                    val intentUri = prefs.getString("${appInfo.packageName}_intent", null)
                    if (intentUri != null) {
                        val intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    }
                }
            } else {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setClassName(appInfo.packageName, appInfo.className)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showUninstallDialog(appInfo: AppInfo) {
        val intent = Intent(requireContext(), AppOptionsActivity::class.java).apply {
            putExtra(AppOptionsActivity.EXTRA_PACKAGE_NAME, appInfo.packageName)
            putExtra(AppOptionsActivity.EXTRA_CLASS_NAME, appInfo.className)
            putExtra(AppOptionsActivity.EXTRA_APP_LABEL, appInfo.label)
            putExtra(AppOptionsActivity.EXTRA_IS_SHORTCUT, appInfo.packageName.startsWith("shortcut_"))
        }
        startActivity(intent)
    }
}