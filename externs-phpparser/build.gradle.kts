import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

val minJavaProperty = providers.gradleProperty("minJava").get()
val minJava =
    minJavaProperty.toIntOrNull()?.takeIf { it >= 8 }
        ?: throw GradleException("minJava must be a Java version of 8 or later, got '$minJavaProperty'.")
val targetJavaVersion = JavaVersion.toVersion(minJava)
val runningJavaVersion = JavaVersion.current()
if (runningJavaVersion < targetJavaVersion) {
    throw GradleException(
        "minJava=$minJava requires JDK $minJava or later to run Gradle. " +
            "Gradle is running on JDK ${runningJavaVersion.majorVersion}.",
    )
}

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
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    useJUnitPlatform { excludeTags("performance") }
}

tasks.register<Test>("performanceTest") {
    useJUnitPlatform { includeTags("performance") }
    jvmArgs("-Xmx2g", "-Xms1g")
    testLogging { showStandardStreams = true }
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
        freeCompilerArgs.add("-Xjdk-release=$minJava")
    }
}

java {
    sourceCompatibility = targetJavaVersion
    targetCompatibility = targetJavaVersion
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(minJava)
}

publishing {
    publications { create<MavenPublication>("maven") { from(components["java"]) } }
}

ktlint {
    version.set("1.8.0")
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
