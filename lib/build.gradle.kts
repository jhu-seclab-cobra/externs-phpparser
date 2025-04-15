plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
//    // This dependency is exported to consumers, that is to say found on their compile classpath.
//    api(libs.commons.math3)
//
//    // This dependency is used internally, and not exposed to consumers on their own compile classpath.
//    implementation(libs.guava)
    testImplementation(kotlin("test"))
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    val srcJavaVersion = libs.versions.javaSource.get()
    val tarJavaVersion = libs.versions.javaTarget.get()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(srcJavaVersion))
    }
    sourceCompatibility = JavaVersion.toVersion(srcJavaVersion)
    targetCompatibility = JavaVersion.toVersion(tarJavaVersion)
}

