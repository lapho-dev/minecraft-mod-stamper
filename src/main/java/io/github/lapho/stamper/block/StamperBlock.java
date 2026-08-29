package io.github.lapho.stamper.block;

import io.github.lapho.stamper.core.StampOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
//? if >=1.21.11 {
import net.minecraft.world.level.redstone.Orientation;
//?}
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The Stamper block. docs/SPEC.md &sect;3, &sect;8 and &sect;11.
 *
 * <p><b>Horizontal facing only</b> (D9). Four-way like a furnace, not the dropper's six-way: the
 * vertical axis is reserved for the template stream (top in, bottom out) and the horizontal plane
 * for the item stream. The restriction is structural, not cosmetic.
 *
 * <p><b>Piston immunity is not implemented here</b>, deliberately. In Java Edition pistons refuse
 * any block with a block entity, so SPEC &sect;3 says to assert it in a gametest rather than
 * hand-implement it. Writing code for it would be writing code for something the engine already
 * guarantees.
 */
public class StamperBlock extends Block implements EntityBlock {
    /** SPEC &sect;3: {@code north}/{@code east}/{@code south}/{@code west}, never up or down. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * SPEC &sect;3: the redstone edge latch, as dropper and crafter have, and <b>only</b> that. It
     * selects no texture: a pulse that finds slot 1 empty sets this and still must not light the
     * block (D43). {@link #STAMPING} is what the lit model keys on.
     */
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;

    /**
     * SPEC &sect;7: drives the lit texture. Set where the success sound is played and cleared
     * {@link #STAMPING_TICKS} ticks later, so the light and the sound always mean the same thing
     * &mdash; an item was consumed and dispensed (D43).
     *
     * <p>Ours, not {@code BlockStateProperties.CRAFTING}: the Stamper does not craft, and a
     * blockstate is user-visible through F3 and {@code /setblock}.
     */
    public static final BooleanProperty STAMPING = BooleanProperty.create("stamping");

    private final Supplier<BlockEntityType<StamperBlockEntity>> blockEntityType;
    private final Supplier<MenuType<?>> menuType;

    /**
     * @param blockEntityType supplied rather than read from a static holder, so that no registry
     *                        state lives in a static field (ARCHITECTURE.md rule 5, D23). Each
     *                        loader passes its own.
     * @param menuType        supplied for the same reason, and additionally because on both loaders
     *                        the menu type is registered after the block that has to reach it
     */
    public StamperBlock(Properties properties,
                        Supplier<BlockEntityType<StamperBlockEntity>> blockEntityType,
                        Supplier<MenuType<?>> menuType) {
        super(properties);
        this.blockEntityType = blockEntityType;
        this.menuType = menuType;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(TRIGGERED, false)
                .setValue(STAMPING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TRIGGERED, STAMPING);
    }

    /** SPEC &sect;3: the front (output) face points toward the player, matching furnace and dropper. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StamperBlockEntity(blockEntityType.get(), pos, state);
    }

    /**
     * Runs only to count the lit texture down (D43); the block has no other per-tick work.
     *
     * <p>The ticker is returned for <b>every</b> server-side Stamper, lit or not &mdash; the same
     * shape as vanilla's {@code CrafterBlock}. It is not withheld when {@code stampingTicks} is
     * zero, because {@code getTicker} is consulted when the block entity is created rather than
     * whenever the count changes, so there would be nothing to install the ticker again on the
     * next stamp. The tick itself returns immediately when there is nothing to count.
     *
     * <p>Server-side only. The client is told about the state change by the {@code setBlock} the
     * ticker performs, and must not run the countdown itself.
     */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                           BlockEntityType<T> type) {
        if (level.isClientSide() || type != blockEntityType.get()) {
            return null;
        }
        // The cast is safe: the type was just checked against this block's own registered type.
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker =
                (BlockEntityTicker<T>) (BlockEntityTicker<StamperBlockEntity>) StamperBlockEntity::serverTick;
        return ticker;
    }

    /** The registered menu type, for the block entity's {@code MenuProvider} (SPEC &sect;10). */
    public MenuType<?> menuType() {
        return menuType.get();
    }

    // --- SPEC section 10: opening the GUI --------------------------------------------------------

    /**
     * Right-click opens the menu, server-side only &mdash; the client gets the screen from the
     * server's open-screen packet, which is what carries the container id the two sides share.
     *
     * <p>{@code useWithoutItem} rather than {@code useItemOn}: holding an item must not stop the
     * GUI opening, and the Stamper has no item-in-hand interaction of its own. The block entity is
     * the {@code MenuProvider}.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof StamperBlockEntity stamper) {
            player.openMenu(stamper);
        }
        return InteractionResult.SUCCESS;
    }

    // --- SPEC section 7: activation and delivery ------------------------------------------------

    /** SPEC &sect;7: powered, wait four game ticks, then act. */
    private static final int ACTIVATION_DELAY_TICKS = 4;

    /**
     * How long the lit texture stays on after a successful stamp. Vanilla's
     * {@code CrafterBlock.MAX_CRAFTING_TICKS}, verified with {@code javap} against the 1.21.11 jar
     * &mdash; long enough to read at a glance, short enough not to lag a fast clock (D43).
     */
    private static final int STAMPING_TICKS = 6;

    /** The speed vanilla's dispenser and dropper use when spitting an item out. */
    private static final int EJECT_SPEED = 6;

    /** How far in front of the block centre the item appears, matching the dispenser. */
    private static final double DISPENSE_OFFSET = 0.7;

    /**
     * Edge-triggered, exactly as dropper and crafter: rising edge schedules, falling edge only
     * clears the flag. Holding the signal high fires once, not repeatedly (gametest G15).
     *
     * <p>The {@code above()} term mirrors the dropper — it is what lets redstone sitting on top of
     * the block above power this one.
     */
    @Override
    //? if >=1.21.11 {
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
    //?} else {
    /*protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
    *///?}
        boolean powered = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above());
        boolean triggered = state.getValue(TRIGGERED);
        if (powered && !triggered) {
            level.scheduleTick(pos, this, ACTIVATION_DELAY_TICKS);
            level.setBlock(pos, state.setValue(TRIGGERED, true), Block.UPDATE_CLIENTS);
        } else if (!powered && triggered) {
            level.setBlock(pos, state.setValue(TRIGGERED, false), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof StamperBlockEntity stamper) {
            stampAndDeliver(level, pos, state, stamper);
        }
    }

    private static void stampAndDeliver(ServerLevel level, BlockPos pos, BlockState state, StamperBlockEntity stamper) {
        ItemStack input = stamper.getItem(StamperBlockEntity.SLOT_INPUT);
        if (input.isEmpty()) {
            // SPEC §7: nothing is produced, and the block says so.
            level.levelEvent(LevelEvent.SOUND_DISPENSER_FAIL, pos, 0);
            return;
        }

        ItemStack stamped = StampOperation.apply(stamper.getItem(StamperBlockEntity.SLOT_TEMPLATE), input);

        // The input is consumed *before* delivery is attempted, and stays consumed however
        // delivery goes. That is Crafter semantics, chosen knowingly over dropper semantics
        // (D6): a Stamper feeding a full chest litters the floor rather than stalling.
        stamper.removeItem(StamperBlockEntity.SLOT_INPUT, 1);

        // SPEC §7 / D30: the success sound belongs to the *stamp*, not the delivery, so all three
        // delivery outcomes below sound identical — the block did its job in all of them. An empty
        // template counts as a success: it erases a name (§11) but still consumes and dispenses.
        level.levelEvent(LevelEvent.SOUND_CRAFTER_CRAFT, pos, 0);

        // D43: the light is set here, in the same branch as the sound and on the same condition,
        // so the two can never disagree. `state` is stale after this, hence the re-read below.
        level.setBlock(pos, state.setValue(STAMPING, true), Block.UPDATE_CLIENTS);
        stamper.setStampingTicks(STAMPING_TICKS);

        deliver(level, pos, level.getBlockState(pos), stamper, stamped);
    }

    /** SPEC &sect;7's three cases: container with space, container that refuses, and everything else. */
    private static void deliver(ServerLevel level, BlockPos pos, BlockState state,
                                StamperBlockEntity stamper, ItemStack stamped) {
        Direction facing = state.getValue(FACING);
        Container target = HopperBlockEntity.getContainerAt(level, pos.relative(facing));

        if (target != null) {
            ItemStack leftover = HopperBlockEntity.addItem(stamper, target, stamped, facing.getOpposite());
            if (leftover.isEmpty()) {
                return;
            }
            // Refused, or only partly accepted. Whatever came back gets ejected.
            stamped = leftover;
        }

        // Vanilla's getContainerAt is used rather than NeoForge's getContainerOrHandlerAt, which
        // would also see capability-based inventories: ARCHITECTURE.md's non-goals make vanilla
        // Container the contract, and getContainerOrHandlerAt does not exist on Fabric anyway.
        Position where = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                .add(DISPENSE_OFFSET * facing.getStepX(),
                        DISPENSE_OFFSET * facing.getStepY(),
                        DISPENSE_OFFSET * facing.getStepZ());
        DefaultDispenseItemBehavior.spawnItem(level, stamped, EJECT_SPEED, facing, where);
    }

    // --- SPEC section 8: comparator -------------------------------------------------------------

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    //? if >=1.21.11 {
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
    //?} else {
    /*protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
    *///?}
        // Reads slot 0 only — the block entity owns that rule (SPEC §8, D3).
        return level.getBlockEntity(pos) instanceof StamperBlockEntity stamper ? stamper.comparatorOutput() : 0;
    }

    // --- SPEC section 11: breaking drops the contents of slots 0 and 1 ---------------------------

    /**
     * Refreshes comparators after the block is gone. It does <b>not</b> drop the contents, and
     * that is not an omission &mdash; see D33.
     *
     * <p><b>On 1.21.11 the drop is vanilla's.</b> {@code LevelChunk.setBlockState} calls
     * {@code BlockEntity.preRemoveSideEffects} &mdash; which calls {@code Containers.dropContents}
     * for any block entity that is a {@link Container}, and this one is &mdash; and only
     * <i>then</i> removes the block entity and calls this method. So by the time we are here
     * {@code level.getBlockEntity(pos)} is already null. Any drop code written inside such a guard
     * is dead, and this method previously contained exactly that.
     *
     * <p><b>Port hazard, and how the 1.21.1 branch below answers it.</b> That ordering is what
     * satisfies SPEC &sect;11 here. 1.21.1 has no {@code preRemoveSideEffects} and removes the block
     * entity <i>after</i> {@code onRemove}, so the drop happens there instead &mdash; still not by
     * hand, because {@code Containers.dropContentsOnDestroy} is vanilla's own helper and is what
     * {@code DropperBlock} calls on that version. Do not read this file as evidence that the mod
     * drops its own contents &mdash; on neither target does it.
     *
     * <p>The comparator refresh is unconditional and outside any block-entity lookup, matching
     * {@code DispenserBlock}. It has to be: it is precisely the call that must still happen once
     * the block entity is gone, and burying it in the dead guard meant a comparator reading the
     * &sect;8 signal <i>through</i> a solid block kept a stale value after the Stamper was broken.
     */
    //? if >=1.21.11 {
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }
    //?} else {
    /*@Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // Drops slots 0 and 1 *and* refreshes comparators, both inside vanilla's helper. That
        // second half is G28: a comparator reading the signal through a solid block must not keep
        // a stale value once the Stamper is gone. Read out of the 1.21.1 bytecode of this helper
        // and of DropperBlock, not recalled (D33).
        Containers.dropContentsOnDestroy(state, newState, level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    *///?}

    // --- Rotation and mirroring -----------------------------------------------------------------

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
