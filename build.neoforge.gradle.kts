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
val neoRange: String = sc.properties["deps.neo_range"]

// NeoForge 26.2 deprecated `logoFile`/`logoBlur` in favour of `iconFile`/`iconBlur` for square
// icons (and `bannerFile` for wide banners). Ours is the square 128x128 of D48, so it is the icon.
// Leaving `logoFile` in place still works on 26.2 but logs a deprecation warning at every launch.
//
// Only the key *names* change, not where they sit: NeoForge's own manifest puts them inside
// [[mods]], but `DefaultModDisplayInfo.icon()` reads the [[mods]] section first and falls back to
// the file level, so the existing file-level position is valid for both spellings. Read out of the
// 26.2 bytecode rather than copied from NeoForge's manifest, which would have implied a move.
val newIconKeys = sc.current.parsed >= "26.2"
val iconKey = if (newIconKeys) "iconFile" else "logoFile"
val iconBlurKey = if (newIconKeys) "iconBlur" else "logoBlur"

// Materialised here, not read inside the task's `onlyIf` below: referencing the script's own
// `sc` from inside a task action captures a script object reference, which the Gradle
// configuration cache cannot serialise. Same trap as the ProcessResources block further down.
val runsTier1Tests: Boolean = sc.current.parsed < "26.2"

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

// Every JavaExec in this project launches on the target's own toolchain JDK, not on whatever JVM
// happens to be running Gradle.
//
// MDG's own runClient/runServer already pin this. IntelliJ's Run button does not use them: it
// creates its own ad-hoc JavaExec (`net.neoforged.devlaunch.Main.main()`) from a generated init
// script, hardcoding the IDE's JDK into `executable`, while `javaLauncher` defaults to the Gradle
// daemon's JVM. Both are Java 21 here, so they agree and the run silently starts on 21 — fatal on
// 26.2, where NeoForge passes `--sun-misc-unsafe-memory-access=allow`, unknown before Java 24:
// "Unrecognized option" and the JVM never starts.
//
// Both properties have to move together. Setting only one produces "Toolchain from `executable`
// property does not match toolchain from `javaLauncher` property" instead — read off the failure,
// not predicted.
//
// It has to happen at `taskGraph.whenReady`. The IDE creates its task in `gradle.afterProject`,
// later than `configureEach` or `afterEvaluate` can reach, and Gradle validates the pair before any
// task action, so `doFirst` is too late. Graph-ready is after the one and before the other.
val toolchainLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
}
val toolchainJavaExecutable: String = toolchainLauncher.get().executablePath.asFile.absolutePath
val neoProjectPath: String = project.path

// Each property needs a different hook, and neither alone is enough:
//   * `javaLauncher` is finalised before the task graph is ready, so it must be set here, at
//     configuration time — where it does survive whatever the IDE does afterwards.
//   * `executable` must be set at graph-ready, because that is the only point after the IDE has
//     created its task and written its own JDK into it.
tasks.withType<JavaExec>().configureEach {
    javaLauncher = toolchainLauncher
}

gradle.taskGraph.whenReady {
    allTasks
        .filterIsInstance<JavaExec>()
        .filter { it.path.startsWith("$neoProjectPath:") }
        .forEach { it.setExecutable(toolchainJavaExecutable) }
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

        // Tier 1 does not run on NeoForge 26.2, and this is a loader-side dead end rather than a
        // choice — both routes are blocked (D53):
        //   * without the component-binding pass in StampOperationTest, FML's test environment
        //     leaves item components unbound and every `new ItemStack(...)` throws;
        //   * with it, NeoForge 26.2.0.72's dev-time component validator rejects a *vanilla* value,
        //     `net.minecraft.core.HolderSet$1`, for not implementing equals/hashCode.
        // The same tests run on Fabric 26.2 over the identical `core/`, so the rule itself is still
        // covered on this Minecraft version. Skipped loudly, not quietly: this prints as SKIPPED.
        // Set as a plain task property rather than via `onlyIf`: an `onlyIf` spec is a lambda, and
        // reading a script-level value from inside one captures the script object, which the
        // configuration cache rejects. A disabled task still reports as SKIPPED.
        enabled = runsTier1Tests
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
            "neoforge" to neoRange,
            "icon_key" to iconKey,
            "icon_blur_key" to iconBlurKey,
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
