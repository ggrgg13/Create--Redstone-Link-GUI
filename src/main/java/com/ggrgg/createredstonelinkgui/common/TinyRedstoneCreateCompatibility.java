package com.ggrgg.createredstonelinkgui.common;

import java.lang.reflect.Method;

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

    // Cached reflection targets (resolved once)
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
     * Create a {@code RedstoneLinkNetworkHandler.Frequency} from an ItemStack.
     * Create mod classes are server-safe. Result is cached after first call.
     */
    private static Object createFrequency(ItemStack item) {
        try {
            if (freqClass == null) {
                freqClass = Class.forName(FREQ_CLASS_NAME);
                freqOfMethod = freqClass.getMethod("of", ItemStack.class);
            }
            return freqOfMethod.invoke(null, item);
        } catch (Exception e) {
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

    /**
     * Set the {@code dirty} flag on TinyRedstoneLink via field reflection.
     * This triggers updatePowerState() on the next tick for visual updates.
     */
    private static void setCellDirty(Object cell) {
        if (cell == null) return;
        try {
            java.lang.reflect.Field dirtyField = cell.getClass().getDeclaredField("dirty");
            dirtyField.setAccessible(true);
            dirtyField.setBoolean(cell, true);
        } catch (Exception ignored) {}
    }

    // ==================== Frequency write ====================

    public static boolean updateFrequencies(Object cell, ItemStack freq1, ItemStack freq2) {
        Object provider = getLinkProvider(cell);
        if (provider == null) return false;

        try {
            Object f1 = createFrequency(freq1);
            Object f2 = createFrequency(freq2);
            if (f1 == null || f2 == null) return false;

            // Ensure freqClass is resolved (createFrequency caches it)
            if (freqClass == null) return false;
            Method method = findDeclaredMethod(provider.getClass(), "updateFrequencies", freqClass, freqClass);
            if (method == null) return false;

            method.invoke(provider, f1, f2);
            setCellDirty(cell);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== Transmitter write ====================

    /**
     * Force network re-evaluation by calling forceUpdate() on the linkInterface.
     * Needed when toggling to receiver mode so existing transmitters push signal.
     */
    private static void forceNetworkUpdate(Object provider) {
        if (provider == null) return;
        try {
            java.lang.reflect.Field linkInterfaceField = provider.getClass().getDeclaredField("linkInterface");
            linkInterfaceField.setAccessible(true);
            Object linkInterface = linkInterfaceField.get(provider);
            if (linkInterface != null) {
                Method forceUpdate = findDeclaredMethod(linkInterface.getClass(), "forceUpdate");
                if (forceUpdate != null) {
                    forceUpdate.invoke(linkInterface);
                }
            }
        } catch (Exception ignored) {}
    }

    public static boolean updateTransmitter(Object cell, boolean transmitter) {
        Object provider = getLinkProvider(cell);
        if (provider == null) return false;

        try {
            Method method = findDeclaredMethod(provider.getClass(), "setTransmitter", boolean.class);
            if (method == null) return false;

            method.invoke(provider, transmitter);
            setCellDirty(cell);
            forceNetworkUpdate(provider);
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