plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.rockbyte.vicu"
    compileSdk = libs.versions.sdk.compile.get().toInt()

    defaultConfig {
        applicationId = "com.rockbyte.vicu"
        minSdk = libs.versions.sdk.min.get().toInt()
        versionCode = libs.versions.version.code.get().toInt()
        versionName = libs.versions.version.name.get()
    }

    signingConfigs {
        create("app") {
            storeFile = rootProject.file("app.jks")
            storePassword = "007007"
            keyAlias = "vicuvicu"
            keyPassword = "007007"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("app")
        }
        release {
            signingConfig = signingConfigs.getByName("app")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// APK 产物命名：应用名前缀 + 变体 + 版本号，如 vicu-release-1.0.apk（与 defaultConfig 同读 version catalog，保持单一来源）
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("vicu-${variant.name}-${libs.versions.version.name.get()}.apk")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Styles API 为实验性：foundation.style + 继承文本样式均需 opt-in
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.style.ExperimentalFoundationStyleApi")
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi")
    }
}

dependencies {
    implementation(files("libs/ffmpeg-kit-next-api.aar"))
    implementation(libs.smart.exception.java)
    implementation(libs.androidx.annotation)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.activity)
    implementation(libs.compose.foundation)
    implementation(libs.compose.foundation.layout)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
