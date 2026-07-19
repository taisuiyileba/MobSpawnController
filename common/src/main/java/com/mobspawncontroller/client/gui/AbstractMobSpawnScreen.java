package com.mobspawncontroller.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shared background policy for the mod's standalone screens.
 *
 * <p>Minecraft 1.21's default {@link Screen#renderBackground} applies the game's blur effect.
 * These screens render their background before their custom panel and may call
 * {@link Screen#render} later for widgets, so the vanilla method would also run twice.
 */
abstract class AbstractMobSpawnScreen extends Screen {

    protected AbstractMobSpawnScreen(Component title) {
        super(title);
    }

    /** Backgrounds are drawn explicitly before each screen's custom panel. */
    @Override
    public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    /** Draws the vanilla transparent gradient without invoking the 1.21 blur pass. */
    protected final void renderUnblurredBackground(GuiGraphics graphics) {
        renderTransparentBackground(graphics);
    }
}
