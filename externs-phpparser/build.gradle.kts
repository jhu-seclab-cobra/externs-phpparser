plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    `java-library`
    `maven-publish`
}

group = "edu.jhu.cobra"
version = "0.1.0"

val sourceJavaVersion = JavaVersion.toVersion(libs.versions.javaSource.get())
val targetJavaVersion = JavaVersion.toVersion(libs.versions.javaTarget.get())

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.mockk)
}

tasks.test {
    useJUnitPlatform { excludeTags("performance") }
}

tasks.register<Test>("performanceTest") {
    useJUnitPlatform { includeTags("performance") }
    jvmArgs("-Xmx2g", "-Xms1g")
    testLogging { showStandardStreams = true }
}

kotlin {
    explicitApi()
    jvmToolchain { languageVersion.set(JavaLanguageVersion.of(sourceJavaVersion.majorVersion)) }
    compilerOptions {
        jvmTarget =
            org.jetbrains.kotlin.gradle.dsl.JvmTarget
                .fromTarget(targetJavaVersion.toString())
    }
}

java {
    sourceCompatibility = sourceJavaVersion
    targetCompatibility = targetJavaVersion
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications { create<MavenPublication>("maven") { from(components["java"]) } }
}

ktlint {
    version.set("1.5.0")
    verbose.set(true)
    android.set(false)
    outputToConsole.set(true)
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

detekt {
    // Resolve the repository's own config even when this module is embedded
    // as a subproject of an enclosing build.
    config.setFrom(files("${projectDir.parentFile}/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
}
