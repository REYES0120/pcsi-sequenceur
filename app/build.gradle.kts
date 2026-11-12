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
        versionCode = 2
        versionName = "0.6"
    }

    // Même clé à chaque build : une nouvelle version s'installe par-dessus l'ancienne
    // sans effacer ton journal. Clé sans valeur de sécurité : garde le dépôt privé.
    signingConfigs {
        create("pcsi") {
            storeFile = file("signing.p12")
            storeType = "pkcs12"
            storePassword = "pcsi-sequenceur"
            keyAlias = "pcsi"
            keyPassword = "pcsi-sequenceur"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("pcsi")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("pcsi")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
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
