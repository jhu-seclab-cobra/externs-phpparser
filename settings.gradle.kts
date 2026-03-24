plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "externs-phpparser"

// Unique module identifier to avoid composite build name collisions (gradle/gradle#847)
include("jhu-seclab-cobra-externs-phpparser")
project(":jhu-seclab-cobra-externs-phpparser").projectDir = file("lib")
