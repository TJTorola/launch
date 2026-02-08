package dev.torola.launch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileOutputStream

class WallpaperAdjustActivity : AppCompatActivity() {

    private lateinit var wallpaperPreview: ZoomableImageView
    private lateinit var cancelButton: Button
    private lateinit var resetButton: Button
    private lateinit var setButton: Button
    private var imageUri: Uri? = null
    private var shouldRestoreState = false
    private var previousScale = 1f
    private var previousTranslateX = 0f
    private var previousTranslateY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = false
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        setContentView(R.layout.activity_wallpaper_adjust)

        wallpaperPreview = findViewById(R.id.wallpaperPreview)
        cancelButton = findViewById(R.id.cancelButton)
        resetButton = findViewById(R.id.resetButton)
        setButton = findViewById(R.id.setButton)
        val buttonContainer = findViewById<View>(R.id.buttonContainer)
        val instructionsText = findViewById<View>(R.id.instructionsText)

        ViewCompat.setOnApplyWindowInsetsListener(buttonContainer) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(16, 16, 16, insets.bottom + 16)
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(instructionsText) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, 25)
            windowInsets
        }

        // Get the image URI from the intent
        imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("imageUri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("imageUri")
        }

        // Check if we should restore previous transform state
        shouldRestoreState = intent.getBooleanExtra("restoreState", false)
        if (shouldRestoreState) {
            previousScale = intent.getFloatExtra("scale", 1f)
            previousTranslateX = intent.getFloatExtra("translateX", 0f)
            previousTranslateY = intent.getFloatExtra("translateY", 0f)
        }

        if (imageUri == null) {
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load the image into the preview
        try {
            wallpaperPreview.setImageURI(imageUri)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
            finish()
            return
        }

        // Restore transform state if provided
        if (shouldRestoreState) {
            wallpaperPreview.post {
                wallpaperPreview.setTransformState(previousScale, previousTranslateX, previousTranslateY)
            }
        }

        // Setup button listeners
        cancelButton.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        resetButton.setOnClickListener {
            wallpaperPreview.resetScale()
        }

        setButton.setOnClickListener {
            saveAdjustedWallpaper()
        }
    }

    private fun saveAdjustedWallpaper() {
        try {
            // Get the displayed bitmap with current zoom and pan
            val bitmap = getViewBitmap()

            if (bitmap != null) {
                // Save to internal storage
                val file = File(filesDir, "wallpaper.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                // Copy original image to internal storage
                copyOriginalImageToInternalStorage()

                // Save the current transform state in preferences
                val transformState = wallpaperPreview.getTransformState()
                val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putString("wallpaper_path", file.absolutePath)
                    putFloat("wallpaper_scale", transformState[0])
                    putFloat("wallpaper_translate_x", transformState[1])
                    putFloat("wallpaper_translate_y", transformState[2])
                    remove("wallpaper_uri") // Remove old URI-based wallpaper
                    apply()
                }

                Toast.makeText(this, "Wallpaper set successfully", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save wallpaper: ${e.message}", Toast.LENGTH_LONG).show()
            android.util.Log.e("WallpaperAdjust", "Failed to save wallpaper", e)
        }
    }

    private fun copyOriginalImageToInternalStorage() {
        try {
            val inputStream = contentResolver.openInputStream(imageUri!!)
            inputStream?.use { input ->
                val originalFile = File(filesDir, "wallpaper_original.png")
                FileOutputStream(originalFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WallpaperAdjust", "Failed to copy original image", e)
        }
    }

    private fun getViewBitmap(): Bitmap? {
        return try {
            // Create a bitmap with the view's dimensions (screen size)
            val bitmap = Bitmap.createBitmap(
                wallpaperPreview.width,
                wallpaperPreview.height,
                Bitmap.Config.ARGB_8888
            )
            
            // Draw the view onto the bitmap
            val canvas = Canvas(bitmap)
            wallpaperPreview.draw(canvas)
            
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
