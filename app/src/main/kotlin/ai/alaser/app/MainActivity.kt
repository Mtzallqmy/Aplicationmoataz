package ai.alaser.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ai.alaser.app.ui.AlaserApp
import ai.alaser.app.ui.theme.AlaserTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlaserTheme {
                AlaserApp()
            }
        }
    }
}
