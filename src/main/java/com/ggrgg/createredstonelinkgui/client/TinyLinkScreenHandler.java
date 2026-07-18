package com.ggrgg.createredstonelinkgui.client;

import java.lang.reflect.Method;

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
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = "createredstonelinkgui")
public class TinyLinkScreenHandler {

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen screen = event.getScreen();
        if (screen == null) return;

        String className = screen.getClass().getName();
        if (!TinyRedstoneCreateCompatibility.isTinyCreateRedstoneLinkScreen(className)) return;

        // Cancel TinyCreate's screen before any reflection
        event.setCanceled(true);

        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            PacketDistributor.sendToServer(new TinyLinkScreenSwapPayload(BlockPos.ZERO, 0, true, ItemStack.EMPTY, ItemStack.EMPTY));
            return;
        }

        var menu = containerScreen.getMenu();
        if (menu == null) {
            PacketDistributor.sendToServer(new TinyLinkScreenSwapPayload(BlockPos.ZERO, 0, true, ItemStack.EMPTY, ItemStack.EMPTY));
            return;
        }

        Class<?> menuClass = menu.getClass();

        BlockPos pos = callMethod(menuClass, menu, "getPos", BlockPos.class, BlockPos.ZERO);
        int cellIndex = callIntMethod(menuClass, menu, "getCellIndex", 0);
        boolean transmitter = callBooleanMethod(menuClass, menu, "isTransmitter", true);
        ItemStack freq1 = callStackMethod(menuClass, menu, "getFreq1", ItemStack.EMPTY);
        ItemStack freq2 = callStackMethod(menuClass, menu, "getFreq2", ItemStack.EMPTY);

        if (pos == null || pos.equals(BlockPos.ZERO)) {
            PacketDistributor.sendToServer(new TinyLinkScreenSwapPayload(BlockPos.ZERO, cellIndex, transmitter, freq1, freq2));
            return;
        }

        // If frequencies are empty, try reading from the cell directly
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
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        PacketDistributor.sendToServer(new TinyLinkScreenSwapPayload(pos, cellIndex, transmitter, freq1, freq2));
    }

    // ==================== Reflection helpers ====================

    @SuppressWarnings("unchecked")
    private static <T> T callMethod(Class<?> clazz, Object instance, String name, Class<T> returnType, T fallback) {
        try {
            Method method = clazz.getMethod(name);
            Object result = method.invoke(instance);
            if (result != null && returnType.isAssignableFrom(result.getClass())) {
                return (T) result;
            }
            return fallback;
        } catch (NoSuchMethodException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null && superClass != Object.class) {
                return callMethod(superClass, instance, name, returnType, fallback);
            }
            return fallback;
        } catch (Exception e) {
            return fallback;
        }
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
            return fallback;
        } catch (Exception e) {
            return fallback;
        }
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
            return fallback;
        } catch (Exception e) {
            return fallback;
        }
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
            return fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}