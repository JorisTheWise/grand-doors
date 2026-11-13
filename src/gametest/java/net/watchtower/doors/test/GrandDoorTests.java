package net.watchtower.doors.test;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.watchtower.doors.GrandDoorBlock;

/**
 * Doors are put down cell by cell, closed, the way the city generator does it by setblock.
 * The test plot is 8x8x8; the floor is y 0 and doors stand on y 1.
 */
public class GrandDoorTests {
	private static final BlockPos ORIGIN = new BlockPos(3, 1, 3);

	// ---- helpers --------------------------------------------------------------------------

	private static GrandDoorBlock door(String name) {
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("granddoors", name));
		if (block instanceof GrandDoorBlock door) {
			return door;
		}
		throw new IllegalStateException("not registered: " + name);
	}

	private static IntegerProperty row(GrandDoorBlock door) {
		return (IntegerProperty) door.getStateDefinition().getProperty("row");
	}

	private static BlockPos closedCell(Direction facing, int part, int row) {
		return ORIGIN.relative(facing.getClockWise(), part).above(row);
	}

	/** Where a single door's cell is when open. */
	private static BlockPos openCell(Direction facing, DoorHingeSide hinge, int part, int row) {
		int at = hinge == DoorHingeSide.LEFT ? 0 : 2;
		return ORIGIN.relative(facing.getClockWise(), at).relative(facing, Math.abs(part - at)).above(row);
	}

	private static List<BlockPos> closedCells(GrandDoorBlock door, Direction facing) {
		List<BlockPos> all = new ArrayList<>();
		for (int part = 0; part < door.width(); part++) {
			for (int row = 0; row < door.height(); row++) {
				all.add(closedCell(facing, part, row));
			}
		}
		return all;
	}

	private static List<BlockPos> openCells(GrandDoorBlock door, Direction facing, DoorHingeSide hinge) {
		List<BlockPos> all = new ArrayList<>();
		for (int part = 0; part < 3; part++) {
			for (int row = 0; row < door.height(); row++) {
				all.add(openCell(facing, hinge, part, row));
			}
		}
		return all;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	private static void build(GameTestHelper helper, GrandDoorBlock door, Direction facing, DoorHingeSide hinge) {
		build(helper, door, facing, hinge, false);
	}

	private static void build(GameTestHelper helper, GrandDoorBlock door, Direction facing, DoorHingeSide hinge, boolean front) {
		for (int part = 0; part < door.width(); part++) {
			for (int row = 0; row < door.height(); row++) {
				BlockState state = door.defaultBlockState()
					.setValue(GrandDoorBlock.FACING, facing)
					.setValue(GrandDoorBlock.FRONT, front)
					.setValue(row(door), row);
				if (door.partProperty() != null) {
					state = state.setValue(door.partProperty(), part);
				}
				if (!door.split()) {
					state = state.setValue(GrandDoorBlock.HINGE, hinge);
				}
				helper.setBlock(closedCell(facing, part, row), state);
			}
		}
	}

	private static void use(GameTestHelper helper, BlockPos pos) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.useBlock(pos, player);
	}

	private static void mine(GameTestHelper helper, BlockPos pos, ItemStack tool) {
		if (helper.makeMockServerPlayer(GameType.SURVIVAL) instanceof ServerPlayer player) {
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tool);
			player.gameMode.destroyBlock(helper.absolutePos(pos));
		} else {
			helper.fail(Component.literal("the mock server player is not a ServerPlayer"));
		}
	}

	private static void assertDoor(GameTestHelper helper, GrandDoorBlock door, List<BlockPos> cells, boolean open) {
		for (BlockPos cell : cells) {
			helper.assertBlockPresent(door, cell);
			helper.assertTrue(helper.getBlockState(cell).getValue(GrandDoorBlock.OPEN) == open,
				Component.literal("cell " + cell + " should have open=" + open));
		}
	}

	private static void assertGone(GameTestHelper helper, List<BlockPos> cells) {
		for (BlockPos cell : cells) {
			helper.assertBlockPresent(Blocks.AIR, cell);
		}
	}

	private static void assertDropped(GameTestHelper helper, GrandDoorBlock door, int expected) {
		AABB plot = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(10);
		int count = 0;
		for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, plot)) {
			if (item.getItem().is(door.asItem())) {
				count += item.getItem().getCount();
			}
		}
		int found = count;
		helper.assertTrue(found == expected, Component.literal("dropped " + found + " door items, expected " + expected));
	}

	// ---- split doors ----------------------------------------------------------------------

	private static void splitByHand(GameTestHelper helper, String name, Direction facing) {
		GrandDoorBlock door = door(name);
		floor(helper);
		build(helper, door, facing, DoorHingeSide.LEFT);
		List<BlockPos> cells = closedCells(door, facing);
		helper.startSequence()
			.thenExecute(() -> assertDoor(helper, door, cells, false))
			.thenExecute(() -> use(helper, closedCell(facing, 1, 0)))
			.thenIdle(2)
			.thenExecute(() -> {
				assertDoor(helper, door, cells, true);
				BlockPos end = closedCell(facing, 0, 0);
				BlockState state = helper.getBlockState(end);
				AABB leaf = state.getCollisionShape(helper.getLevel(), helper.absolutePos(end)).bounds();
				double reach = Math.max(leaf.getXsize(), leaf.getZsize());
				double want = door.width() / 2.0;
				helper.assertTrue(Math.abs(reach - want) < 1e-6, Component.literal("open leaf should be " + want + " long, was " + reach));
				helper.assertTrue(state.hasLargeCollisionShape() == want > 1, Component.literal("open leaf collides past its cell only if longer than it"));
				for (int part = 1; part < door.width() - 1; part++) {
					BlockPos middle = closedCell(facing, part, 0);
					helper.assertTrue(helper.getBlockState(middle).getCollisionShape(helper.getLevel(), helper.absolutePos(middle)).isEmpty(),
						Component.literal("the middle of an open split door should be free"));
				}
			})
			.thenExecute(() -> use(helper, closedCell(facing, door.width() - 1, door.height() - 1)))
			.thenIdle(2)
			.thenExecute(() -> assertDoor(helper, door, cells, false))
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void splitOpensByHandNorth(GameTestHelper helper) {
		splitByHand(helper, "oak_split_door_3x2", Direction.NORTH);
	}

	@GameTest(maxTicks = 100)
	public void splitOpensByHandEast(GameTestHelper helper) {
		splitByHand(helper, "spruce_split_door_3x3", Direction.EAST);
	}

	@GameTest(maxTicks = 100)
	public void splitOpensByHandSouth(GameTestHelper helper) {
		splitByHand(helper, "dark_oak_split_door_3x4", Direction.SOUTH);
	}

	@GameTest(maxTicks = 100)
	public void splitOpensByHandWest(GameTestHelper helper) {
		splitByHand(helper, "oak_split_door_3x2", Direction.WEST);
	}

	@GameTest(maxTicks = 100)
	public void stoneOpensByHand(GameTestHelper helper) {
		splitByHand(helper, "stone_brick_split_door_3x3", Direction.NORTH);
	}

	@GameTest(maxTicks = 100)
	public void tallStoneOpensByHand(GameTestHelper helper) {
		splitByHand(helper, "cobblestone_split_door_3x4", Direction.WEST);
	}

	@GameTest(maxTicks = 100)
	public void doubleDoorOpensInPlace(GameTestHelper helper) {
		splitByHand(helper, "oak_split_door_2x2", Direction.NORTH);
	}

	@GameTest(maxTicks = 100)
	public void fourWideArchedOpens(GameTestHelper helper) {
		splitByHand(helper, "oak_arched_split_door_4x3", Direction.EAST);
	}

	@GameTest(maxTicks = 100)
	public void ironGateOpensByHand(GameTestHelper helper) {
		splitByHand(helper, "iron_arched_split_gate_3x3", Direction.SOUTH);
	}

	@GameTest(maxTicks = 100)
	public void ironDoorOpensByHand(GameTestHelper helper) {
		splitByHand(helper, "iron_split_door_4x4", Direction.WEST);
	}

	/** A single leaf one or two blocks long opens without its cells moving. */
	private static void singleInPlace(GameTestHelper helper, String name, Direction facing, DoorHingeSide hinge) {
		GrandDoorBlock door = door(name);
		floor(helper);
		build(helper, door, facing, hinge);
		List<BlockPos> cells = closedCells(door, facing);
		int hingePart = hinge == DoorHingeSide.LEFT ? 0 : door.width() - 1;
		helper.startSequence()
			.thenExecute(() -> use(helper, closedCell(facing, 0, 0)))
			.thenIdle(2)
			.thenExecute(() -> {
				assertDoor(helper, door, cells, true);
				for (int part = 0; part < door.width(); part++) {
					BlockPos cell = closedCell(facing, part, 1);
					var shape = helper.getBlockState(cell).getCollisionShape(helper.getLevel(), helper.absolutePos(cell));
					if (part == hingePart) {
						double reach = Math.max(shape.bounds().getXsize(), shape.bounds().getZsize());
						helper.assertTrue(Math.abs(reach - door.width()) < 1e-6,
							Component.literal("open leaf should be " + door.width() + " long, was " + reach));
					} else {
						helper.assertTrue(shape.isEmpty(), Component.literal("only the hinge column carries the open leaf"));
					}
				}
			})
			.thenExecute(() -> use(helper, closedCell(facing, hingePart, 1)))
			.thenIdle(2)
			.thenExecute(() -> assertDoor(helper, door, cells, false))
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void oneWideOpens(GameTestHelper helper) {
		singleInPlace(helper, "oak_door_1x2", Direction.NORTH, DoorHingeSide.RIGHT);
	}

	@GameTest(maxTicks = 100)
	public void oneWideArchedStoneOpens(GameTestHelper helper) {
		singleInPlace(helper, "stone_brick_door_1x3", Direction.EAST, DoorHingeSide.LEFT);
	}

	@GameTest(maxTicks = 100)
	public void twoWideSingleOpensLeft(GameTestHelper helper) {
		singleInPlace(helper, "oak_arched_door_2x3", Direction.WEST, DoorHingeSide.LEFT);
	}

	@GameTest(maxTicks = 100)
	public void twoWideSingleGateOpensRight(GameTestHelper helper) {
		singleInPlace(helper, "iron_gate_2x2", Direction.SOUTH, DoorHingeSide.RIGHT);
	}

	@GameTest(maxTicks = 100)
	public void twoWideSingleStaysShutWhenBlocked(GameTestHelper helper) {
		GrandDoorBlock door = door("oak_door_2x2");
		Direction facing = Direction.NORTH;
		floor(helper);
		build(helper, door, facing, DoorHingeSide.RIGHT);
		BlockPos pillar = closedCell(facing, 1, 0).relative(facing);
		helper.setBlock(pillar, Blocks.STONE);
		helper.startSequence()
			.thenExecute(() -> use(helper, closedCell(facing, 0, 0)))
			.thenIdle(2)
			.thenExecute(() -> assertDoor(helper, door, closedCells(door, facing), false))
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void oneWideDropsOne(GameTestHelper helper) {
		GrandDoorBlock door = door("oak_door_1x4");
		Direction facing = Direction.NORTH;
		floor(helper);
		build(helper, door, facing, DoorHingeSide.LEFT);
		helper.startSequence()
			.thenExecute(() -> mine(helper, closedCell(facing, 0, 3), ItemStack.EMPTY))
			.thenIdle(2)
			.thenExecute(() -> {
				assertGone(helper, closedCells(door, facing));
				assertDropped(helper, door, 1);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void splitStaysShutWhenBlocked(GameTestHelper helper) {
		GrandDoorBlock door = door("oak_split_door_3x2");
		Direction facing = Direction.NORTH;
		floor(helper);
		build(helper, door, facing, DoorHingeSide.LEFT);
		BlockPos pillar = closedCell(facing, 2, 1).relative(facing);
		helper.setBlock(pillar, Blocks.STONE);
		helper.startSequence()
			.thenExecute(() -> use(helper, closedCell(facing, 1, 0)))
			.thenIdle(2)
			.thenExecute(() -> {
				assertDoor(helper, door, closedCells(door, facing), false);
				helper.assertBlockPresent(Blocks.STONE, pillar);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void splitFollowsRedstone(GameTestHelper helper) {
		GrandDoorBlock door = door("oak_split_door_3x2");
		Direction facing = Direction.EAST;
		floor(helper);
		build(helper, door, facing, DoorHingeSide.LEFT);
		List<BlockPos> cells = closedCells(door, facing);
		BlockPos power = closedCell(facing, 0, 0).below();
		helper.startSequence()
			.thenExecute(() -> helper.setBlock(power, Blocks.REDSTONE_BLOCK))
			.thenIdle(4)
			.thenExecute(() -> assertDoor(helper, door, cells, true))
			.thenExecute(() -> helper.setBlock(power, Blocks.STONE))
			.thenIdle(4)
			.thenExecute(() -> assertDoor(helper, door, cells, false))
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void builtBesideSignalOpens(GameTestHelper helper) {
		GrandDoorBlock door = door("oak_split_door_3x2");
		Direction facing = Direction.NORTH;
		floor(helper);
		helper.setBlock(closedCell(facing, 1, 0).below(), Blocks.REDSTONE_BLOCK);
		build(helper, door, facing, DoorHingeSide.LEFT);
		helper.startSequence()
			.thenIdle(4)
			.thenExecute(() -> assertDoor(helper, door, closedCells(door, facing), true))
			.thenSucceed();
	}

	// ---- single doors ---------------------------------------------------------------------

	private static void singleByHand(GameTestHelper helper, String name, Direction facing, DoorHingeSide hinge) {
		GrandDoorBlock door = door(name);
		floor(helper);
		build(helper, door, facing, hinge);
		List<BlockPos> closed = closedCells(door, facing);
		List<BlockPos> open = openCells(door, facing, hinge);
		helper.startSequence()
			.thenExecute(() -> use(helper, closedCell(facing, 1, 0)))
			.thenIdle(2)
			.thenExecute(() -> {
				assertDoor(helper, door, open, true);
				for (BlockPos cell : closed) {
					if (!open.contains(cell)) {
						helper.assertBlockPresent(Blocks.AIR, cell);
					}
				}
			})
			.thenExecute(() -> use(helper, openCell(facing, hinge, 1, 1)))
			.thenIdle(2)
			.thenExecute(() -> {
				assertDoor(helper, door, closed, false);
				for (BlockPos cell : open) {
					if (!closed.contains(cell)) {
						helper.assertBlockPresent(Blocks.AIR, cell);
					}
				}
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void singleOpensLeftNorth(GameTestHelper helper) {
		singleByHand(helper, "oak_door_3x4", Direction.NORTH, DoorHingeSide.LEFT);
	}

	@GameTest(maxTicks = 100)
	public void singleOpensRightNorth(GameTestHelper helper) {
		singleByHand(helper, "oak_door_3x2", Direction.NORTH, DoorHingeSide.RIGHT);
	}

	@GameTest(maxTicks = 100)
	public void singleOpensLeftWest(GameTestHelper helper) {
		singleByHand(helper, "birch_door_3x3", Direction.WEST, DoorHingeSide.LEFT);
	}

	@GameTest(maxTicks = 100)
	public void singleOpensRightEast(GameTestHelper helper) {
		singleByHand(helper, "oak_door_3x4", Direction.EAST, DoorHingeSide.RIGHT);
	}

	@GameTest(maxTicks = 100)
	public void singleStaysShutWhenBlocked(GameTestHelper helper) {
		GrandDoorBlock door = door("oak_door_3x2");
		Direction facing = Direction.NORTH;
		floor(helper);
		build(helper, door, facing, DoorHingeSide.LEFT);
		BlockPos pillar = openCell(facing, DoorHingeSide.LEFT, 2, 0);
		helper.setBlock(pillar, Blocks.STONE);
		helper.startSequence()
			.thenExecute(() -> use(helper, closedCell(facing, 1, 0)))
			.thenIdle(2)
			.thenExecute(() -> {
				assertDoor(helper, door, closedCells(door, facing), false);
				helper.assertBlockPresent(Blocks.STONE, pillar);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void singleFollowsRedstone(GameTestHelper helper) {
		GrandDoorBlock door = door("oak_door_3x2");
		Direction facing = Direction.NORTH;
		floor(helper);
		build(helper, door, facing, DoorHingeSide.LEFT);
		// Under the far end: the cell above it is empty while the door is open.
		BlockPos power = closedCell(facing, 2, 0).below();
		helper.startSequence()
			.thenExecute(() -> helper.setBlock(power, Blocks.REDSTONE_BLOCK))
			.thenIdle(4)
			.thenExecute(() -> assertDoor(helper, door, openCells(door, facing, DoorHingeSide.LEFT), true))
			.thenExecute(() -> helper.setBlock(power, Blocks.STONE))
			.thenIdle(12)
			.thenExecute(() -> assertDoor(helper, door, closedCells(door, facing), false))
			.thenSucceed();
	}

	// ---- doors on the front face ----------------------------------------------------------

	/** Open cells of a door with front=true: every cell that carries leaf is one cell out. */
	private static List<BlockPos> frontOpenCells(GrandDoorBlock door, Direction facing, DoorHingeSide hinge) {
		boolean moves = !door.split() && door.width() > 2;
		int hingePart = hinge == DoorHingeSide.LEFT ? 0 : door.width() - 1;
		List<BlockPos> all = new ArrayList<>();
		for (int part = 0; part < door.width(); part++) {
			boolean carries = moves || (door.split() ? part == 0 || part == door.width() - 1 : part == hingePart);
			for (int row = 0; row < door.height(); row++) {
				BlockPos at = moves ? openCell(facing, hinge, part, row) : closedCell(facing, part, row);
				all.add(carries ? at.relative(facing) : at);
			}
		}
		return all;
	}

	private static void frontByHand(GameTestHelper helper, String name, Direction facing, DoorHingeSide hinge) {
		GrandDoorBlock door = door(name);
		floor(helper);
		build(helper, door, facing, hinge, true);
		List<BlockPos> closed = closedCells(door, facing);
		List<BlockPos> open = frontOpenCells(door, facing, hinge);
		helper.startSequence()
			.thenExecute(() -> {
				assertDoor(helper, door, closed, false);
				BlockPos cell = closed.get(0);
				AABB leaf = helper.getBlockState(cell).getShape(helper.getLevel(), helper.absolutePos(cell)).bounds();
				// The closed leaf lies against the face the door swings towards.
				double toFace = switch (facing) {
					case NORTH -> leaf.minZ;
					case SOUTH -> 1 - leaf.maxZ;
					case WEST -> leaf.minX;
					default -> 1 - leaf.maxX;
				};
				helper.assertTrue(Math.abs(toFace) < 1e-6, Component.literal("closed leaf should lie on the front face"));
			})
			.thenExecute(() -> use(helper, closed.get(0)))
			.thenIdle(2)
			.thenExecute(() -> {
				assertDoor(helper, door, open, true);
				for (BlockPos cell : closed) {
					if (!open.contains(cell)) {
						helper.assertBlockPresent(Blocks.AIR, cell);
					}
				}
			})
			.thenExecute(() -> use(helper, open.get(0)))
			.thenIdle(2)
			.thenExecute(() -> {
				assertDoor(helper, door, closed, false);
				for (BlockPos cell : open) {
					if (!closed.contains(cell)) {
						helper.assertBlockPresent(Blocks.AIR, cell);
					}
				}
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void frontSplitThreeWide(GameTestHelper helper) {
		frontByHand(helper, "oak_split_door_3x2", Direction.NORTH, DoorHingeSide.LEFT);
	}

	@GameTest(maxTicks = 100)
	public void frontArchedSplitFourWide(GameTestHelper helper) {
		frontByHand(helper, "stone_brick_split_door_4x3", Direction.EAST, DoorHingeSide.LEFT);
	}

	@GameTest(maxTicks = 100)
	public void frontDoubleDoor(GameTestHelper helper) {
		frontByHand(helper, "iron_split_gate_2x2", Direction.SOUTH, DoorHingeSide.LEFT);
	}

	@GameTest(maxTicks = 100)
	public void frontOneWide(GameTestHelper helper) {
		frontByHand(helper, "oak_arched_door_1x3", Direction.WEST, DoorHingeSide.RIGHT);
	}

	@GameTest(maxTicks = 100)
	public void frontSingleTwoWide(GameTestHelper helper) {
		frontByHand(helper, "oak_door_2x2", Direction.NORTH, DoorHingeSide.RIGHT);
	}

	@GameTest(maxTicks = 100)
	public void frontSingleThreeWide(GameTestHelper helper) {
		frontByHand(helper, "oak_door_3x2", Direction.NORTH, DoorHingeSide.LEFT);
	}

	@GameTest(maxTicks = 100)
	public void frontStaysShutWhenBlocked(GameTestHelper helper) {
		GrandDoorBlock door = door("oak_split_door_3x2");
		Direction facing = Direction.NORTH;
		floor(helper);
		build(helper, door, facing, DoorHingeSide.LEFT, true);
		BlockPos pillar = closedCell(facing, 0, 1).relative(facing);
		helper.setBlock(pillar, Blocks.STONE);
		helper.startSequence()
			.thenExecute(() -> use(helper, closedCell(facing, 1, 0)))
			.thenIdle(2)
			.thenExecute(() -> {
				assertDoor(helper, door, closedCells(door, facing), false);
				helper.assertBlockPresent(Blocks.STONE, pillar);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void frontFollowsRedstone(GameTestHelper helper) {
		GrandDoorBlock door = door("oak_split_door_3x2");
		Direction facing = Direction.WEST;
		floor(helper);
		build(helper, door, facing, DoorHingeSide.LEFT, true);
		// Under an end column: the cell above it is empty while the door is open.
		BlockPos power = closedCell(facing, 0, 0).below();
		helper.startSequence()
			.thenExecute(() -> helper.setBlock(power, Blocks.REDSTONE_BLOCK))
			.thenIdle(4)
			.thenExecute(() -> assertDoor(helper, door, frontOpenCells(door, facing, DoorHingeSide.LEFT), true))
			.thenExecute(() -> helper.setBlock(power, Blocks.STONE))
			.thenIdle(12)
			.thenExecute(() -> assertDoor(helper, door, closedCells(door, facing), false))
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void breakingAnOpenFrontDoorDropsOne(GameTestHelper helper) {
		GrandDoorBlock door = door("oak_split_door_3x2");
		Direction facing = Direction.NORTH;
		floor(helper);
		build(helper, door, facing, DoorHingeSide.LEFT, true);
		List<BlockPos> open = frontOpenCells(door, facing, DoorHingeSide.LEFT);
		helper.startSequence()
			.thenExecute(() -> use(helper, closedCell(facing, 1, 0)))
			.thenIdle(2)
			.thenExecute(() -> mine(helper, open.get(open.size() - 1), ItemStack.EMPTY))
			.thenIdle(2)
			.thenExecute(() -> {
				assertGone(helper, open);
				assertGone(helper, closedCells(door, facing));
				assertDropped(helper, door, 1);
			})
			.thenSucceed();
	}

	// ---- redstone that watches a door -----------------------------------------------------

	/** An observer looking at a door cell pulses when the door is opened by hand. */
	private static void observerSees(GameTestHelper helper, String name, boolean front) {
		GrandDoorBlock door = door(name);
		Direction facing = Direction.NORTH;
		Direction wall = facing.getClockWise();
		floor(helper);
		build(helper, door, facing, DoorHingeSide.LEFT, front);
		// West of the left column: an observer looking east at it, and a bulb behind the
		// observer that flips on every pulse.
		BlockPos watcher = closedCell(facing, 0, 0).relative(wall.getOpposite());
		BlockPos bulb = watcher.relative(wall.getOpposite());
		Block copperBulb = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_bulb"));
		helper.setBlock(bulb, copperBulb);
		helper.setBlock(watcher, Blocks.OBSERVER.defaultBlockState().setValue(net.minecraft.world.level.block.ObserverBlock.FACING, wall));
		helper.startSequence()
			.thenIdle(10)
			.thenExecute(() -> helper.assertTrue(!helper.getBlockState(bulb).getValue(net.minecraft.world.level.block.CopperBulbBlock.LIT),
				Component.literal("the bulb should start dark")))
			.thenExecute(() -> use(helper, closedCell(facing, door.width() - 1, 0)))
			.thenIdle(10)
			.thenExecute(() -> helper.assertTrue(helper.getBlockState(bulb).getValue(net.minecraft.world.level.block.CopperBulbBlock.LIT),
				Component.literal("the observer should have seen the door open")))
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void observerSeesASplitDoorOpen(GameTestHelper helper) {
		observerSees(helper, "oak_split_door_3x2", false);
	}

	@GameTest(maxTicks = 100)
	public void observerSeesAFrontDoorOpen(GameTestHelper helper) {
		observerSees(helper, "oak_split_door_3x2", true);
	}

	@GameTest(maxTicks = 100)
	public void buttonOpensThenCloses(GameTestHelper helper) {
		GrandDoorBlock door = door("iron_split_gate_3x2");
		Direction facing = Direction.NORTH;
		floor(helper);
		build(helper, door, facing, DoorHingeSide.LEFT);
		List<BlockPos> cells = closedCells(door, facing);
		// A wall block beside the door with a stone button on it, facing the way the door opens.
		BlockPos post = closedCell(facing, 0, 0).relative(facing.getCounterClockWise());
		BlockPos button = post.relative(facing);
		helper.setBlock(post, Blocks.STONE);
		helper.setBlock(button, Blocks.STONE_BUTTON.defaultBlockState()
			.setValue(net.minecraft.world.level.block.ButtonBlock.FACING, facing));
		helper.startSequence()
			.thenExecute(() -> use(helper, button))
			.thenIdle(4)
			.thenExecute(() -> assertDoor(helper, door, cells, true))
			.thenIdle(30)
			.thenExecute(() -> assertDoor(helper, door, cells, false))
			.thenSucceed();
	}

	// ---- copper ---------------------------------------------------------------------------

	/** Turning one cell into its twin (what an axe, honeycomb or time does) turns the whole door. */
	private static void becomes(GameTestHelper helper, String from, String to, boolean open) {
		GrandDoorBlock door = door(from);
		GrandDoorBlock twin = door(to);
		Direction facing = Direction.NORTH;
		floor(helper);
		build(helper, door, facing, DoorHingeSide.LEFT);
		List<BlockPos> cells = closedCells(door, facing);
		BlockPos changed = closedCell(facing, door.width() - 1, 1);
		helper.startSequence()
			.thenExecute(() -> {
				if (open) {
					use(helper, cells.get(0));
				}
			})
			.thenIdle(2)
			.thenExecute(() -> helper.setBlock(changed, twin.withPropertiesOf(helper.getBlockState(changed))))
			.thenIdle(2)
			.thenExecute(() -> {
				assertDoor(helper, twin, cells, open);
				assertDropped(helper, door, 0);
				assertDropped(helper, twin, 0);
			})
			.thenExecute(() -> use(helper, cells.get(0)))
			.thenIdle(2)
			.thenExecute(() -> assertDoor(helper, twin, cells, !open))
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void copperWeathersAsOneDoor(GameTestHelper helper) {
		becomes(helper, "copper_split_door_3x3", "exposed_copper_split_door_3x3", false);
	}

	@GameTest(maxTicks = 100)
	public void copperIsWaxedAsOneDoor(GameTestHelper helper) {
		becomes(helper, "weathered_copper_arched_split_door_4x3",
			"waxed_weathered_copper_arched_split_door_4x3", true);
	}

	@GameTest(maxTicks = 100)
	public void copperIsScrapedAsOneDoor(GameTestHelper helper) {
		becomes(helper, "oxidized_copper_door_2x2", "weathered_copper_door_2x2", false);
	}

	@GameTest(maxTicks = 100)
	public void glazedDoorOpens(GameTestHelper helper) {
		splitByHand(helper, "oak_arched_split_glazed_door_3x3", Direction.NORTH);
	}

	// ---- villagers ------------------------------------------------------------------------

	/** A room cut in two by a wall with a door in it; a villager has to get to the other half. */
	private static void villagerCrosses(GameTestHelper helper, String name, boolean front, boolean shouldCross) {
		GrandDoorBlock door = door(name);
		Direction facing = Direction.NORTH;
		floor(helper);
		for (int x = 0; x < 8; x++) {
			for (int y = 1; y <= 4; y++) {
				for (int z = 0; z < 8; z++) {
					if (x == 0 || x == 7 || z == 0 || z == 7 || z == ORIGIN.getZ()) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
					}
				}
			}
		}
		for (BlockPos cell : closedCells(door, facing)) {
			helper.setBlock(cell, Blocks.AIR);
		}
		build(helper, door, facing, DoorHingeSide.LEFT, front);
		BlockPos start = new BlockPos(4, 1, 5);
		BlockPos goal = new BlockPos(4, 1, 1);
		var villager = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.VILLAGER, start);
		helper.walkTo(villager, goal, 0.6F);
		if (shouldCross) {
			helper.succeedWhen(() -> helper.assertTrue(villager.blockPosition().getZ() < helper.absolutePos(ORIGIN).getZ(),
				Component.literal("the villager should get through the door")));
		} else {
			helper.runAfterDelay(150, () -> {
				helper.assertTrue(villager.blockPosition().getZ() > helper.absolutePos(ORIGIN).getZ(),
					Component.literal("the villager should not get through this door"));
				assertDoor(helper, door, closedCells(door, facing), false);
				helper.succeed();
			});
		}
	}

	@GameTest(maxTicks = 300)
	public void villagerOpensAWoodenDoor(GameTestHelper helper) {
		villagerCrosses(helper, "oak_split_door_3x2", false, true);
	}

	@GameTest(maxTicks = 300)
	public void villagerOpensAOneWideDoor(GameTestHelper helper) {
		villagerCrosses(helper, "spruce_door_1x2", false, true);
	}

	@GameTest(maxTicks = 300)
	public void villagerOpensAFrontDoor(GameTestHelper helper) {
		villagerCrosses(helper, "oak_split_glazed_door_2x3", true, true);
	}

	@GameTest(maxTicks = 300)
	public void villagerLeavesAnIronDoorShut(GameTestHelper helper) {
		villagerCrosses(helper, "iron_split_door_3x2", false, false);
	}

	// ---- breaking -------------------------------------------------------------------------

	@GameTest(maxTicks = 100)
	public void breakingAnyCellDropsOne(GameTestHelper helper) {
		GrandDoorBlock door = door("oak_split_door_3x4");
		Direction facing = Direction.NORTH;
		floor(helper);
		build(helper, door, facing, DoorHingeSide.LEFT);
		helper.startSequence()
			.thenExecute(() -> mine(helper, closedCell(facing, 1, 2), ItemStack.EMPTY))
			.thenIdle(2)
			.thenExecute(() -> {
				assertGone(helper, closedCells(door, facing));
				assertDropped(helper, door, 1);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void breakingTheAnchorDropsOne(GameTestHelper helper) {
		GrandDoorBlock door = door("oak_split_door_3x2");
		Direction facing = Direction.SOUTH;
		floor(helper);
		build(helper, door, facing, DoorHingeSide.LEFT);
		helper.startSequence()
			.thenExecute(() -> mine(helper, closedCell(facing, 0, 0), ItemStack.EMPTY))
			.thenIdle(2)
			.thenExecute(() -> {
				assertGone(helper, closedCells(door, facing));
				assertDropped(helper, door, 1);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void breakingAnOpenSingleDropsOne(GameTestHelper helper) {
		GrandDoorBlock door = door("oak_door_3x2");
		Direction facing = Direction.NORTH;
		DoorHingeSide hinge = DoorHingeSide.RIGHT;
		floor(helper);
		build(helper, door, facing, hinge);
		helper.startSequence()
			.thenExecute(() -> use(helper, closedCell(facing, 1, 0)))
			.thenIdle(2)
			.thenExecute(() -> mine(helper, openCell(facing, hinge, 0, 1), ItemStack.EMPTY))
			.thenIdle(2)
			.thenExecute(() -> {
				assertGone(helper, openCells(door, facing, hinge));
				assertGone(helper, closedCells(door, facing));
				assertDropped(helper, door, 1);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void stoneNeedsAPickaxe(GameTestHelper helper) {
		GrandDoorBlock door = door("stone_brick_split_door_3x3");
		Direction facing = Direction.NORTH;
		floor(helper);
		build(helper, door, facing, DoorHingeSide.LEFT);
		helper.startSequence()
			.thenExecute(() -> mine(helper, closedCell(facing, 2, 1), new ItemStack(Items.IRON_PICKAXE)))
			.thenIdle(2)
			.thenExecute(() -> {
				assertGone(helper, closedCells(door, facing));
				assertDropped(helper, door, 1);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void stoneByHandDropsNothing(GameTestHelper helper) {
		GrandDoorBlock door = door("stone_brick_split_door_3x3");
		Direction facing = Direction.NORTH;
		floor(helper);
		build(helper, door, facing, DoorHingeSide.LEFT);
		helper.startSequence()
			.thenExecute(() -> mine(helper, closedCell(facing, 2, 1), ItemStack.EMPTY))
			.thenIdle(2)
			.thenExecute(() -> {
				assertGone(helper, closedCells(door, facing));
				assertDropped(helper, door, 0);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void replacingOneCellRemovesTheDoor(GameTestHelper helper) {
		GrandDoorBlock door = door("oak_split_door_3x2");
		Direction facing = Direction.NORTH;
		floor(helper);
		build(helper, door, facing, DoorHingeSide.LEFT);
		helper.startSequence()
			.thenExecute(() -> helper.setBlock(closedCell(facing, 1, 1), Blocks.AIR))
			.thenIdle(2)
			.thenExecute(() -> {
				assertGone(helper, closedCells(door, facing));
				assertDropped(helper, door, 0);
			})
			.thenSucceed();
	}
}
