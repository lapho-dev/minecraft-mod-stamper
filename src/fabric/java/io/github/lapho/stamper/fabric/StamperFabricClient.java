package io.github.lapho.stamper.fabric;

import io.github.lapho.stamper.client.StamperScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * Fabric client entrypoint: binds {@link StamperScreen} to the menu type. That is the whole job.
 *
 * <p>Runs after {@link StamperFabric#onInitialize()} &mdash; Fabric Loader invokes {@code main}
 * entrypoints before {@code client} ones &mdash; so the menu type is registered by the time this
 * asks for it.
 *
 * <p>{@code MenuScreens.register} is private in vanilla; the transitive access widener in
 * {@code fabric-transitive-access-wideners-v1} is what makes it callable (D27). NeoForge, which
 * cannot reach it either, hands out {@code RegisterMenuScreensEvent} instead.
 */
public final class StamperFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(StamperFabric.menuType(), StamperScreen::new);
    }
}
