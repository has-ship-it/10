package iq.school.manager
import android.app.Application
import dagger.hilt.android.HiltAndroidApp
@HiltAndroidApp class App : Application() {
  override fun onCreate() { super.onCreate(); net.sqlcipher.database.SQLiteDatabase.loadLibs(this) }
}
