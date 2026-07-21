package com.ggrgg.createredstonelinkgui.compat.propulsion;

import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Optional-dependency-safe helper for Create: Propulsion Simulated's
 * {@code VectorRedstoneLinkBehaviour}.
 *
 * <p>All methods are guarded by a class-load check so the mod compiles and runs
 * normally when CPS is not installed.</p>
 */
public class VectorThrusterHelper {

    private static boolean checked = false;
    private static boolean loaded = false;

    /** The 4 side types from CPS's VectorRedstoneLinkBehaviour. */
    private static BehaviourType<?> WEST_TYPE;
    private static BehaviourType<?> EAST_TYPE;
    private static BehaviourType<?> DOWN_TYPE;
    private static BehaviourType<?> UP_TYPE;

    /** Maps side key strings to their BehaviourType (lazily populated). */
    private static final Map<String, BehaviourType<?>> SIDE_TYPE_MAP = new HashMap<>();

    // Reflection method handles for VectorRedstoneLinkBehaviour instances
    private static Method METHOD_setFrequency;
    private static Method METHOD_getFrequency;
    private static Method METHOD_testHit;
    private static Method METHOD_getStack;

    private static void check() {
        if (checked) return;
        checked = true;
        try {
            // Load the VectorRedstoneLinkBehaviour class to verify CPS is present
            Class<?> behaviourClass = Class.forName(
                    "dev.propulsionteam.propulsionsimulated.content.thruster.vector_thruster.VectorRedstoneLinkBehaviour");

            // Static BehaviourType fields — these are public static final
            WEST_TYPE = (BehaviourType<?>) behaviourClass.getField("WEST_TYPE").get(null);
            EAST_TYPE = (BehaviourType<?>) behaviourClass.getField("EAST_TYPE").get(null);
            DOWN_TYPE = (BehaviourType<?>) behaviourClass.getField("DOWN_TYPE").get(null);
            UP_TYPE = (BehaviourType<?>) behaviourClass.getField("UP_TYPE").get(null);

            SIDE_TYPE_MAP.put("West", WEST_TYPE);
            SIDE_TYPE_MAP.put("East", EAST_TYPE);
            SIDE_TYPE_MAP.put("Down", DOWN_TYPE);
            SIDE_TYPE_MAP.put("Up", UP_TYPE);

            // Public instance methods on VectorRedstoneLinkBehaviour
            METHOD_setFrequency = behaviourClass.getMethod("setFrequency", boolean.class, ItemStack.class);
            METHOD_getFrequency = behaviourClass.getMethod("getFrequency", boolean.class);
            METHOD_testHit = behaviourClass.getMethod("testHit", Boolean.class, Vec3.class);

            // Frequency.getStack() — resolve from METHOD_getFrequency return type
            Class<?> freqClass = METHOD_getFrequency.getReturnType();
            METHOD_getStack = freqClass.getMethod("getStack");

            loaded = true;
        } catch (Throwable ignored) {
            loaded = false;
        }
    }

    // ==================== Public API ====================

    /** @return true if Create: Propulsion Simulated is loaded. */
    public static boolean isLoaded() {
        check();
        return loaded;
    }

    /**
     * @return the VectorRedstoneLinkBehaviour for the given side, or null.
     */
    public static Object getBehaviour(Level level, BlockPos pos, String sideKey) {
        check();
        if (!loaded) return null;
        BehaviourType<?> type = SIDE_TYPE_MAP.get(sideKey);
        if (type == null) return null;
        return BlockEntityBehaviour.get(level, pos, type);
    }

    /**
     * @return true if the block entity at pos has any VectorRedstoneLinkBehaviour
     *         (i.e. is a vector thruster block).
     */
    public static boolean hasAnyBehaviour(Level level, BlockPos pos) {
        check();
        if (!loaded) return false;
        for (String side : new String[]{"West", "East", "Down", "Up"}) {
            if (getBehaviour(level, pos, side) != null) return true;
        }
        return false;
    }

    /**
     * Hit-test all 4 side behaviours and return the first matching side key.
     *
     * @return the side key ("West"/"East"/"Down"/"Up") that was hit, or null.
     */
    public static String hitTest(Level level, BlockPos pos, Vec3 hitLocation) {
        check();
        if (!loaded) return null;
        for (String side : new String[]{"West", "East", "Down", "Up"}) {
            Object behaviour = getBehaviour(level, pos, side);
            if (behaviour == null) continue;
            try {
                // Test both frequency slots on this side (first = true for slot 0, false for slot 1)
                if ((boolean) METHOD_testHit.invoke(behaviour, Boolean.TRUE, hitLocation) ||
                    (boolean) METHOD_testHit.invoke(behaviour, Boolean.FALSE, hitLocation)) {
                    return side;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * Get the frequency ItemStack from the given vector thruster behaviour.
     */
    public static ItemStack getFrequency(Object behaviour, boolean first) {
        check();
        if (!loaded || behaviour == null) return ItemStack.EMPTY;
        try {
            Object freq = METHOD_getFrequency.invoke(behaviour, first);
            if (freq != null && METHOD_getStack != null) {
                return (ItemStack) METHOD_getStack.invoke(freq);
            }
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }

    /**
     * Set the frequency on the given vector thruster behaviour and notify the network.
     */
    public static void setFrequency(Object behaviour, boolean first, ItemStack stack) {
        check();
        if (!loaded || behaviour == null || METHOD_setFrequency == null) return;
        try {
            METHOD_setFrequency.invoke(behaviour, first, stack.copy());
        } catch (Throwable ignored) {}
    }
}