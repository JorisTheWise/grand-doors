package net.watchtower.doors;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A door one to four blocks wide and two to four blocks tall, one block per cell.
 *
 * <p>{@code facing} is the way the door swings (out of the building), {@code part} counts the
 * columns from the left as you look that way, and {@code row} counts up from the floor.
 *
 * <p>A split door has two leaves, each half the door wide, hinged at the two ends. A single
 * door has one leaf, hinged at one end.
 *
 * <p>A leaf up to two blocks long stays in place: the door's cells never move. When it opens,
 * the hinge column draws the whole leaf turned out along the jamb, up to one block past its
 * own cell, and the other columns draw nothing. That covers every split door (leaves of 1,
 * 1.5 and 2 blocks) and the single doors one and two wide.
 *
 * <p>A single leaf three blocks long cannot be drawn from one cell (a block model reaches one
 * block out at most), so its cells move when it opens: the hinge column stays and the other
 * two go to the two cells in front of it.
 *
 * <p>{@code front} says which face of its cells the closed door stands on. By default that is
 * the back face, away from {@code facing}: the leaves swing out through their own cells.
 * With {@code front=true} it is the face the door swings towards, so the leaves turn on the
 * outer corners and an open leaf lies wholly outside the wall: exactly one cell further out
 * than for a door on the back face. A cell cannot draw that far, so every cell that carries a
 * leaf steps one cell forward when such a door opens, and back when it closes.
 */
public class GrandDoorBlock extends Block {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final BooleanProperty FRONT = BooleanProperty.create("front");
	public static final int MAX_WIDTH = 4;
	private static final IntegerProperty[] PARTS = {
		null, null, IntegerProperty.create("part", 0, 1), IntegerProperty.create("part", 0, 2), IntegerProperty.create("part", 0, 3)
	};
	public static final int MIN_HEIGHT = 2;
	public static final int MAX_HEIGHT = 4;
	private static final IntegerProperty[] ROWS = {
		null, null, IntegerProperty.create("row", 0, 1), IntegerProperty.create("row", 0, 2), IntegerProperty.create("row", 0, 3)
	};

	/** Leaf thickness in pixels. */
	private static final double THICK = 3.0;
	private static final int SIGNAL_POLL_TICKS = 5;
	/** Set a cell without telling its neighbours, so a half-moved door never sees itself. */
	private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	private static final int QUIET_NO_DROPS = QUIET | Block.UPDATE_SUPPRESS_DROPS;

	private static final Map<Direction, VoxelShape> CLOSED_BACK = shapes(0, 16 - THICK, 16, 16);
	private static final Map<Direction, VoxelShape> CLOSED_FRONT = shapes(0, 0, 16, THICK);
	private static final Map<Direction, VoxelShape> SIDE_LEFT = shapes(0, 0, THICK, 16);
	private static final Map<Direction, VoxelShape> SIDE_RIGHT = shapes(16 - THICK, 0, 16, 16);

	/** The state definition is built inside Block's constructor, before our own fields exist. */
	private static final ThreadLocal<Size> BUILDING = new ThreadLocal<>();
	/** True while this thread is moving or removing a door's cells itself. */
	private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> false);

	private final int width;
	private final int height;
	private final boolean split;
	/** True for the one kind of door whose cells move when it opens. */
	private final boolean moves;
	/** How far an open leaf reaches past its hinge cell, in pixels. */
	private final double reach;
	private final Map<Direction, VoxelShape> leafLeft;
	private final Map<Direction, VoxelShape> leafRight;
	private final BlockSetType type;
	private final boolean opensForMobs;
	/** Copper: the door this one turns into with time, and how likely on a try. */
	private @Nullable GrandDoorBlock weathersTo;
	private float weatherChance;
	/** Once a day on average, as vanilla copper: the share of random ticks that try at all. */
	private static final float WEATHER_TRY = 0.05688889F;

	private record Size(int width, int height, boolean split) {
	}

	/** One cell of a door: where it is and which cell it is. */
	private record Cell(BlockPos pos, int part, int row) {
	}

	public static <T extends Block> T sized(int width, int height, boolean split, Supplier<T> factory) {
		if (width < 1 || width > MAX_WIDTH) {
			throw new IllegalArgumentException("width must be 1.." + MAX_WIDTH + ", was " + width);
		}
		if (height < MIN_HEIGHT || height > MAX_HEIGHT) {
			throw new IllegalArgumentException("height must be " + MIN_HEIGHT + ".." + MAX_HEIGHT + ", was " + height);
		}
		if (split && width < 2) {
			throw new IllegalArgumentException("a split door is at least 2 wide");
		}
		BUILDING.set(new Size(width, height, split));
		try {
			return factory.get();
		} finally {
			BUILDING.remove();
		}
	}

	public GrandDoorBlock(int width, int height, boolean split, BlockSetType type, boolean opensForMobs, BlockBehaviour.Properties properties) {
		super(properties);
		this.opensForMobs = opensForMobs;
		this.width = width;
		this.height = height;
		this.split = split;
		this.type = type;
		double leaf = split ? width * 8.0 : width * 16.0;
		this.moves = leaf > 32;
		this.reach = this.moves ? 0 : leaf - 16;
		this.leafLeft = shapes(0, -this.reach, THICK, 16);
		this.leafRight = shapes(16 - THICK, -this.reach, 16, 16);
		BlockState state = this.stateDefinition.any()
			.setValue(FACING, Direction.NORTH)
			.setValue(ROWS[height], 0)
			.setValue(OPEN, false)
			.setValue(POWERED, false)
			.setValue(FRONT, false);
		if (width > 1) {
			state = state.setValue(PARTS[width], 0);
		}
		this.registerDefaultState(split ? state : state.setValue(HINGE, DoorHingeSide.LEFT));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		Size size = BUILDING.get();
		if (size == null) {
			throw new IllegalStateException("make a GrandDoorBlock through GrandDoorBlock.sized");
		}
		builder.add(FACING, ROWS[size.height()], OPEN, POWERED, FRONT);
		if (size.width() > 1) {
			builder.add(PARTS[size.width()]);
		}
		if (!size.split()) {
			builder.add(HINGE);
		}
	}

	public int width() {
		return this.width;
	}

	/** The column property; a door one wide has none. */
	public @Nullable IntegerProperty partProperty() {
		return PARTS[this.width];
	}

	public int partOf(BlockState state) {
		return this.width == 1 ? 0 : state.getValue(PARTS[this.width]);
	}

	public int height() {
		return this.height;
	}

	public boolean split() {
		return this.split;
	}

	// ---- geometry -------------------------------------------------------------------------

	private int rowOf(BlockState state) {
		return state.getValue(ROWS[this.height]);
	}

	/** The column a single leaf turns on. A split door has a hinge at both ends. */
	private int hingePart(BlockState state) {
		return state.getValue(HINGE) == DoorHingeSide.LEFT ? 0 : this.width - 1;
	}

	/** True for a column that carries a leaf: both ends of a split door, one end of a single. */
	private boolean isHinge(BlockState state, int part) {
		return this.split ? part == 0 || part == this.width - 1 : part == this.hingePart(state);
	}

	/** True if the leaf on this column turns on the door's left end. */
	private boolean turnsLeft(BlockState state, int part) {
		return this.split ? part == 0 : state.getValue(HINGE) == DoorHingeSide.LEFT;
	}

	/** True for a column that draws leaf when the door is open. */
	private boolean carriesLeaf(BlockState state, int part) {
		return this.moves || this.isHinge(state, part);
	}

	/** True if the door leaves some of its closed cells when it opens. */
	private boolean wanders(BlockState state) {
		return this.moves || state.getValue(FRONT);
	}

	/** Where a cell sits, counted from the door's left bottom cell when closed. */
	private BlockPos offset(BlockState door, int part, int row, boolean open) {
		Direction facing = door.getValue(FACING);
		Direction wall = facing.getClockWise();
		BlockPos at = BlockPos.ZERO.relative(wall, part);
		if (open && this.moves) {
			int hinge = this.hingePart(door);
			at = BlockPos.ZERO.relative(wall, hinge).relative(facing, Math.abs(part - hinge));
		}
		if (open && door.getValue(FRONT) && this.carriesLeaf(door, part)) {
			at = at.relative(facing);
		}
		return at.above(row);
	}

	private BlockPos originOf(BlockState state, BlockPos pos) {
		return pos.subtract(this.offset(state, this.partOf(state), this.rowOf(state), state.getValue(OPEN)));
	}

	private List<Cell> cells(BlockState door, BlockPos origin, boolean open) {
		List<Cell> all = new ArrayList<>(this.width * this.height);
		for (int part = 0; part < this.width; part++) {
			for (int row = 0; row < this.height; row++) {
				all.add(new Cell(origin.offset(this.offset(door, part, row, open)), part, row));
			}
		}
		return all;
	}

	private BlockState cellState(BlockState door, Cell cell) {
		BlockState state = door.setValue(ROWS[this.height], cell.row());
		return this.width == 1 ? state : state.setValue(PARTS[this.width], cell.part());
	}

	// ---- shape ----------------------------------------------------------------------------

	/** A box drawn for a door facing north (out is -z, left is -x), turned to every facing. */
	private static Map<Direction, VoxelShape> shapes(double x0, double z0, double x1, double z1) {
		Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
		out.put(Direction.NORTH, flat(x0, z0, x1, z1));
		out.put(Direction.EAST, flat(16 - z0, x0, 16 - z1, x1));
		out.put(Direction.SOUTH, flat(16 - x0, 16 - z0, 16 - x1, 16 - z1));
		out.put(Direction.WEST, flat(z0, 16 - x0, z1, 16 - x1));
		return out;
	}

	private static VoxelShape flat(double xa, double za, double xb, double zb) {
		return Shapes.box(Math.min(xa, xb) / 16, 0, Math.min(za, zb) / 16, Math.max(xa, xb) / 16, 1, Math.max(za, zb) / 16);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		Direction facing = state.getValue(FACING);
		if (!state.getValue(OPEN)) {
			return (state.getValue(FRONT) ? CLOSED_FRONT : CLOSED_BACK).get(facing);
		}
		if (this.moves) {
			return (this.hingePart(state) == 0 ? SIDE_LEFT : SIDE_RIGHT).get(facing);
		}
		int part = this.partOf(state);
		if (!this.isHinge(state, part)) {
			return Shapes.empty();
		}
		return (this.turnsLeft(state, part) ? this.leafLeft : this.leafRight).get(facing);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return type != PathComputationType.WATER && state.getValue(OPEN);
	}

	// ---- placing --------------------------------------------------------------------------

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos clicked = context.getClickedPos();
		if (clicked.getY() + this.height - 1 > level.getMaxY()) {
			return null;
		}
		// The door stands on the near side of the clicked cell and swings away from the player;
		// placed while sneaking, it stands there too but swings towards the player.
		boolean towards = context.isSecondaryUseActive();
		Direction looking = context.getHorizontalDirection();
		BlockState door = this.defaultBlockState()
			.setValue(FACING, towards ? looking.getOpposite() : looking)
			.setValue(FRONT, towards);
		if (!this.split) {
			door = door.setValue(HINGE, hingeFor(context, door.getValue(FACING)));
		}
		// The clicked cell is the middle of the door if it fits there, else nearer an end.
		for (int part : PLACING_ORDER[this.width]) {
			BlockPos origin = clicked.subtract(this.offset(door, part, 0, false));
			boolean fits = true;
			for (Cell cell : this.cells(door, origin, false)) {
				BlockState there = level.getBlockState(cell.pos());
				if (!there.canBeReplaced(context) || !there.getFluidState().isEmpty()) {
					fits = false;
					break;
				}
			}
			if (fits) {
				return this.width == 1 ? door : door.setValue(PARTS[this.width], part);
			}
		}
		return null;
	}

	private static final int[][] PLACING_ORDER = {null, {0}, {0, 1}, {1, 0, 2}, {1, 2, 0, 3}};

	/** Hinge on the side of the clicked block the player aimed at. */
	private static DoorHingeSide hingeFor(BlockPlaceContext context, Direction facing) {
		Direction wall = facing.getClockWise();
		Vec3 aim = context.getClickLocation().subtract(Vec3.atCenterOf(context.getClickedPos()));
		double along = aim.x * wall.getStepX() + aim.z * wall.getStepZ();
		return along < 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		BlockPos origin = this.originOf(state, pos);
		for (Cell cell : this.cells(state, origin, false)) {
			if (!cell.pos().equals(pos)) {
				level.setBlock(cell.pos(), this.cellState(state, cell), Block.UPDATE_ALL);
			}
		}
		if (!level.isClientSide()) {
			this.followSignal(level, level.getBlockState(pos), pos);
		}
	}

	// ---- opening and closing --------------------------------------------------------------

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			this.swing(level, state, pos, !state.getValue(OPEN), state.getValue(POWERED), true);
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * Puts the whole door in one position. Returns false, and changes nothing, if something
	 * stands where a leaf has to go.
	 */
	private boolean swing(Level level, BlockState state, BlockPos pos, boolean open, boolean powered, boolean audible) {
		boolean was = state.getValue(OPEN);
		boolean moves = open != was;
		BlockPos origin = this.originOf(state, pos);
		List<Cell> from = this.cells(state, origin, was);
		List<Cell> to = this.cells(state, origin, open);

		if (moves && !this.wayIsClear(level, state, origin, from, to, open)) {
			if (audible) {
				level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.6F, 1.0F);
			}
			return false;
		}

		BlockState target = state.setValue(OPEN, open).setValue(POWERED, powered);
		BUSY.set(true);
		try {
			for (Cell cell : from) {
				if (to.stream().noneMatch(other -> other.pos().equals(cell.pos())) && level.getBlockState(cell.pos()).is(this)) {
					level.setBlock(cell.pos(), Blocks.AIR.defaultBlockState(), QUIET_NO_DROPS);
				}
			}
			for (Cell cell : to) {
				BlockState there = level.getBlockState(cell.pos());
				if (!there.isAir() && !there.is(this)) {
					level.destroyBlock(cell.pos(), true);
				}
				level.setBlock(cell.pos(), this.cellState(target, cell), QUIET);
			}
		} finally {
			BUSY.set(false);
		}

		if (moves) {
			// The cells were set without telling anyone. Now that the door is whole again, tell
			// the neighbours: redstone, and whatever watches or leans on these cells (observers,
			// fences and walls that join up).
			for (List<Cell> cells : List.of(from, to)) {
				for (Cell cell : cells) {
					level.updateNeighborsAt(cell.pos(), this);
					level.getBlockState(cell.pos()).updateNeighbourShapes(level, cell.pos(), Block.UPDATE_ALL);
				}
			}
			level.playSound(null, pos, open ? this.type.doorOpen() : this.type.doorClose(), SoundSource.BLOCKS, 1.0F,
				level.getRandom().nextFloat() * 0.1F + 0.9F);
		}
		// A door whose cells moved has left the cells its redstone was wired to, so it has to look.
		if (open && powered && this.wanders(target)) {
			level.scheduleTick(to.get(0).pos(), this, SIGNAL_POLL_TICKS);
		}
		return true;
	}

	private boolean wayIsClear(Level level, BlockState door, BlockPos origin, List<Cell> from, List<Cell> to, boolean open) {
		// Cells the door moves into must be empty.
		for (Cell cell : to) {
			if (from.stream().noneMatch(other -> other.pos().equals(cell.pos()))) {
				BlockState there = level.getBlockState(cell.pos());
				if (!there.isAir() && !(there.canBeReplaced() && there.getFluidState().isEmpty())) {
					return false;
				}
			}
		}
		// A leaf that reaches past the cell that draws it needs the next cell free to swing in.
		if (open && this.reach > 0) {
			Direction facing = door.getValue(FACING);
			for (Cell cell : to) {
				if (this.carriesLeaf(door, cell.part())) {
					BlockPos front = cell.pos().relative(facing);
					if (!level.getBlockState(front).getCollisionShape(level, front).isEmpty()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	// ---- mobs -----------------------------------------------------------------------------

	/** True for a door villagers may open: the wooden ones. */
	public boolean opensForMobs() {
		return this.opensForMobs;
	}

	/** How the path finder sees a cell: like a vanilla door of wood or of iron. */
	public PathType pathType(BlockState state) {
		if (state.getValue(OPEN)) {
			return PathType.DOOR_OPEN;
		}
		return this.opensForMobs ? PathType.DOOR_WOOD_CLOSED : PathType.DOOR_IRON_CLOSED;
	}

	/** The middle of the doorway, at floor height. */
	public Vec3 middleOf(BlockState state, BlockPos pos) {
		BlockPos origin = this.originOf(state, pos);
		Direction wall = state.getValue(FACING).getClockWise();
		double along = (this.width - 1) / 2.0;
		return Vec3.atBottomCenterOf(origin).add(wall.getStepX() * along, 0, wall.getStepZ() * along);
	}

	/**
	 * Opens or closes the door without a hand. Returns where its left bottom cell now is, or
	 * null if the door could not move.
	 */
	public @Nullable BlockPos setOpen(Level level, BlockState state, BlockPos pos, boolean open) {
		if (!this.swing(level, state, pos, open, state.getValue(POWERED), false)) {
			return null;
		}
		return this.cells(state, this.originOf(state, pos), open).get(0).pos();
	}

	// ---- copper ---------------------------------------------------------------------------

	/** Sets what this door weathers to. A fresh (unweathered) door is slower, as in vanilla. */
	public void weathersTo(GrandDoorBlock next, boolean fresh) {
		this.weathersTo = next;
		this.weatherChance = fresh ? 0.75F : 1.0F;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// Only one cell counts, or a big door would weather faster than a small one.
		if (this.weathersTo != null && this.partOf(state) == 0 && this.rowOf(state) == 0
				&& random.nextFloat() < WEATHER_TRY && random.nextFloat() < this.weatherChance) {
			level.setBlockAndUpdate(pos, this.weathersTo.withPropertiesOf(state));
		}
	}

	/** True if {@code now} is this very cell turned into another door of the same build. */
	private boolean isTwin(BlockState was, BlockState now) {
		return now.getBlock() instanceof GrandDoorBlock other
			&& other != this
			&& other.width == this.width && other.height == this.height && other.split == this.split
			&& other.withPropertiesOf(was) == now;
	}

	/** One cell became another door (weathered, waxed, scraped): the rest follow it. */
	private void convertRest(Level level, BlockPos pos, BlockState was, Block twin) {
		BUSY.set(true);
		try {
			for (Cell cell : this.cells(was, this.originOf(was, pos), was.getValue(OPEN))) {
				BlockState there = level.getBlockState(cell.pos());
				if (!cell.pos().equals(pos) && there.is(this)) {
					level.setBlock(cell.pos(), twin.withPropertiesOf(there), QUIET);
				}
			}
		} finally {
			BUSY.set(false);
		}
		// The old door's look-out for its redstone does not carry over to the new block.
		if (was.getValue(OPEN) && was.getValue(POWERED) && this.wanders(was)) {
			level.scheduleTick(pos, twin, SIGNAL_POLL_TICKS);
		}
	}

	// ---- redstone -------------------------------------------------------------------------

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
		if (!level.isClientSide() && !BUSY.get()) {
			this.followSignal(level, state, pos);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(OPEN) && state.getValue(POWERED) && this.wanders(state)) {
			this.followSignal(level, state, pos);
			BlockState now = level.getBlockState(pos);
			if (now.is(this) && now.getValue(OPEN) && now.getValue(POWERED)) {
				level.scheduleTick(pos, this, SIGNAL_POLL_TICKS);
			}
		}
	}

	/** Opens on a rising signal and closes on a falling one, like a vanilla door. */
	private void followSignal(Level level, BlockState state, BlockPos pos) {
		if (!this.isWhole(level, state, pos)) {
			return;
		}
		boolean open = state.getValue(OPEN);
		BlockPos origin = this.originOf(state, pos);
		boolean signal = false;
		for (Cell cell : this.cells(state, origin, false)) {
			signal |= level.hasNeighborSignal(cell.pos());
		}
		if (open && this.wanders(state)) {
			for (Cell cell : this.cells(state, origin, true)) {
				signal |= level.hasNeighborSignal(cell.pos());
			}
		}
		if (signal != state.getValue(POWERED)) {
			if (signal == open || !this.swing(level, state, pos, signal, signal, true)) {
				this.swing(level, state, pos, open, signal, false);
			}
		}
	}

	/** False while a door is still being put down cell by cell, by a command or a structure. */
	private boolean isWhole(Level level, BlockState state, BlockPos pos) {
		BlockPos origin = this.originOf(state, pos);
		for (Cell cell : this.cells(state, origin, state.getValue(OPEN))) {
			if (!level.getBlockState(cell.pos()).is(this)) {
				return false;
			}
		}
		return true;
	}

	// ---- breaking -------------------------------------------------------------------------

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide()) {
			// The loot table only drops from the left bottom cell, so breaking any other cell
			// drops that one here: one item for the whole door.
			Cell anchor = this.cells(state, this.originOf(state, pos), state.getValue(OPEN)).get(0);
			BlockState anchorState = level.getBlockState(anchor.pos());
			if (!anchor.pos().equals(pos) && anchorState.is(this) && !player.isCreative() && player.hasCorrectToolForDrops(anchorState)) {
				Block.dropResources(anchorState, level, pos);
			}
			this.removeRest(level, pos, state);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		if (BUSY.get()) {
			return;
		}
		BlockState now = level.getBlockState(pos);
		if (this.isTwin(state, now)) {
			this.convertRest(level, pos, state, now.getBlock());
		} else {
			this.removeRest(level, pos, state);
		}
	}

	/** Takes away every other cell of the door that {@code state} at {@code pos} belongs to. */
	private void removeRest(Level level, BlockPos pos, BlockState state) {
		List<BlockPos> cleared = new ArrayList<>();
		BUSY.set(true);
		try {
			for (Cell cell : this.cells(state, this.originOf(state, pos), state.getValue(OPEN))) {
				if (!cell.pos().equals(pos) && level.getBlockState(cell.pos()).is(this)) {
					level.setBlock(cell.pos(), Blocks.AIR.defaultBlockState(), QUIET_NO_DROPS);
					cleared.add(cell.pos());
				}
			}
		} finally {
			BUSY.set(false);
		}
		for (BlockPos gone : cleared) {
			level.updateNeighborsAt(gone, this);
			level.getBlockState(gone).updateNeighbourShapes(level, gone, Block.UPDATE_ALL);
		}
	}

	// ---- structures -----------------------------------------------------------------------

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		if (mirror == Mirror.NONE) {
			return state;
		}
		BlockState turned = this.rotate(state, mirror.getRotation(state.getValue(FACING)));
		if (this.width > 1) {
			turned = turned.setValue(PARTS[this.width], this.width - 1 - this.partOf(state));
		}
		return this.split ? turned : turned.cycle(HINGE);
	}
}
