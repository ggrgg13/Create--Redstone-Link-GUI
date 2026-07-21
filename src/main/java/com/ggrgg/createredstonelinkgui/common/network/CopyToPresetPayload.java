package com.ggrgg.createredstonelinkgui.common.network;

import com.ggrgg.createredstonelinkgui.common.TinyRedstoneCreateCompatibility;
import com.ggrgg.createredstonelinkgui.common.preset.FrequencyPresetHelper;
import com.ggrgg.createredstonelinkgui.compat.propulsion.VectorThrusterHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
 * Supports regular LinkBehaviour links, TinyRedstoneLink cells, and
 * Vector Thruster side-specific behaviours.
 */
public record CopyToPresetPayload(BlockPos pos, int presetIndex, int cellIndex, String sideKey) implements CustomPacketPayload {

    public static final Type<CopyToPresetPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("createredstonelinkgui", "copy_to_preset")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CopyToPresetPayload> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, CopyToPresetPayload::pos,
        ByteBufCodecs.INT, CopyToPresetPayload::presetIndex,
        ByteBufCodecs.INT, CopyToPresetPayload::cellIndex,
        ByteBufCodecs.STRING_UTF8, CopyToPresetPayload::sideKey,
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

            // Vector thruster path (sideKey non-empty)
            if (payload.sideKey != null && !payload.sideKey.isEmpty()) {
                if (!VectorThrusterHelper.isLoaded()) return;
                Object behaviour = VectorThrusterHelper.getBehaviour(level, pos, payload.sideKey);
                if (behaviour == null) return;

                ItemStack freq1 = VectorThrusterHelper.getFrequency(behaviour, true);
                ItemStack freq2 = VectorThrusterHelper.getFrequency(behaviour, false);

                CompoundTag tag = new CompoundTag();
                tag.put("First", freq1.saveOptional(level.registryAccess()));
                tag.put("Last", freq2.saveOptional(level.registryAccess()));

                com.ggrgg.createredstonelinkgui.common.preset.FrequencyPresetData data =
                    com.ggrgg.createredstonelinkgui.common.preset.FrequencyPresetData.get(player);
                data.setFromTag(payload.presetIndex(), tag, level.registryAccess());
                return;
            }

            if (payload.cellIndex >= 0) {
                // TinyRedstoneLink path
                com.ggrgg.createredstonelinkgui.common.preset.FrequencyPresetData data =
                    com.ggrgg.createredstonelinkgui.common.preset.FrequencyPresetData.get(player);

                Object cell = TinyRedstoneCreateCompatibility.findCell(level, pos, payload.cellIndex());
                if (cell == null) return;

                ItemStack freq1 = TinyRedstoneCreateCompatibility.getFreq1(cell);
                ItemStack freq2 = TinyRedstoneCreateCompatibility.getFreq2(cell);

                CompoundTag tag = new CompoundTag();
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
