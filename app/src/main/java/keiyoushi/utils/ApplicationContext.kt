package keiyoushi.utils

import android.app.Application

lateinit var applicationContext: Application
    private set

fun initializeApplicationContext(app: Application) {
    applicationContext = app
}