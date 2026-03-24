plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.kover)
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
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
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
    jvmToolchain { languageVersion.set(JavaLanguageVersion.of(sourceJavaVersion.majorVersion)) }
    compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(targetJavaVersion.toString()) }
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

