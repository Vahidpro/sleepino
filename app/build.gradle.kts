plugins {
 alias(libs.plugins.android.application)
 alias(libs.plugins.kotlin.android)
 alias(libs.plugins.kotlin.compose)
 alias(libs.plugins.kotlin.serialization)
 alias(libs.plugins.ksp)
 alias(libs.plugins.hilt)
}
android {
 namespace = "dev.sleeptime"
 compileSdk = 36
 defaultConfig {
  applicationId = "dev.sleeptime"
  minSdk = 26
  targetSdk = 36
  versionCode = 1
  versionName = "0.1.0"
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  vectorDrawables.useSupportLibrary = true
 }
 buildFeatures { compose = true; buildConfig = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
 packaging { resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}") }
 lint { abortOnError = true; checkReleaseBuilds = true }
 testOptions { unitTests.isIncludeAndroidResources = true }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
 implementation(project(":core"))
 implementation(platform(libs.compose.bom)); androidTestImplementation(platform(libs.compose.bom))
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.compose.ui:ui-tooling-preview")
 debugImplementation("androidx.compose.ui:ui-tooling")
 implementation("androidx.activity:activity-compose:1.12.4")
 implementation("androidx.appcompat:appcompat:1.7.1")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
 implementation("androidx.navigation:navigation-compose:2.9.7")
 implementation("androidx.core:core-ktx:1.17.0")
 implementation("androidx.datastore:datastore-preferences:1.2.1")
 implementation(libs.hilt.android); ksp(libs.hilt.compiler)
 implementation(libs.room.runtime); implementation(libs.room.ktx); ksp(libs.room.compiler)
 implementation(libs.serialization.json)
 implementation("androidx.work:work-runtime-ktx:2.11.1")
 implementation("androidx.glance:glance-appwidget:1.1.1")
 implementation("androidx.glance:glance-material3:1.1.1")
 testImplementation(libs.junit)
 androidTestImplementation("androidx.test.ext:junit:1.3.0")
 androidTestImplementation("androidx.test:runner:1.7.0")
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
}
