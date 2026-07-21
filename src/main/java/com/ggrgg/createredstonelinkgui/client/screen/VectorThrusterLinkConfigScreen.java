package com.ggrgg.createredstonelinkgui.client.screen;

import com.ggrgg.createredstonelinkgui.common.menu.VectorThrusterLinkMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class VectorThrusterLinkConfigScreen extends AbstractLinkConfigScreen<VectorThrusterLinkMenu> {

    private static final ResourceLocation OVERLAY_TEXTURE =
            ResourceLocation.parse("createredstonelinkgui:textures/redstone_link.png");

    public VectorThrusterLinkConfigScreen(VectorThrusterLinkMenu menu, Inventory playerInv, Component title) {
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
    protected void applyFrequencyChange(int slotIndex, boolean isFirst, net.minecraft.world.item.ItemStack stack) {
        // Send to server via VectorThrusterFrequencyPayload
        Object behaviour = this.menu.getBehaviour();
        if (behaviour != null) {
            // Apply locally for immediate feedback
            com.ggrgg.createredstonelinkgui.compat.propulsion.VectorThrusterHelper.setFrequency(behaviour, isFirst, stack);
        }
        // Use the existing updateFrequencySlot from AbstractLinkConfigScreen
        // but override to use VectorThrusterFrequencyPayload instead of RedstoneLinkFrequencyPayload
    }

    @Override
    public void updateFrequencySlot(int slotIndex, net.minecraft.world.item.ItemStack stack) {
        // Override to send VectorThrusterFrequencyPayload instead of RedstoneLinkFrequencyPayload
        applyFrequencyChange(slotIndex, slotIndex == 0, stack);
        com.ggrgg.createredstonelinkgui.common.network.VectorThrusterFrequencyPayload payload =
            new com.ggrgg.createredstonelinkgui.common.network.VectorThrusterFrequencyPayload(
                this.menu.getPos(), stack, slotIndex, this.menu.getSideKey());
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
    }

    @Override
    protected boolean hasMoveButton() {
        return true; // Move function — will be validated separately
    }
}