plugins {
  alias(libs.plugins.android.application)
  //  id("com.android.library")
    alias(libs.plugins.kotlin.android)
    //id("kotlin-kapt")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.ftpclient"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ftpclient"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    /*sourceSets {
        getByName("main") {
            java.srcDirs("build/generated/ksp/main/kotlin")
        }
    }*/
}

dependencies {
   // implementation("commons-net:commons-net:3.11.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
   implementation(project(":ftpclient"))
    implementation("com.github.bumptech.glide:glide:4.16.0")
  //  kapt("com.github.bumptech.glide:compiler:4.15.1")
    ksp("androidx.room:room-compiler:2.5.0")
    implementation ("com.google.android.exoplayer:exoplayer:2.19.1")
    // implementation ("com.github.nisath-android:ftp-library:1.0.4")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}