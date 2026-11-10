plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "pcsi.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "pcsi.sequenceur"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.4"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":engine"))
}
