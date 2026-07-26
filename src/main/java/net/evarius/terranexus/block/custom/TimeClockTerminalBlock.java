package net.evarius.terranexus.block.custom;

import com.mojang.serialization.MapCodec;
import net.evarius.terranexus.item.ModItems;
import net.evarius.terranexus.item.custom.EmployeeChipItem;
import net.evarius.terranexus.management.TimeClockScreen;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class TimeClockTerminalBlock extends HorizontalFacingBlock {
    public static final MapCodec<TimeClockTerminalBlock> CODEC = createCodec(TimeClockTerminalBlock::new);
    private static final VoxelShape NORTH_SOUTH = Block.createCuboidShape(1, 0, 3, 15, 16, 13);
    private static final VoxelShape EAST_WEST = Block.createCuboidShape(3, 0, 1, 13, 16, 15);

    public TimeClockTerminalBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends HorizontalFacingBlock> getCodec() { return CODEC; }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
    }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world,
                                                    BlockPos pos, ShapeContext context) {
        return state.get(FACING).getAxis() == Direction.Axis.X ? EAST_WEST : NORTH_SOUTH;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity user, BlockHitResult hit) {
        if (world.isClient() || !(user instanceof ServerPlayerEntity player)) return ActionResult.SUCCESS;
        ItemStack credential = player.getMainHandStack().isOf(ModItems.EMPLOYEE_CHIP)
                ? player.getMainHandStack()
                : player.getOffHandStack().isOf(ModItems.EMPLOYEE_CHIP)
                ? player.getOffHandStack() : ItemStack.EMPTY;
        String institutionId = EmployeeChipItem.resolveInstitution(player, credential);
        if (institutionId == null) {
            player.sendMessage(Text.literal(
                    "Zugriff verweigert: persönlichen gültigen Dienstausweis an das Terminal halten.")
                    .formatted(Formatting.RED), false);
            return ActionResult.FAIL;
        }
        TimeClockScreen.open(player, institutionId);
        return ActionResult.SUCCESS_SERVER;
    }
}
