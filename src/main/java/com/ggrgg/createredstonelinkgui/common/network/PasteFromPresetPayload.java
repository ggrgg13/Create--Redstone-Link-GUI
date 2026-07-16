package com.ggrgg.createredstonelinkgui.common.network;

import com.ggrgg.createredstonelinkgui.common.TinyRedstoneCreateCompatibility;
import com.ggrgg.createredstonelinkgui.common.preset.FrequencyPresetHelper;
import com.ggrgg.createredstonelinkgui.common.preset.FrequencyPresetData;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: Paste preset frequencies into the current link.
 * Supports both regular LinkBehaviour links and TinyRedstoneLink cells.
 *
 * <p>When {@code cellIndex >= 0}, writes frequencies to the TinyRedstoneLink cell
 * via reflection. Otherwise uses the existing ClipboardCloneable path.
 */
public record PasteFromPresetPayload(BlockPos pos, int presetIndex, int cellIndex) implements CustomPacketPayload {

    public static final Type<PasteFromPresetPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("createredstonelinkgui", "paste_from_preset")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PasteFromPresetPayload> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, PasteFromPresetPayload::pos,
        ByteBufCodecs.INT, PasteFromPresetPayload::presetIndex,
        ByteBufCodecs.INT, PasteFromPresetPayload::cellIndex,
        PasteFromPresetPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(PasteFromPresetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            Level level = player.level();
            BlockPos pos = payload.pos();

            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > 64.0) return;

            if (payload.cellIndex >= 0) {
                // TinyRedstoneLink path
                FrequencyPresetData data = FrequencyPresetData.get(player);
                net.minecraft.nbt.CompoundTag presetTag = data.getAsTag(payload.presetIndex(), level.registryAccess());
                if (presetTag.isEmpty()) return;

                ItemStack freq1 = ItemStack.parseOptional(level.registryAccess(), presetTag.getCompound("First"));
                ItemStack freq2 = ItemStack.parseOptional(level.registryAccess(), presetTag.getCompound("Last"));

                Object cell = TinyRedstoneCreateCompatibility.findCell(level, pos, payload.cellIndex());
                if (cell == null) return;

                TinyRedstoneCreateCompatibility.updateFrequencies(cell, freq1, freq2);

                var be = level.getBlockEntity(pos);
                TinyRedstoneCreateCompatibility.flagSync(be);
                if (be != null) {
                    be.setChanged();
                    level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
                }
            } else {
                // Regular LinkBehaviour path (existing behaviour)
                FrequencyPresetHelper.pasteToLink(player, payload.pos, payload.presetIndex);
            }
        });
    }
}