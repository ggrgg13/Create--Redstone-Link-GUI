package com.ggrgg.createredstonelinkgui.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Central reflection helper for TinyCreate / TinyRedstone mod compatibility.
 * All reflection into TinyCreate's {@code TinyRedstoneLink} and TinyRedstone's
 * {@code PanelCellPos} / {@code PanelTile} is isolated here.
 *
 * <p>This avoids scattered try/catch blocks and lets us fail gracefully
 * (returning null / false) when the mods are not present.
 */
public class TinyRedstoneCreateCompatibility {

    private static Class<?> panelTileClass;
    private static Class<?> panelCellPosClass;
    private static Class<?> tinyRedstoneLinkClass;

    private static boolean resolved = false;

    /**
     * Attempt to resolve all TinyRedstone/TinyCreate classes reflectively.
     * Safe to call multiple times — only attempts once.
     */
    private static void resolveClasses() {
        if (resolved) return;
        resolved = true;
        try {
            panelTileClass = Class.forName("com.dannyandson.tinyredstone.blocks.PanelTile");
            panelCellPosClass = Class.forName("com.dannyandson.tinyredstone.blocks.PanelCellPos");
            tinyRedstoneLinkClass = Class.forName("com.dfined.minecraft.create.TinyRedstoneLink");
        } catch (ClassNotFoundException ignored) {
            // Mods not present — all methods will return null/false
        }
    }

    // ==================== Cell lookup ====================

    /**
     * Find a TinyRedstoneLink cell at the given position and cell index.
     *
     * @return the cell object (instance of TinyRedstoneLink), or null on failure
     */
    public static Object findCell(Level level, BlockPos pos, int cellIndex) {
        resolveClasses();
        if (panelTileClass == null || panelCellPosClass == null || tinyRedstoneLinkClass == null) return null;

        try {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null || !panelTileClass.isInstance(be)) return null;

            java.lang.reflect.Method fromIdx = panelCellPosClass.getMethod("fromIndex", panelTileClass, int.class);
            Object cellPos = fromIdx.invoke(null, be, cellIndex);
            Object cell = cellPos.getClass().getMethod("getIPanelCell").invoke(cellPos);

            if (cell != null && tinyRedstoneLinkClass.isInstance(cell)) {
                return cell;
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ==================== Frequency read ====================

    public static ItemStack getFreq1(Object cell) {
        if (cell == null) return ItemStack.EMPTY;
        try {
            java.lang.reflect.Method method = cell.getClass().getMethod("getFreq1");
            return (ItemStack) method.invoke(cell);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    public static ItemStack getFreq2(Object cell) {
        if (cell == null) return ItemStack.EMPTY;
        try {
            java.lang.reflect.Method method = cell.getClass().getMethod("getFreq2");
            return (ItemStack) method.invoke(cell);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    // ==================== Frequency write ====================

    public static boolean updateFrequencies(Object cell, ItemStack freq1, ItemStack freq2) {
        if (cell == null) return false;
        try {
            cell.getClass().getMethod("updateFrequencies", ItemStack.class, ItemStack.class)
                .invoke(cell, freq1, freq2);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== Transmitter mode ====================

    public static boolean isTransmitter(Object cell) {
        if (cell == null) return true; // default to transmitter
        try {
            java.lang.reflect.Method method = cell.getClass().getMethod("isTransmitter");
            return (boolean) method.invoke(cell);
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean updateTransmitter(Object cell, boolean transmitter) {
        if (cell == null) return false;
        try {
            cell.getClass().getMethod("updateTransmitter", boolean.class).invoke(cell, transmitter);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== PanelTile sync ====================

    /**
     * Call {@code PanelTile.flagSync()} to persist changes.
     * Should be called after updating frequencies or transmitter mode.
     */
    public static void flagSync(BlockEntity be) {
        if (be == null || panelTileClass == null) return;
        if (!panelTileClass.isInstance(be)) return;
        try {
            panelTileClass.getMethod("flagSync").invoke(be);
        } catch (Exception ignored) {}
    }

    // ==================== Utility ====================

    /**
     * Check if the given class name is TinyCreate's RedstoneLinkGUI screen.
     */
    public static boolean isTinyCreateRedstoneLinkScreen(String className) {
        return "com.dfined.minecraft.create.gui.RedstoneLinkGUI".equals(className);
    }
}