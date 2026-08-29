package io.github.lapho.stamper.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Result slot from docs/SPEC.md &sect;4 and &sect;10: it renders an item and serves a tooltip,
 * and nothing can ever take that item out or put anything in.
 *
 * <p><b>This class is not what makes the result unextractable.</b> That guarantee comes from the
 * container: the block entity reports a size of 2 and has no third slot at all, so there is
 * nothing for a hopper, a dropper, a mod pipe or {@code /data} to reach (D7). This slot only has
 * to close the <i>menu</i> paths, which is a much smaller job &mdash; every one of them funnels
 * through {@link #mayPickup} or {@link #mayPlace} in
 * {@code AbstractContainerMenu.doClick}, verified against the 1.21.11 source:
 *
 * <ul>
 *   <li>{@code PICKUP} (click) and {@code THROW} (Q) &mdash; go through {@code Slot.tryRemove},
 *       which returns empty unless {@code mayPickup}</li>
 *   <li>{@code QUICK_MOVE} (shift-click) &mdash; {@code doClick} returns early on
 *       {@code !mayPickup} without ever calling {@code quickMoveStack}</li>
 *   <li>{@code SWAP} (hotbar number key, and offhand F) &mdash; all three of its branches are
 *       guarded by {@code mayPickup} and {@code mayPlace}</li>
 *   <li>{@code QUICK_CRAFT} (click-drag) &mdash; each collected slot is filtered by
 *       {@code mayPlace} and {@code canDragTo}</li>
 *   <li>{@code PICKUP_ALL} (double-click collect) &mdash; guarded by {@code mayPickup} <i>and</i>
 *       by the menu's {@code canTakeItemForPickAll}</li>
 * </ul>
 *
 * <p>The one vanilla path that ignores both is {@code CLONE} &mdash; creative middle-click &mdash;
 * which copies any slot that has an item. Vanilla's own crafting-result and merchant-result slots
 * behave the same way, and a creative player can conjure the stack anyway, so this is left alone
 * rather than special-cased.
 *
 * <p>Kept as a plain {@link Slot} subclass with no Stamper-specific state so that a later mod can
 * reuse it by composition (docs/ARCHITECTURE.md rule 6).
 */
public class GhostResultSlot extends Slot {
    public GhostResultSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    /** Nothing goes in. SPEC &sect;10. */
    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    /** Nothing comes out. SPEC &sect;10. */
    @Override
    public boolean mayPickup(Player player) {
        return false;
    }
}
