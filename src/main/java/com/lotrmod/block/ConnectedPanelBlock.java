package com.lotrmod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import javax.annotation.Nullable;

/**
 * A full-cube wall/panel block whose visible {@link #FACING} face swaps between 13
 * connected-texture tiles ({@link WallVariant}) depending on which same-type, same-facing
 * neighbors surround it, so a patch of blocks reads as one bordered panel instead of a
 * flatly-tiled texture.
 *
 * <p>3+ missing orthogonal neighbors (e.g. a fully isolated block) has no dedicated tile in
 * this 13-tile set and falls back to the nearest corner variant — fine for wall/panel use,
 * not meant for isolated single blocks.
 */
public class ConnectedPanelBlock extends Block {

    public static final MapCodec<ConnectedPanelBlock> CODEC = simpleCodec(ConnectedPanelBlock::new);

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<WallVariant> VARIANT = EnumProperty.create("variant", WallVariant.class);

    public ConnectedPanelBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(VARIANT, WallVariant.BASE));
    }

    @Override
    protected MapCodec<ConnectedPanelBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, VARIANT);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        BlockPos pos = ctx.getClickedPos();
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(VARIANT, computeVariant(ctx.getLevel(), pos, facing, this));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                      LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(VARIANT, computeVariant(level, pos, state.getValue(FACING), this));
    }

    // ── Connected-texture variant selection ────────────────────────────────────────

    public static WallVariant computeVariant(BlockGetter level, BlockPos pos, Direction facing, Block self) {
        Direction right = facing.getClockWise();
        Direction left = right.getOpposite();

        boolean up = connects(level, pos.above(), facing, self);
        boolean down = connects(level, pos.below(), facing, self);
        boolean lft = connects(level, pos.relative(left), facing, self);
        boolean rgt = connects(level, pos.relative(right), facing, self);

        if (up && down && lft && rgt) {
            boolean tl = connects(level, pos.above().relative(left), facing, self);
            boolean tr = connects(level, pos.above().relative(right), facing, self);
            boolean bl = connects(level, pos.below().relative(left), facing, self);
            boolean br = connects(level, pos.below().relative(right), facing, self);
            int missingDiagonals = (tl ? 0 : 1) + (tr ? 0 : 1) + (bl ? 0 : 1) + (br ? 0 : 1);
            if (missingDiagonals == 1) {
                if (!tl) return WallVariant.TOP_LEFT_INV;
                if (!tr) return WallVariant.TOP_RIGHT_INV;
                if (!bl) return WallVariant.BOTTOM_LEFT_INV;
                return WallVariant.BOTTOM_RIGHT_INV;
            }
            return WallVariant.BASE;
        }

        boolean missTop = !up, missBottom = !down, missLeft = !lft, missRight = !rgt;
        if (missTop && missLeft) return WallVariant.TOP_LEFT;
        if (missTop && missRight) return WallVariant.TOP_RIGHT;
        if (missBottom && missLeft) return WallVariant.BOTTOM_LEFT;
        if (missBottom && missRight) return WallVariant.BOTTOM_RIGHT;
        if (missTop) return WallVariant.TOP;
        if (missBottom) return WallVariant.BOTTOM;
        if (missLeft) return WallVariant.LEFT;
        return WallVariant.RIGHT;
    }

    private static boolean connects(BlockGetter level, BlockPos pos, Direction facing, Block self) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() == self && state.hasProperty(FACING) && state.getValue(FACING) == facing;
    }
}
