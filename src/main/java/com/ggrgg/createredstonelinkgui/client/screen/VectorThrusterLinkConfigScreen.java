package com.ggrgg.createredstonelinkgui.client.screen;

import com.ggrgg.createredstonelinkgui.common.menu.VectorThrusterLinkMenu;
import com.ggrgg.createredstonelinkgui.common.network.OpenLinkMenuPayload;
import com.ggrgg.createredstonelinkgui.common.network.VectorThrusterFrequencyPayload;
import com.ggrgg.createredstonelinkgui.compat.frequency.FrequencyItemHelper;
import com.ggrgg.createredstonelinkgui.compat.frequency.SymbolPickerScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle middle-click on frequency slots for vector thruster menus
        if (button == 2) {
            int slot = hitTestFrequencySlot(mouseX, mouseY);
            if (slot >= 0) {
                ItemStack current = this.menu.getSlot(slot).getItem();
                if (FrequencyItemHelper.isFrequencySymbol(current)) {
                    Minecraft.getInstance().setScreen(new SymbolPickerScreen(
                        this.menu.getPos(),
                        // onPick: update local slot and send VectorThrusterFrequencyPayload
                        picked -> {
                            this.menu.getSlot(slot).set(picked);
                            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                                new VectorThrusterFrequencyPayload(
                                    this.menu.getPos(), picked, slot, this.menu.getSideKey()));
                        },
                        // onClose: reopen the vector thruster menu with correct side key
                        () -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                            new OpenLinkMenuPayload(this.menu.getPos(), this.menu.getSideKey()))
                    ));
                    return true;
                }
                return true; // consume click even if not a frequency symbol
            }
        }
        // Fall through to the base class for preset panel handling
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void applyFrequencyChange(int slotIndex, boolean isFirst, ItemStack stack) {
        // Send to server via VectorThrusterFrequencyPayload
        Object behaviour = this.menu.getBehaviour();
        if (behaviour != null) {
            // Apply locally for immediate feedback
            com.ggrgg.createredstonelinkgui.compat.propulsion.VectorThrusterHelper.setFrequency(behaviour, isFirst, stack);
        }
    }

    @Override
    public void updateFrequencySlot(int slotIndex, ItemStack stack) {
        // Override to send VectorThrusterFrequencyPayload instead of RedstoneLinkFrequencyPayload
        applyFrequencyChange(slotIndex, slotIndex == 0, stack);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new VectorThrusterFrequencyPayload(this.menu.getPos(), stack, slotIndex, this.menu.getSideKey()));
    }

    @Override
    protected boolean hasMoveButton() {
        return true;
    }
}
