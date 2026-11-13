package app.chaseom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.chaseom.ui.scenes.SceneHost
import app.chaseom.ui.theme.PleosAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PleosApp).container
        setContent {
            PleosAppTheme {
                SceneHost(container)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Round 2 P-4: the only point where a refused background start of the stay service is tried again.
        // onResume, not onStart: the activity is then resumed and on top, so the background-start
        // exemption surely applies.
        (application as PleosApp).container.stay.onScreenForeground()
    }
}
