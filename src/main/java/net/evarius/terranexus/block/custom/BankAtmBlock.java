package net.evarius.terranexus.block.custom;

import com.mojang.serialization.MapCodec;
import net.evarius.terranexus.management.BankAtmScreen;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class BankAtmBlock extends HorizontalFacingBlock {
    public static final MapCodec<BankAtmBlock> CODEC = createCodec(BankAtmBlock::new);
    private static final VoxelShape NORTH_SOUTH = Block.createCuboidShape(1, 0, 2, 15, 31, 14);
    private static final VoxelShape EAST_WEST = Block.createCuboidShape(2, 0, 1, 14, 31, 15);

    public BankAtmBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends HorizontalFacingBlock> getCodec() { return CODEC; }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
    }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(FACING).getAxis() == Direction.Axis.X ? EAST_WEST : NORTH_SOUTH;
    }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                           PlayerEntity user, BlockHitResult hit) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player) BankAtmScreen.open(player);
        return ActionResult.SUCCESS;
    }
}
