package ai.alaser.app

import android.app.Application
import ai.alaser.app.data.AlaserRepository

class AlaserApplication : Application() {
    val repository: AlaserRepository by lazy { AlaserRepository(this) }
}
