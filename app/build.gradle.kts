plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
}

import org.gradle.api.tasks.Sync
import org.gradle.api.GradleException
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import java.io.File
import java.util.Properties
import java.util.zip.ZipFile

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use(::load)
    }
}

fun secretProperty(name: String): String? {
    val envName = name.replace(Regex("([a-z])([A-Z])"), "\$1_\$2").uppercase()
    return providers.gradleProperty(name).orNull
        ?: localProperties.getProperty(name)
        ?: providers.environmentVariable(name).orNull
        ?: providers.environmentVariable(envName).orNull
}

android {
    namespace = "com.example.yakuzaiapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.yakuzaiapp"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            val releaseStoreFile = secretProperty("releaseStoreFile")
            val releaseStorePassword = secretProperty("releaseStorePassword")
            val releaseKeyAlias = secretProperty("releaseKeyAlias")
            val releaseKeyPassword = secretProperty("releaseKeyPassword")
            val hasReleaseSigning = listOf(
                releaseStoreFile,
                releaseStorePassword,
                releaseKeyAlias,
                releaseKeyPassword,
            ).all { !it.isNullOrBlank() }
            val allowDebugReleaseSigning = secretProperty("allowDebugReleaseSigning")
                ?.toBooleanStrictOrNull() == true

            val debugKeystore = File(System.getProperty("user.home"), ".android/debug.keystore")
            if (hasReleaseSigning) {
                storeFile = File(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            } else if (allowDebugReleaseSigning) {
                logger.warn(
                    "Release APK is signed with the Android debug keystore. " +
                        "Use this only for local device testing.",
                )
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            } else {
                throw GradleException(
                    "Release signing is not configured. Set releaseStoreFile, " +
                        "releaseStorePassword, releaseKeyAlias, and releaseKeyPassword, " +
                        "or set allowDebugReleaseSigning=true only for local device testing.",
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = true
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
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("io.github.zxing-cpp:android:2.3.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.0")

    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.compose.ui:ui:1.6.1")
    implementation("androidx.compose.ui:ui-graphics:1.6.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.1")
    implementation("androidx.compose.material:material-icons-core:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.apache.commons:commons-csv:1.10.0")
    implementation("com.github.albfernandez:juniversalchardet:2.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling:1.6.1")
}

tasks.register("printApkSizeReport") {
    group = "verification"
    description = "Prints APK size and largest ZIP entries. Pass -PapkPath=... or build release first."

    doLast {
        val explicitApkPath = providers.gradleProperty("apkPath").orNull
        val signedApk = layout.buildDirectory.file(
            "outputs/apk/release/app-arm64-v8a-release.apk",
        ).get().asFile
        val unsignedApk = layout.buildDirectory.file(
            "outputs/apk/release/app-arm64-v8a-release-unsigned.apk",
        ).get().asFile
        val apk = explicitApkPath?.let(::File)
            ?: signedApk.takeIf { it.isFile }
            ?: unsignedApk
        require(apk.isFile) {
            "APK not found: ${apk.absolutePath}. Run assembleRelease or pass -PapkPath=<apk>."
        }

        println("APK: ${apk.absolutePath}")
        println("APK size MB: ${"%.2f".format(apk.length().toDouble() / 1024.0 / 1024.0)}")
        ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .sortedByDescending { it.size }
                .take(30)
                .forEach { entry ->
                    val sizeMb = entry.size.toDouble() / 1024.0 / 1024.0
                    println("${"%.2f".format(sizeMb)} MB  ${entry.name}")
                }
        }
    }
}

val debugUnitTestAsciiClassesDir = File(
    System.getProperty("java.io.tmpdir"),
    "gx_handy_ge_ver2/debugUnitTest/classes",
)

val debugKotlinAsciiClassesDir = File(
    System.getProperty("java.io.tmpdir"),
    "gx_handy_ge_ver2/debug/classes",
)

val syncDebugKotlinClassesToAsciiPath by tasks.registering(Sync::class) {
    dependsOn("compileDebugKotlin")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(layout.buildDirectory.dir("tmp/kotlin-classes/debug"))
    into(debugKotlinAsciiClassesDir)
}

val syncDebugUnitTestClassesToAsciiPath by tasks.registering(Sync::class) {
    dependsOn(
        "compileDebugKotlin",
        "compileDebugJavaWithJavac",
        "compileDebugUnitTestKotlin",
        "compileDebugUnitTestJavaWithJavac",
        "processDebugUnitTestJavaRes",
    )
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(layout.buildDirectory.dir("tmp/kotlin-classes/debug"))
    from(layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes"))
    from(layout.buildDirectory.dir("tmp/kotlin-classes/debugUnitTest"))
    from(layout.buildDirectory.dir("intermediates/javac/debugUnitTest/classes"))
    from(layout.buildDirectory.dir("intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"))
    from(layout.buildDirectory.dir("intermediates/java_res/debugUnitTest/processDebugUnitTestJavaRes/out"))
    into(debugUnitTestAsciiClassesDir)
}

afterEvaluate {
    tasks.named<JavaCompile>("compileDebugJavaWithJavac") {
        dependsOn(syncDebugKotlinClassesToAsciiPath)
        classpath = files(debugKotlinAsciiClassesDir) + classpath
    }

    tasks.named<Test>("testDebugUnitTest") {
        dependsOn(syncDebugUnitTestClassesToAsciiPath)
        testClassesDirs = files(debugUnitTestAsciiClassesDir)
        classpath = files(debugUnitTestAsciiClassesDir) + classpath
    }
}
