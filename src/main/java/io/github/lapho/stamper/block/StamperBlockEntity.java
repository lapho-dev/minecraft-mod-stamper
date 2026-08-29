package io.github.lapho.stamper.block;

import io.github.lapho.stamper.Stamper;
import io.github.lapho.stamper.menu.StamperMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.11 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
*///?}
import org.jspecify.annotations.Nullable;

/**
 * Storage and sided IO for the Stamper. docs/SPEC.md &sect;4, &sect;5 and &sect;8.
 *
 * <p><b>Two slots, not three.</b> {@link #getContainerSize()} returns 2 and that is deliberate
 * (D7): the Result slot shown in the GUI is a menu-only construct with no backing storage. Making
 * it non-existent at the container level is what *guarantees* the spec rule that nothing can ever
 * extract the result — there is nothing to extract, by any means, including {@code /data}.
 *
 * <p><b>Slot 0 is never written to by this block</b> (SPEC &sect;4, D3). It is a reusable die, so
 * an item withdrawn from the bottom face is byte-identical to the one inserted at the top.
 */
public class StamperBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    /** The template. Read-only for the lifetime of the item; never consumed. */
    public static final int SLOT_TEMPLATE = 0;

    /** The item stream. One item is consumed per activation. */
    public static final int SLOT_INPUT = 1;

    /** SPEC &sect;4: two real slots. The third is the menu's ghost (D7). */
    public static final int CONTAINER_SIZE = 2;

    private static final int[] TEMPLATE_ONLY = {SLOT_TEMPLATE};
    private static final int[] INPUT_ONLY = {SLOT_INPUT};
    private static final int[] NOTHING = {};

    private final NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

    /**
     * The {@link BlockEntityType} is injected rather than read from a static field. Vanilla and
     * most mods use a static holder here; ARCHITECTURE.md rule 5 forbids static singletons holding
     * registry state, because they make {@code core/} extraction and testing painful. Each loader
     * supplies its own type at registration instead (D23).
     */
    public StamperBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // --- SPEC section 5: the sided IO map -------------------------------------------------------
    //
    // Exposed as static functions of (face, facing) rather than buried in the overrides, so the
    // matrix is testable on its own and reusable without subclassing this class
    // (ARCHITECTURE.md rule 6).

    /** Which slots {@code face} can see, given the block's {@code facing}. SPEC &sect;5. */
    public static int[] slotsForFace(Direction face, Direction facing) {
        if (face == Direction.UP || face == Direction.DOWN) {
            return TEMPLATE_ONLY;
        }
        // The front is inert to hoppers, matching the Crafter. The other three horizontals feed
        // the input stream.
        return face == facing ? NOTHING : INPUT_ONLY;
    }

    /** SPEC &sect;5: top and bottom insert into slot 0; the three non-front horizontals into slot 1. */
    public static boolean canInsertThroughFace(int slot, Direction face, Direction facing) {
        if (face == Direction.UP || face == Direction.DOWN) {
            return slot == SLOT_TEMPLATE;
        }
        return face != facing && slot == SLOT_INPUT;
    }

    /**
     * SPEC &sect;5: the bottom face is the only way anything comes out, and only slot 0.
     *
     * <p>Nothing can ever remove an item from slot 1 — a jammed input is cleared by pulsing the
     * block until it empties, or by breaking it. That is intended, not an oversight.
     */
    public static boolean canExtractThroughFace(int slot, Direction face) {
        return face == Direction.DOWN && slot == SLOT_TEMPLATE;
    }

    @Override
    public int[] getSlotsForFace(Direction face) {
        return slotsForFace(face, facingOf(getBlockState()));
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction face) {
        // A null face means "no particular side" (some callers ask this way); fall back to the
        // unsided rule, which is simply whether the slot exists. Deliberate and recorded in D37:
        // §5 is written in terms of faces and says nothing about a caller without one. No vanilla
        // path reaches this with the Stamper as target; a modded pipe passing null could reach
        // slot 1 from any side, which D37 accepts.
        return face == null ? slot < CONTAINER_SIZE : canInsertThroughFace(slot, face, facingOf(getBlockState()));
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction face) {
        return canExtractThroughFace(slot, face);
    }

    private static Direction facingOf(BlockState state) {
        return state.getValue(StamperBlock.FACING);
    }

    // --- SPEC section 8: comparator -------------------------------------------------------------

    /**
     * Reads <b>slot 0 only</b>, treated as a one-slot container. SPEC &sect;8.
     *
     * <p>Vanilla's {@code AbstractContainerMenu.getRedstoneSignalFromContainer} averages across the
     * whole container and would therefore let slot 1 contribute, which the spec forbids — so the
     * formula is written out here instead. Since the template is never consumed (D3) this value is
     * static until a player or hopper changes it, which makes it usable as a "which template am I
     * loaded with" identity channel. That is a feature.
     *
     * <p>The comparator deliberately cannot see whether slot 1 has anything to stamp.
     */
    public int comparatorOutput() {
        ItemStack template = items.get(SLOT_TEMPLATE);
        if (template.isEmpty()) {
            return 0;
        }
        float fill = (float) template.getCount() / Math.min(template.getMaxStackSize(), getMaxStackSize());
        return Mth.floor(1.0F + fill * 14.0F);
    }

    // --- Container ------------------------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }


    // --- SPEC section 10: the menu ---------------------------------------------------------------

    /**
     * SPEC &sect;1: the menu id is {@code stamper:stamper}, so the title key is
     * {@code container.stamper.stamper}. Both halves derive from {@link Stamper#MOD_ID} rather than
     * being inlined; the namespace and the registry path happen to be the same word.
     */
    private static final Component TITLE =
            Component.translatable("container." + Stamper.MOD_ID + "." + Stamper.MOD_ID);

    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    /**
     * The menu is handed <i>this</i> container, so the two real slots it shows are the real slots
     * &mdash; there is no copy to keep in step. The third slot it shows is its own (SPEC &sect;4,
     * D7).
     *
     * <p>The menu type comes from the block rather than from a static field, for the same reason
     * the block entity type does (D23): no static in shared code holds registry state. The block
     * under a Stamper block entity is always a {@link StamperBlock}; returning null if it somehow
     * is not means no menu opens, which beats a crash.
     */
    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return getBlockState().getBlock() instanceof StamperBlock block
                ? new StamperMenu(block.menuType(), containerId, playerInventory, this)
                : null;
    }

    // --- SPEC section 7 / D43: the "a stamp happened" light -------------------------------------

    /**
     * Ticks of lit texture left to show. Zero means idle.
     *
     * <p>This exists because the block's {@code scheduleTick} is already spoken for by the 4-tick
     * activation delay, so the reset cannot be a scheduled block tick and has to be counted down by
     * a ticker instead. That is not a workaround &mdash; it is exactly what vanilla's
     * {@code CrafterBlockEntity.craftingTicksRemaining} does, and for the same reason (D43).
     */
    private int stampingTicks;

    /** Called where the success sound is played, and nowhere else &mdash; the two must agree (D43). */
    public void setStampingTicks(int ticks) {
        stampingTicks = ticks;
        setChanged();
    }

    /**
     * Counts the lit state down and clears it. Server-side only; {@code StamperBlock.getTicker}
     * returns null on the client, so this never runs there.
     *
     * <p>The state is only written on the tick it actually changes &mdash; a {@code setBlock} every
     * tick would resend the chunk section to every watching client for no reason.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, StamperBlockEntity stamper) {
        if (stamper.stampingTicks <= 0) {
            return;
        }
        if (--stamper.stampingTicks == 0) {
            level.setBlock(pos, state.setValue(StamperBlock.STAMPING, false), Block.UPDATE_CLIENTS);
        }
        stamper.setChanged();
    }

    // --- Serialisation --------------------------------------------------------------------------
    //
    // 1.21.11 uses the ValueInput/ValueOutput abstraction. The early 1.21 line took
    // (CompoundTag, HolderLookup.Provider) instead — both shapes below were read off the
    // BlockEntity of their own target, not assumed. G26 is the test that catches getting this
    // wrong, and it compiles cleanly either way, so do not skip it.
    //
    // The tag name and the meaning of every field are identical on both, which is the point: a
    // world saved by one build must load in the other.

    //? if >=1.21.11 {
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        // Saved so that a chunk unloaded mid-flash does not come back lit forever with no ticker
        // run left to clear it. Vanilla persists its Crafter counterpart for the same reason.
        stampingTicks = input.getIntOr(TAG_STAMPING_TICKS, 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt(TAG_STAMPING_TICKS, stampingTicks);
    }
    //?} else {
    /*@Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        // See the 1.21.11 branch. getInt returns 0 for an absent key on this version, which is the
        // same default the other branch asks for explicitly.
        stampingTicks = tag.getInt(TAG_STAMPING_TICKS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt(TAG_STAMPING_TICKS, stampingTicks);
    }
    *///?}

    private static final String TAG_STAMPING_TICKS = "stamping_ticks";
}
