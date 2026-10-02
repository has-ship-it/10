package iq.school.manager.di
import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import iq.school.manager.data.AppDatabase
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object DbModule {
  /** مفتاح التشفير عشوائي، يُحفظ داخل EncryptedSharedPreferences المحمي بـ Android Keystore. */
  private fun passphrase(ctx: Context): ByteArray {
    val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    val prefs = EncryptedSharedPreferences.create(ctx, "secure", key,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    val saved = prefs.getString("db_key", null)
    if (saved != null) return Base64.decode(saved, Base64.NO_WRAP)
    val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
    prefs.edit().putString("db_key", Base64.encodeToString(fresh, Base64.NO_WRAP)).apply()
    return fresh
  }
  @Provides @Singleton fun db(@ApplicationContext ctx: Context): AppDatabase =
    Room.databaseBuilder(ctx, AppDatabase::class.java, "school.db")
      .openHelperFactory(SupportFactory(passphrase(ctx))).build()
  @Provides fun school(db: AppDatabase) = db.school()
  @Provides fun users(db: AppDatabase) = db.users()
  @Provides fun permissions(db: AppDatabase) = db.permissions()
  @Provides fun staff(db: AppDatabase) = db.staff()
  @Provides fun students(db: AppDatabase) = db.students()
  @Provides fun classes(db: AppDatabase) = db.classes()
  @Provides fun enrollment(db: AppDatabase) = db.enrollment()
  @Provides fun attendance(db: AppDatabase) = db.attendance()
  @Provides fun studentAttendance(db: AppDatabase) = db.studentAttendance()
  @Provides fun allowances(db: AppDatabase) = db.allowances()
  @Provides fun promotions(db: AppDatabase) = db.promotions()
  @Provides fun transfers(db: AppDatabase) = db.transfers()
  @Provides fun grades(db: AppDatabase) = db.grades()
  @Provides fun primaryGrades(db: AppDatabase) = db.primaryGrades()
  @Provides fun reports(db: AppDatabase) = db.reports()
  @Provides fun notifications(db: AppDatabase) = db.notifications()
  @Provides fun audit(db: AppDatabase) = db.audit()
  @Provides fun settings(db: AppDatabase) = db.settings()
  @Provides fun backups(db: AppDatabase) = db.backups()
  @Provides fun documents(db: AppDatabase) = db.documents()
}
