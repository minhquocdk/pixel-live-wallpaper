// The island engine: plain Kotlin, no Android, so it builds and runs anywhere (the preview tool draws stills of it on a
// laptop, and the tests hold it to the almanac's own numbers).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// A still of the island, for looking at the engine without a phone:
//   ./gradlew :core:preview --args="--seed heron-bay-8 --t 13 --out island.png --scale 2"
tasks.register<JavaExec>("preview") {
    group = "application"
    description = "Draws a still of the island to a PNG."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.almanac.island.preview.PreviewMainKt")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
}
