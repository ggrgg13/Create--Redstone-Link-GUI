package com.ggrgg.createredstonelinkgui.common.menu;

import com.ggrgg.createredstonelinkgui.common.network.TinyLinkFreqUpdatePayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

/**
 * Menu for TinyRedstoneLink cells (from TinyCreate mod).
 * Extends {@link AbstractLinkMenu} like {@link RedstoneLinkMenu} but stores
 * the cell index and transmitter state for the TinyRedstoneLink panel cell.
 *
 * <p>Frequency slots are at positions (101, 34) and (137, 34) — same as
 * {@link RedstoneLinkMenu}. Ghost slot lambdas send
 * {@link TinyLinkFreqUpdatePayload} to the server on every change.
 */
public class TinyRedstoneLinkMenu extends AbstractLinkMenu {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, "createredstonelinkgui");

    public static final DeferredHolder<MenuType<?>, MenuType<TinyRedstoneLinkMenu>> TYPE = MENUS.register("tiny_redstone_link_menu",
        () -> IMenuTypeExtension.create((windowId, inv, data) -> {
            BlockPos pos = data.readBlockPos();
            int cellIndex = data.readInt();
            boolean transmitter = data.readBoolean();
            ItemStack freq1 = ItemStack.OPTIONAL_STREAM_CODEC.decode(data);
            ItemStack freq2 = ItemStack.OPTIONAL_STREAM_CODEC.decode(data);
            List<ItemStack> freqs = new ArrayList<>(2);
            freqs.add(freq1);
            freqs.add(freq2);
            return new TinyRedstoneLinkMenu(windowId, inv, pos, cellIndex, transmitter, freqs);
        })
    );

    private final int cellIndex;
    private boolean transmitter;
    private boolean initializing = true;

    @Override
    public Object getBehaviour() { return null; } // No LinkBehaviour — uses reflection instead

    public int getCellIndex() { return cellIndex; }
    public boolean isTransmitter() { return transmitter; }

    public TinyRedstoneLinkMenu(int containerId, Inventory playerInventory, BlockPos pos,
                                 int cellIndex, boolean transmitter, List<ItemStack> freqs) {
        super(containerId, playerInventory, pos, TYPE.get());
        this.cellIndex = cellIndex;
        this.transmitter = transmitter;

        // 1. Frequency slots FIRST — indices 0, 1
        // The getter returns the stored stack directly (no behaviour to query)
        this.addSlot(new GhostRecipeSlot(0, 101, 34,
            () -> getLocalFreq(0),
            (id, stack) -> {
                setLocalFreq(0, stack);
                if (!initializing) sendUpdateToServer();
            }));
        this.addSlot(new GhostRecipeSlot(1, 137, 34,
            () -> getLocalFreq(1),
            (id, stack) -> {
                setLocalFreq(1, stack);
                if (!initializing) sendUpdateToServer();
            }));

        // Initialize frequency stacks from the constructor argument (suppressed by initializing flag)
        if (freqs.size() >= 2) {
            getSlot(0).set(freqs.get(0));
            getSlot(1).set(freqs.get(1));
        }

        // Allow user interaction updates from now on
        initializing = false;

        // 2. Preset slots at indices 2-9
        addPresetSlots(playerInventory);

        // 3. Player inventory at indices 10+
        addPlayerInventorySlots(playerInventory);
    }

    // ==================== Frequency storage ====================

    private final ItemStack[] localFreqs = new ItemStack[] { ItemStack.EMPTY, ItemStack.EMPTY };

    private ItemStack getLocalFreq(int slot) {
        return localFreqs[slot];
    }

    private void setLocalFreq(int slot, ItemStack stack) {
        localFreqs[slot] = stack.copy();
        localFreqs[slot].setCount(1);
    }

    // ==================== Transmitter toggle ====================

    /**
     * Update the transmitter mode and send the change to the server.
     */
    public void setTransmitter(boolean transmitter) {
        this.transmitter = transmitter;
        sendUpdateToServer();
    }

    // ==================== Click handling override ====================

    /**
     * Override frequency slot clicks to use our own packet type.
     * The base class sends {@code RedstoneLinkFrequencyPayload} which talks to
     * LinkBehaviour — we need to send {@code TinyLinkFreqUpdatePayload} instead.
     */
    @Override
    protected void handleFrequencySlotClick(int slotId, int button, ClickType clickType, Player player) {
        var slot = this.getSlot(slotId);

        if (button == 1 || clickType == ClickType.THROW) {
            // Right-click or Q: clear the slot
            slot.set(ItemStack.EMPTY);
        } else {
            // Left-click: place a single copy of the carried item; item stays on cursor
            ItemStack carried = getCarried();
            if (!carried.isEmpty()) {
                ItemStack targetStack = carried.copy();
                targetStack.setCount(1);
                slot.set(targetStack);
            }
        }
        // sendUpdateToServer() is already called by the ghost slot callback on slot.set()
    }

    // ==================== Network sync ====================

    /**
     * Send the current frequency slot contents and transmitter state to the server.
     * Called automatically by the ghost slot lambdas on every slot.set().
     */
    public void sendUpdateToServer() {
        if (player.level().isClientSide()) {
            ItemStack freq1 = getSlot(0).getItem();
            ItemStack freq2 = getSlot(1).getItem();
            PacketDistributor.sendToServer(new TinyLinkFreqUpdatePayload(
                this.pos, this.cellIndex, this.transmitter, freq1, freq2));
        }
    }
}
