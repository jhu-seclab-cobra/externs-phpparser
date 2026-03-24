plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "externs-phpparser"
include("jhu-seclab-cobra-externs-phpparser")
project(":jhu-seclab-cobra-externs-phpparser").projectDir = file("externs-phpparser")
