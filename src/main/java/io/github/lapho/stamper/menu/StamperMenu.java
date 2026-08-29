package io.github.lapho.stamper.menu;

import io.github.lapho.stamper.core.StampOperation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Stamper's container menu. docs/SPEC.md &sect;10, and &sect;4 for the slot model.
 *
 * <p>Three slots are shown; only two exist. Slots 0 and 1 are ordinary slots over the block
 * entity's container &mdash; slot 0 is a {@link TemplateSlot}, which differs from a plain
 * {@code Slot} only in drawing an {@code @} hint while it is empty (SPEC &sect;10, D44) and
 * restricts nothing. Slot 2 is the ghost result: a {@link GhostResultSlot} over a one-slot
 * container this menu owns, created here, thrown away when the menu closes and never written to
 * disk. That is what makes "nothing can ever extract the result" a structural fact rather than a
 * rule enforced in several places and hoped to be complete (D7).
 *
 * <p>The preview is computed <b>server-side</b> from slots 0 and 1 by the same
 * {@link StampOperation#apply} the block itself uses, and reaches the client through the ordinary
 * slot sync. The client never computes it, so what the player sees in the ghost is exactly what a
 * redstone pulse would produce.
 */
public class StamperMenu extends AbstractContainerMenu {
    /** Menu slot indices. The first two line up with the block entity's; the third is the ghost. */
    public static final int SLOT_TEMPLATE = 0;

    public static final int SLOT_INPUT = 1;

    public static final int SLOT_RESULT = 2;

    /**
     * SPEC &sect;4: the block's container has exactly two slots. Declared here rather than imported
     * from {@code block/} so this package stays usable over any two-slot container
     * (docs/ARCHITECTURE.md rule 6); {@code checkContainerSize} enforces the agreement at runtime.
     */
    private static final int STAMPER_SLOT_COUNT = 2;

    private static final int INVENTORY_START = 3;
    private static final int HOTBAR_END = 39;

    // Slot positions, in GUI-sheet pixels. These are the vanilla anvil's, which is what the
    // placeholder sheet was drawn around — docs/ART.md records them, and they were re-read off
    // assets/stamper/textures/gui/container/stamper.png rather than trusted.
    private static final int TEMPLATE_X = 27;
    private static final int INPUT_X = 76;
    private static final int RESULT_X = 134;
    private static final int SLOT_ROW_Y = 47;
    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 84;

    private final Container stamper;

    /**
     * Backs the ghost. Menu-owned and menu-lifetime: it exists only while someone has the GUI
     * open, and no part of the world can see it (SPEC &sect;4, D7).
     */
    private final Container preview = new SimpleContainer(1);

    // What the preview was last computed from. Recomputing the stamp every tick would allocate for
    // nothing; these let broadcastChanges() notice that neither slot has moved.
    private ItemStack lastTemplate = ItemStack.EMPTY;
    private ItemStack lastInput = ItemStack.EMPTY;

    /**
     * Client-side constructor, called by the {@link MenuType} when the server opens the screen.
     * The real contents arrive from the server as slot updates, so a throwaway container of the
     * right size is all that is needed here — the same thing vanilla's {@code DispenserMenu} does.
     */
    public StamperMenu(MenuType<?> type, int containerId, Inventory playerInventory) {
        this(type, containerId, playerInventory, new SimpleContainer(STAMPER_SLOT_COUNT));
    }

    /**
     * @param type    the registered menu type, injected rather than read from a static holder, for
     *                the same reason the block entity type is (D23, ARCHITECTURE.md rule 5)
     * @param stamper the block entity's container, server-side
     */
    public StamperMenu(MenuType<?> type, int containerId, Inventory playerInventory, Container stamper) {
        super(type, containerId);
        checkContainerSize(stamper, STAMPER_SLOT_COUNT);
        this.stamper = stamper;
        stamper.startOpen(playerInventory.player);

        addSlot(new TemplateSlot(stamper, SLOT_TEMPLATE, TEMPLATE_X, SLOT_ROW_Y));
        addSlot(new Slot(stamper, SLOT_INPUT, INPUT_X, SLOT_ROW_Y));
        addSlot(new GhostResultSlot(preview, 0, RESULT_X, SLOT_ROW_Y));
        addStandardInventorySlots(playerInventory, PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y);

        // The first sync to the client happens when the menu is opened, before any tick runs, so
        // the preview has to be right by the time this constructor returns or the player sees an
        // empty result slot until the next tick.
        refreshPreview();
    }

    @Override
    public boolean stillValid(Player player) {
        return stamper.stillValid(player);
    }

    /**
     * Refreshes the preview once per tick, before the sync that carries it to the client.
     *
     * <p>This is the hook rather than {@link #slotsChanged}, which vanilla only calls for
     * menu-owned containers: slots 0 and 1 live in the block entity, so a hopper or a redstone
     * pulse can change them with no click involved and the preview still has to follow.
     */
    @Override
    public void broadcastChanges() {
        refreshPreview();
        super.broadcastChanges();
    }

    private void refreshPreview() {
        ItemStack template = stamper.getItem(SLOT_TEMPLATE);
        ItemStack input = stamper.getItem(SLOT_INPUT);
        if (ItemStack.matches(template, lastTemplate) && ItemStack.matches(input, lastInput)) {
            return;
        }

        lastTemplate = template.copy();
        lastInput = input.copy();
        // The same call the block makes on a pulse (SPEC §6), so the preview cannot drift from the
        // behaviour. An empty input gives ItemStack.EMPTY, which renders as an empty result slot.
        preview.setItem(0, StampOperation.apply(template, input));
    }

    /**
     * Shift-click. From the Stamper, out to the player; from the player, <b>into the Input slot
     * first</b> and only then into the Template.
     *
     * <p>That order is deliberate and is the opposite of the anvil's left-to-right fill. Filling
     * the template first would mean a player shift-clicking a stack into an empty Stamper silently
     * arms it to stamp <i>those items' name</i> onto everything that follows afterwards — SPEC
     * &sect;11's footgun, reached by accident. The input slot is the stream; the template is set
     * once, on purpose, by hand.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        // Unreachable: doClick refuses QUICK_MOVE on a slot that cannot be picked up, so the ghost
        // never gets here. Kept because the alternative — moveItemStackTo over a range that
        // contains slot 2 — would happily merge into the ghost, and this is the file where that
        // mistake would get made.
        if (index == SLOT_RESULT) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index == SLOT_TEMPLATE || index == SLOT_INPUT) {
            if (!moveItemStackTo(stack, INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, SLOT_INPUT, SLOT_INPUT + 1, false)
                && !moveItemStackTo(stack, SLOT_TEMPLATE, SLOT_TEMPLATE + 1, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, stack);
        return original;
    }

    /** Double-click-to-collect must not sweep the ghost up. Belt and braces over {@code mayPickup}. */
    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof GhostResultSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    /** Click-drag must not deposit into the ghost. Belt and braces over {@code mayPlace}. */
    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof GhostResultSlot) && super.canDragTo(slot);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        stamper.stopOpen(player);
        // The preview was never anyone's item. It dies with the menu.
        preview.clearContent();
    }
}
