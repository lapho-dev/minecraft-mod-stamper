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
 * <p><b>Note for porters:</b> the id class below is spelled differently before and after 1.21.11,
 * because Mojang mappings renamed it there. Source in git is stored in the 1.21.11 state and
 * Stonecutter rewrites it for older targets. <b>Do not write both spellings into a sentence here:</b>
 * the rewrite is a plain string replacement and applies to prose as well as code, so a sentence
 * naming both survives exactly one switch before it reads as the same word twice. That is why this
 * paragraph names neither. See docs/VERSIONING.md and D52.
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
