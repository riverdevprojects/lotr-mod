package com.lotrmod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * A round hobbit door — a 3-wide x 3-tall multiblock door.
 *
 * <p>The door is made of nine cells, each carrying its grid position ({@link #COL}/{@link #ROW})
 * so its model knows which corner/edge/base tile to draw, giving the square 3x3 grid a rounded
 * silhouette when closed. Right-clicking any cell toggles the whole door {@link #OPEN}: when open
 * every cell becomes passable, the hinge column ({@code COL == 2}) draws a thin leaf swung flush
 * against that edge, and the other two columns draw nothing.
 */
public class HobbitDoorBlock extends Block {

    public static final MapCodec<HobbitDoorBlock> CODEC = simpleCodec(HobbitDoorBlock::new);

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final IntegerProperty COL = IntegerProperty.create("col", 0, 2); // 0=left .. 2=right (hinge)
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 2); // 0=bottom .. 2=top

    public HobbitDoorBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(COL, 1)
                .setValue(ROW, 0));
    }

    public HobbitDoorBlock() {
        this(BlockBehaviour.Properties.of().strength(3.0f).noOcclusion());
    }

    @Override
    protected MapCodec<HobbitDoorBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, COL, ROW);
    }

    // ── Geometry helpers ─────────────────────────────────────────────────────────

    /** The door's local "right" (toward the hinge column), perpendicular to FACING. */
    private static Direction rightOf(Direction facing) { return facing.getClockWise(); }

    /** Bottom-centre cell (col=1, row=0) of the door this cell belongs to. */
    private static BlockPos baseOf(BlockState state, BlockPos pos) {
        Direction right = rightOf(state.getValue(FACING));
        int col = state.getValue(COL);
        int row = state.getValue(ROW);
        return pos.relative(right, -(col - 1)).below(row);
    }

    private static BlockPos cellPos(BlockPos base, Direction right, int col, int row) {
        return base.relative(right, col - 1).above(row);
    }

    // ── Placement ────────────────────────────────────────────────────────────────

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite(); // face the placer
        Direction right = rightOf(facing);
        BlockPos base = ctx.getClickedPos(); // anchor = bottom-centre
        Level level = ctx.getLevel();
        // Need the whole 3x3 to be free.
        for (int c = 0; c < 3; c++) {
            for (int r = 0; r < 3; r++) {
                if (c == 1 && r == 0) continue; // the clicked cell itself
                BlockPos p = cellPos(base, right, c, r);
                if (!level.getBlockState(p).canBeReplaced(ctx)) return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(COL, 1).setValue(ROW, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        Direction right = rightOf(facing);
        BlockPos base = pos; // anchor placed at bottom-centre
        for (int c = 0; c < 3; c++) {
            for (int r = 0; r < 3; r++) {
                if (c == 1 && r == 0) continue;
                BlockPos p = cellPos(base, right, c, r);
                level.setBlock(p, state.setValue(COL, c).setValue(ROW, r), 3);
            }
        }
    }

    // ── Interaction ──────────────────────────────────────────────────────────────

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean newOpen = !state.getValue(OPEN);
            setOpenAll(level, state, pos, newOpen);
            level.playSound(null, pos,
                    newOpen ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE,
                    SoundSource.BLOCKS, 1.0f, 0.9f + level.random.nextFloat() * 0.2f);
            level.gameEvent(player, newOpen ? net.minecraft.world.level.gameevent.GameEvent.BLOCK_OPEN
                    : net.minecraft.world.level.gameevent.GameEvent.BLOCK_CLOSE, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Toggle OPEN on every cell of the door this cell belongs to. */
    public static void setOpenAll(Level level, BlockState state, BlockPos pos, boolean open) {
        Direction right = rightOf(state.getValue(FACING));
        BlockPos base = baseOf(state, pos);
        for (int c = 0; c < 3; c++) {
            for (int r = 0; r < 3; r++) {
                BlockPos p = cellPos(base, right, c, r);
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof HobbitDoorBlock && s.getValue(OPEN) != open) {
                    level.setBlock(p, s.setValue(OPEN, open), 10);
                }
            }
        }
    }

    // ── Break the whole door together ────────────────────────────────────────────

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            Direction right = rightOf(state.getValue(FACING));
            BlockPos base = baseOf(state, pos);
            for (int c = 0; c < 3; c++) {
                for (int r = 0; r < 3; r++) {
                    BlockPos p = cellPos(base, right, c, r);
                    if (p.equals(pos)) continue;
                    if (level.getBlockState(p).getBlock() instanceof HobbitDoorBlock) {
                        level.setBlock(p, level.getFluidState(p).createLegacyBlock(), 35);
                    }
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // ── Shapes (passable when open) ──────────────────────────────────────────────

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }
}
