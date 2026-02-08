package dev.torola.launch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class AppOptionsActivity : AppCompatActivity() {

    private lateinit var appIcon: ImageView
    private lateinit var appName: TextView
    private lateinit var packageNameText: TextView
    private lateinit var hideCard: com.google.android.material.card.MaterialCardView
    private lateinit var hideSwitch: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var hideStatus: TextView
    private lateinit var settingsButton: com.google.android.material.card.MaterialCardView
    private lateinit var settingsStatus: TextView

    private var packageName: String = ""
    private var className: String = ""
    private var appLabel: String = ""
    private var isShortcut: Boolean = false
    private var isHidden: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_app_options)

        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        className = intent.getStringExtra(EXTRA_CLASS_NAME) ?: ""
        appLabel = intent.getStringExtra(EXTRA_APP_LABEL) ?: ""
        isShortcut = intent.getBooleanExtra(EXTRA_IS_SHORTCUT, false)

        initViews()
        loadAppInfo()
        setupClickListeners()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        loadAppInfo()
    }

    private fun initViews() {
        appIcon = findViewById(R.id.appIcon)
        appName = findViewById(R.id.appName)
        packageNameText = findViewById(R.id.packageNameText)
        hideCard = findViewById(R.id.hideCard)
        hideSwitch = findViewById(R.id.hideSwitch)
        hideStatus = findViewById(R.id.hideStatus)
        settingsButton = findViewById(R.id.settingsButton)
        settingsStatus = findViewById(R.id.settingsStatus)
    }

    private fun loadAppInfo() {
        try {
            if (isShortcut) {
                appIcon.setImageDrawable(packageManager.defaultActivityIcon)
                appName.text = appLabel
                packageNameText.text = "Shortcut"
                updateOptionStates(true)
            } else {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                appIcon.setImageDrawable(packageManager.getApplicationIcon(appInfo))
                appName.text = packageManager.getApplicationLabel(appInfo).toString()
                packageNameText.text = packageName

                val hiddenPrefs = getSharedPreferences("hidden_apps", Context.MODE_PRIVATE)
                val hiddenApps = hiddenPrefs.getStringSet("hidden_list", emptySet()) ?: emptySet()
                isHidden = hiddenApps.contains(packageName)

                updateOptionStates(false)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load app info", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updateOptionStates(isShortcut: Boolean) {
        val launcherPackage = applicationContext.packageName

        if (isShortcut) {
            hideSwitch.isEnabled = false
            hideSwitch.isChecked = false
            hideStatus.text = "Not applicable to shortcuts"
        } else {
            hideSwitch.isEnabled = true
            hideSwitch.isChecked = isHidden
            hideStatus.text = if (isHidden) "App is hidden" else "Hide from app list"
        }
        settingsStatus.text = "Open app system settings"
    }

    private fun isSystemApp(): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }

    private fun setupClickListeners() {
        hideSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isShortcut) {
                if (isChecked && !isHidden) {
                    hideApp()
                } else if (!isChecked && isHidden) {
                    unhideApp()
                }
            }
        }

        settingsButton.setOnClickListener {
            openSystemSettings()
        }
    }

    private fun hideApp() {
        val prefs = getSharedPreferences("hidden_apps", Context.MODE_PRIVATE)
        val hiddenApps = prefs.getStringSet("hidden_list", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        hiddenApps.add(packageName)

        prefs.edit().apply {
            putStringSet("hidden_list", hiddenApps)
            apply()
        }

        isHidden = true
        hideStatus.text = "App is hidden"
        Toast.makeText(this, "\"${appName.text}\" hidden from list", Toast.LENGTH_SHORT).show()
    }

    private fun unhideApp() {
        val prefs = getSharedPreferences("hidden_apps", Context.MODE_PRIVATE)
        val hiddenApps = prefs.getStringSet("hidden_list", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        hiddenApps.remove(packageName)

        prefs.edit().apply {
            putStringSet("hidden_list", hiddenApps)
            apply()
        }

        isHidden = false
        hideStatus.text = "Hide from app list"
        Toast.makeText(this, "\"${appName.text}\" unhidden", Toast.LENGTH_SHORT).show()
    }

    private fun openSystemSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open settings: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_CLASS_NAME = "extra_class_name"
        const val EXTRA_APP_LABEL = "extra_app_label"
        const val EXTRA_IS_SHORTCUT = "extra_is_shortcut"
    }
}