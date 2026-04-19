import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

fun Project.readGitCommit(): String {
    val commit = providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.get().trim()

    check(commit.isNotBlank()) { "Failed to read git commit hash." }
    return commit
}

val gitCommit = project.readGitCommit()
val versionNameProp = providers.gradleProperty("versionName").orNull
val versionCodeProp = providers.gradleProperty("versionCode").orNull
val releaseVersionName = versionNameProp ?: "0.1.0+$gitCommit"
val releaseVersionCode = versionCodeProp?.toIntOrNull() ?: 1

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    jacoco
}

android {
    namespace = "hanabix.hubu"
    compileSdk = 36

    defaultConfig {
        applicationId = "hanabix.hubu"
        minSdk = 29
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
        buildConfigField("String", "APP_VERSION_NAME", "\"$releaseVersionName\"")
        buildConfigField("int", "APP_VERSION_CODE", releaseVersionCode.toString())

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
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(true)
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "androidx/**/*.*",
        "**/*\$Lambda$*.*",
        "**/*Companion*.*",
        "**/*Preview*.*",
        "**/*ComposableSingletons*.*",
        "**/*\$inlined$*.*",
    )

    val debugTree = fileTree(layout.buildDirectory.dir("intermediates/javac/debug/classes").get().asFile) {
        exclude(fileFilter)
    }
    val kotlinTree = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug").get().asFile) {
        exclude(fileFilter)
    }

    classDirectories.setFrom(files(debugTree, kotlinTree))
    sourceDirectories.setFrom(
        files(
            "src/main/java",
            "src/main/kotlin",
        ),
    )
    executionData.setFrom(
        files(
            layout.buildDirectory.file("jacoco/testDebugUnitTest.exec").get().asFile,
        ),
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.google.material)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
