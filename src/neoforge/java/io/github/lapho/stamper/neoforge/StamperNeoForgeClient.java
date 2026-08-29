package io.github.lapho.stamper.neoforge;

import io.github.lapho.stamper.Stamper;
import io.github.lapho.stamper.client.StamperScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * NeoForge client init: binds {@link StamperScreen} to the menu type. That is the whole job.
 *
 * <p>{@code MenuScreens.register} is private in vanilla and NeoForge does not patch it, so
 * screens are bound through {@code RegisterMenuScreensEvent} instead. Fabric, which has no such
 * event, reaches the private method through an access widener (D27).
 *
 * <p>{@code value = Dist.CLIENT} keeps this class off a dedicated server entirely; the bus is
 * chosen by NeoForge from the event type, which is why none is named here.
 */
@EventBusSubscriber(modid = Stamper.MOD_ID, value = Dist.CLIENT)
public final class StamperNeoForgeClient {
    private StamperNeoForgeClient() {
    }

    @SubscribeEvent
    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(StamperNeoForge.menuType().get(), StamperScreen::new);
    }
}
