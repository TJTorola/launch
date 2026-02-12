package dev.torola.launch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText

class AppOptionsActivity : AppCompatActivity() {

    private lateinit var appIcon: ImageView
    private lateinit var appName: TextView
    private lateinit var packageNameText: TextView
    private lateinit var hideCard: com.google.android.material.card.MaterialCardView
    private lateinit var hideSwitch: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var hideStatus: TextView
    private lateinit var renameCard: com.google.android.material.card.MaterialCardView
    private lateinit var renameInputLayout: TextInputLayout
    private lateinit var renameTextInput: TextInputEditText
    private lateinit var settingsButton: com.google.android.material.card.MaterialCardView
    private lateinit var settingsStatus: TextView

    private var packageName: String = ""
    private var className: String = ""
    private var appLabel: String = ""
    private var isShortcut: Boolean = false
    private var isHidden: Boolean = false
    private var originalAppName: String = ""

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
        renameCard = findViewById(R.id.renameCard)
        renameInputLayout = findViewById(R.id.renameInputLayout)
        renameTextInput = findViewById(R.id.renameTextInput)
        settingsButton = findViewById(R.id.settingsButton)
        findViewById<ImageView>(R.id.backIcon).setOnClickListener { finish() }
        settingsStatus = findViewById(R.id.settingsStatus)
    }

    private fun loadAppInfo() {
        try {
            if (isShortcut) {
                appIcon.setImageDrawable(packageManager.defaultActivityIcon)
                appName.text = appLabel
                packageNameText.text = "Shortcut"
                originalAppName = appLabel
                updateOptionStates(true)
            } else {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                appIcon.setImageDrawable(packageManager.getApplicationIcon(appInfo))
                val label = packageManager.getApplicationLabel(appInfo).toString()
                originalAppName = label
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

        appName.text = originalAppName

        val customNamePrefs = getSharedPreferences("app_names", Context.MODE_PRIVATE)
        val customName = customNamePrefs.getString("${packageName}_custom_name", null)
        if (customName != null) {
            renameTextInput.setText(customName)
        } else {
            renameTextInput.setText(originalAppName)
        }
        updateClearIconForText()
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

        renameInputLayout.setEndIconOnClickListener {
            renameTextInput.setText(originalAppName)
            saveCurrentName()
            updateClearIconForText()
        }

        renameTextInput.doOnTextChanged { text, _, _, _ ->
            updateClearIconForText()
        }

        renameTextInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveCurrentName()
            }
        }

        renameTextInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveCurrentName()
                renameTextInput.clearFocus()
                hideKeyboard()
                true
            } else {
                false
            }
        }

        settingsButton.setOnClickListener {
            openSystemSettings()
        }
    }

    private fun updateClearIconForText() {
        val text = renameTextInput.text?.toString() ?: ""
        val customNamePrefs = getSharedPreferences("app_names", Context.MODE_PRIVATE)
        val currentCustomName = customNamePrefs.getString("${packageName}_custom_name", null)
        
        renameInputLayout.endIconMode = if (text.isNotEmpty() && text != originalAppName) {
            com.google.android.material.textfield.TextInputLayout.END_ICON_CLEAR_TEXT
        } else {
            com.google.android.material.textfield.TextInputLayout.END_ICON_NONE
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

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(renameTextInput.windowToken, 0)
    }

    private fun saveCurrentName() {
        val newName = renameTextInput.text?.toString()?.trim() ?: ""
        val customNamePrefs = getSharedPreferences("app_names", Context.MODE_PRIVATE)
        val currentCustomName = customNamePrefs.getString("${packageName}_custom_name", null)

        val prefs = customNamePrefs

        if (newName.isNotEmpty() && newName != currentCustomName && newName != originalAppName) {
            prefs.edit().putString("${packageName}_custom_name", newName).apply()
        } else if (newName == originalAppName || newName.isEmpty()) {
            if (currentCustomName != null) {
                prefs.edit().remove("${packageName}_custom_name").apply()
            }
            if (newName.isEmpty()) {
                renameTextInput.setText(originalAppName)
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_CLASS_NAME = "extra_class_name"
        const val EXTRA_APP_LABEL = "extra_app_label"
        const val EXTRA_IS_SHORTCUT = "extra_is_shortcut"
    }
}