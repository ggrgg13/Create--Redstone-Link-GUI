package com.ggrgg.createredstonelinkgui.common.network;

import com.ggrgg.createredstonelinkgui.common.TinyRedstoneCreateCompatibility;
import com.ggrgg.createredstonelinkgui.common.preset.FrequencyPresetHelper;

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
 * Client -> Server: Copy current link frequencies into a preset slot.
 * Supports both regular LinkBehaviour links and TinyRedstoneLink cells.
 *
 * <p>When {@code cellIndex >= 0}, reads frequencies from the TinyRedstoneLink cell
 * via reflection. Otherwise uses the existing ClipboardCloneable path.
 *
 * <p>Tag keys MUST be {@code "First"/"Last"} to match FrequencyPresetData.
 */
public record CopyToPresetPayload(BlockPos pos, int presetIndex, int cellIndex) implements CustomPacketPayload {

    public static final Type<CopyToPresetPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("createredstonelinkgui", "copy_to_preset")
    );

    /**
     * Stream codec for the payload.
     * Keeps backward compatibility by defaulting cellIndex to -1 (regular link behaviour).
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, CopyToPresetPayload> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, CopyToPresetPayload::pos,
        ByteBufCodecs.INT, CopyToPresetPayload::presetIndex,
        ByteBufCodecs.INT, CopyToPresetPayload::cellIndex,
        CopyToPresetPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(CopyToPresetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            Level level = player.level();
            BlockPos pos = payload.pos();

            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > 64.0) return;

            if (payload.cellIndex >= 0) {
                // TinyRedstoneLink path
                com.ggrgg.createredstonelinkgui.common.preset.FrequencyPresetData data =
                    com.ggrgg.createredstonelinkgui.common.preset.FrequencyPresetData.get(player);

                Object cell = TinyRedstoneCreateCompatibility.findCell(level, pos, payload.cellIndex());
                if (cell == null) return;

                ItemStack freq1 = TinyRedstoneCreateCompatibility.getFreq1(cell);
                ItemStack freq2 = TinyRedstoneCreateCompatibility.getFreq2(cell);

                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                tag.put("First", freq1.saveOptional(level.registryAccess()));
                tag.put("Last", freq2.saveOptional(level.registryAccess()));

                data.setFromTag(payload.presetIndex(), tag, level.registryAccess());
            } else {
                // Regular LinkBehaviour path (existing behaviour)
                FrequencyPresetHelper.copyFromLink(player, payload.pos, payload.presetIndex);
            }
        });
    }
}