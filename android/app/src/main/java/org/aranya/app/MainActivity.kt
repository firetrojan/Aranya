package org.aranya.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import org.aranya.app.data.source.SourceRegistry
import org.aranya.app.ui.AranyaShell
import org.aranya.app.ui.theme.AranyaTheme

/**
 * The single Activity.
 *
 * Stage 01 has one screen, so there is no navigation library yet. Navigation is added in
 * Stage 02 once there is a second real destination; adding it now would be scaffolding for
 * a structure that does not exist.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            // Loaded from the bundled registry snapshot. No network call at startup, so the
            // app opens instantly and works offline.
            val registry = remember { SourceRegistry.load(applicationContext) }

            AranyaTheme {
                AranyaShell(registry = registry)
            }
        }
    }
}
