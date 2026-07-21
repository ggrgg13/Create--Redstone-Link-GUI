package com.ggrgg.createredstonelinkgui.compat.jei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.ggrgg.createredstonelinkgui.client.screen.RedstoneLinkConfigScreen;
import com.ggrgg.createredstonelinkgui.client.screen.TinyRedstoneLinkConfigScreen;
import com.ggrgg.createredstonelinkgui.client.screen.VectorThrusterLinkConfigScreen;
import com.ggrgg.createredstonelinkgui.client.screen.VoidLinkConfigScreen;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public class AddonJEIPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_ID = ResourceLocation.fromNamespaceAndPath("createredstonelinkgui", "jei_plugin");

    /**
     * Exact ordering matching maze.frequency.init.FrequencyModItems.SYMBOL_NAMES,
     * minus symbol_empty which is already visible via creative tab.
     */
    private static final List<String> SYMBOL_NAMES = Arrays.asList(
        "symbol_1", "symbol_2", "symbol_3", "symbol_4", "symbol_5",
        "symbol_6", "symbol_7", "symbol_8", "symbol_9", "symbol_0",
        "symbol_a", "symbol_b", "symbol_c", "symbol_d", "symbol_e",
        "symbol_f", "symbol_g", "symbol_h", "symbol_i", "symbol_j",
        "symbol_k", "symbol_l", "symbol_m", "symbol_n", "symbol_o",
        "symbol_p", "symbol_q", "symbol_r", "symbol_s", "symbol_t",
        "symbol_u", "symbol_v", "symbol_w", "symbol_x", "symbol_y",
        "symbol_z",
        "symbol_a_small", "symbol_b_small", "symbol_c_small", "symbol_d_small", "symbol_e_small",
        "symbol_f_small", "symbol_g_small", "symbol_h_small", "symbol_i_small", "symbol_j_small",
        "symbol_k_small", "symbol_l_small", "symbol_m_small", "symbol_n_small", "symbol_o_small",
        "symbol_p_small", "symbol_q_small", "symbol_r_small", "symbol_s_small", "symbol_t_small",
        "symbol_u_small", "symbol_v_small", "symbol_w_small", "symbol_x_small", "symbol_y_small",
        "symbol_z_small",
        "symbol_up_arrow", "symbol_down_arrow", "symbol_left_arrow", "symbol_right_arrow",
        "symbol_darrow_up", "symbol_darrow_down", "symbol_darrow_left", "symbol_darrow_right",
        "symbol_skull",
        "symbol_creeperhead"
    );

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(RedstoneLinkConfigScreen.class, new RedstoneLinkGhostHandler());
        registration.addGhostIngredientHandler(VoidLinkConfigScreen.class, new VoidLinkGhostHandler());
        registration.addGhostIngredientHandler(TinyRedstoneLinkConfigScreen.class, new TinyRedstoneLinkGhostHandler());
        registration.addGhostIngredientHandler(VectorThrusterLinkConfigScreen.class, new VectorThrusterLinkGhostHandler());
        registration.addGenericGuiContainerHandler(RedstoneLinkConfigScreen.class, new IGuiContainerHandler<RedstoneLinkConfigScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(RedstoneLinkConfigScreen screen) {
                List<Rect2i> areas = new ArrayList<>();
                if (screen.blockPreviewBounds != null) areas.add(screen.blockPreviewBounds);
                if (screen.presetPanelBounds != null) areas.add(screen.presetPanelBounds);
                return areas;
            }
        });
        registration.addGenericGuiContainerHandler(VoidLinkConfigScreen.class, new IGuiContainerHandler<VoidLinkConfigScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(VoidLinkConfigScreen screen) {
                List<Rect2i> areas = new ArrayList<>();
                if (screen.blockPreviewBounds != null) areas.add(screen.blockPreviewBounds);
                if (screen.presetPanelBounds != null) areas.add(screen.presetPanelBounds);
                return areas;
            }
        });
        registration.addGenericGuiContainerHandler(TinyRedstoneLinkConfigScreen.class, new IGuiContainerHandler<TinyRedstoneLinkConfigScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(TinyRedstoneLinkConfigScreen screen) {
                List<Rect2i> areas = new ArrayList<>();
                if (screen.blockPreviewBounds != null) areas.add(screen.blockPreviewBounds);
                if (screen.presetPanelBounds != null) areas.add(screen.presetPanelBounds);
                return areas;
            }
        });
        registration.addGenericGuiContainerHandler(VectorThrusterLinkConfigScreen.class, new IGuiContainerHandler<VectorThrusterLinkConfigScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(VectorThrusterLinkConfigScreen screen) {
                List<Rect2i> areas = new ArrayList<>();
                if (screen.blockPreviewBounds != null) areas.add(screen.blockPreviewBounds);
                if (screen.presetPanelBounds != null) areas.add(screen.presetPanelBounds);
                return areas;
            }
        });
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        List<ItemStack> symbolStacks = new ArrayList<>();
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            if (!id.getNamespace().equals("frequency")) continue;
            String path = id.getPath();
            if (!path.startsWith("symbol_")) continue;
            if (path.equals("symbol_frame")) continue;
            if (path.equals("symbol_empty")) continue;

            symbolStacks.add(new ItemStack(entry.getValue()));
        }
        // Sort into the frequency mod's exact order
        symbolStacks.sort(Comparator.comparingInt(s -> {
            String p = s.getItem().builtInRegistryHolder().key().location().getPath();
            int idx = SYMBOL_NAMES.indexOf(p);
            return idx >= 0 ? idx : Integer.MAX_VALUE;
        }));
        if (!symbolStacks.isEmpty()) {
            jeiRuntime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, symbolStacks);
        }
    }

    private static class TinyRedstoneLinkGhostHandler implements IGhostIngredientHandler<TinyRedstoneLinkConfigScreen> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(TinyRedstoneLinkConfigScreen screen, ITypedIngredient<I> typed, boolean start) {
            var stack = typed.getIngredient(VanillaTypes.ITEM_STACK);
            if (stack.isEmpty()) return List.of();
            ItemStack item = stack.get();
            List<Target<I>> targets = new ArrayList<>(10);
            // Frequency slots
            targets.add(new Target<>() {
                @Override public Rect2i getArea() { return screen.slot1Bounds; }
                @Override public void accept(I ing) { screen.updateFrequencySlot(0, item); }
            });
            targets.add(new Target<>() {
                @Override public Rect2i getArea() { return screen.slot2Bounds; }
                @Override public void accept(I ing) { screen.updateFrequencySlot(1, item); }
            });
            // Preset slots
            if (screen.presetPanel != null) {
                for (int row = 0; row < 4; row++) {
                    for (int col = 0; col < 2; col++) {
                        final int r = row;
                        final int c = col;
                        Rect2i bounds = screen.presetPanel.getSlotBounds(row, col);
                        if (bounds != null) {
                            targets.add(new Target<>() {
                                @Override public Rect2i getArea() { return bounds; }
                                @Override public void accept(I ing) {
                                    screen.presetPanel.getPresetData().setStack(r, c, item);
                                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                                        new com.ggrgg.createredstonelinkgui.common.network.PresetSlotUpdatePayload(r, c, item));
                                }
                            });
                        }
                    }
                }
            }
            return targets;
        }
        @Override public void onComplete() {}
    }

    private static class VoidLinkGhostHandler implements IGhostIngredientHandler<VoidLinkConfigScreen> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(VoidLinkConfigScreen screen, ITypedIngredient<I> typed, boolean start) {
            var stack = typed.getIngredient(VanillaTypes.ITEM_STACK);
            if (stack.isEmpty()) return List.of();
            ItemStack item = stack.get();
            List<Target<I>> targets = new ArrayList<>(10);
            // Frequency slots
            targets.add(new Target<>() {
                @Override public Rect2i getArea() { return screen.slot1Bounds; }
                @Override public void accept(I ing) { screen.updateFrequencySlot(0, item); }
            });
            targets.add(new Target<>() {
                @Override public Rect2i getArea() { return screen.slot2Bounds; }
                @Override public void accept(I ing) { screen.updateFrequencySlot(1, item); }
            });
            // Preset slots
            if (screen.presetPanel != null) {
                for (int row = 0; row < 4; row++) {
                    for (int col = 0; col < 2; col++) {
                        final int r = row;
                        final int c = col;
                        Rect2i bounds = screen.presetPanel.getSlotBounds(row, col);
                        if (bounds != null) {
                            targets.add(new Target<>() {
                                @Override public Rect2i getArea() { return bounds; }
                                @Override public void accept(I ing) {
                                    screen.presetPanel.getPresetData().setStack(r, c, item);
                                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                                        new com.ggrgg.createredstonelinkgui.common.network.PresetSlotUpdatePayload(r, c, item));
                                }
                            });
                        }
                    }
                }
            }
            return targets;
        }
        @Override public void onComplete() {}
    }

    private static class VectorThrusterLinkGhostHandler implements IGhostIngredientHandler<VectorThrusterLinkConfigScreen> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(VectorThrusterLinkConfigScreen screen, ITypedIngredient<I> typed, boolean start) {
            var stack = typed.getIngredient(VanillaTypes.ITEM_STACK);
            if (stack.isEmpty()) return List.of();
            ItemStack item = stack.get();
            List<Target<I>> targets = new ArrayList<>(10);
            // Frequency slots
            targets.add(new Target<>() {
                @Override public Rect2i getArea() { return screen.slot1Bounds; }
                @Override public void accept(I ing) { screen.updateFrequencySlot(0, item); }
            });
            targets.add(new Target<>() {
                @Override public Rect2i getArea() { return screen.slot2Bounds; }
                @Override public void accept(I ing) { screen.updateFrequencySlot(1, item); }
            });
            // Preset slots
            if (screen.presetPanel != null) {
                for (int row = 0; row < 4; row++) {
                    for (int col = 0; col < 2; col++) {
                        final int r = row;
                        final int c = col;
                        Rect2i bounds = screen.presetPanel.getSlotBounds(row, col);
                        if (bounds != null) {
                            targets.add(new Target<>() {
                                @Override public Rect2i getArea() { return bounds; }
                                @Override public void accept(I ing) {
                                    screen.presetPanel.getPresetData().setStack(r, c, item);
                                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                                        new com.ggrgg.createredstonelinkgui.common.network.PresetSlotUpdatePayload(r, c, item));
                                }
                            });
                        }
                    }
                }
            }
            return targets;
        }
        @Override public void onComplete() {}
    }

    private static class RedstoneLinkGhostHandler implements IGhostIngredientHandler<RedstoneLinkConfigScreen> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(RedstoneLinkConfigScreen screen, ITypedIngredient<I> typed, boolean start) {
            var stack = typed.getIngredient(VanillaTypes.ITEM_STACK);
            if (stack.isEmpty()) return List.of();
            ItemStack item = stack.get();
            List<Target<I>> targets = new ArrayList<>(10);
            // Frequency slots
            targets.add(new Target<>() {
                @Override public Rect2i getArea() { return screen.slot1Bounds; }
                @Override public void accept(I ing) { screen.updateFrequencySlot(0, item); }
            });
            targets.add(new Target<>() {
                @Override public Rect2i getArea() { return screen.slot2Bounds; }
                @Override public void accept(I ing) { screen.updateFrequencySlot(1, item); }
            });
            // Preset slots
            if (screen.presetPanel != null) {
                for (int row = 0; row < 4; row++) {
                    for (int col = 0; col < 2; col++) {
                        final int r = row;
                        final int c = col;
                        Rect2i bounds = screen.presetPanel.getSlotBounds(row, col);
                        if (bounds != null) {
                            targets.add(new Target<>() {
                                @Override public Rect2i getArea() { return bounds; }
                                @Override public void accept(I ing) {
                                    screen.presetPanel.getPresetData().setStack(r, c, item);
                                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                                        new com.ggrgg.createredstonelinkgui.common.network.PresetSlotUpdatePayload(r, c, item));
                                }
                            });
                        }
                    }
                }
            }
            return targets;
        }
        @Override public void onComplete() {}
    }
}