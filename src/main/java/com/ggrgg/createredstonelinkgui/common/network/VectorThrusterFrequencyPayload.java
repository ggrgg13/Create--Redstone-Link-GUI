package com.ggrgg.createredstonelinkgui.common.network;

import com.ggrgg.createredstonelinkgui.compat.propulsion.VectorThrusterHelper;

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

public record VectorThrusterFrequencyPayload(BlockPos pos, ItemStack selectedItem, int slotIndex, String sideKey) implements CustomPacketPayload {

    public static final Type<VectorThrusterFrequencyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createredstonelinkgui", "vector_thruster_frequency"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VectorThrusterFrequencyPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, VectorThrusterFrequencyPayload::pos,
            ItemStack.OPTIONAL_STREAM_CODEC, VectorThrusterFrequencyPayload::selectedItem,
            ByteBufCodecs.INT, VectorThrusterFrequencyPayload::slotIndex,
            ByteBufCodecs.STRING_UTF8, VectorThrusterFrequencyPayload::sideKey,
            VectorThrusterFrequencyPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(VectorThrusterFrequencyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            Level level = player.level();
            BlockPos pos = payload.pos();

            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > 64.0) return;

            if (!VectorThrusterHelper.isLoaded()) return;

            Object behaviour = VectorThrusterHelper.getBehaviour(level, pos, payload.sideKey());
            if (behaviour != null) {
                VectorThrusterHelper.setFrequency(behaviour, payload.slotIndex() == 0, payload.selectedItem());
                var be = level.getBlockEntity(pos);
                if (be != null) {
                    be.setChanged();
                    level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
                }
            }
        });
    }
}