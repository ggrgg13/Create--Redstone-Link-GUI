package com.ggrgg.createredstonelinkgui.client.screen;

import com.ggrgg.createredstonelinkgui.common.menu.TinyRedstoneLinkMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Config screen for TinyRedstoneLink cells (from TinyCreate mod).
 * Uses the same overlay texture as the regular RedstoneLinkConfigScreen,
 * with a TX/RX toggle widget that sends updates via TinyLinkFreqUpdatePayload.
 */
public class TinyRedstoneLinkConfigScreen extends AbstractLinkConfigScreen<TinyRedstoneLinkMenu> {

    private static final ResourceLocation OVERLAY_TEXTURE =
            ResourceLocation.parse("createredstonelinkgui:textures/redstone_link.png");

    public TinyRedstoneLinkConfigScreen(TinyRedstoneLinkMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected ResourceLocation getOverlayTexture() {
        return OVERLAY_TEXTURE;
    }

    @Override
    protected int getBlockPreviewX() {
        return 215;
    }

    @Override
    protected int getBlockPreviewY() {
        return 30;
    }

    @Override
    protected void addExtraWidgets(int contentLeft, int contentTop) {
        // TX/RX toggle widget — always shown for TinyRedstoneLink cells
        this.addRenderableWidget(new TinyRedstoneLinkToggleWidget(
            contentLeft + 65, contentTop + 64,
            this.menu
        ));
    }

    /**
     * Override frequency update to use TinyLinkFreqUpdatePayload instead of
     * the base class's RedstoneLinkFrequencyPayload (which targets LinkBehaviour).
     * Both JEI and EMI drag-drop call this method.
     */
    @Override
    public void updateFrequencySlot(int slotIndex, ItemStack stack) {
        this.menu.getSlot(slotIndex).set(stack);
        // sendUpdateToServer() is called automatically by the ghost slot callback
    }

    /**
     * TX/RX toggle widget for TinyRedstoneLink cells.
     * Sends a TinyLinkFreqUpdatePayload on every click to keep the cell in sync.
     */
    private static class TinyRedstoneLinkToggleWidget extends net.createmod.catnip.gui.widget.AbstractSimiWidget {

        private static final int TRACK_WIDTH = 18;
        private static final int TRACK_HEIGHT = 4;
        private static final int KNOB_SIZE = 10;

        private final TinyRedstoneLinkMenu menu;
        private net.minecraft.client.gui.Font font;

        public TinyRedstoneLinkToggleWidget(int x, int y, TinyRedstoneLinkMenu menu) {
            super(x, y, 54, 16);
            this.menu = menu;
        }

        @Override
        public void doRender(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (!visible) return;

            if (font == null)
                font = net.minecraft.client.Minecraft.getInstance().font;

            boolean isReceiver = !menu.isTransmitter();

            // Update tooltip based on current state
            toolTip = java.util.List.of(Component.translatable(
                isReceiver ? "gui.createredstonelinkgui.receive" : "gui.createredstonelinkgui.send"));

            // === "S" label (left) ===
            graphics.drawString(font, "S", getX() + 3, getY() + 4, 0xFFFFFFFF);

            // === Track (centered vertically) ===
            int trackX = getX() + 13;
            int trackY = getY() + 6;
            graphics.fill(trackX, trackY, trackX + TRACK_WIDTH, trackY + TRACK_HEIGHT, 0xFF555555);
            graphics.fill(trackX, trackY, trackX + TRACK_WIDTH, trackY + 1, 0xFF777777);

            // === Knob (square) ===
            int knobX = isReceiver ? trackX + TRACK_WIDTH - KNOB_SIZE : trackX;
            int knobY = trackY - 3;

            // Knob shadow
            graphics.fill(knobX + 1, knobY + 1, knobX + KNOB_SIZE + 1, knobY + KNOB_SIZE + 1, 0xFF333333);
            // Knob face
            graphics.fill(knobX, knobY, knobX + KNOB_SIZE, knobY + KNOB_SIZE, 0xFFC6C6C6);
            // Knob top-left highlight
            graphics.fill(knobX, knobY, knobX + KNOB_SIZE, knobY + 1, 0xFFE8E8E8);
            graphics.fill(knobX, knobY, knobX + 1, knobY + KNOB_SIZE, 0xFFE8E8E8);
            // Knob bottom-right shadow
            graphics.fill(knobX, knobY + KNOB_SIZE - 1, knobX + KNOB_SIZE, knobY + KNOB_SIZE, 0xFF888888);
            graphics.fill(knobX + KNOB_SIZE - 1, knobY, knobX + KNOB_SIZE, knobY + KNOB_SIZE, 0xFF888888);

            // === "R" label (right) — symmetric to "S" ===
            graphics.drawString(font, "R", getX() + 35, getY() + 4, 0xFFFFFFFF);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            if (!visible) return false;
            int trackX = getX() + 13;
            int trackY = getY() + 3;
            return mouseX >= trackX && mouseY >= trackY
                && mouseX < trackX + TRACK_WIDTH
                && mouseY < trackY + KNOB_SIZE;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            // Toggle transmitter state
            menu.setTransmitter(!menu.isTransmitter());
        }
    }
}