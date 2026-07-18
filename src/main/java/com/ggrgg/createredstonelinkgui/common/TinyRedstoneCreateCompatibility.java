package com.ggrgg.createredstonelinkgui.common;

import java.lang.reflect.Method;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Central reflection helper for TinyCreate / TinyRedstone mod compatibility.
 *
 * <p>CRITICAL: All method lookups use {@code getDeclaredMethod()} + {@code setAccessible(true)}
 * instead of {@code getMethod()}. Using getMethod() traverses the FULL inheritance chain
 * including interfaces like IPanelCell. Since TinyRedstoneLink implements IPanelCell,
 * and IPanelCell declares render(PoseStack, ...), getMethod() forces the JVM to resolve
 * PoseStack on the server thread, triggering RuntimeDistCleaner errors.
 *
 * <p>getDeclaredMethod() only looks at the class's own declarations, avoiding interface
 * resolution and the client-only class loading entirely.
 */
public class TinyRedstoneCreateCompatibility {

    private static final String PANEL_TILE_CLASS_NAME = "com.dannyandson.tinyredstone.blocks.PanelTile";
    private static final String TINY_REDSTONE_LINK_CLASS_NAME = "com.dfined.minecraft.create.TinyRedstoneLink";
    private static final String PANEL_CELL_POS_CLASS_NAME = "com.dannyandson.tinyredstone.blocks.PanelCellPos";
    private static final String REDSTONE_LINK_PROVIDER_CLASS_NAME = "com.dfined.minecraft.create.integration.RedstoneLinkProvider";

    // ==================== Reflection helper ====================

    /**
     * Find a declared method on the given class or any of its superclasses.
     * Uses getDeclaredMethod + setAccessible to avoid interface resolution.
     */
    private static Method findDeclaredMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                Method m = current.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    // ==================== Cell lookup ====================

    public static Object findCell(Level level, BlockPos pos, int cellIndex) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        if (!PANEL_TILE_CLASS_NAME.equals(be.getClass().getName())) return null;

        try {
            Method getCellPositions = findDeclaredMethod(be.getClass(), "getCellPositions");
            if (getCellPositions == null) return null;
            @SuppressWarnings("unchecked")
            java.util.List<Object> cellPositions = (java.util.List<Object>) getCellPositions.invoke(be);
            if (cellPositions == null) return null;

            for (Object cellPos : cellPositions) {
                if (cellPos == null || !PANEL_CELL_POS_CLASS_NAME.equals(cellPos.getClass().getName())) continue;

                Method getIndex = findDeclaredMethod(cellPos.getClass(), "getIndex");
                if (getIndex == null) continue;
                int idx = (int) getIndex.invoke(cellPos);
                if (idx != cellIndex) continue;

                Method getCell = findDeclaredMethod(cellPos.getClass(), "getIPanelCell");
                if (getCell == null) continue;
                Object cell = getCell.invoke(cellPos);
                if (cell != null && TINY_REDSTONE_LINK_CLASS_NAME.equals(cell.getClass().getName())) {
                    return cell;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ==================== LinkProvider access ====================

    private static Object getLinkProvider(Object cell) {
        if (cell == null) return null;
        try {
            java.lang.reflect.Field providerField = cell.getClass().getDeclaredField("linkProvider");
            providerField.setAccessible(true);
            return providerField.get(cell);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== Frequency read ====================

    public static ItemStack getFreq1(Object cell) {
        Object provider = getLinkProvider(cell);
        if (provider == null) return ItemStack.EMPTY;
        try {
            Method method = findDeclaredMethod(provider.getClass(), "getFreq1");
            if (method == null) return ItemStack.EMPTY;
            return (ItemStack) method.invoke(provider);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    public static ItemStack getFreq2(Object cell) {
        Object provider = getLinkProvider(cell);
        if (provider == null) return ItemStack.EMPTY;
        try {
            Method method = findDeclaredMethod(provider.getClass(), "getFreq2");
            if (method == null) return ItemStack.EMPTY;
            return (ItemStack) method.invoke(provider);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    // ==================== Transmitter read ====================

    public static boolean isTransmitter(Object cell) {
        Object provider = getLinkProvider(cell);
        if (provider == null) return true;
        try {
            Method method = findDeclaredMethod(provider.getClass(), "isTransmitter");
            if (method == null) return true;
            return (boolean) method.invoke(provider);
        } catch (Exception e) {
            return true;
        }
    }

    // ==================== Frequency write ====================

    public static boolean updateFrequencies(Object cell, ItemStack freq1, ItemStack freq2) {
        if (cell == null) return false;
        try {
            Method method = findDeclaredMethod(cell.getClass(), "updateFrequencies", ItemStack.class, ItemStack.class);
            if (method == null) return false;
            method.invoke(cell, freq1, freq2);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== Transmitter write ====================

    public static boolean updateTransmitter(Object cell, boolean transmitter) {
        if (cell == null) return false;
        try {
            Method method = findDeclaredMethod(cell.getClass(), "updateTransmitter", boolean.class);
            if (method == null) return false;
            method.invoke(cell, transmitter);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== PanelTile sync ====================

    public static void flagSync(BlockEntity be) {
        if (be == null) return;
        if (!PANEL_TILE_CLASS_NAME.equals(be.getClass().getName())) return;
        try {
            Method method = findDeclaredMethod(be.getClass(), "flagSync");
            if (method != null) method.invoke(be);
        } catch (Exception ignored) {}
    }

    // ==================== Utility ====================

    public static boolean isTinyCreateRedstoneLinkScreen(String className) {
        return "com.dfined.minecraft.create.gui.RedstoneLinkGUI".equals(className);
    }
}