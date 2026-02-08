package dev.torola.launch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class WallpaperAdjustActivity : AppCompatActivity() {

    private lateinit var wallpaperPreview: ZoomableImageView
    private lateinit var cancelButton: Button
    private lateinit var resetButton: Button
    private lateinit var setButton: Button
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallpaper_adjust)

        wallpaperPreview = findViewById(R.id.wallpaperPreview)
        cancelButton = findViewById(R.id.cancelButton)
        resetButton = findViewById(R.id.resetButton)
        setButton = findViewById(R.id.setButton)

        // Get the image URI from the intent
        imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("imageUri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("imageUri")
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

                // Save the file path in preferences
                val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putString("wallpaper_path", file.absolutePath)
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
