package com.ggrgg.createredstonelinkgui.common.network;

import com.ggrgg.createredstonelinkgui.common.TinyRedstoneCreateCompatibility;
import com.ggrgg.createredstonelinkgui.common.menu.TinyRedstoneLinkMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client -> Server: Swap from TinyCreate's RedstoneLinkGUI to our TinyRedstoneLinkConfigScreen.
 * Sent by {@code TinyLinkScreenHandler} when it intercepts the screen opening event.
 *
 * <p>If the frequencies sent from the client are empty (race condition with ghost
 * inventory sync, or client-side reflection failed), the server reads them directly
 * from the TinyRedstoneLink cell via reflection.
 *
 * <p>IMPORTANT: Do NOT call {@code player.closeContainer()} before {@code openMenu()}
 * — it causes a race condition with the open-menu packet.
 */
public record TinyLinkScreenSwapPayload(BlockPos pos, int cellIndex, boolean transmitter,
                                         ItemStack freq1, ItemStack freq2) implements CustomPacketPayload {

    public static final Type<TinyLinkScreenSwapPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("createredstonelinkgui", "tiny_link_screen_swap")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TinyLinkScreenSwapPayload> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, TinyLinkScreenSwapPayload::pos,
        ByteBufCodecs.INT, TinyLinkScreenSwapPayload::cellIndex,
        ByteBufCodecs.BOOL, TinyLinkScreenSwapPayload::transmitter,
        ItemStack.OPTIONAL_STREAM_CODEC, TinyLinkScreenSwapPayload::freq1,
        ItemStack.OPTIONAL_STREAM_CODEC, TinyLinkScreenSwapPayload::freq2,
        TinyLinkScreenSwapPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(TinyLinkScreenSwapPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            BlockPos pos = payload.pos();
            Level level = player.level();

            // Validate position
            if (pos.equals(BlockPos.ZERO)) return;

            // Distance check
            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > 64.0) return;

            // Determine frequencies — try cell fallback if both are empty
            final ItemStack finalFreq1;
            final ItemStack finalFreq2;

            if (payload.freq1().isEmpty() && payload.freq2().isEmpty() && payload.cellIndex() >= 0) {
                Object cell = TinyRedstoneCreateCompatibility.findCell(level, pos, payload.cellIndex());
                if (cell != null) {
                    finalFreq1 = TinyRedstoneCreateCompatibility.getFreq1(cell);
                    finalFreq2 = TinyRedstoneCreateCompatibility.getFreq2(cell);
                } else {
                    finalFreq1 = payload.freq1();
                    finalFreq2 = payload.freq2();
                }
            } else {
                finalFreq1 = payload.freq1();
                finalFreq2 = payload.freq2();
            }

            List<ItemStack> freqs = new ArrayList<>(2);
            freqs.add(finalFreq1);
            freqs.add(finalFreq2);

            final int fCellIndex = payload.cellIndex();
            final boolean fTransmitter = payload.transmitter();

            // Open our menu directly — no closeContainer() call to avoid race condition
            player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new TinyRedstoneLinkMenu(id, inv, pos, fCellIndex, fTransmitter, freqs),
                net.minecraft.network.chat.Component.translatable("container.createredstonelinkgui.redstone_link_menu")
            ), buf -> {
                buf.writeBlockPos(pos);
                buf.writeInt(fCellIndex);
                buf.writeBoolean(fTransmitter);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, finalFreq1);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, finalFreq2);
            });
        });
    }
}