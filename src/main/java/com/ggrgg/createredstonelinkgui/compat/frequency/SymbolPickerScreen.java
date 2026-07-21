package com.ggrgg.createredstonelinkgui.compat.frequency;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import com.ggrgg.createredstonelinkgui.common.network.OpenLinkMenuPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A standalone screen for choosing frequency mod symbols.
 * Visually matches maze.frequency.client.gui.SymbolSwapScreen layout and rendering.
 * When a symbol is picked (or Escape is pressed), reopens the original frequency config
 * menu by sending OpenLinkMenuPayload to the server.
 *
 * Reference source (frequency-create-NeoForge-1.21.1):
 *   src/main/java/maze/frequency/client/gui/SymbolSwapScreen.java
 *   src/main/java/maze/frequency/client/gui/DynamicGuiRenderer.java
 */
public class SymbolPickerScreen extends Screen {

    // ==================== Layout constants (direct copy from SymbolSwapScreen) ====================
    private static final int BUTTON_SIZE = 20;
    private static final int SPACING = 2;
    private static final int MAX_ITEMS_PER_ROW = 10;
    private static final int ROW_SPACING = 22;
    private static final int LABEL_OFFSET = 12;
    private static final int CATEGORY_SPACING = 4;
    private static final int CONTENT_PADDING = 8;
    private static final int CHECKBOX_SIZE = 8;
    private static final int CHECKBOX_ROW_HEIGHT = 14;

    // ==================== Atlas constants (direct copy from DynamicGuiRenderer) ====================
    private static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath("frequency", "textures/gui/symbol_swap.png");
    private static final int ATLAS_WIDTH = 64;
    private static final int ATLAS_HEIGHT = 64;
    private static final int CORNER_WIDTH = 3;
    private static final int HEADER_HEIGHT = 16;
    private static final int BOTTOM_HEIGHT = 2;
    private static final int EDGE_WIDTH = 3;
    private static final int EDGE_HEIGHT = 16;
    private static final int HEADER_CENTER_WIDTH = 16;
    private static final int HEADER_CENTER_HEIGHT = 16;
    private static final int BOTTOM_CENTER_WIDTH = 16;
    private static final int BOTTOM_CENTER_HEIGHT = 2;
    private static final int BACKGROUND_TILE_SIZE = 16;
    private static final int SLOT_SIZE = 20;

    // UV coordinates from DynamicGuiRenderer
    private static final int CORNER_TOP_LEFT_U = 0, CORNER_TOP_LEFT_V = 0;
    private static final int HEADER_CENTER_U = 4, HEADER_CENTER_V = 0;
    private static final int CORNER_TOP_RIGHT_U = 21, CORNER_TOP_RIGHT_V = 0;
    private static final int SLOT_U = 25, SLOT_V = 0;
    private static final int EDGE_LEFT_U = 0, EDGE_LEFT_V = 17;
    private static final int BACKGROUND_U = 4, BACKGROUND_V = 17;
    private static final int EDGE_RIGHT_U = 21, EDGE_RIGHT_V = 17;
    private static final int SLOT_HOVER_U = 25, SLOT_HOVER_V = 21;
    private static final int CORNER_BOTTOM_LEFT_U = 0, CORNER_BOTTOM_LEFT_V = 34;
    private static final int BOTTOM_CENTER_U = 4, BOTTOM_CENTER_V = 34;
    private static final int CORNER_BOTTOM_RIGHT_U = 21, CORNER_BOTTOM_RIGHT_V = 34;

    // ==================== Symbol name list (matching FrequencyModItems.SYMBOL_NAMES) ====================
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
        "symbol_creeperhead",
        "symbol_empty"
    );

    // ==================== State ====================
    private final BlockPos blockPos;
    private final int slotIndex;
    private final Consumer<ItemStack> onPick; // callback when a symbol is selected
    private final List<ItemStack> digits = new ArrayList<>();
    private final List<ItemStack> uppercase = new ArrayList<>();
    private final List<ItemStack> lowercase = new ArrayList<>();
    private final List<ItemStack> specials = new ArrayList<>();
    private List<ItemStack> currentLetters;
    private boolean lettersUppercase = true;

    private final boolean[] collapsed = { false, false, true }; // symbols collapsed by default

    // Computed layout
    private int panelX, panelY, panelWidth, panelHeight;
    private int contentWidth, contentHeight;
    private final List<CategoryRow> categoryRows = new ArrayList<>();
    private int[] categoryLabelY = new int[3];
    private int checkboxY;

    private static class CategoryRow {
        List<ItemStack> items = new ArrayList<>();
        int y;
    }

    /**
     * Creates a SymbolPickerScreen that sends a RedstoneLinkFrequencyPayload when a symbol is picked.
     */
    public SymbolPickerScreen(BlockPos blockPos, int slotIndex) {
        this(blockPos, stack -> PacketDistributor.sendToServer(
            new com.ggrgg.createredstonelinkgui.common.network.RedstoneLinkFrequencyPayload(
                blockPos, stack, slotIndex)));
    }

    /**
     * Creates a SymbolPickerScreen with a custom callback for when a symbol is picked.
     * Use this for preset slots to send a PresetSlotUpdatePayload instead.
     */
    public SymbolPickerScreen(BlockPos blockPos, Consumer<ItemStack> onPick) {
        super(Component.translatable("gui.frequency.symbol_swap.title"));
        this.blockPos = blockPos;
        this.slotIndex = -1; // unused when onPick is provided
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        super.init();
        loadSymbols();
        rebuildLayout();
    }

    // ==================== Symbol discovery (BuiltInRegistries, no frequency-mod imports) ====================
    private void loadSymbols() {
        Registry<net.minecraft.world.item.Item> itemRegistry = BuiltInRegistries.ITEM;
        for (String name : SYMBOL_NAMES) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("frequency", name);
            var item = itemRegistry.get(id);
            if (item != null) {
                String path = id.getPath();
                ItemStack stack = new ItemStack(item);
                if (path.matches("symbol_[0-9]")) {
                    digits.add(stack);
                } else if (path.matches("symbol_[a-z]")) {
                    uppercase.add(stack);
                } else if (path.matches("symbol_[a-z]_small")) {
                    lowercase.add(stack);
                } else {
                    specials.add(stack);
                }
            }
        }
        currentLetters = lettersUppercase ? uppercase : lowercase;
    }

    // ==================== Layout (mirrors SymbolSwapScreen.rebuildLayout) ====================
    private void rebuildLayout() {
        int fixedContentWidth = MAX_ITEMS_PER_ROW * (BUTTON_SIZE + SPACING) - SPACING + 28;

        List<List<ItemStack>> catItems = List.of(digits, currentLetters, specials);
        int[] rowsPerCat = new int[3];
        for (int i = 0; i < 3; i++) {
            rowsPerCat[i] = (catItems.get(i).size() + MAX_ITEMS_PER_ROW - 1) / MAX_ITEMS_PER_ROW;
        }

        contentWidth = fixedContentWidth;
        int tempContentHeight = CONTENT_PADDING;
        for (int i = 0; i < 3; i++) {
            tempContentHeight += LABEL_OFFSET;
            if (!collapsed[i]) {
                tempContentHeight += rowsPerCat[i] * ROW_SPACING;
                if (i == 1) tempContentHeight += CHECKBOX_ROW_HEIGHT;
            }
            if (i < 2) tempContentHeight += CATEGORY_SPACING;
        }
        tempContentHeight += CONTENT_PADDING;
        contentHeight = tempContentHeight;

        panelWidth = CORNER_WIDTH * 2 + contentWidth;
        panelHeight = HEADER_HEIGHT + contentHeight + BOTTOM_HEIGHT;

        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;

        categoryRows.clear();
        for (int cat = 0; cat < 3; cat++) {
            if (collapsed[cat]) continue;
            List<ItemStack> items = catItems.get(cat);
            for (int i = 0; i < items.size(); i += MAX_ITEMS_PER_ROW) {
                CategoryRow row = new CategoryRow();
                row.items.addAll(items.subList(i, Math.min(i + MAX_ITEMS_PER_ROW, items.size())));
                categoryRows.add(row);
            }
        }

        int startY = panelY + HEADER_HEIGHT + CONTENT_PADDING + LABEL_OFFSET;
        int rowIdx = 0;
        int currentY = startY;

        for (int cat = 0; cat < 3; cat++) {
            categoryLabelY[cat] = currentY - LABEL_OFFSET;
            int catRows = rowsPerCat[cat];
            if (!collapsed[cat]) {
                if (cat == 1) {
                    checkboxY = currentY;
                    currentY += CHECKBOX_ROW_HEIGHT;
                }
                for (int i = 0; i < catRows; i++) {
                    categoryRows.get(rowIdx + i).y = currentY + i * ROW_SPACING;
                }
                rowIdx += catRows;
                currentY += catRows * ROW_SPACING;
            }
            currentY += CATEGORY_SPACING + LABEL_OFFSET;
        }
    }

    // ==================== Rendering (direct copy of DynamicGuiRenderer + SymbolSwapScreen) ====================
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        renderGuiPanel(graphics);

        int labelX = panelX + 9;

        int iconYoff = (font.lineHeight - 6) / 2;
        for (int cat = 0; cat < 3; cat++) {
            int iconU = collapsed[cat] ? 46 : 51;
            int iconW = collapsed[cat] ? 5 : 6;
            int iconX = collapsed[cat] ? labelX + 1 : labelX;
            graphics.blit(ATLAS, iconX, categoryLabelY[cat] + iconYoff, iconU, 0, iconW, 6, ATLAS_WIDTH, ATLAS_HEIGHT);
            graphics.drawString(font, categoryLabel(cat), labelX + 8, categoryLabelY[cat], 0xF8F8EC, true);
        }

        if (!collapsed[1]) {
            int cbU = lettersUppercase ? 54 : 46;
            graphics.blit(ATLAS, labelX + 10, checkboxY + 1, cbU, 8, CHECKBOX_SIZE, CHECKBOX_SIZE, ATLAS_WIDTH, ATLAS_HEIGHT);
            graphics.drawString(font,
                    Component.translatable(lettersUppercase ? "gui.frequency.capital" : "gui.frequency.small").getString(),
                    labelX + 22, checkboxY + 1, 0xF8F8EC, true);
        }

        for (CategoryRow row : categoryRows) {
            if (row.items.isEmpty()) continue;
            int startX = getRowStartX(row);
            for (int i = 0; i < row.items.size(); i++) {
                int x = startX + i * (BUTTON_SIZE + SPACING);
                renderSlot(graphics, x, row.y);
                graphics.renderItem(row.items.get(i), x + 2, row.y + 2);
            }
        }

        for (CategoryRow row : categoryRows) {
            if (row.items.isEmpty()) continue;
            int startX = getRowStartX(row);
            for (int i = 0; i < row.items.size(); i++) {
                int x = startX + i * (BUTTON_SIZE + SPACING);
                if (mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= row.y && mouseY < row.y + BUTTON_SIZE) {
                    renderSlotHover(graphics, x, row.y);
                    graphics.renderTooltip(font, row.items.get(i), mouseX, mouseY);
                }
            }
        }
    }

    /**
     * Direct copy of DynamicGuiRenderer.renderGui
     */
    private void renderGuiPanel(GuiGraphics graphics) {
        int contentH = contentHeight;

        // Header
        graphics.blit(ATLAS, panelX, panelY, CORNER_TOP_LEFT_U, CORNER_TOP_LEFT_V, CORNER_WIDTH, HEADER_HEIGHT, ATLAS_WIDTH, ATLAS_HEIGHT);
        int centerX = panelX + CORNER_WIDTH;
        int centerW = panelWidth - CORNER_WIDTH * 2;
        graphics.blit(ATLAS, centerX, panelY, centerW, HEADER_HEIGHT, HEADER_CENTER_U, HEADER_CENTER_V, HEADER_CENTER_WIDTH, HEADER_CENTER_HEIGHT, ATLAS_WIDTH, ATLAS_HEIGHT);
        graphics.blit(ATLAS, panelX + panelWidth - CORNER_WIDTH, panelY, CORNER_TOP_RIGHT_U, CORNER_TOP_RIGHT_V, CORNER_WIDTH, HEADER_HEIGHT, ATLAS_WIDTH, ATLAS_HEIGHT);

        // Content
        int contentX = panelX;
        int contentTopY = panelY + HEADER_HEIGHT;
        graphics.blit(ATLAS, contentX, contentTopY, EDGE_WIDTH, contentH, EDGE_LEFT_U, EDGE_LEFT_V, EDGE_WIDTH, EDGE_HEIGHT, ATLAS_WIDTH, ATLAS_HEIGHT);
        graphics.blit(ATLAS, contentX + panelWidth - EDGE_WIDTH, contentTopY, EDGE_WIDTH, contentH, EDGE_RIGHT_U, EDGE_RIGHT_V, EDGE_WIDTH, EDGE_HEIGHT, ATLAS_WIDTH, ATLAS_HEIGHT);
        int bgContentWidth = panelWidth - EDGE_WIDTH * 2;
        int bgContentX = contentX + EDGE_WIDTH;
        for (int j = 0; j < contentH; j += BACKGROUND_TILE_SIZE) {
            int drawH = Math.min(BACKGROUND_TILE_SIZE, contentH - j);
            for (int i = 0; i < bgContentWidth; i += BACKGROUND_TILE_SIZE) {
                int drawW = Math.min(BACKGROUND_TILE_SIZE, bgContentWidth - i);
                graphics.blit(ATLAS, bgContentX + i, contentTopY + j, drawW, drawH, BACKGROUND_U, BACKGROUND_V, drawW, drawH, ATLAS_WIDTH, ATLAS_HEIGHT);
            }
        }

        // Bottom
        int bottomY = contentTopY + contentH;
        graphics.blit(ATLAS, panelX, bottomY, CORNER_BOTTOM_LEFT_U, CORNER_BOTTOM_LEFT_V, CORNER_WIDTH, BOTTOM_HEIGHT, ATLAS_WIDTH, ATLAS_HEIGHT);
        graphics.blit(ATLAS, centerX, bottomY, centerW, BOTTOM_HEIGHT, BOTTOM_CENTER_U, BOTTOM_CENTER_V, BOTTOM_CENTER_WIDTH, BOTTOM_CENTER_HEIGHT, ATLAS_WIDTH, ATLAS_HEIGHT);
        graphics.blit(ATLAS, panelX + panelWidth - CORNER_WIDTH, bottomY, CORNER_BOTTOM_RIGHT_U, CORNER_BOTTOM_RIGHT_V, CORNER_WIDTH, BOTTOM_HEIGHT, ATLAS_WIDTH, ATLAS_HEIGHT);
    }

    // Direct copy of DynamicGuiRenderer.renderSlot
    private static void renderSlot(GuiGraphics graphics, int x, int y) {
        graphics.blit(ATLAS, x, y, SLOT_U, SLOT_V, SLOT_SIZE, SLOT_SIZE, ATLAS_WIDTH, ATLAS_HEIGHT);
    }

    // Direct copy of DynamicGuiRenderer.renderSlotHover
    private static void renderSlotHover(GuiGraphics graphics, int x, int y) {
        graphics.blit(ATLAS, x, y, SLOT_HOVER_U, SLOT_HOVER_V, SLOT_SIZE, SLOT_SIZE, ATLAS_WIDTH, ATLAS_HEIGHT);
    }

    private int getRowStartX(CategoryRow row) {
        int rowWidth = row.items.size() * (BUTTON_SIZE + SPACING) - SPACING;
        return panelX + (panelWidth - rowWidth) / 2;
    }

    private static String categoryLabel(int cat) {
        return Component.translatable(
            cat == 0 ? "gui.frequency.category.digits" :
            cat == 1 ? "gui.frequency.category.letters" :
                       "gui.frequency.category.symbols"
        ).getString();
    }

    // ==================== Input handling ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        int labelX = panelX + 9;

        // Category collapse/expand toggle
        for (int cat = 0; cat < 3; cat++) {
            String label = categoryLabel(cat);
            int textWidth = font.width(label);
            if (mouseX >= labelX - 4 && mouseX < labelX + 8 + textWidth + 4
                    && mouseY >= categoryLabelY[cat] - 2
                    && mouseY < categoryLabelY[cat] + 10) {
                collapsed[cat] = !collapsed[cat];
                rebuildLayout();
                return true;
            }
        }

        // Letter case checkbox
        if (!collapsed[1]) {
            if (mouseX >= labelX + 10 && mouseX < labelX + 10 + CHECKBOX_SIZE
                    && mouseY >= checkboxY + 1 && mouseY < checkboxY + 1 + CHECKBOX_SIZE) {
                lettersUppercase = !lettersUppercase;
                currentLetters = lettersUppercase ? uppercase : lowercase;
                rebuildLayout();
                return true;
            }
        }

        // Symbol selection
        for (CategoryRow row : categoryRows) {
            if (row.items.isEmpty()) continue;
            int startX = getRowStartX(row);
            for (int i = 0; i < row.items.size(); i++) {
                int x = startX + i * (BUTTON_SIZE + SPACING);
                if (mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= row.y && mouseY < row.y + BUTTON_SIZE) {
                    ItemStack picked = row.items.get(i);
                    // Use callback if provided, otherwise send frequency payload
                    if (onPick != null) {
                        onPick.accept(picked);
                    } else {
                        PacketDistributor.sendToServer(
                            new com.ggrgg.createredstonelinkgui.common.network.RedstoneLinkFrequencyPayload(
                                blockPos, picked, slotIndex));
                    }
                    // Close this screen; removed() will reopen the config menu
                    this.onClose();
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void removed() {
        // When this screen is closed (Escape, inventory key, or programmatic close),
        // reopen the original frequency config menu on the server.
        PacketDistributor.sendToServer(new OpenLinkMenuPayload(blockPos, ""));
    }
}