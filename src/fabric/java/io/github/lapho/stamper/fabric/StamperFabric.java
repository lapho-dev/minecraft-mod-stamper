package io.github.lapho.stamper.fabric;

import io.github.lapho.stamper.block.StamperBlockEntity;
import io.github.lapho.stamper.menu.StamperMenu;
import io.github.lapho.stamper.reg.StamperContent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fabric entrypoint. Registration only &mdash; no behaviour lives here.
 *
 * <p>This runs while the built-in registries are still writable, which on Fabric is true only
 * because Fabric API's {@code fabric-registry-sync-v0} redirects vanilla's freeze out of
 * {@code Bootstrap.bootStrap()}. Stock Fabric Loader freezes them first, and then even
 * {@code new Block(...)} throws. See D21.
 */
public final class StamperFabric implements ModInitializer {
    /**
     * The registered menu type, for {@link StamperFabricClient} to bind a screen to.
     *
     * <p>A static holder, which shared code is not allowed (ARCHITECTURE.md rule 5, D23) &mdash;
     * but a loader adapter is not shared code, it is never extracted and never unit-tested, and the
     * client entrypoint is a separate object with no other channel to reach this. The NeoForge side
     * holds its registry objects in statics for the same reason. Set exactly once, in
     * {@link #onInitialize()}.
     */
    private static final AtomicReference<MenuType<StamperMenu>> MENU_TYPE = new AtomicReference<>();

    static MenuType<StamperMenu> menuType() {
        return Objects.requireNonNull(MENU_TYPE.get(), "Menu type read before onInitialize() registered it");
    }

    @Override
    public void onInitialize() {
        // The block needs its block entity type and the type needs the block, so the cycle is
        // broken with a local holder rather than a static field (D23, ARCHITECTURE.md rule 5).
        AtomicReference<BlockEntityType<StamperBlockEntity>> blockEntityType = new AtomicReference<>();

        // The menu type has the same shape of cycle and is registered last, so the block reads it
        // through the holder too. Nothing calls either supplier before a player uses the block.
        Block stamper = StamperContent.createStamperBlock(blockEntityType::get, MENU_TYPE::get);
        Registry.register(BuiltInRegistries.BLOCK, StamperContent.STAMPER_BLOCK, stamper);

        Item stamperItem = StamperContent.createStamperItem(stamper);
        Registry.register(BuiltInRegistries.ITEM, StamperContent.STAMPER_ITEM, stamperItem);

        // Vanilla's BlockEntityType constructor is private, so this goes through Fabric API's
        // builder. NeoForge patches the constructor public and needs no equivalent (D21).
        blockEntityType.set(Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                StamperContent.STAMPER_BLOCK_ENTITY,
                FabricBlockEntityTypeBuilder.<StamperBlockEntity>create(
                        (pos, state) -> StamperContent.createBlockEntity(blockEntityType::get, pos, state),
                        stamper).build()));

        // Vanilla's MenuType constructor and MenuSupplier are private as well, and Fabric API has
        // no builder for them — what it has is fabric-transitive-access-wideners-v1, which reopens
        // both. That module is why this line compiles; see D27.
        MENU_TYPE.set(Registry.register(
                BuiltInRegistries.MENU,
                StamperContent.STAMPER_MENU,
                new MenuType<>((containerId, playerInventory) ->
                        new StamperMenu(menuType(), containerId, playerInventory), FeatureFlags.VANILLA_SET)));

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS)
                .register(entries -> entries.accept(stamperItem));
    }
}
