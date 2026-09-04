pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "KikuGie Releases"
            url = uri("https://maven.kikugie.dev/releases")
        }
        maven {
            name = "KikuGie Snapshots"
            url = uri("https://maven.kikugie.dev/snapshots")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Applies the correct Loom variant per Minecraft version automatically
    id("dev.kikugie.loom-back-compat") version "0.4.2"
    // Needed for Gradle to reliably resolve the toolchain JDKs used across versions (Java 21 for older, Java 25 for 26.1+)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // Add/remove versions here; matching entries must exist in stonecutter.properties.toml
        versions("1.21.11", "26.1", "26.1.1", "26.1.2", "26.2")
        vcsVersion = "26.2"
    }
}

// Should match your modid
rootProject.name = "teamspeak-hud"
