plugins {
    // Selects `fabric-loom` or `fabric-loom-remap` based on the target Minecraft version.
    id("dev.kikugie.loom-back-compat")
}

// Do not set `group` here — loom-back-compat manages it.
version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "${property("mod.id") as String}-fabric"

val modId: String = sc.properties["mod.id"]
val modName: String = sc.properties["mod.name"]
val modVersion: String = sc.properties["mod.version"]
val mcCompat: String = sc.properties["mod.mc_compat"]

// Fabric API renamed modules across the 26 renumbering: `fabric-item-group-api-v1` became
// `fabric-creative-tab-api-v1`. Diffed between each target's own API pom on 2026-08-29, not
// recalled. The manifest has to agree with this, so it is templated in from the same value.
val creativeTabModule: String =
    if (sc.current.parsed >= "26.2") "fabric-creative-tab-api-v1" else "fabric-item-group-api-v1"

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

// The loader axis is source sets; the version axis is Stonecutter (ARCHITECTURE.md, D17).
// Stonecutter preprocesses `src/<source set name>` for every registered source set, so declaring
// them here is all that is needed for `src/client` and `src/fabric` to be version-processed too.
val main: SourceSet = sourceSets.main.get()
val client: SourceSet = sourceSets.create("client") {
    compileClasspath += main.compileClasspath + main.output
}
val loader: SourceSet = sourceSets.create("fabric") {
    compileClasspath += main.compileClasspath + main.output + client.output
}

// Loom's dev-run tasks build their classpath from exactly ONE source set's runtimeClasspath —
// `main` unless told otherwise (see AbstractRunTask) — and the `loom { mods { ... } }` block below
// only *groups* entries that are already on it, so Fabric Loader treats them as one mod. It does
// not put them there. Without this line the dev client launches with no `fabric.mod.json` on the
// classpath at all, Loader finds no mod, and nothing registers: `/give stamper:stamper` fails with
// "unknown item" while `./gradlew build` stays perfectly green, because the jar is assembled from
// the source set outputs directly.
//
// NeoForge needs no equivalent: ModDevGradle's `mods { sourceSet(...) }` really does extend the
// run classpath.
main.runtimeClasspath += client.output + loader.output

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // Mojang mappings, per CLAUDE.md conventions. No-op on unobfuscated targets.
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")

    // Only the Fabric API modules actually used, so each version bump downloads two jars rather
    // than the whole API. See D21 for why the dependency exists at all.
    //   registry-sync-v0     — reopens the built-in registries so mods can register (mandatory)
    //   item-group-api-v1    — the creative tab hook (closes OPEN-5)
    //   object-builder-api-v1 — BlockEntityType builder; vanilla's constructor is private
    //   transitive-access-wideners-v1 — reopens MenuType's private constructor and the private
    //                           MenuScreens.register, neither of which Fabric API wraps in an API
    //                           of its own (D27). It is the module whose entries are marked
    //                           `transitive-`, which is what makes them apply to *our* compilation
    //                           as well as its own; a module's ordinary access widener does not.
    for (module in listOf(
        "fabric-registry-sync-v0",
        creativeTabModule,
        "fabric-object-builder-api-v1",
        "fabric-transitive-access-wideners-v1",
    )) {
        modImplementation(fabricApi.module(module, sc.properties["deps.fabric_api"]))
    }

    // Tier 1 tests (docs/TESTING.md). Pinned to the JUnit 5 line: 6.1.3 is current, but NeoForge's
    // test framework and ModDevGradle's own documentation are both on 5.x, and a build scaffold is
    // the wrong place to be first through a major version. Test-only; never ships (D20, D22).
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    // Groups the mod's source sets under one mod id so Fabric Loader sees them as one mod in dev.
    mods {
        register(modId) {
            sourceSet(main)
            sourceSet(client)
            sourceSet(loader)
        }
    }

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run") // shared between targets
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

    // Stonecutter's per-version overlay (versions/<node>/src/main/resources) replaces the file at
    // the same path under the shared src/ — but it only *excludes* the shared copy by itself on
    // nodes that are not the active one. On the active node both directories sit on the source set,
    // the overridden file appears twice, and Gradle refuses to guess. EXCLUDE keeps the first, and
    // the node's own directory is registered ahead of the shared root, so the overlay wins.
    // Verified by unzipping the built jar and reading the file back, not assumed.
    // Scoped to these two task types on purpose. Stonecutter's own generate task copies the raw
    // shared source and the preprocessed copy into one directory and relies on INCLUDE so the
    // processed file lands last and wins; widening this to every AbstractCopyTask overwrites that
    // and silently compiles unprocessed source on every non-active node.
    withType<ProcessResources>().configureEach {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    withType<Jar>().configureEach {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
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
            "creative_tab_module" to creativeTabModule,
        )
        inputs.properties(props)

        filesMatching("fabric.mod.json") { expand(props) }
    }

    // `jar` picks these up on its own (SourceSetOutput carries its build dependency), but Loom's
    // dev-run tasks only depend on `main`'s `classes`. Without this, runClient launches with the
    // loader adapter uncompiled and the mod silently does not load. NeoForge's ModDevGradle wires
    // this itself; Loom does not.
    //
    // Matched by name rather than by Loom's task type on purpose: loom-back-compat swaps between
    // the `fabric-loom` and `fabric-loom-remap` plugins by target version, and the task class is
    // not the same on both sides of that swap.
    project.tasks.matching { it.name.startsWith("run") }.configureEach {
        dependsOn(client.classesTaskName, loader.classesTaskName)
    }

    named<Jar>("jar") {
        from(client.output)
        from(loader.output)
    }

    named<Jar>("sourcesJar") {
        from(client.allSource)
        from(loader.allSource)
    }
}
