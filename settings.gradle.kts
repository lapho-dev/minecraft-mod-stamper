pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
    }
}

plugins {
    // Version axis. See docs/VERSIONING.md.
    id("dev.kikugie.stonecutter") version "0.9.7"

    // Lets one Fabric build script drive both `fabric-loom` (obfuscated, <=1.21.11) and
    // `fabric-loom-remap` (unobfuscated, 26.1+). Build-time only; not a runtime dependency.
    id("dev.kikugie.loom-back-compat") version "0.4.2"

    // Downloads the required JDK if the local one does not match the target.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        /** Registers one node per loader for a Minecraft version, as `versions/{project}-{loader}`. */
        fun match(project: String, vararg loaders: String, version: String = project) {
            for (loader in loaders) version("$project-$loader", version).buildscript("build.$loader.gradle.kts")
        }

        // Registered targets. One version is added at a time, and only once the previous one is
        // green on both loaders (docs/TODO.md task 5).
        match("1.21.1", "fabric", "neoforge")
        match("1.21.11", "fabric", "neoforge")
        match("26.2", "fabric", "neoforge")

        // The code state committed to git. 1.21.11 is the reference version (D11).
        vcsVersion = "1.21.11-fabric"
    }
}

rootProject.name = "Stamper"
