package io.github.lapho.stamper.client;

import io.github.lapho.stamper.Stamper;
import io.github.lapho.stamper.menu.StamperMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Stamper's screen. docs/SPEC.md &sect;10.
 *
 * <p>Deliberately thin. Everything that decides <i>what</i> is in the three slots is in
 * {@link StamperMenu}, server-side; this class draws a background and lets the vanilla container
 * screen do the rest. The ghost result needs no code here at all: vanilla renders any slot that
 * has an item and serves a tooltip for any slot the mouse is over, without consulting
 * {@code mayPickup}, so a preview that cannot be taken still looks and reads like an item.
 *
 * <p>There is no XP bar, no level cost and no "too expensive" text, because this is not an anvil
 * (SPEC &sect;10). The only reason it looks like one is the slot layout.
 *
 * <p><b>Porters:</b> docs/VERSIONING.md ranks this file as the churniest in the mod. In 1.21.11
 * the background draw is
 * {@code blit(RenderPipeline, Identifier, x, y, u, v, w, h, texW, texH)} and the base
 * {@code render} does <i>not</i> draw slot tooltips, so this class calls
 * {@code renderTooltip} itself &mdash; both read off {@code DispenserScreen} in this version's
 * sources, and both have moved before. Verify them against the target, do not port either from
 * memory.
 */
public class StamperScreen extends AbstractContainerScreen<StamperMenu> {
    /** docs/ART.md: a 256x256 sheet with the usual 176x166 panel at the origin. */
    private static final Identifier BACKGROUND = Stamper.id("textures/gui/container/stamper.png");

    private static final int TEXTURE_SIZE = 256;

    public StamperScreen(StamperMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        // Centred title, as the anvil and dispenser have. The default is left-aligned at x=8.
        titleLabelX = (imageWidth - font.width(title)) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // SPEC §10: the ghost result serves a normal tooltip, which is how the player previews the
        // stamped name. This call is what draws it — the base render does not.
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0F, 0.0F,
                imageWidth, imageHeight, TEXTURE_SIZE, TEXTURE_SIZE);
    }
}
