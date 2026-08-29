import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

// Stops NeoForge from decompiling and recompiling Minecraft for several versions at once,
// which with org.gradle.parallel=true will happily eat every core and all the RAM.
interface NeoForgeMutex : BuildService<BuildServiceParameters.None>

val mutex = gradle.sharedServices.registerIfAbsent("createMinecraftArtifactsMutex", NeoForgeMutex::class.java) {
    maxParallelUsages.set(1)
}

tasks.named { it == "createMinecraftArtifacts" }.configureEach {
    usesService(mutex)
}
