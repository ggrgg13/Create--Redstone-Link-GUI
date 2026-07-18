package com.ggrgg.createredstonelinkgui.common;

import java.lang.reflect.Method;

import com.ggrgg.createredstonelinkgui.CreateRedstoneLinkGUI;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Central reflection helper for TinyCreate / TinyRedstone mod compatibility.
 *
 * <p>CRITICAL: Never call getMethod()/getDeclaredMethod() on TinyRedstoneLink
 * class, OR iterate its declared methods, OR do anything that touches its class.
 * TinyRedstoneLink.render(PoseStack, ...) forces the JVM to load PoseStack on
 * any method-table access, which throws on the server.
 *
 * <p>Instead, we access the linkProvider field directly (field access doesn't
 * trigger method-table loading), and operate on RedstoneLinkProvider only
 * (which has zero client imports).
 */
public class TinyRedstoneCreateCompatibility {

    private static final String PANEL_TILE_CLASS_NAME = "com.dannyandson.tinyredstone.blocks.PanelTile";
    private static final String TINY_REDSTONE_LINK_CLASS_NAME = "com.dfined.minecraft.create.TinyRedstoneLink";
    private static final String PANEL_CELL_POS_CLASS_NAME = "com.dannyandson.tinyredstone.blocks.PanelCellPos";
    private static final String FREQ_CLASS_NAME = "com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler$Frequency";

    // Cache for reflection targets (resolved once)
    private static Class<?> freqClass = null;
    private static Method freqOfMethod = null;

    // ==================== Reflection helper ====================

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

    /**
     * Resolve {@code RedstoneLinkNetworkHandler.Frequency.of(ItemStack)} once.
     * Create mod classes are server-safe.
     */
    private static Object createFrequency(ItemStack item) {
        try {
            if (freqClass == null) {
                freqClass = Class.forName(FREQ_CLASS_NAME);
                freqOfMethod = freqClass.getMethod("of", ItemStack.class);
            }
            return freqOfMethod.invoke(null, item);
        } catch (Exception e) {
            CreateRedstoneLinkGUI.LOGGER.error("createFrequency failed", e);
            return null;
        }
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
    // ONLY field access on TinyRedstoneLink — no method calls that would trigger
    // class method-table loading (which would force PoseStack resolution).

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
    // These operate on RedstoneLinkProvider ONLY, avoiding any method-table
    // access on TinyRedstoneLink (which would trigger PoseStack loading).

    public static boolean updateFrequencies(Object cell, ItemStack freq1, ItemStack freq2) {
        Object provider = getLinkProvider(cell);
        if (provider == null) return false;

        try {
            // Create Frequency objects from ItemStacks (Create mod class — server-safe)
            Object f1 = createFrequency(freq1);
            Object f2 = createFrequency(freq2);
            if (f1 == null || f2 == null) return false;

            // Resolve Frequency class for method parameter lookup
            Class<?> fCls = Class.forName(FREQ_CLASS_NAME);
            Method method = findDeclaredMethod(provider.getClass(), "updateFrequencies", fCls, fCls);
            if (method == null) return false;

            method.invoke(provider, f1, f2);
            return true;
        } catch (Exception e) {
            CreateRedstoneLinkGUI.LOGGER.error("updateFrequencies failed", e);
            return false;
        }
    }

    // ==================== Transmitter write ====================

    public static boolean updateTransmitter(Object cell, boolean transmitter) {
        Object provider = getLinkProvider(cell);
        if (provider == null) return false;

        try {
            Method method = findDeclaredMethod(provider.getClass(), "setTransmitter", boolean.class);
            if (method == null) return false;

            method.invoke(provider, transmitter);
            return true;
        } catch (Exception e) {
            CreateRedstoneLinkGUI.LOGGER.error("updateTransmitter failed", e);
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