package com.ggrgg.createredstonelinkgui.client;

import java.lang.reflect.Method;

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
 * Intercepts TinyCreate's RedstoneLinkGUI at {@link ScreenEvent.Opening},
 * cancels it, and sends a swap packet so the server opens our
 * {@code TinyRedstoneLinkMenu} instead.
 *
 * <p>Uses {@code getMethod()} to call PUBLIC getters on TinyCreate's LinkMenu:
 * {@code getPos()}, {@code getCellIndex()}, {@code isTransmitter()},
 * {@code getFreq1()}, {@code getFreq2()}. No field reflection needed.
 *
 * <p>The ghost inventory data may not be synced yet at Opening time, so
 * frequency getters may return empty stacks. The server handler fills them
 * in by reading directly from the TinyRedstoneLink cell.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = CreateRedstoneLinkGUI.MODID)
public class TinyLinkScreenHandler {

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen screen = event.getScreen();
        if (screen == null) return;

        String className = screen.getClass().getName();
        if (!TinyRedstoneCreateCompatibility.isTinyCreateRedstoneLinkScreen(className)) return;

        CreateRedstoneLinkGUI.LOGGER.info("TinyLinkScreenHandler: Intercepted TinyCreate RedstoneLinkGUI (class={})", className);

        // === ALWAYS CANCEL THE SCREEN FIRST ===
        event.setCanceled(true);

        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkScreenHandler: Screen is not AbstractContainerScreen, sending empty swap");
            PacketDistributor.sendToServer(new TinyLinkScreenSwapPayload(BlockPos.ZERO, 0, true, ItemStack.EMPTY, ItemStack.EMPTY));
            return;
        }

        var menu = containerScreen.getMenu();
        if (menu == null) {
            CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkScreenHandler: Menu is null, sending empty swap");
            PacketDistributor.sendToServer(new TinyLinkScreenSwapPayload(BlockPos.ZERO, 0, true, ItemStack.EMPTY, ItemStack.EMPTY));
            return;
        }

        Class<?> menuClass = menu.getClass();
        CreateRedstoneLinkGUI.LOGGER.info("TinyLinkScreenHandler: Menu class = {}", menuClass.getName());

        // Use getMethod() to call public getters — no field reflection needed!
        BlockPos pos = callMethod(menuClass, menu, "getPos", BlockPos.class, BlockPos.ZERO);
        int cellIndex = callIntMethod(menuClass, menu, "getCellIndex", 0);
        boolean transmitter = callBooleanMethod(menuClass, menu, "isTransmitter", true);
        ItemStack freq1 = callStackMethod(menuClass, menu, "getFreq1", ItemStack.EMPTY);
        ItemStack freq2 = callStackMethod(menuClass, menu, "getFreq2", ItemStack.EMPTY);

        CreateRedstoneLinkGUI.LOGGER.info("TinyLinkScreenHandler: Got data: pos={}, cellIndex={}, transmitter={}, freq1={}, freq2={}",
            pos, cellIndex, transmitter, freq1, freq2);

        if (pos == null || pos.equals(BlockPos.ZERO)) {
            CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkScreenHandler: pos is ZERO, cannot proceed");
            PacketDistributor.sendToServer(new TinyLinkScreenSwapPayload(BlockPos.ZERO, cellIndex, transmitter, freq1, freq2));
            return;
        }

        // If frequencies are empty and we have a valid pos, try reading from the cell directly
        if (freq1.isEmpty() && freq2.isEmpty()) {
            try {
                var level = Minecraft.getInstance().level;
                if (level != null) {
                    Object cell = TinyRedstoneCreateCompatibility.findCell(level, pos, cellIndex);
                    if (cell != null) {
                        ItemStack cf1 = TinyRedstoneCreateCompatibility.getFreq1(cell);
                        ItemStack cf2 = TinyRedstoneCreateCompatibility.getFreq2(cell);
                        if (!cf1.isEmpty() || !cf2.isEmpty()) {
                            freq1 = cf1;
                            freq2 = cf2;
                            CreateRedstoneLinkGUI.LOGGER.info("TinyLinkScreenHandler: Filled frequencies from cell: {} / {}", freq1, freq2);
                        }
                    }
                }
            } catch (Exception e) {
                CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkScreenHandler: Could not read frequencies from cell", e);
            }
        }

        PacketDistributor.sendToServer(new TinyLinkScreenSwapPayload(pos, cellIndex, transmitter, freq1, freq2));
        CreateRedstoneLinkGUI.LOGGER.info("TinyLinkScreenHandler: Swap packet sent successfully");
    }

    // ==================== Reflection helpers for public getter methods ====================

    /**
     * Call a method that returns a given type with superclass fallback.
     */
    @SuppressWarnings("unchecked")
    private static <T> T callMethod(Class<?> clazz, Object instance, String name, Class<T> returnType, T fallback) {
        try {
            Method method = clazz.getMethod(name);
            Object result = method.invoke(instance);
            if (result != null && returnType.isAssignableFrom(result.getClass())) {
                return (T) result;
            }
            CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkScreenHandler: Method '{}' returned null or wrong type", name);
            return fallback;
        } catch (NoSuchMethodException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null && superClass != Object.class) {
                return callMethod(superClass, instance, name, returnType, fallback);
            }
            CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkScreenHandler: Method '{}' not found in {} or superclasses", name, clazz.getName());
        } catch (Exception e) {
            CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkScreenHandler: Could not call method '{}' on {}", name, clazz.getName(), e);
        }
        return fallback;
    }

    private static int callIntMethod(Class<?> clazz, Object instance, String name, int fallback) {
        try {
            Method method = clazz.getMethod(name);
            return (int) method.invoke(instance);
        } catch (NoSuchMethodException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null && superClass != Object.class) {
                return callIntMethod(superClass, instance, name, fallback);
            }
            CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkScreenHandler: Int method '{}' not found in {} or superclasses", name, clazz.getName());
        } catch (Exception e) {
            CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkScreenHandler: Could not call int method '{}' on {}", name, clazz.getName(), e);
        }
        return fallback;
    }

    private static boolean callBooleanMethod(Class<?> clazz, Object instance, String name, boolean fallback) {
        try {
            Method method = clazz.getMethod(name);
            return (boolean) method.invoke(instance);
        } catch (NoSuchMethodException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null && superClass != Object.class) {
                return callBooleanMethod(superClass, instance, name, fallback);
            }
            CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkScreenHandler: Boolean method '{}' not found in {} or superclasses", name, clazz.getName());
        } catch (Exception e) {
            CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkScreenHandler: Could not call boolean method '{}' on {}", name, clazz.getName(), e);
        }
        return fallback;
    }

    private static ItemStack callStackMethod(Class<?> clazz, Object instance, String name, ItemStack fallback) {
        try {
            Method method = clazz.getMethod(name);
            Object result = method.invoke(instance);
            if (result instanceof ItemStack stack) return stack;
            return fallback;
        } catch (NoSuchMethodException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null && superClass != Object.class) {
                return callStackMethod(superClass, instance, name, fallback);
            }
            CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkScreenHandler: Stack method '{}' not found in {} or superclasses", name, clazz.getName());
        } catch (Exception e) {
            CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkScreenHandler: Could not call stack method '{}' on {}", name, clazz.getName(), e);
        }
        return fallback;
    }
}