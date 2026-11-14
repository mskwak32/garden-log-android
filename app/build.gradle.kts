import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.service)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.play.publisher)
}

android {
    namespace = "com.mskwak.gardendailylog"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt())
    }
    defaultConfig {
        applicationId = "com.mskwak.gardendailylog"
        minSdk = libs.versions.minSdk.get().toInt()
        versionCode = 20
        versionName = "2.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        base.archivesName = "${rootProject.name} $versionName($versionCode)"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
        }
        create("release") {
            storeFile = rootProject.file("keystore/mskwakAndroidKey.jks")
            storePassword = localProperties.getProperty("STORE_PASSWORD", "")
            keyAlias = localProperties.getProperty("KEY_ALIAS", "")
            keyPassword = localProperties.getProperty("KEY_PASSWORD", "")
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

play {
    serviceAccountCredentials.set(rootProject.file("keystore/play-service-account.json"))
    defaultToAppBundles.set(true)
    track.set("production")
    // 업로드 후 Play Console에서 확인하고 직접 출시하도록 초안으로 올린다
    releaseStatus.set(ReleaseStatus.DRAFT)
}

// 출시 노트 원본은 docs/release_note.xml 하나로 두고, GPP가 읽는 언어별 파일은 업로드 직전에 생성한다
val syncPlayReleaseNotes by tasks.registering {
    val source = rootProject.file("docs/release_note.xml")
    val outputDir = layout.projectDirectory.dir("src/main/play/release-notes").asFile
    inputs.file(source)
    outputs.dir(outputDir)
    doLast {
        val notes = Regex("<([a-z]{2}-[A-Z]{2})>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL)
            .findAll(source.readText())
            .toList()
        check(notes.isNotEmpty()) { "release_note.xml에서 언어별 출시 노트를 찾지 못했습니다." }
        outputDir.deleteRecursively()
        notes.forEach { match ->
            val (locale, body) = match.destructured
            val text = body.trim().lines().joinToString("\n") { it.trim() }
            File(outputDir, "$locale/default.txt").apply {
                parentFile.mkdirs()
                writeText(text + "\n")
            }
        }
    }
}
tasks.matching { it.name == "generateReleasePlayResources" }.configureEach {
    dependsOn(syncPlayReleaseNotes)
}

dependencies {
    implementation(project(":data"))
    implementation(project(":domain"))
    implementation(project(":feature:plant"))
    implementation(project(":feature:setting"))
    implementation(project(":core:common_ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.savedstate)
    implementation(libs.material)
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.timber)
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlin.serialization)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    testImplementation(libs.bundles.test)
    androidTestImplementation(libs.bundles.androidTest)
    androidTestImplementation(platform(libs.compose.bom))
    debugImplementation(libs.bundles.debug.compose)
}