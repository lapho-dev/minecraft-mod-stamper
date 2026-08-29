/**
 * Client-only code: the screen and any rendering.
 *
 * <p>docs/VERSIONING.md ranks this as the churniest surface in the mod &mdash; {@code GuiGraphics}
 * and {@code blit} overloads move between every target &mdash; so this is where per-version
 * Stonecutter directives are expected and acceptable. Nothing here decides behaviour: the screen
 * draws what the menu, server-side, has already decided.
 */
package io.github.lapho.stamper.client;
