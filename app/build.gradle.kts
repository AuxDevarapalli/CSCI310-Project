plugins {
    alias(libs.plugins.android.application)
}

// Firebase needs app/google-services.json (see README -> "Firebase setup"). Without it the app
// still builds and the unit tests still run; the app then shows setup instructions on launch.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.warn("app/google-services.json not found: Firebase is not configured. See README.md.")
}

android {
    namespace = "com.team04.splitsmart"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.team04.splitsmart"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Normally empty, so the app uses the real Firebase project. For local testing, build with
        // -Psplitsmart.emulatorHost=10.0.2.2 to use the Auth and Firestore emulators instead.
        val emulatorHost = (project.findProperty("splitsmart.emulatorHost") as String?) ?: ""
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"$emulatorHost\"")
    }

    buildFeatures {
        buildConfig = true
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.coordinatorlayout)

    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
