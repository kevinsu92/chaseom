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
}
