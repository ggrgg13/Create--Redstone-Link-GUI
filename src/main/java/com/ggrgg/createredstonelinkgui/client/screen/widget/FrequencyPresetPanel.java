package com.ggrgg.createredstonelinkgui.client.screen.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.ggrgg.createredstonelinkgui.common.preset.FrequencyPresetData;
import com.ggrgg.createredstonelinkgui.common.network.CopyToPresetPayload;
import com.ggrgg.createredstonelinkgui.common.network.PasteFromPresetPayload;
import com.ggrgg.createredstonelinkgui.compat.frequency.FrequencyItemHelper;

import com.ggrgg.createredstonelinkgui.common.menu.TinyRedstoneLinkMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Panel displayed to the left of the link config screen.
 * Shows rows of preset frequencies with copy/paste buttons.
 * 
 * <p>Background image can be offset independently via BG_OFFSET_X/Y,
 * while buttons and slots remain at their original positions.
 *
 * <h2>Layout Constants</h2>
 * These public constants are the SINGLE SOURCE OF TRUTH for slot positions.
 * Both RedstoneLinkMenu and VoidLinkMenu read from these to position
 * their GhostRecipeSlots at the same coordinates where items render.
 * If you change these, the menus auto-adjust — no manual sync needed.
 */
public class FrequencyPresetPanel {

    // ==================== 布局常量（public — 供菜单按相同坐标定位） ====================
    /** Slot X position within the panel (relative to panelX). */
    public static final int SLOT_X = 6;
    /** Slot Y offset within each row (relative to row top). */
    public static final int SLOT_Y_OFFSET = 8;
    /** Distance from slot 0 left edge to slot 1 left edge. */
    public static final int SLOT_SPACING_X = 26;
    /** Standard inventory slot size. */
    public static final int SLOT_SIZE = 16;
    /** Height of the header segment. */
    public static final int HEADER_H = 36;
    /** Height of each row segment. */
    public static final int ROW_H = 31;
    /** Panel X offset from leftPos: panelX = leftPos + PANEL_OFFSET = leftPos - 71. */
    public static final int PANEL_OFFSET = 40 - 111; // = -71

    private static final int COPY_BTN_X = 55;
    private static final int PASTE_BTN_X = 75;
    private static final int BTN_Y_OFFSET = 7;
    private static final int BTN_SIZE = 18;

    // ==================== 背景偏移（只移动背景图片，不移动按钮/槽位） ====================
    private static final int BG_OFFSET_X = -7;
    private static final int BG_OFFSET_Y = 0;

    // ==================== 纹理UV坐标 — 面板分段 ====================
    private static final int HEADER_U = 0;
    private static final int HEADER_V = 0;
    private static final int HEADER_W = 111;

    private static final int ROW_U = 0;
    private static final int ROW_V = 39;
    private static final int ROW_W = 112;

    private static final int FOOTER_U = 0;
    private static final int FOOTER_V = 73;
    private static final int FOOTER_W = 111;
    private static final int FOOTER_H = 12;

    // ==================== 纹理UV坐标 — 槽位/按钮 ====================
    private static final int SLOT0_UV_U = 13;
    private static final int SLOT0_UV_V = 47;
    private static final int SLOT1_UV_U = 39;
    private static final int SLOT1_UV_V = 47;
    private static final int SLOT_UV_W = 16;
    private static final int SLOT_UV_H = 16;

    private static final int COPY_BTN_UV_U = 62;
    private static final int COPY_BTN_UV_V = 46;
    private static final int COPY_BTN_UV_W = 18;
    private static final int COPY_BTN_UV_H = 18;

    private static final int PASTE_BTN_UV_U = 82;
    private static final int PASTE_BTN_UV_V = 46;
    private static final int PASTE_BTN_UV_W = 18;
    private static final int PASTE_BTN_UV_H = 18;

    private static final int COPY_BTN_HOVER_UV_U = 114;
    private static final int COPY_BTN_HOVER_UV_V = 46;
    private static final int COPY_BTN_HOVER_UV_W = 18;
    private static final int COPY_BTN_HOVER_UV_H = 18;

    private static final int PASTE_BTN_HOVER_UV_U = 134;
    private static final int PASTE_BTN_HOVER_UV_V = 46;
    private static final int PASTE_BTN_HOVER_UV_W = 18;
    private static final int PASTE_BTN_HOVER_UV_H = 18;

    // ==================== 面板尺寸 ====================
    public static final int PANEL_WIDTH = 111;
    public static final int PANEL_HEIGHT = HEADER_H + FrequencyPresetData.PRESET_COUNT * ROW_H + FOOTER_H;

    // ==================== 纹理资源 ====================
    private static final ResourceLocation PANEL_TEXTURE =
        ResourceLocation.parse("createredstonelinkgui:textures/frequency_preset_panel.png");

    // ==================== 纹理总尺寸 ====================
    private static final int TEX_WIDTH = 256;
    private static final int TEX_HEIGHT = 256;

    // ==================== 纹理可用性 ====================
    private static boolean isTextureAvailable() {
        return true;
    }

    // ==================== 实例字段 ====================
    private final FrequencyPresetData presetData;
    private final BlockPos linkPos;
    private final int panelX;
    private final int panelY;
    private final int cellIndex; // -1 for regular links, >= 0 for TinyRedstoneLink
    private final String sideKey; // "" for regular links/VoidLink, non-empty for vector thruster
    private final List<Rect2i> slotBounds;
    private final List<Rect2i> copyBtnBounds;
    private final List<Rect2i> pasteBtnBounds;
    private final Supplier<Boolean> copyEnabled;

    public FrequencyPresetPanel(int panelX, int panelY, BlockPos linkPos, FrequencyPresetData presetData,
                                Supplier<Boolean> copyEnabled) {
        this(panelX, panelY, linkPos, presetData, copyEnabled, -1, "");
    }

    public FrequencyPresetPanel(int panelX, int panelY, BlockPos linkPos, FrequencyPresetData presetData,
                                Supplier<Boolean> copyEnabled, int cellIndex) {
        this(panelX, panelY, linkPos, presetData, copyEnabled, cellIndex, "");
    }

    public FrequencyPresetPanel(int panelX, int panelY, BlockPos linkPos, FrequencyPresetData presetData,
                                Supplier<Boolean> copyEnabled, int cellIndex, String sideKey) {
        this.panelX = panelX;
        this.panelY = panelY;
        this.linkPos = linkPos;
        this.presetData = presetData;
        this.copyEnabled = copyEnabled;
        this.cellIndex = cellIndex;
        this.sideKey = sideKey;
        this.slotBounds = new ArrayList<>(FrequencyPresetData.PRESET_COUNT * 2);
        this.copyBtnBounds = new ArrayList<>(FrequencyPresetData.PRESET_COUNT);
        this.pasteBtnBounds = new ArrayList<>(FrequencyPresetData.PRESET_COUNT);

        for (int row = 0; row < FrequencyPresetData.PRESET_COUNT; row++) {
            int rowY = panelY + HEADER_H + row * ROW_H;
            slotBounds.add(new Rect2i(panelX + SLOT_X, rowY + SLOT_Y_OFFSET, SLOT_SIZE, SLOT_SIZE));
            slotBounds.add(new Rect2i(panelX + SLOT_X + SLOT_SPACING_X, rowY + SLOT_Y_OFFSET, SLOT_SIZE, SLOT_SIZE));
            copyBtnBounds.add(new Rect2i(panelX + COPY_BTN_X, rowY + BTN_Y_OFFSET, BTN_SIZE, BTN_SIZE));
            pasteBtnBounds.add(new Rect2i(panelX + PASTE_BTN_X, rowY + BTN_Y_OFFSET, BTN_SIZE, BTN_SIZE));
        }
    }

    // Cached paste-enabled state per row, updated every render frame
    private final boolean[] pasteEnabledCache = new boolean[FrequencyPresetData.PRESET_COUNT];

    private boolean isCopyGloballyEnabled() {
        return copyEnabled.get();
    }

    private boolean isPasteEnabled(int row) {
        return pasteEnabledCache[row];
    }

    /** Refresh the per-row paste-enabled cache. Called once per render. */
    private void refreshPasteCache() {
        for (int row = 0; row < FrequencyPresetData.PRESET_COUNT; row++) {
            pasteEnabledCache[row] = !presetData.getStack(row, 0).isEmpty()
                                  || !presetData.getStack(row, 1).isEmpty();
        }
    }

    // ==================== 渲染入口 ====================
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderWithTexture(graphics, mouseX, mouseY);
    }

    // ==================== 纹理渲染路径 ====================
    private void renderWithTexture(GuiGraphics graphics, int mouseX, int mouseY) {
        refreshPasteCache();
        Font font = Minecraft.getInstance().font;
        boolean globalCopyEnabled = isCopyGloballyEnabled();

        int bgX = panelX + BG_OFFSET_X;
        int bgY = panelY + BG_OFFSET_Y;

        // 1. 顶部
        graphics.blit(PANEL_TEXTURE, bgX, bgY,
            HEADER_U, HEADER_V, HEADER_W, HEADER_H, TEX_WIDTH, TEX_HEIGHT);

        // 2. 中部（每行）
        for (int row = 0; row < FrequencyPresetData.PRESET_COUNT; row++) {
            int rowY = bgY + HEADER_H + row * ROW_H;
            graphics.blit(PANEL_TEXTURE, bgX, rowY,
                ROW_U, ROW_V, ROW_W, ROW_H, TEX_WIDTH, TEX_HEIGHT);
        }

        // 3. 底部
        int footerY = bgY + HEADER_H + FrequencyPresetData.PRESET_COUNT * ROW_H;
        graphics.blit(PANEL_TEXTURE, bgX, footerY,
            FOOTER_U, FOOTER_V, FOOTER_W, FOOTER_H, TEX_WIDTH, TEX_HEIGHT);

        // 4. 标题
        Component title = Component.translatable("gui.createredstonelinkgui.presets");
        int titleWidth = font.width(title);
        graphics.drawString(font, title, bgX + (PANEL_WIDTH - titleWidth) / 2, bgY + 30, 0xFF70493F, false);

        // 5. 行内容（槽位 + 按钮）
        for (int row = 0; row < FrequencyPresetData.PRESET_COUNT; row++) {
            int rowY = panelY + HEADER_H + row * ROW_H;
            boolean pasteEnabled = isPasteEnabled(row);

            for (int col = 0; col < 2; col++) {
                int slotX = panelX + SLOT_X + col * SLOT_SPACING_X;
                int uvU = (col == 0) ? SLOT0_UV_U : SLOT1_UV_U;
                int uvV = (col == 0) ? SLOT0_UV_V : SLOT1_UV_V;
                graphics.blit(PANEL_TEXTURE, slotX, rowY + SLOT_Y_OFFSET,
                    uvU, uvV, SLOT_UV_W, SLOT_UV_H, TEX_WIDTH, TEX_HEIGHT);
                ItemStack stack = presetData.getStack(row, col);
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, slotX, rowY + SLOT_Y_OFFSET);
                }
            }

            int copyBtnX = panelX + COPY_BTN_X;
            boolean copyHover = globalCopyEnabled && isHovered(mouseX, mouseY, copyBtnX, rowY + BTN_Y_OFFSET, BTN_SIZE, BTN_SIZE);
            graphics.blit(PANEL_TEXTURE, copyBtnX, rowY + BTN_Y_OFFSET,
                copyHover ? COPY_BTN_HOVER_UV_U : COPY_BTN_UV_U,
                copyHover ? COPY_BTN_HOVER_UV_V : COPY_BTN_UV_V,
                COPY_BTN_UV_W, COPY_BTN_UV_H, TEX_WIDTH, TEX_HEIGHT);

            int pasteBtnX = panelX + PASTE_BTN_X;
            boolean pasteHover = pasteEnabled && isHovered(mouseX, mouseY, pasteBtnX, rowY + BTN_Y_OFFSET, BTN_SIZE, BTN_SIZE);
            graphics.blit(PANEL_TEXTURE, pasteBtnX, rowY + BTN_Y_OFFSET,
                pasteHover ? PASTE_BTN_HOVER_UV_U : PASTE_BTN_UV_U,
                pasteHover ? PASTE_BTN_HOVER_UV_V : PASTE_BTN_UV_V,
                PASTE_BTN_UV_W, PASTE_BTN_UV_H, TEX_WIDTH, TEX_HEIGHT);
        }
    }

    // ==================== 工具提示 ====================
    public void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        for (int row = 0; row < FrequencyPresetData.PRESET_COUNT; row++) {
            for (int col = 0; col < 2; col++) {
                Rect2i bounds = slotBounds.get(row * 2 + col);
                if (bounds.contains(mouseX, mouseY)) {
                    ItemStack stack = presetData.getStack(row, col);
                    Component label = Component.translatable(
                        col == 0 ? "gui.createredstonelinkgui.frequency_first"
                                 : "gui.createredstonelinkgui.frequency_second")
                        .withStyle(ChatFormatting.BLUE);
                    if (!stack.isEmpty()) {
                        List<Component> tooltipLines = new ArrayList<>();
                        tooltipLines.add(label);
                        int lineCount = 1;
                        if (FrequencyItemHelper.isFrequencySymbol(stack)) {
                            tooltipLines.add(Component.translatable("gui.createredstonelinkgui.middle_click_swap")
                                    .withStyle(ChatFormatting.GOLD));
                            lineCount = 2;
                        }
                        int yOffset = -20 - (lineCount - 1) * font.lineHeight;
                        graphics.renderTooltip(font, tooltipLines,
                            java.util.Optional.empty(), mouseX, mouseY + yOffset);
                    } else {
                        int yOffset = -20;
                        graphics.renderTooltip(font,
                            java.util.List.of(label), java.util.Optional.empty(), mouseX, mouseY + yOffset);
                    }
                    return;
                }
            }
        }

        for (int row = 0; row < FrequencyPresetData.PRESET_COUNT; row++) {
            Rect2i bounds = copyBtnBounds.get(row);
            if (bounds.contains(mouseX, mouseY)) {
                if (!isCopyGloballyEnabled()) return;
                graphics.renderTooltip(font,
                    Component.translatable("gui.createredstonelinkgui.copy_to_preset", row + 1)
                        .withStyle(ChatFormatting.GREEN),
                    mouseX, mouseY);
                return;
            }
        }

        for (int row = 0; row < FrequencyPresetData.PRESET_COUNT; row++) {
            Rect2i bounds = pasteBtnBounds.get(row);
            if (bounds.contains(mouseX, mouseY)) {
                if (!isPasteEnabled(row)) return;
                graphics.renderTooltip(font,
                    Component.translatable("gui.createredstonelinkgui.paste_from_preset", row + 1)
                        .withStyle(ChatFormatting.GOLD),
                    mouseX, mouseY);
                return;
            }
        }
    }

    // ==================== 鼠标点击 ====================
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        int mx = (int) mouseX;
        int my = (int) mouseY;

        boolean globalCopyEnabled = isCopyGloballyEnabled();

        for (int row = 0; row < FrequencyPresetData.PRESET_COUNT; row++) {
            if (globalCopyEnabled && copyBtnBounds.get(row).contains(mx, my)) {
                PacketDistributor.sendToServer(new CopyToPresetPayload(linkPos, row, cellIndex, sideKey));
                return true;
            }
            if (isPasteEnabled(row) && pasteBtnBounds.get(row).contains(mx, my)) {
                PacketDistributor.sendToServer(new PasteFromPresetPayload(linkPos, row, cellIndex, sideKey));

                // Update local cache for TinyRedstoneLinkMenu so the display updates immediately
                if (cellIndex >= 0) {
                    var mc = Minecraft.getInstance();
                    if (mc.screen instanceof AbstractContainerScreen<?> containerScreen) {
                        var menu = containerScreen.getMenu();
                        if (menu instanceof TinyRedstoneLinkMenu tinyMenu) {
                            ItemStack freq1 = presetData.getStack(row, 0);
                            ItemStack freq2 = presetData.getStack(row, 1);
                            tinyMenu.applyPresetPaste(freq1, freq2);
                        }
                    }
                }

                return true;
            }
        }

        return false;
    }

    // ==================== 辅助绘制方法（回退用） ====================
    private void drawSlotBackground(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + SLOT_SIZE + 1, y + SLOT_SIZE + 1, 0xFF555555);
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF333333);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, String label, boolean hovered, boolean enabled) {
        if (!enabled) {
            graphics.fill(x, y, x + BTN_SIZE, y + BTN_SIZE, 0xFF444444);
            graphics.fill(x + 1, y + 1, x + BTN_SIZE - 1, y + BTN_SIZE - 1, 0xFF333333);
            Font font = Minecraft.getInstance().font;
            graphics.drawString(font, label, x + 4, y + 3, 0xFF888888, false);
            return;
        }
        graphics.fill(x, y, x + BTN_SIZE, y + BTN_SIZE, 0xFF4488EE);
        graphics.fill(x + 1, y + 1, x + BTN_SIZE - 1, y + BTN_SIZE - 1,
            hovered ? 0xFF77BBFF : 0xFF5599FF);
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, label, x + 4, y + 3, 0xFFFFFFFF, false);
    }

    private boolean isHovered(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    // ==================== 边界获取 ====================
    public Rect2i getSlotBounds(int row, int col) {
        int index = row * 2 + col;
        if (index < 0 || index >= slotBounds.size()) return null;
        return slotBounds.get(index);
    }

    /**
     * The full visual bounding box of the panel, including the background
     * texture offset. This is the area where background is rendered.
     */
    public Rect2i getVisualBounds() {
        return new Rect2i(panelX + BG_OFFSET_X, panelY, PANEL_WIDTH - BG_OFFSET_X, PANEL_HEIGHT);
    }

    public Rect2i getBounds() {
        return new Rect2i(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);
    }

    /**
     * The rendered background offset in the X direction (negative = panel extends leftwards).
     */
    public int getBgOffsetX() {
        return BG_OFFSET_X;
    }

    public int getPanelX() { return panelX; }
    public int getPanelY() { return panelY; }
    public FrequencyPresetData getPresetData() { return presetData; }
    public BlockPos getLinkPos() { return linkPos; }
}