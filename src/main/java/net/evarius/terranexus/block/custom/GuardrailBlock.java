package net.evarius.terranexus.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

public class GuardrailBlock extends HorizontalFacingBlock {
    public static final MapCodec<GuardrailBlock> CODEC = createCodec(GuardrailBlock::new);
    private static final VoxelShape NORTH_SOUTH = VoxelShapes.union(
            Block.createCuboidShape(0, 7, 6, 16, 13, 10),
            Block.createCuboidShape(1, 0, 7, 3, 10, 9),
            Block.createCuboidShape(13, 0, 7, 15, 10, 9));
    private static final VoxelShape EAST_WEST = VoxelShapes.union(
            Block.createCuboidShape(6, 7, 0, 10, 13, 16),
            Block.createCuboidShape(7, 0, 1, 9, 10, 3),
            Block.createCuboidShape(7, 0, 13, 9, 10, 15));

    public GuardrailBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
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
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(FACING).getAxis() == Direction.Axis.X ? EAST_WEST : NORTH_SOUTH;
    }
}
