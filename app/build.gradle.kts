plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("app.cash.paparazzi")
}

// Build Rust native library when sources change
val buildRustLibrary by tasks.registering(Exec::class) {
    description = "Build Rust native library for all Android ABIs"
    workingDir = file("../rust")
    commandLine("./build-android.sh")

    // Inputs: Rust source files
    inputs.files(fileTree("../rust/src") { include("**/*.rs") })
    inputs.file("../rust/Cargo.toml")
    inputs.file("../rust/Cargo.lock")

    // Outputs: compiled libraries
    outputs.files(
        file("src/main/jniLibs/arm64-v8a/libspread_core.so"),
        file("src/main/jniLibs/armeabi-v7a/libspread_core.so"),
        file("src/main/jniLibs/x86/libspread_core.so"),
        file("src/main/jniLibs/x86_64/libspread_core.so")
    )
}

// Make JNI merge tasks depend on Rust build
tasks.configureEach {
    if (name.contains("merge") && name.contains("JniLibFolders")) {
        dependsOn(buildRustLibrary)
    }
}

android {
    namespace = "app.spread"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.spread"
        minSdk = 21  // Android 5.0 Lollipop (2014) - broadest reasonable compatibility
        targetSdk = 34

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.5"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // DataStore for settings persistence
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Room for book/progress persistence
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.robolectric:robolectric:4.11.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
