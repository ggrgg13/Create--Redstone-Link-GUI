package com.ggrgg.createredstonelinkgui.common.network;

import com.ggrgg.createredstonelinkgui.Config;
import com.ggrgg.createredstonelinkgui.common.SableHelper;
import com.ggrgg.createredstonelinkgui.compat.propulsion.VectorThrusterHelper;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSupportBehaviour;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RedstoneLinkMovePayload(BlockPos sourcePos, BlockPos targetPos, Direction clickedFace) implements CustomPacketPayload {

    public static final Type<RedstoneLinkMovePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("createredstonelinkgui", "link_move"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RedstoneLinkMovePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RedstoneLinkMovePayload::sourcePos,
            BlockPos.STREAM_CODEC, RedstoneLinkMovePayload::targetPos,
            ByteBufCodecs.VAR_INT, p -> p.clickedFace().get3DDataValue(),
            (pos1, pos2, face) -> new RedstoneLinkMovePayload(pos1, pos2, Direction.from3DDataValue(face))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(RedstoneLinkMovePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            Level level = player.level();
            BlockPos sourcePos = payload.sourcePos();
            BlockPos targetPos = payload.targetPos();
            Direction clickedFace = payload.clickedFace();

            if (!level.isLoaded(targetPos)) return;

            BlockEntity sourceBE = level.getBlockEntity(sourcePos);
            if (sourceBE == null) return;

            LinkBehaviour sourceLink = BlockEntityBehaviour.get(sourceBE, LinkBehaviour.TYPE);
            if (sourceLink == null) {
                Object vlb = com.ggrgg.createredstonelinkgui.common.VoidLinkHelper.getBehaviour(level, sourcePos);
                boolean hasVector = VectorThrusterHelper.isLoaded() && VectorThrusterHelper.hasAnyBehaviour(level, sourcePos);
                if (vlb == null && !hasVector) return;
            }

            FactoryPanelSupportBehaviour gaugeSupport = BlockEntityBehaviour.get(sourceBE, FactoryPanelSupportBehaviour.TYPE);
            boolean hasGaugeConnection = gaugeSupport != null && !gaugeSupport.getLinkedPanels().isEmpty();

            int maxRange = Config.MOVE_RANGE.get();
            if (hasGaugeConnection) {
                maxRange = Math.min(maxRange, 24);
                for (FactoryPanelPosition gaugePos : gaugeSupport.getLinkedPanels())
                    if (!gaugePos.pos().closerThan(targetPos, 24)) return;
            }

            if (player.distanceToSqr(sourcePos.getX(), sourcePos.getY(), sourcePos.getZ()) > maxRange * maxRange) return;
            if (SableHelper.distanceSquared(level, Vec3.atCenterOf(sourcePos), Vec3.atCenterOf(targetPos)) > maxRange * maxRange) return;

            BlockState sourceState = sourceBE.getBlockState();
            BlockState targetState = level.getBlockState(targetPos);
            boolean inPlace = sourcePos.equals(targetPos);

            if (!inPlace && !targetState.isAir() && !targetState.canBeReplaced()) return;

            Block block = sourceState.getBlock();
            BlockPlaceContext placeContext = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND,
                    ItemStack.EMPTY, new BlockHitResult(Vec3.atCenterOf(targetPos), clickedFace, targetPos, false));
            BlockState newState = block.getStateForPlacement(placeContext);
            if (newState == null) return;

            newState = copyNonOrientationProperties(newState, sourceState);

            // For void link blocks, reverse facing so frequency slots face the player
            // Iterate block properties to find any property named "facing" — this handles
            // both FACING (6-direction) and HORIZONTAL_FACING (4-direction) regardless of
            // which DirectionProperty instance the block actually uses.
            String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
            if (blockId.equals("createutilities:void_motor") ||
                blockId.equals("createutilities:void_chest") ||
                blockId.equals("createutilities:void_battery")) {
                for (Property<?> prop : newState.getProperties()) {
                    if (prop.getName().equals("facing") && prop instanceof DirectionProperty dp) {
                        Direction opposite = newState.getValue(dp).getOpposite();
                        newState = newState.setValue(dp, opposite);
                        break;
                    }
                }
            }

            if (hasGaugeConnection) {
                Direction oldFace = sourceState.getValue(BlockStateProperties.FACING);
                Direction newFace = newState.getValue(BlockStateProperties.FACING);
                if (oldFace != newFace) return;
            }

            if (!newState.canSurvive(level, targetPos)) return;

            HolderLookup.Provider registries = level.registryAccess();
            CompoundTag beTag = sourceBE.saveWithoutMetadata(registries);

            level.setBlock(targetPos, newState, Block.UPDATE_ALL);

            BlockEntity newBE = level.getBlockEntity(targetPos);
            if (newBE != null) {
                newBE.loadWithComponents(beTag, registries);
                newBE.setChanged();
            }

            if (gaugeSupport != null && !inPlace) {
                for (FactoryPanelPosition gaugePos : gaugeSupport.getLinkedPanels()) {
                    FactoryPanelBehaviour panel = FactoryPanelBehaviour.at(level, gaugePos);
                    if (panel != null) {
                        panel.targetedByLinks.remove(sourcePos);
                        panel.targetedByLinks.put(targetPos,
                            new FactoryPanelConnection(new FactoryPanelPosition(targetPos, gaugePos.slot()), 1));
                        panel.blockEntity.notifyUpdate();
                    }
                }
            }

            level.sendBlockUpdated(targetPos, newState, newState, Block.UPDATE_ALL);

            if (!inPlace) {
                level.setBlock(sourcePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
                level.sendBlockUpdated(sourcePos, sourceState, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        });
    }

    private static BlockState copyNonOrientationProperties(BlockState target, BlockState source) {
        BlockState result = target;
        for (Property<?> property : source.getProperties()) {
            String name = property.getName();
            if (name.equals("facing") || name.equals("face") || name.equals("attachment"))
                continue;
            if (result.hasProperty(property)) {
                result = copyPropertyUntyped(result, source, property);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState copyPropertyUntyped(BlockState target, BlockState source, Property<?> property) {
        Property<T> typed = (Property<T>) property;
        return target.setValue(typed, source.getValue(typed));
    }
}