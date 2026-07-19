package net.evarius.terranexus.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.block.ShapeContext;

public class RoadFurnitureBlock extends HorizontalFacingBlock {
    public static final MapCodec<RoadFurnitureBlock> CODEC = createCodec(RoadFurnitureBlock::new);

    private static final VoxelShape NORTH_SOUTH_SHAPE = VoxelShapes.union(
            Block.createCuboidShape(0, 0, 5, 16, 16, 11),
            Block.createCuboidShape(0, 0, 3, 5, 4, 13),
            Block.createCuboidShape(11, 0, 3, 16, 4, 13));
    private static final VoxelShape EAST_WEST_SHAPE = VoxelShapes.union(
            Block.createCuboidShape(5, 0, 0, 11, 16, 16),
            Block.createCuboidShape(3, 0, 0, 13, 4, 5),
            Block.createCuboidShape(3, 0, 11, 13, 4, 16));

    public RoadFurnitureBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, net.minecraft.util.math.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(FACING).getAxis() == net.minecraft.util.math.Direction.Axis.X
                ? EAST_WEST_SHAPE : NORTH_SOUTH_SHAPE;
    }
}
