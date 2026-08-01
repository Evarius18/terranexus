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
    private static final VoxelShape NORTH = Block.createCuboidShape(1, 0, 1.5, 14, 7.5, 11);
    private static final VoxelShape EAST = Block.createCuboidShape(5, 0, 1, 14.5, 7.5, 14);
    private static final VoxelShape SOUTH = Block.createCuboidShape(2, 0, 5, 15, 7.5, 14.5);
    private static final VoxelShape WEST = Block.createCuboidShape(1.5, 0, 2, 11, 7.5, 15);

    public ManagementComputerBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }
    @Override protected MapCodec<? extends HorizontalFacingBlock> getCodec() { return CODEC; }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) { return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite()); }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(FACING)) {
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> NORTH;
        };
    }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity user, BlockHitResult hit) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            AdminDesktopScreen.open(player);
        }
        return ActionResult.SUCCESS;
    }
}
