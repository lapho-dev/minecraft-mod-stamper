plugins {
    id("dev.kikugie.stonecutter")
}

// The version whose code state is currently checked out into src/.
stonecutter active "1.21.11-fabric"

stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    // Makes the [<version>] and [<loader>.<version>] tables in stonecutter.properties.toml apply.
    properties {
        tags(version, loader)
    }

    // Available to `//? if fabric` / `//? if neoforge` conditions.
    //
    // NOTE: the loader axis is handled by source sets (src/fabric, src/neoforge), not by these
    // constants — see ARCHITECTURE.md and D17. They exist for the rare case where a *shared*
    // file genuinely needs to know, and should stay unused if at all possible.
    constants {
        match(loader, "fabric", "neoforge")
    }

    // 1.21.11 renamed `ResourceLocation` to `Identifier` in Mojang mappings, across every
    // signature that mentions it. Source is stored in the 1.21.11 state (vcsVersion), so this
    // applies as a no-op here and as its inverse on the 1.21.1 backport.
    // Verified against https://docs.neoforged.net/primer/docs/1.21.11/ and mappings.dev.
    replacements {
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }
    }
}
