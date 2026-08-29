plugins {
    // 2.0.144 was current on 2026-08-27. Known-good fallback: 2.0.140 (the Stonecutter
    // multiloader template's pin for this exact NeoForge version).
    id("net.neoforged.moddev") version "2.0.144"
    id("neoforge-mutex")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "${property("mod.id") as String}-neoforge"

val modId: String = sc.properties["mod.id"]
val modName: String = sc.properties["mod.name"]
val modVersion: String = sc.properties["mod.version"]
val mcCompat: String = sc.properties["mod.mc_compat"]

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

// Mirrors build.fabric.gradle.kts — see the comment there.
val main: SourceSet = sourceSets.main.get()
val client: SourceSet = sourceSets.create("client") {
    compileClasspath += main.compileClasspath + main.output
    runtimeClasspath += main.runtimeClasspath + main.output
}
val loader: SourceSet = sourceSets.create("neoforge") {
    compileClasspath += main.compileClasspath + main.output + client.output
    runtimeClasspath += main.runtimeClasspath + main.output + client.output
}

dependencies {
    // Tier 1 tests (docs/TESTING.md). Pinned to the JUnit 5 line: 6.1.3 is current, but NeoForge's
    // test framework and ModDevGradle's own documentation are both on 5.x, and a build scaffold is
    // the wrong place to be first through a major version. Test-only; never ships (D20, D22).
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

neoForge {
    version = property("deps.neo_loader") as String

    mods {
        register(modId) {
            sourceSet(main)
            sourceSet(client)
            sourceSet(loader)
        }
    }

    // Runs the Tier 1 tests inside a real FML environment, so Minecraft classes behave as they
    // do in game rather than as bare classpath entries.
    unitTest {
        enable()
        testedMod = mods.getByName(modId)
    }

    runs {
        register("client") {
            client()
            gameDirectory = rootProject.file("run") // shared between targets
        }
        register("server") {
            server()
            gameDirectory = rootProject.file("run")
        }
    }
}

java {
    withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava

    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    named<Test>("test") {
        useJUnitPlatform()
    }

    withType<ProcessResources>().configureEach {
        // Materialise the values into a plain map here. Referencing the script's own properties
        // from inside the filesMatching action captures a script object reference, which the
        // Gradle configuration cache cannot serialise.
        val props: Map<String, String> = mapOf(
            "id" to modId,
            "name" to modName,
            "version" to modVersion,
            "minecraft" to mcCompat,
        )
        inputs.properties(props)

        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
    }

    named<Jar>("jar") {
        from(client.output)
        from(loader.output)
    }

    named<Jar>("sourcesJar") {
        from(client.allSource)
        from(loader.allSource)
    }

    // Minecraft must not be decompiled before Stonecutter has written this target's sources.
    // One generate task exists per source set (stonecutterGenerate, ...Client, ...Neoforge, ...Test),
    // so match on the prefix rather than naming them.
    named("createMinecraftArtifacts") {
        dependsOn(project.tasks.matching { it.name.startsWith("stonecutterGenerate") })
    }
}
