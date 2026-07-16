package com.ggrgg.createredstonelinkgui.client;

import com.ggrgg.createredstonelinkgui.CreateRedstoneLinkGUI;
import com.ggrgg.createredstonelinkgui.common.TinyRedstoneCreateCompatibility;
import com.ggrgg.createredstonelinkgui.common.network.TinyLinkScreenSwapPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Intercepts TinyCreate's RedstoneLinkGUI (com.dfined.minecraft.create.gui.RedstoneLinkGUI)
 * when it is about to open, cancels it, and sends a swap packet to the server so that
 * our TinyRedstoneLinkConfigScreen opens instead.
 *
 * <p>The screen is cancelled <b>before</b> reflection, ensuring TinyCreate's screen
 * never appears even if frequency reading fails. The server handler will fill in
 * any missing data by reading from the TinyRedstoneLink cell directly.
 *
 * <p>We use per-field try-catches for each reflected field so that a missing field
 * in a different TinyCreate version doesn't break the entire swap.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = CreateRedstoneLinkGUI.MODID)
public class TinyLinkScreenHandler {

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen screen = event.getScreen();
        if (screen == null) return;

        String className = screen.getClass().getName();
        if (!TinyRedstoneCreateCompatibility.isTinyCreateRedstoneLinkScreen(className)) return;

        CreateRedstoneLinkGUI.LOGGER.info("TinyLinkScreenHandler: Intercepted TinyCreate RedstoneLinkGUI");

        // === CANCEL THE SCREEN FIRST ===
        // This must happen before any reflection that might fail, so TinyCreate's
        // screen is always blocked and the player doesn't see it.
        event.setCanceled(true);

        // === TRY TO READ TINYCREATE DATA VIA REFLECTION ===
        // The screen is an AbstractContainerScreen (TinyCreate's RedstoneLinkGUI extends it).
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            // Can't get the menu, but the screen is already cancelled.
            // Still need to send the swap — send minimal data and let the server fill in.
            CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkScreenHandler: Screen is not an AbstractContainerScreen, sending empty swap");
            PacketDistributor.sendToServer(new TinyLinkScreenSwapPayload(BlockPos.ZERO, 0, true, ItemStack.EMPTY, ItemStack.EMPTY));
            return;
        }

        var menu = containerScreen.getMenu();
        if (menu == null) {
            // Menu not available yet, but screen is already cancelled.
            PacketDistributor.sendToServer(new TinyLinkScreenSwapPayload(BlockPos.ZERO, 0, true, ItemStack.EMPTY, ItemStack.EMPTY));
            return;
        }

        // Per-field reflection with individual fallbacks
        Class<?> menuClass = menu.getClass();

        // Read pos
        BlockPos pos = null;
        try {
            java.lang.reflect.Field posField = menuClass.getField("pos");
            pos = (BlockPos) posField.get(menu);
        } catch (Exception e) {
            CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkScreenHandler: Could not read 'pos' from menu", e);
        }

        // Read cellIndex
        int cellIndex = 0;
        try {
            java.lang.reflect.Field cellIndexField = menuClass.getField("cellIndex");
            cellIndex = cellIndexField.getInt(menu);
        } catch (Exception e) {
            CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkScreenHandler: Could not read 'cellIndex' from menu", e);
        }

        // Read transmitter
        boolean transmitter = true;
        try {
            java.lang.reflect.Field transmitterField = menuClass.getField("transmitter");
            transmitter = transmitterField.getBoolean(menu);
        } catch (Exception e) {
            CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkScreenHandler: Could not read 'transmitter' from menu", e);
        }

        // Read frequencies from ghostInventory slots 0 and 1
        ItemStack freq1 = ItemStack.EMPTY;
        ItemStack freq2 = ItemStack.EMPTY;
        try {
            java.lang.reflect.Field ghostInvField = menuClass.getField("ghostInventory");
            Object ghostInv = ghostInvField.get(menu);
            java.lang.reflect.Method getStackMethod = ghostInv.getClass().getMethod("getStackInSlot", int.class);
            freq1 = (ItemStack) getStackMethod.invoke(ghostInv, 0);
            freq2 = (ItemStack) getStackMethod.invoke(ghostInv, 1);
        } catch (Exception e) {
            CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkScreenHandler: Could not read frequencies from ghostInventory", e);
        }

        // If frequencies are empty AND we have a valid position, try reading from the cell directly
        if ((pos != null && !pos.equals(BlockPos.ZERO)) && freq1.isEmpty() && freq2.isEmpty()) {
            try {
                var level = Minecraft.getInstance().level;
                if (level != null) {
                    Object cell = TinyRedstoneCreateCompatibility.findCell(level, pos, cellIndex);
                    if (cell != null) {
                        freq1 = TinyRedstoneCreateCompatibility.getFreq1(cell);
                        freq2 = TinyRedstoneCreateCompatibility.getFreq2(cell);
                        CreateRedstoneLinkGUI.LOGGER.info("TinyLinkScreenHandler: Read frequencies from cell directly");
                    }
                }
            } catch (Exception e) {
                CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkScreenHandler: Could not read frequencies from cell directly", e);
            }
        }

        // Use BlockPos.ZERO as fallback if pos wasn't read
        BlockPos swapPos = (pos != null) ? pos : BlockPos.ZERO;

        // Send swap packet to server
        PacketDistributor.sendToServer(new TinyLinkScreenSwapPayload(swapPos, cellIndex, transmitter, freq1, freq2));

        CreateRedstoneLinkGUI.LOGGER.info("TinyLinkScreenHandler: Swapped to our screen (pos={}, cellIndex={}, transmitter={})",
            swapPos, cellIndex, transmitter);
    }
}