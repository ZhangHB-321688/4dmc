package com.gmail.inayakitorikhurram.fdmc.mixin.block;

import com.gmail.inayakitorikhurram.fdmc.Direction4;
import com.gmail.inayakitorikhurram.fdmc.FDMCProperties;
import com.gmail.inayakitorikhurram.fdmc.mixininterfaces.RedstoneWireBlockI;
import com.gmail.inayakitorikhurram.fdmc.mixininterfaces.WorldAccessI;
import com.gmail.inayakitorikhurram.fdmc.mixininterfaces.AbstractBlockI;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.enums.WireConnection;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Map;
import java.util.Set;

import static com.gmail.inayakitorikhurram.fdmc.FDMCProperties.WIRE_CONNECTION_MAP;
import static net.minecraft.block.RedstoneWireBlock.POWER;

@Mixin(RedstoneWireBlock.class)
abstract
class RedstoneWireBlockMixin
        extends Block implements RedstoneWireBlockI {

    public RedstoneWireBlockMixin(Settings settings) {
        super(settings);
    }

    @Shadow protected abstract int increasePower(BlockState state);

    @Shadow private boolean wiresGivePower;

    @Shadow
    protected static boolean connectsTo(BlockState state) {
        return false;
    }

    @Shadow protected abstract boolean canRunOnTop(BlockView world, BlockPos pos, BlockState floor);

    @Shadow protected abstract BlockState getDefaultWireState(BlockView world, BlockState state, BlockPos pos);

    @Mutable
    @Shadow @Final private BlockState dotState;

    @Shadow protected abstract void updateNeighbors(World world, BlockPos pos);

    @Shadow protected abstract BlockState getPlacementState(BlockView world, BlockState state, BlockPos pos);

    @Shadow protected abstract void updateForNewState(World world, BlockPos pos, BlockState oldState, BlockState newState);
    

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/RedstoneWireBlock;setDefaultState(Lnet/minecraft/block/BlockState;)V"))//set default state to not have kata/ana up
    private BlockState injectedDefaultState(BlockState defaultState){
        return defaultState.with(WIRE_CONNECTION_MAP.get(Direction4.KATA), WireConnection.NONE).with(WIRE_CONNECTION_MAP.get(Direction4.ANA), WireConnection.NONE);
    }
    @Redirect(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/block/RedstoneWireBlock;dotState:Lnet/minecraft/block/BlockState;", opcode = Opcodes.PUTFIELD))//set default state to not have kata/ana up
    private void injectedDotState(RedstoneWireBlock instance, BlockState state){
        dotState = state.with(WIRE_CONNECTION_MAP.get(Direction4.KATA), WireConnection.SIDE).with(WIRE_CONNECTION_MAP.get(Direction4.ANA), WireConnection.SIDE);
    }

    @Inject(method = "getDefaultWireState", at = @At("RETURN"), cancellable = true)
    private void getDefaultWireState(BlockView world, BlockState state, BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        state = cir.getReturnValue();
        boolean emptyAbove = !world.getBlockState(pos.up()).isSolidBlock(world, pos);
        for (Direction4 direction : Direction4.WDIRECTIONS) {
            if (state.get(WIRE_CONNECTION_MAP.get(direction)).isConnected()) continue;
            WireConnection wireConnection = getRenderConnectionType4(world, pos, direction, emptyAbove);
            state = state.with(WIRE_CONNECTION_MAP.get(direction), wireConnection);
        }

        cir.setReturnValue(state);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction4 dir, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {

        WireConnection wireConnection = getRenderConnectionType4(world, pos, dir);
        if (wireConnection.isConnected() == state.get(WIRE_CONNECTION_MAP.get(dir)).isConnected() && !RedstoneWireBlockI.isFullyConnected4(state)) {
            return state.with(WIRE_CONNECTION_MAP.get(dir), wireConnection);
        }
        return getPlacementState(world, this.dotState.with(POWER, state.get(POWER)).with(WIRE_CONNECTION_MAP.get(dir), wireConnection), pos);
    }

    //look this could be injected but have you considered I'm really lazy and this is easier k thnx <3
    @Inject(method = "getPlacementState(Lnet/minecraft/world/BlockView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;", at = @At("HEAD"), cancellable = true)
    private void getPlacementState(BlockView world, BlockState state, BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        boolean wasNotConnected = RedstoneWireBlockI.isNotConnected4(state);
        state = getDefaultWireState(world, getDefaultState().with(POWER, state.get(POWER)), pos);
        if (wasNotConnected && RedstoneWireBlockI.isNotConnected4(state)) {
            cir.setReturnValue(state);
            cir.cancel();
            return;
        }
        boolean connectedNorth = state.get(WIRE_CONNECTION_MAP.get(Direction4.NORTH)).isConnected();
        boolean connectedSouth = state.get(WIRE_CONNECTION_MAP.get(Direction4.SOUTH)).isConnected();
        boolean connectedEast = state.get(WIRE_CONNECTION_MAP.get(Direction4.EAST)).isConnected();
        boolean connectedWest = state.get(WIRE_CONNECTION_MAP.get(Direction4.WEST)).isConnected();
        boolean connectedAna = state.get(WIRE_CONNECTION_MAP.get(Direction4.ANA)).isConnected();
        boolean connectedKata = state.get(WIRE_CONNECTION_MAP.get(Direction4.KATA)).isConnected();
        boolean shouldConnectByDefaultZ = !connectedKata  && !connectedAna   && !connectedEast  && !connectedWest;
        boolean shouldConnectByDefaultX = !connectedNorth && !connectedSouth && !connectedAna   && !connectedKata;
        boolean shouldConnectByDefaultW = !connectedEast  && !connectedWest  && !connectedNorth && !connectedSouth;
        if(shouldConnectByDefaultZ) {
            if (!connectedNorth) {
                state = state.with(WIRE_CONNECTION_MAP.get(Direction4.NORTH), WireConnection.SIDE);
            }
            if (!connectedSouth) {
                state = state.with(WIRE_CONNECTION_MAP.get(Direction4.SOUTH), WireConnection.SIDE);
            }
        }
        if(shouldConnectByDefaultX){
            if (!connectedWest) {
                state = state.with(WIRE_CONNECTION_MAP.get(Direction4.WEST), WireConnection.SIDE);
            }
            if (!connectedEast) {
                state = state.with(WIRE_CONNECTION_MAP.get(Direction4.EAST), WireConnection.SIDE);
            }
        }
        if(shouldConnectByDefaultW) {
            if (!connectedAna) {
                state = state.with(WIRE_CONNECTION_MAP.get(Direction4.ANA), WireConnection.SIDE);
            }
            if (!connectedKata) {
                state = state.with(WIRE_CONNECTION_MAP.get(Direction4.KATA), WireConnection.SIDE);
            }
        }
        cir.setReturnValue(state);
        cir.cancel();
    }
    @Inject(method = "update", at = @At(value = "INVOKE", target = "Ljava/util/Set;add(Ljava/lang/Object;)Z", ordinal = 0, shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    private void injected(World world, BlockPos pos, BlockState state, CallbackInfo ci, int i, Set<BlockPos> set) {
        for (Direction4 direction : Direction4.WDIRECTIONS) {
            set.add(pos.add(direction.getVec3()));
        }
    }


    @Inject(method = "isFullyConnected", at = @At("HEAD"), cancellable = true)
    private static void isFullyConnected(BlockState state, CallbackInfoReturnable<Boolean> cir){
        cir.setReturnValue(RedstoneWireBlockI.isFullyConnected4(state));
        cir.cancel();
    }


    @Inject(method = "isNotConnected", at = @At("HEAD"), cancellable = true)
    private static void isNotConnected(BlockState state, CallbackInfoReturnable<Boolean> cir){
        cir.setReturnValue(RedstoneWireBlockI.isNotConnected4(state));
        cir.cancel();
    }

    @Inject(method = "prepare", at = @At("RETURN"))
    public void prepare(BlockState state, WorldAccess world, BlockPos pos, int flags, int maxUpdateDepth, CallbackInfo ci) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (Direction4 dir : Direction4.WDIRECTIONS) {
            WireConnection wireConnection = state.get(WIRE_CONNECTION_MAP.get(dir));
            mutable.set(pos, dir.getVec3());
            if (wireConnection == WireConnection.NONE || world.getBlockState(mutable).isOf(this)) continue;
            mutable.move(Direction.DOWN);
            BlockState blockState = world.getBlockState(mutable);
            if (blockState.isOf(this)) {
                BlockPos blockPos = mutable.add(dir.getOpposite().getVec3());
                ((WorldAccessI)world).replaceWithStateForNeighborUpdate(dir.getOpposite(), world.getBlockState(blockPos), mutable, blockPos, flags, maxUpdateDepth);
            }
            mutable.set(pos, dir.getVec3()).move(Direction.UP);
            BlockState blockState2 = world.getBlockState(mutable);
            if (!blockState2.isOf(this)) continue;
            BlockPos blockPos2 = mutable.add(dir.getOpposite().getVec3());
            ((WorldAccessI)world).replaceWithStateForNeighborUpdate(dir.getOpposite(), world.getBlockState(blockPos2), mutable, blockPos2, flags, maxUpdateDepth);
        }
    }

    private WireConnection getRenderConnectionType4(BlockView world, BlockPos pos, Direction4 dir) {
        return getRenderConnectionType4(world, pos, dir, !world.getBlockState(pos.up()).isSolidBlock(world, pos));
    }
    private WireConnection getRenderConnectionType4(BlockView world, BlockPos pos, Direction4 direction, boolean isEmptyAbove) {
        BlockPos blockPos = pos.add(direction.getVec3());
        BlockState blockState = world.getBlockState(blockPos);
        boolean canRunOnTop = canRunOnTop(world, blockPos, blockState);
        if (isEmptyAbove && canRunOnTop && connectsTo(world.getBlockState(blockPos.up()))) {
            //if (blockState.isSideSolidFullSquare(world, blockPos, direction.getOpposite())) {
                return WireConnection.UP;
            //}
            //return WireConnection.SIDE;
        }
        if (connectsTo(blockState) || !blockState.isSolidBlock(world, blockPos) && connectsTo(world.getBlockState(blockPos.down()))) {
            return WireConnection.SIDE;
        }
        return WireConnection.NONE;
    }

    @Inject(method = "onStateReplaced", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/RedstoneWireBlock;update(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V", shift = At.Shift.AFTER))
    private void after3DirectionStatesReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved, CallbackInfo ci){
        for (Direction4 dir : Direction4.WDIRECTIONS) {
            world.updateNeighborsAlways(pos.add(dir.getVec3()), this);
        }
    }

    @Inject(method = "updateNeighbors", at = @At("TAIL"))
    private void updateNeighbors(World world, BlockPos pos, CallbackInfo ci) {
        for (Direction4 dir : Direction4.WDIRECTIONS) {
            world.updateNeighborsAlways(pos.add(dir.getVec3()), this);
        }
    }

    @Inject(method = "updateOffsetNeighbors", at = @At("HEAD"))
    private void updateOffsetNeighborsStart(World world, BlockPos pos, CallbackInfo ci) {
        for (Direction4 dir : Direction4.WDIRECTIONS) {
            updateNeighbors(world, pos.add(dir.getVec3()));
        }
    }


    @Inject(method = "updateOffsetNeighbors", at = @At("TAIL"))
    private void updateOffsetNeighborsEnd(World world, BlockPos pos, CallbackInfo ci) {
        for (Direction4 dir : Direction4.WDIRECTIONS) {
            BlockPos blockPos = pos.add(dir.getVec3());
            if (world.getBlockState(blockPos).isSolidBlock(world, blockPos)) {
                this.updateNeighbors(world, blockPos.up());
                continue;
            }
            this.updateNeighbors(world, blockPos.down());
        }
    }

    @Inject(method = "getReceivedRedstonePower", at = @At("RETURN"), cancellable = true)
    private void afterGetReceivedRedstonePower(World world, BlockPos pos, CallbackInfoReturnable<Integer> cir){
        wiresGivePower = false;
        int i = world.getReceivedRedstonePower(pos);
        wiresGivePower = true;
        int j = cir.getReturnValueI() + 1;
        if(i >= 15){
            return;
        }

        for(Direction4 dir : Direction4.WDIRECTIONS){
            BlockPos blockPos = pos.add(dir.getVec3());
            BlockState blockState = world.getBlockState(blockPos);
            j = Math.max(j, increasePower(blockState));
            BlockPos blockPos2 = pos.up();
            if (blockState.isSolidBlock(world, blockPos) && !world.getBlockState(blockPos2).isSolidBlock(world, blockPos2)) {
                j = Math.max(j, this.increasePower(world.getBlockState(blockPos.up())));
                continue;
            }
            if (blockState.isSolidBlock(world, blockPos)) continue;
            j = Math.max(j, this.increasePower(world.getBlockState(blockPos.down())));
        }
        cir.setReturnValue(Math.max(i, j - 1));
    }

    @Inject(method = "appendProperties", at = @At("TAIL"))
    protected void appendProperties4D(StateManager.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(WIRE_CONNECTION_MAP.get(Direction4.KATA), WIRE_CONNECTION_MAP.get(Direction4.ANA));
    }

    @Inject(method = "updateForNewState",at = @At("RETURN"))
    private void updateForNewState(World world, BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        for (Direction4 dir : Direction4.WDIRECTIONS) {
            BlockPos blockPos = pos.add(dir.getVec3());
            if (oldState.get(WIRE_CONNECTION_MAP.get(dir)).isConnected() == newState.get(WIRE_CONNECTION_MAP.get(dir)).isConnected() || !world.getBlockState(blockPos).isSolidBlock(world, blockPos)) continue;
            ((WorldAccessI)world).updateNeighborsExcept(blockPos, newState.getBlock(), dir.getOpposite());
        }
    }

    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    public void onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        if (!player.getAbilities().allowModifyWorld) {
            cir.setReturnValue(ActionResult.PASS);
            cir.cancel();
            return;
        }
        if(world.isClient){
            cir.setReturnValue(ActionResult.SUCCESS);
            cir.cancel();
            return;
        }
        if (RedstoneWireBlockI.isFullyConnected4(state) || RedstoneWireBlockI.isNotConnected4(state)) {
            BlockState blockState = RedstoneWireBlockI.isFullyConnected4(state) ? getDefaultState() : this.dotState;
            blockState = blockState.with(POWER, state.get(POWER));
            //blockState = this.getPlacementState(world, blockState, pos);
            if (blockState != state) {
                world.setBlockState(pos, blockState, Block.NOTIFY_ALL);
                updateForNewState(world, pos, state, blockState);
                cir.setReturnValue(ActionResult.SUCCESS);
                cir.cancel();
                return;
            }
        }
        cir.setReturnValue(ActionResult.PASS);
        cir.cancel();
    }

}
