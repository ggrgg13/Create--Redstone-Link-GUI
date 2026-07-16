package com.ggrgg.createredstonelinkgui.common.network;

import com.ggrgg.createredstonelinkgui.common.TinyRedstoneCreateCompatibility;

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
 * Client -> Server: Update frequencies and/or transmitter mode for a TinyRedstoneLink cell.
 * Sent by TinyRedstoneLinkMenu when the player clicks a frequency slot or toggles TX/RX.
 */
public record TinyLinkFreqUpdatePayload(BlockPos pos, int cellIndex, boolean transmitter,
                                         ItemStack freq1, ItemStack freq2) implements CustomPacketPayload {

    public static final Type<TinyLinkFreqUpdatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("createredstonelinkgui", "tiny_link_freq_update")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TinyLinkFreqUpdatePayload> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, TinyLinkFreqUpdatePayload::pos,
        ByteBufCodecs.INT, TinyLinkFreqUpdatePayload::cellIndex,
        ByteBufCodecs.BOOL, TinyLinkFreqUpdatePayload::transmitter,
        ItemStack.OPTIONAL_STREAM_CODEC, TinyLinkFreqUpdatePayload::freq1,
        ItemStack.OPTIONAL_STREAM_CODEC, TinyLinkFreqUpdatePayload::freq2,
        TinyLinkFreqUpdatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(TinyLinkFreqUpdatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            Level level = player.level();
            BlockPos pos = payload.pos();

            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > 64.0) return;

            Object cell = TinyRedstoneCreateCompatibility.findCell(level, pos, payload.cellIndex());
            if (cell == null) return;

            TinyRedstoneCreateCompatibility.updateFrequencies(cell, payload.freq1(), payload.freq2());
            TinyRedstoneCreateCompatibility.updateTransmitter(cell, payload.transmitter());

            var be = level.getBlockEntity(pos);
            TinyRedstoneCreateCompatibility.flagSync(be);
            if (be != null) {
                be.setChanged();
                level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
            }
        });
    }
}