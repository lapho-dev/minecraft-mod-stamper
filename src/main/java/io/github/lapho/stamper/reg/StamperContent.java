package io.github.lapho.stamper.reg;

import io.github.lapho.stamper.Stamper;
import io.github.lapho.stamper.block.StamperBlock;
import io.github.lapho.stamper.block.StamperBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Supplier;

/**
 * The single list of what this mod registers, as loader-agnostic descriptors: a registry key and
 * a factory. Each loader source set walks these and registers them in its own idiom &mdash;
 * vanilla {@code Registry.register} on Fabric, {@code DeferredRegister} on NeoForge.
 *
 * <p>Adding an object later means editing this one file rather than both loader entrypoints.
 * See docs/ARCHITECTURE.md, {@code reg/}.
 */
public final class StamperContent {
    /**
     * Registry path of the Stamper block, its item and its block entity, giving
     * {@code stamper:stamper} for all three (docs/SPEC.md &sect;1). It coincides with
     * {@link Stamper#MOD_ID} but is a different thing: this is the path within the namespace.
     */
    public static final String STAMPER_PATH = "stamper";

    public static final ResourceKey<Block> STAMPER_BLOCK =
            ResourceKey.create(Registries.BLOCK, Stamper.id(STAMPER_PATH));

    public static final ResourceKey<Item> STAMPER_ITEM =
            ResourceKey.create(Registries.ITEM, Stamper.id(STAMPER_PATH));

    public static final ResourceKey<BlockEntityType<?>> STAMPER_BLOCK_ENTITY =
            ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, Stamper.id(STAMPER_PATH));

    /**
     * SPEC &sect;1: menu id {@code stamper:stamper}.
     *
     * <p>Only the key lives here. The {@link MenuType} itself is built by each loader, because
     * vanilla's {@code MenuType} constructor and its {@code MenuSupplier} are <b>private</b> and
     * the two loaders open them by different means &mdash; NeoForge patches them public and offers
     * {@code IMenuTypeExtension.create}; on Fabric the transitive access widener in
     * {@code fabric-transitive-access-wideners-v1} reopens them (D27). Same situation as
     * {@code BlockEntityType} (D21), same answer: the difference stays in the loader source sets.
     */
    public static final ResourceKey<MenuType<?>> STAMPER_MENU =
            ResourceKey.create(Registries.MENU, Stamper.id(STAMPER_PATH));

    private StamperContent() {
    }

    /**
     * SPEC &sect;3. Hardness and resistance match the Crafter, which is {@code strength(1.5F, 3.5F)}
     * &mdash; read off {@code Blocks.CRAFTER}, not guessed. Note the Crafter itself does <i>not</i>
     * require a correct tool for drops; SPEC lists the tool as a separate row and asks for it, so
     * that is set here deliberately rather than copied from the Crafter.
     *
     * <p>The {@code minecraft:mineable/pickaxe} tag is the data half of "Tool: pickaxe" and is a
     * resource file, so it belongs with the assets rather than here.
     *
     * <p>Sound is left at the default {@code SoundType.STONE}, which is what both the Crafter and
     * the dropper use &mdash; neither sets one explicitly.
     *
     * <p>A block's {@code Properties} must carry its own registry key since 1.21.5, or construction
     * throws.
     */
    public static Block createStamperBlock(Supplier<BlockEntityType<StamperBlockEntity>> blockEntityType,
                                           Supplier<MenuType<?>> menuType) {
        return new StamperBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(1.5F, 3.5F)
                .requiresCorrectToolForDrops()
                //? if >=1.21.11 {
                .setId(STAMPER_BLOCK)
                //?}
                , blockEntityType, menuType);
    }

    /** {@code useBlockDescriptionPrefix} makes the item reuse the block's translation key. */
    public static BlockItem createStamperItem(Block block) {
        return new BlockItem(block, new Item.Properties()
                //? if >=1.21.11 {
                .useBlockDescriptionPrefix()
                .setId(STAMPER_ITEM)
                //?}
                );
    }

    /**
     * Builds the block entity. Deliberately <i>not</i> a {@code BlockEntityType} factory: vanilla's
     * {@code BlockEntityType} constructor and its {@code BlockEntitySupplier} are <b>private</b>,
     * and NeoForge only makes them public through its own Minecraft patches. So each loader
     * assembles the type with the API it actually has &mdash; NeoForge with the patched
     * constructor, Fabric with {@code FabricBlockEntityTypeBuilder} &mdash; and shares this
     * factory. That split is exactly what the loader source sets exist for (D21).
     *
     * @param self supplier of the type being built, to break the block/type cycle (D23)
     */
    public static StamperBlockEntity createBlockEntity(
            Supplier<BlockEntityType<StamperBlockEntity>> self, BlockPos pos, BlockState state) {
        return new StamperBlockEntity(self.get(), pos, state);
    }
}
