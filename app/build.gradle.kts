plugins {
  id("com.android.application") version "8.7.2"
  id("org.jetbrains.kotlin.android") version "2.0.21"
}
android {
  namespace = "com.ekrem.mangaoverlay"
  compileSdk = 35
  defaultConfig {
    applicationId = "com.ekrem.mangaoverlay"
    minSdk = 26
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"
  }
  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    debug { isDebuggable = true }
  }
  buildFeatures { viewBinding = true }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
}
dependencies {
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.constraintlayout:constraintlayout:2.1.4")
  implementation("com.google.mlkit:text-recognition:16.0.1")
  implementation("com.google.mlkit:translate:17.0.3")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
