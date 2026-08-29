package io.github.lapho.stamper;

import net.minecraft.resources.Identifier;

/**
 * Mod-wide identity.
 *
 * <p>The mod id literal is declared here and nowhere else in Java code &mdash; block ids,
 * translation keys and asset paths all derive from {@link #MOD_ID}. See CLAUDE.md, "Conventions".
 * The build side has its own single declaration, {@code mod.id} in
 * {@code stonecutter.properties.toml}, which is what fills in the loader manifests.
 *
 * <p>Note for porters: 1.21.11 renamed {@code ResourceLocation} to {@code Identifier} in Mojang
 * mappings. The source in git is stored in the 1.21.11 state; Stonecutter rewrites it for older
 * targets. See docs/VERSIONING.md.
 */
public final class Stamper {
    /** See docs/SPEC.md &sect;1. */
    public static final String MOD_ID = "stamper";

    private Stamper() {
    }

    /** A namespaced id in this mod's namespace. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
