package ai.alaser.app

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ai.alaser.app.ui.AlaserApp
import ai.alaser.app.ui.i18n.localizedContext
import ai.alaser.app.ui.theme.AlaserTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlaserTheme {
                AlaserApp()
            }
        }
    }
}
