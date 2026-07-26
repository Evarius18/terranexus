package net.evarius.terranexus.block.custom;

import com.mojang.serialization.MapCodec;
import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.management.AdminDesktopScreen;
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

public class ManagementComputerBlock extends HorizontalFacingBlock {
    public static final MapCodec<ManagementComputerBlock> CODEC = createCodec(ManagementComputerBlock::new);
    private static final VoxelShape NORTH_SOUTH =
            Block.createCuboidShape(0, 0, 0.5, 15, 9.25, 11.5);
    private static final VoxelShape EAST_WEST =
            Block.createCuboidShape(4.5, 0, 0, 15.5, 9.25, 15);

    public ManagementComputerBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }
    @Override protected MapCodec<? extends HorizontalFacingBlock> getCodec() { return CODEC; }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) { return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite()); }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(FACING).getAxis() == Direction.Axis.X ? EAST_WEST : NORTH_SOUTH;
    }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity user, BlockHitResult hit) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            AdminDesktopScreen.open(player);
        }
        return ActionResult.SUCCESS;
    }
}
