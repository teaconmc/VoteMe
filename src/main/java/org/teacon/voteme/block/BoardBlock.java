package org.teacon.voteme.block;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.IBlockReader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ObjectHolder;
import org.teacon.voteme.item.BoardItem;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BoardBlock extends Block {

    public static final String ID = "voteme:board";

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty BOARD_SIZE = IntegerProperty.create("size", 0, 7);
    public static final EnumProperty<State> BOARD_STATE = EnumProperty.create("state", State.class);

    public static final List<VoxelShape> NORTH_AABB_LIST = IntStream.range(0, 8)
            .mapToObj(i -> makeCuboidShape(0, 0, 14 - i * 2, 16, 16, 16)).collect(Collectors.toList());
    public static final List<VoxelShape> SOUTH_AABB_LIST = IntStream.range(0, 8)
            .mapToObj(i -> makeCuboidShape(0, 0, 0, 16, 16, 2 + i * 2)).collect(Collectors.toList());
    public static final List<VoxelShape> WEST_AABB_LIST = IntStream.range(0, 8)
            .mapToObj(i -> makeCuboidShape(14 - i * 2, 0, 0, 16, 16, 16)).collect(Collectors.toList());
    public static final List<VoxelShape> EAST_AABB_LIST = IntStream.range(0, 8)
            .mapToObj(i -> makeCuboidShape(0, 0, 0, 2 + i * 2, 16, 16)).collect(Collectors.toList());

    @ObjectHolder(ID)
    public static BoardBlock INSTANCE;

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(new BoardBlock(Properties.create(Material.IRON)));
    }

    private BoardBlock(Properties properties) {
        super(properties);
        this.setRegistryName(ID);
        this.setDefaultState(this.stateContainer.getBaseState().with(FACING, Direction.NORTH).with(BOARD_SIZE, 7).with(BOARD_STATE, State.SINGLE));
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return this.getDefaultState()
                .with(FACING, context.getPlacementHorizontalFacing().getOpposite())
                .with(BOARD_SIZE, BoardItem.INSTANCE.getBoardSize(context.getItem()));
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(FACING, BOARD_SIZE, BOARD_STATE);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.toRotation(state.get(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
        switch (state.get(FACING)) {
            case NORTH:
                return NORTH_AABB_LIST.get(state.get(BOARD_SIZE));
            case SOUTH:
                return SOUTH_AABB_LIST.get(state.get(BOARD_SIZE));
            case WEST:
                return WEST_AABB_LIST.get(state.get(BOARD_SIZE));
            case EAST:
                return EAST_AABB_LIST.get(state.get(BOARD_SIZE));
        }
        throw new IllegalStateException();
    }

    public enum State implements IStringSerializable {
        SINGLE, LEFT, MIDDLE, RIGHT;

        @Override
        public String getString() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
