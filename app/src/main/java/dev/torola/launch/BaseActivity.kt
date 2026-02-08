package dev.torola.launch

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors

/**
 * Base activity that applies Material You dynamic colors theme
 */
abstract class BaseActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply dynamic colors theme overlay before super.onCreate
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
    }
}