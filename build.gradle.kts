plugins {
  id("com.android.application"); id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose"); id("com.google.devtools.ksp"); id("com.google.dagger.hilt.android")
}
android {
  namespace = "iq.school.manager"; compileSdk = 34
  defaultConfig { applicationId = "iq.school.manager"; minSdk = 26; targetSdk = 34; versionCode = 1; versionName = "0.1" }
  buildFeatures { compose = true }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
  signingConfigs { create("release") { val ks = System.getenv("KEYSTORE_PATH"); if (ks != null) { storeFile = file(ks); storePassword = System.getenv("KEYSTORE_PASSWORD"); keyAlias = System.getenv("KEY_ALIAS"); keyPassword = System.getenv("KEY_PASSWORD") } } }
  buildTypes { release { isMinifyEnabled = false; if (System.getenv("KEYSTORE_PATH") != null) signingConfig = signingConfigs.getByName("release") } }
  ksp { arg("room.schemaLocation", "$projectDir/schemas") }
}
dependencies {
  implementation(platform("androidx.compose:compose-bom:2024.09.00"))
  implementation("androidx.compose.material3:material3"); implementation("androidx.compose.ui:ui")
  implementation("androidx.activity:activity-compose:1.9.2")
  implementation("androidx.navigation:navigation-compose:2.8.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
  implementation("androidx.room:room-runtime:2.6.1"); implementation("androidx.room:room-ktx:2.6.1")
  ksp("androidx.room:room-compiler:2.6.1")
  implementation("net.zetetic:android-database-sqlcipher:4.5.4"); implementation("androidx.sqlite:sqlite-ktx:2.4.0")
  implementation("androidx.security:security-crypto:1.1.0-alpha06")
  implementation("androidx.biometric:biometric:1.1.0"); implementation("androidx.fragment:fragment-ktx:1.8.3")
  implementation("com.google.dagger:hilt-android:2.52"); ksp("com.google.dagger:hilt-compiler:2.52")
  implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
  testImplementation("junit:junit:4.13.2")
}
