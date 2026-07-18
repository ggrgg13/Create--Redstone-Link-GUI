package com.ggrgg.createredstonelinkgui.common.network;

import com.ggrgg.createredstonelinkgui.CreateRedstoneLinkGUI;
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
import net.minecraft.world.level.block.entity.BlockEntity;
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

            CreateRedstoneLinkGUI.LOGGER.info("TinyLinkFreqUpdatePayload: Received (pos={}, cellIndex={}, transmitter={}, freq1={}, freq2={})",
                pos, payload.cellIndex(), payload.transmitter(), payload.freq1(), payload.freq2());

            // Distance check
            double distSq = player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
            if (distSq > 64.0) {
                CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkFreqUpdatePayload: Player too far from pos {} (distSq={})", pos, distSq);
                return;
            }
            CreateRedstoneLinkGUI.LOGGER.info("TinyLinkFreqUpdatePayload: Distance check passed (distSq={})", distSq);

            // Find cell
            Object cell = TinyRedstoneCreateCompatibility.findCell(level, pos, payload.cellIndex());
            if (cell == null) {
                // Debug: check what BlockEntity is at the position
                BlockEntity be = level.getBlockEntity(pos);
                CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkFreqUpdatePayload: findCell returned null! pos={}, cellIndex={}, BlockEntity={}",
                    pos, payload.cellIndex(), be != null ? be.getClass().getName() : "null");
                return;
            }
            CreateRedstoneLinkGUI.LOGGER.info("TinyLinkFreqUpdatePayload: Cell found: {}", cell.getClass().getName());

            // Update frequencies
            boolean freqUpdated = TinyRedstoneCreateCompatibility.updateFrequencies(cell, payload.freq1(), payload.freq2());
            CreateRedstoneLinkGUI.LOGGER.info("TinyLinkFreqUpdatePayload: updateFrequencies returned {}", freqUpdated);

            // Update transmitter
            boolean txUpdated = TinyRedstoneCreateCompatibility.updateTransmitter(cell, payload.transmitter());
            CreateRedstoneLinkGUI.LOGGER.info("TinyLinkFreqUpdatePayload: updateTransmitter returned {}", txUpdated);

            // Sync
            var be = level.getBlockEntity(pos);
            if (be != null) {
                CreateRedstoneLinkGUI.LOGGER.info("TinyLinkFreqUpdatePayload: Syncing BlockEntity {}", be.getClass().getName());
                TinyRedstoneCreateCompatibility.flagSync(be);
                be.setChanged();
                level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
                CreateRedstoneLinkGUI.LOGGER.info("TinyLinkFreqUpdatePayload: BlockEntity synced");
            } else {
                CreateRedstoneLinkGUI.LOGGER.warn("TinyLinkFreqUpdatePayload: No BlockEntity at pos {}", pos);
            }

            CreateRedstoneLinkGUI.LOGGER.info("TinyLinkFreqUpdatePayload: Update complete");
        });
    }
}