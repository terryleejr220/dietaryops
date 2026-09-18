import java.text.SimpleDateFormat
import java.util.Date
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.util.Properties

val appVersionCode = 17
val appVersionName = "1.0.17"

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val releaseStorePassword = localProperties.getProperty("KEYSTORE_STORE_PASSWORD")
    ?: System.getenv("KEYSTORE_STORE_PASSWORD")
    ?: ""
val releaseKeyPassword = localProperties.getProperty("KEYSTORE_KEY_PASSWORD")
    ?: System.getenv("KEYSTORE_KEY_PASSWORD")
    ?: ""
val releaseKeyAlias = localProperties.getProperty("KEYSTORE_KEY_ALIAS")
    ?: System.getenv("KEYSTORE_KEY_ALIAS")
    ?: "century_villa"

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.dietaryops.manager"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dietaryops.manager"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        create("release") {
            storeFile = file("century_villa_release.jks")
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.1")

    // Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.7")

    // Google Play Billing Library
    implementation("com.android.billingclient:billing-ktx:8.0.0")

    // Firebase
    val firebaseBom = platform("com.google.firebase:firebase-bom:34.19.0")
    implementation(firebaseBom)
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-analytics")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")

    // Google Material Components
    implementation("com.google.android.material:material:1.14.0")

    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Google ML Kit Barcode Scanning
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Room Database
    val roomVersion = "2.7.0"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Retrofit & Networking
    val retrofitVersion = "2.11.0"
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-gson:$retrofitVersion")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}

tasks.register("prepareKotlinBuildScriptModel") {}

fun backupArtifacts(buildType: String, extension: String) {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
    val backupDir = file("${rootDir}/build_backups")
    if (!backupDir.exists()) {
        backupDir.mkdirs()
    }

    val searchDir = if (extension == "apk") {
        file("${layout.buildDirectory.get().asFile}/outputs/apk/${buildType}")
    } else {
        file("${layout.buildDirectory.get().asFile}/outputs/bundle/${buildType}")
    }

    if (searchDir.exists()) {
        searchDir.walkTopDown().filter { it.isFile && it.extension == extension }.forEach { sourceFile ->
            val destFileName = "DietaryOpsManager_v${appVersionName}_code${appVersionCode}_${timeStamp}_${buildType}.${extension}"
            val destFile = File(backupDir, destFileName)
            sourceFile.copyTo(destFile, overwrite = true)
            logger.lifecycle("Successfully backed up build artifact to: ${destFile.absolutePath}")

            if (extension == "apk") {
                val rootApkFile = File(rootDir, if (buildType == "release") "DietaryOpsManager-Release.apk" else "DietaryOpsManager.apk")
                sourceFile.copyTo(rootApkFile, overwrite = true)
                logger.lifecycle("Successfully updated root APK at: ${rootApkFile.absolutePath}")
            }
        }
    } else {
        logger.warn("Search directory for backups does not exist: ${searchDir.absolutePath}")
    }
}

fun copyMappingFileToRoot() {
    val mappingFile = file("${layout.buildDirectory.get().asFile}/outputs/mapping/release/mapping.txt")
    if (mappingFile.exists()) {
        val rootMappingFile = File(rootDir, "mapping.txt")
        mappingFile.copyTo(rootMappingFile, overwrite = true)
        logger.lifecycle("Successfully updated root mapping.txt at: ${rootMappingFile.absolutePath}")
    } else {
        logger.warn("Mapping file not found at: ${mappingFile.absolutePath}")
    }
}

fun copyNativeDebugSymbolsToRoot() {
    val candidates = listOf(
        file("${layout.buildDirectory.get().asFile}/outputs/native-debug-symbols/release/native-debug-symbols.zip"),
        file("${layout.buildDirectory.get().asFile}/outputs/symbols/release/native-debug-symbols.zip")
    )
    var sourceZip: File? = candidates.firstOrNull { it.exists() }

    if (sourceZip == null) {
        val dir1 = file("${layout.buildDirectory.get().asFile}/outputs/native-debug-symbols/release")
        val dir2 = file("${layout.buildDirectory.get().asFile}/outputs/symbols/release")
        sourceZip = if (dir1.exists()) dir1.walkTopDown().firstOrNull { it.isFile && it.extension == "zip" } else null
        if (sourceZip == null && dir2.exists()) {
            sourceZip = dir2.walkTopDown().firstOrNull { it.isFile && it.extension == "zip" }
        }
    }

    if (sourceZip == null || !sourceZip.exists()) {
        val nativeLibsDir = file("${layout.buildDirectory.get().asFile}/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib")
        if (nativeLibsDir.exists()) {
            val targetDir = file("${layout.buildDirectory.get().asFile}/outputs/native-debug-symbols/release")
            targetDir.mkdirs()
            val generatedZip = File(targetDir, "native-debug-symbols.zip")

            val fos = FileOutputStream(generatedZip)
            val zos = ZipOutputStream(BufferedOutputStream(fos))

            nativeLibsDir.walkTopDown().filter { it.isFile && it.extension == "so" }.forEach { soFile ->
                val relativePath = soFile.relativeTo(nativeLibsDir).path.replace('\\', '/')
                val entry = ZipEntry(relativePath)
                zos.putNextEntry(entry)
                soFile.inputStream().use { input -> input.copyTo(zos) }
                zos.closeEntry()
            }
            zos.close()
            fos.close()

            sourceZip = generatedZip
            logger.lifecycle("Generated native-debug-symbols.zip at: ${generatedZip.absolutePath}")
        }
    }

    if (sourceZip != null && sourceZip.exists()) {
        val rootZipFile = File(rootDir, "native-debug-symbols.zip")
        sourceZip.copyTo(rootZipFile, overwrite = true)
        logger.lifecycle("Successfully updated root native-debug-symbols.zip at: ${rootZipFile.absolutePath}")
    } else {
        logger.warn("Native debug symbols zip could not be found or generated.")
    }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    doLast {
        backupArtifacts("debug", "apk")
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    doLast {
        backupArtifacts("release", "apk")
        copyMappingFileToRoot()
        copyNativeDebugSymbolsToRoot()
    }
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    doLast {
        backupArtifacts("release", "aab")
        val searchDir = file("${layout.buildDirectory.get().asFile}/outputs/bundle/release")
        if (searchDir.exists()) {
            searchDir.walkTopDown().filter { it.isFile && it.extension == "aab" }.forEach { sourceFile ->
                val rootAabFile = File(rootDir, "DietaryOpsManager.aab")
                sourceFile.copyTo(rootAabFile, overwrite = true)
                logger.lifecycle("Successfully updated root AAB at: ${rootAabFile.absolutePath}")
            }
        }
        copyMappingFileToRoot()
        copyNativeDebugSymbolsToRoot()
    }
}


