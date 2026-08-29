package io.github.lapho.stamper.neoforge;

import io.github.lapho.stamper.Stamper;
import io.github.lapho.stamper.block.StamperBlockEntity;
import io.github.lapho.stamper.menu.StamperMenu;
import io.github.lapho.stamper.reg.StamperContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;
import java.util.function.Supplier;

/**
 * NeoForge entrypoint. Registration only &mdash; no behaviour lives here.
 *
 * <p>NeoForge keeps the vanilla registries reopenable through its own Minecraft patches, but only
 * during its registry events, so the descriptors go through {@code DeferredRegister}. Both loaders
 * end up calling the identical factories in {@code reg/}.
 */
@Mod(Stamper.MOD_ID)
public final class StamperNeoForge {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, Stamper.MOD_ID);

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, Stamper.MOD_ID);

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Stamper.MOD_ID);

    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, Stamper.MOD_ID);

    // The block needs its block entity type and the type needs the block. Both references are
    // deferred through suppliers, so neither is resolved until NeoForge fires its registry events
    // and both objects exist (D23).
    private static final Supplier<Block> STAMPER =
            BLOCKS.register(StamperContent.STAMPER_PATH, () -> StamperContent.createStamperBlock(
                    StamperNeoForge.STAMPER_BLOCK_ENTITY, () -> StamperNeoForge.STAMPER_MENU.get()));

    private static final Supplier<Item> STAMPER_ITEM =
            ITEMS.register(StamperContent.STAMPER_PATH, () -> StamperContent.createStamperItem(STAMPER.get()));

    private static final Supplier<BlockEntityType<StamperBlockEntity>> STAMPER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register(StamperContent.STAMPER_PATH,
                    () -> new BlockEntityType<>(
                            (pos, state) -> StamperContent.createBlockEntity(
                                    StamperNeoForge.STAMPER_BLOCK_ENTITY, pos, state),
                            //? if >=1.21.11 {
                            Set.of(STAMPER.get())));
                            //?} else {
                            /*// 1.21.1 still takes the datafixer Type as a third argument. null is
                            // what a mod passes — only vanilla block entities have a registered
                            // schema — and the parameter was dropped from the constructor later.
                            Set.of(STAMPER.get()), null));
                            *///?}

    /**
     * Vanilla's {@code MenuType} constructor is private; NeoForge patches it public and wraps it in
     * {@code IMenuTypeExtension.create}, which is used here so the loader API is visible in the
     * code rather than implied. The factory ignores the extra-data buffer &mdash; the Stamper sends
     * none, because everything the screen shows is ordinary synced slot contents (D27).
     */
    private static final Supplier<MenuType<StamperMenu>> STAMPER_MENU =
            MENUS.register(StamperContent.STAMPER_PATH,
                    () -> IMenuTypeExtension.create((containerId, playerInventory, extraData) ->
                            new StamperMenu(StamperNeoForge.STAMPER_MENU.get(), containerId, playerInventory)));

    /** For {@link StamperNeoForgeClient} to bind a screen to. */
    static Supplier<MenuType<StamperMenu>> menuType() {
        return STAMPER_MENU;
    }

    public StamperNeoForge(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(StamperNeoForge::addToCreativeTab);
    }

    /** Same tab as the Fabric side picks, so the two builds stay indistinguishable in game. */
    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.REDSTONE_BLOCKS)) {
            event.accept(STAMPER_ITEM.get());
        }
    }
}
