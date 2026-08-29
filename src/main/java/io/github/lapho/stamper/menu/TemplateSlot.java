package io.github.lapho.stamper.menu;

import io.github.lapho.stamper.Stamper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

/**
 * The Template slot from docs/SPEC.md &sect;4 and &sect;10. An ordinary slot in every way that
 * matters &mdash; it accepts anything, it gives anything back &mdash; that draws an {@code @} hint
 * while it is empty.
 *
 * <p><b>Why this class exists at all:</b> slot 0 is the one square in the GUI that does not explain
 * itself. Input and Result are read from their position and from the arrow between them; Template
 * is a blank square that silently decides what the machine does to everything routed through it
 * (SPEC &sect;11's footgun). D31 called for a marker, D42 established that a marker which
 * <i>disappears when an item is placed</i> is menu-side Java rather than sheet art, and D44 is the
 * decision to build it.
 *
 * <p><b>How the hint draws, verified against the 1.21.11 jar and not from memory (rule 4):</b>
 * {@link Slot#getNoItemIcon()} returns an {@link Identifier}, and the client blits that sprite
 * <i>only while the slot is empty</i>. That is the whole mechanism &mdash; there is deliberately no
 * matching change in {@code client/StamperScreen}, which docs/VERSIONING.md ranks the churniest
 * file in the mod. This is a copy of {@code BrewingStandMenu$FuelSlot}, which is exactly this
 * class minus the {@code mayPlace} restriction we do not want.
 *
 * <p><b>The icon is a GUI sprite, not part of the background sheet.</b> The id below resolves to
 * {@code assets/stamper/textures/gui/sprites/container/slot/template.png}; vanilla's
 * {@code atlases/gui.json} stitches it with a {@code minecraft:directory} source over
 * {@code gui/sprites}, and a directory source scans every namespace in the pack, so the mod ships
 * the PNG and no atlas JSON. Anyone moving that file must move the sprite id with it.
 *
 * <p>Kept as a plain {@link Slot} subclass with no Stamper-specific state, so a later mod can reuse
 * it by composition (docs/ARCHITECTURE.md rule 6), the same way {@link GhostResultSlot} is.
 */
public class TemplateSlot extends Slot {
    /**
     * The {@code @} hint. A sprite id, so no {@code textures/} prefix and no {@code .png} &mdash;
     * the path derives from {@link Stamper#MOD_ID} like every other asset path (ARCHITECTURE rule
     * 4), and the word "stamper" is not inlined here (rule 3).
     */
    private static final Identifier EMPTY_SLOT_TEMPLATE = Stamper.id("container/slot/template");

    public TemplateSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    /**
     * Drawn by the client only while this slot holds nothing, which is the entire feature: place an
     * item and the hint is gone, remove it and the hint is back. SPEC &sect;10, D44.
     */
    @Override
    public Identifier getNoItemIcon() {
        return EMPTY_SLOT_TEMPLATE;
    }
}
