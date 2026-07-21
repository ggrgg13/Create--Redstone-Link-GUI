package com.ggrgg.createredstonelinkgui.common.menu;

import com.ggrgg.createredstonelinkgui.common.network.VectorThrusterFrequencyPayload;
import com.ggrgg.createredstonelinkgui.compat.propulsion.VectorThrusterHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class VectorThrusterLinkMenu extends AbstractLinkMenu {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, "createredstonelinkgui");

    public static final DeferredHolder<MenuType<?>, MenuType<VectorThrusterLinkMenu>> TYPE = MENUS.register("vector_thruster_link_menu",
        () -> IMenuTypeExtension.create((windowId, inv, data) -> {
            BlockPos pos = data.readBlockPos();
            String sideKey = data.readUtf();
            return new VectorThrusterLinkMenu(windowId, inv, pos, sideKey);
        })
    );

    private final String sideKey;
    private Object cachedBehaviour;

    public VectorThrusterLinkMenu(int containerId, Inventory playerInventory, BlockPos pos, String sideKey) {
        super(containerId, playerInventory, pos, TYPE.get());
        this.sideKey = sideKey;

        // Cache the behaviour on the menu (server only — client will resolve when needed)
        if (!playerInventory.player.level().isClientSide) {
            this.cachedBehaviour = VectorThrusterHelper.getBehaviour(playerInventory.player.level(), pos, sideKey);
        }

        // 1. Frequency slots — indices 0, 1 (same layout as RedstoneLinkMenu)
        // Note: Slot callbacks only update the ghost slot display. The actual frequency
        //       is applied server-side via VectorThrusterFrequencyPayload.handleServer().
        this.addSlot(new GhostRecipeSlot(0, 101, 34,
            () -> {
                Object behaviour = resolveBehaviour();
                if (behaviour != null) return VectorThrusterHelper.getFrequency(behaviour, true);
                return net.minecraft.world.item.ItemStack.EMPTY;
            },
            (id, stack) -> {})); // no-op — handleFrequencySlotClick handles networking
        this.addSlot(new GhostRecipeSlot(1, 137, 34,
            () -> {
                Object behaviour = resolveBehaviour();
                if (behaviour != null) return VectorThrusterHelper.getFrequency(behaviour, false);
                return net.minecraft.world.item.ItemStack.EMPTY;
            },
            (id, stack) -> {})); // no-op — handleFrequencySlotClick handles networking

        // 2. Preset slots at indices 2-9
        addPresetSlots(playerInventory);

        // 3. Player inventory at indices 10+
        addPlayerInventorySlots(playerInventory);
    }

    public String getSideKey() {
        return sideKey;
    }

    @Override
    public Object getBehaviour() {
        return resolveBehaviour();
    }

    @Override
    protected void handleFrequencySlotClick(int slotId, int button, ClickType clickType, Player player) {
        ItemStack targetStack = processSlotUpdate(slotId, button, clickType);
        if (player.level().isClientSide()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new VectorThrusterFrequencyPayload(this.pos, targetStack, slotId, this.sideKey)
            );
        }
    }

    private Object resolveBehaviour() {
        if (cachedBehaviour != null) return cachedBehaviour;
        return VectorThrusterHelper.getBehaviour(player.level(), pos, sideKey);
    }
}
