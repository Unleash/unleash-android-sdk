import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL

plugins {
    `maven-publish`
    signing
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("org.jetbrains.dokka") version "1.9.20"
    id("pl.allegro.tech.build.axion-release") version "1.20.1"
    jacoco
    id("tech.yanand.maven-central-publish").version("1.3.0")
}

val tagVersion = System.getenv("GITHUB_REF")?.split('/')?.last()
project.version = scmVersion.version

jacoco {
    toolVersion = "0.8.12"
}

android {
    namespace = "io.getunleash.android"
    compileSdk = 34

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 21

        aarMetadata {
            minCompileSdk = 29
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("proguard-rules.pro")

        buildConfigField("String", "VERSION", "\"${project.version}\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.moshi.kotlin)
    implementation(libs.moshi.adapters)
    implementation(libs.kotlinx.immutable)
    api(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.assertj)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito)
    testImplementation(libs.robolectric.test)
    testImplementation(libs.okhttp.mockserver)
    testImplementation(libs.awaitility)
    testImplementation(libs.jsonunit)
}

publishing {
    repositories {
        repositories {
            maven {
                url = uri(layout.buildDirectory.dir("repo"))
                name = "test"
            }
        }
        mavenLocal()
    }

    publications {
        afterEvaluate {
            create<MavenPublication>("mavenJava") {
                from(components["release"])
                groupId = "io.getunleash"
                artifactId = "unleash-android"
                version = version
                pom {
                    name.set("Unleash Android")
                    description.set("Android SDK for Unleash")
                    url.set("https://gh.getunleash.io/unleash-android-sdk")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://raw.githubusercontent.com/Unleash/unleash-android-sdk/refs/heads/main/LICENSE")
                        }
                    }
                    developers {
                        developer {
                            id.set("gastonfournier")
                            name.set("Gastón Fournier")
                            email.set("gaston@getunleash.io")
                        }
                        developer {
                            id.set("chrkolst")
                            name.set("Christopher Kolstad")
                            email.set("chriswk@getunleash.io")
                        }
                        developer {
                            id.set("ivarconr")
                            name.set("Ivar Conradi Østhus")
                            email.set("ivarconr@getunleash.io")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/Unleash/unleash-android-sdk")
                        developerConnection.set("scm:git:ssh://git@github.com:Unleash/unleash-android-sdk")
                        url.set("https://github.com/Unleash/unleash-android-sdk")
                    }
                }
            }
        }
    }
}

if (System.getenv("CI") == "true") {
    val signingKey: String? by project
    val signingPassphrase: String? by project
    signing {
        if (signingKey != null && signingPassphrase != null) {
            useInMemoryPgpKeys(signingKey, signingPassphrase)
            sign(publishing.publications)
        }
    }
}


tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets {
        named("main") {
            moduleName.set("Unleash Android SDK")
            sourceLink {
                localDirectory.set(file("unleashandroidsdk/src/main/java"))
                remoteUrl.set(URL("https://github.com/Unleash/unleash-android/tree/${tagVersion ?: "main"}/unleashandroidsdk/src/main/java"))
                remoteLineSuffix.set("#L")
            }
        }
    }
}

val jacocoTestReport by tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileTreeConfig: (ConfigurableFileTree) -> Unit = {
        it.exclude("**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*", "android/**/*.*",
            "**/data/**", "**/errors/**", "**/events/**")
    }

    sourceDirectories.setFrom(files("${projectDir}/src/main/java"))
    classDirectories.setFrom(listOf(
        fileTree("${buildDir}/tmp/kotlin-classes/debug", fileTreeConfig)
    ))
    executionData.setFrom(fileTree(buildDir) {
        include("jacoco/*.exec")
    })
}

tasks.withType<Test> {
    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        events("passed", "skipped", "failed")
    }
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
    finalizedBy(jacocoTestReport)
}

val mavenCentralToken: String? by project

mavenCentral {
    authToken = mavenCentralToken
    publishingType = "AUTOMATIC"
    maxWait = 120
}
