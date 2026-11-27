package net.watchtower.doors.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.levelgen.Heightmap;
import net.watchtower.doors.GrandDoorBlock;

/**
 * Pictures for the mod's page, taken in a real client: little scenes in a generated landscape
 * (the "cinematic" shots, with a shader pack when Iris and GD_SHADER are there) and walls of
 * doors side by side (the catalogue, without shaders). Runs only with GD_GALLERY set; the
 * pictures land in build/run/clientGameTest/screenshots.
 */
public class GalleryShots implements FabricClientGameTest {
	private static final int W = 1920;
	private static final int H = 1080;

	@Override
	public void runTest(ClientGameTestContext context) {
		String mode = System.getenv("GD_GALLERY");
		if (mode == null) {
			return;
		}
		context.getInput().resizeWindow(W, H);
		context.runOnClient(client -> {
			client.options.renderDistance().set(16);
			client.options.enableVsync().set(false);
			client.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
		});
		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(state -> state.setSeed(System.getenv().getOrDefault("GD_SEED", "grand doors")))
				.create()) {
			run(world, "gamerule advance_time false");
			run(world, "gamerule doDaylightCycle false");
			run(world, "gamerule advance_weather false");
			run(world, "gamerule doWeatherCycle false");
			run(world, "gamerule doMobSpawning false");
			run(world, "gamerule spawn_mobs false");
			run(world, "gamerule randomTickSpeed 0");
			run(world, "gamerule random_tick_speed 0");
			run(world, "weather clear");
			run(world, "gamemode spectator @a");
			context.getInput().pressKey(options -> options.keyToggleGui);
			// The catalogue in vanilla's own light, so the colours are true; the scenes with shaders.
			if (!"cinematic".equals(mode)) {
				catalogue(context, world);
			}
			if (!"catalogue".equals(mode)) {
				enableShader(context);
				cinematic(context, world);
			}
		}
	}

	// ---- the catalogue ---------------------------------------------------------------------

	private static void catalogue(ClientGameTestContext context, TestSingleplayerContext world) {
		BlockPos site = site(world, Biomes.MEADOW, 40, 20);
		fov(context, 50);
		time(world, 5000);
		String[] woods = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "bamboo", "crimson", "warped"};

		List<String> doors = new ArrayList<>();
		for (String wood : woods) {
			doors.add(wood + "_split_door_2x3");
		}
		wall(context, world, site, "catalogue-woods", doors, Blocks.STONE_BRICKS, "alternate");

		wall(context, world, site, "catalogue-sizes", List.of("oak_door_1x1", "oak_door_1x2", "oak_split_door_2x2",
				"oak_door_2x3", "oak_split_door_3x2", "oak_split_door_3x3", "oak_arched_split_door_3x3", "oak_door_1x4",
				"oak_split_door_4x3", "oak_arched_split_door_3x4", "oak_arched_split_door_4x4"), Blocks.STONE_BRICKS, "shut");

		wall(context, world, site, "catalogue-looks", List.of("oak_door_1x2", "oak_glazed_door_1x2", "oak_tinted_door_1x2",
				"oak_boarded_door_1x2", "oak_windowed_door_1x2", "oak_lattice_door_1x2", "spruce_split_door_2x2",
				"spruce_split_glazed_door_2x2", "spruce_split_tinted_door_2x2", "spruce_split_boarded_door_2x2",
				"spruce_split_windowed_door_2x2", "spruce_split_lattice_door_2x2"), block("minecraft", "white_terracotta"), "shut");

		wall(context, world, site, "catalogue-materials", List.of("cobblestone_split_door_2x3", "stone_brick_split_door_2x3",
				"cobbled_deepslate_split_door_2x3", "deepslate_brick_split_door_2x3", "iron_split_door_2x3",
				"iron_split_gate_2x3", "copper_split_door_2x3", "exposed_copper_split_door_2x3",
				"weathered_copper_split_door_2x3", "oxidized_copper_split_door_2x3"), Blocks.SMOOTH_STONE, "shut");

		wall(context, world, site, "catalogue-grand", List.of("oak_arched_split_door_4x4", "cherry_arched_split_glazed_door_3x4",
				"stone_brick_split_door_4x4", "iron_arched_split_gate_3x4", "dark_oak_arched_split_door_3x4"),
				Blocks.MUD_BRICKS, "alternate");

		fences(context, world, site, woods);
	}

	/** A wall with the doors in it, side by side with one block between, photographed straight on. */
	private static void wall(ClientGameTestContext context, TestSingleplayerContext world, BlockPos site, String name,
			List<String> doors, Block material, String open) {
		if (!want(name)) {
			return;
		}
		int width = 1;
		int tall = 0;
		for (String door : doors) {
			GrandDoorBlock block = door(door);
			width += block.width() + 1;
			tall = Math.max(tall, block.height());
		}
		int x0 = -width / 2;
		int top = tall + 1;
		int w = width;
		world.getServer().runOnServer(server -> {
			Scene s = new Scene(server.overworld(), site);
			s.clear(-50, 50, -30, 40);
			s.fill(x0 - 1, 0, -1, x0 + w, top, 0, material.defaultBlockState());
			s.fill(x0 - 1, top + 1, 0, x0 + w, top + 1, 0, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
			s.fill(x0 - 1, top + 1, -1, x0 + w, top + 1, -1, Blocks.STONE_BRICK_SLAB.defaultBlockState());
			s.fill(x0 - 2, 0, 1, x0 + w + 1, 0, 3, Blocks.POLISHED_ANDESITE.defaultBlockState());
			s.fill(x0 - 2, 0, 4, x0 + w + 1, 0, 4, Blocks.STONE_BRICKS.defaultBlockState());
			int x = x0;
			int i = 0;
			for (String door : doors) {
				GrandDoorBlock block = door(door);
				s.fill(x, 1, -1, x + block.width() - 1, block.height(), 0, Blocks.AIR.defaultBlockState());
				boolean opened = "alternate".equals(open) && i % 2 == 1;
				s.door(door, x, 1, 0, DoorHingeSide.LEFT, opened);
				x += block.width() + 1;
				i++;
			}
			s.finish();
		});
		// Straight on, the wall filling the width; the picture is cropped to a wide strip afterwards.
		double y = top / 2.0 + 1.0;
		shoot(context, world, site, name, 0.5, y + 0.8, w * 0.63, 0.5, y, 0);
		world.getServer().runOnServer(server -> new Scene(server.overworld(), site).clear(-50, 50, -30, 40));
	}

	/** Every wood's wide fence gate in one fence, with the iron one at the end. */
	private static void fences(ClientGameTestContext context, TestSingleplayerContext world, BlockPos site, String[] woods) {
		if (!want("catalogue-fence-gates")) {
			return;
		}
		world.getServer().runOnServer(server -> {
			Scene s = new Scene(server.overworld(), site);
			s.clear(-50, 50, -30, 40);
			// Two fences of seven gates, the back one six blocks behind the front one.
			for (int i = 0; i <= woods.length; i++) {
				int row = i / 7;
				int x = -21 + (i % 7) * 6 + (row == 1 ? 3 : 0);
				int z = row == 0 ? -5 : 0;
				boolean iron = i == woods.length;
				BlockState post = iron ? Blocks.IRON_BARS.defaultBlockState() : block("minecraft", woods[i] + "_fence").defaultBlockState();
				s.set(x, 1, z, post);
				s.door((iron ? "iron" : woods[i]) + "_split_fence_gate_3x1", x + 1, 1, z, DoorHingeSide.LEFT, false);
				s.set(x + 4, 1, z, post);
			}
			s.finish();
		});
		shoot(context, world, site, "catalogue-fence-gates", 14.5, 3.6, 11, -2.5, 0.6, -3);
		world.getServer().runOnServer(server -> new Scene(server.overworld(), site).clear(-50, 50, -30, 40));
	}

	// ---- the cinematic shots ----------------------------------------------------------------

	/** GD_ONLY, a comma list of picture names, re-takes only those pictures. */
	private static boolean want(String name) {
		String only = System.getenv("GD_ONLY");
		return only == null || java.util.Arrays.asList(only.split(",")).contains(name);
	}

	private static void cinematic(ClientGameTestContext context, TestSingleplayerContext world) {
		fov(context, 60);
		if (want("cinematic-cottage")) {
		BlockPos cottage = site(world, Biomes.CHERRY_GROVE, 22, 18);
		world.getServer().runOnServer(server -> cottage(new Scene(server.overworld(), cottage)));
		time(world, 300);
		shoot(context, world, cottage, "cinematic-cottage", 8.5, 2.6, 16, 0.5, 4.2, 0);
		}
		if (want("cinematic-castle-gate")) {
		BlockPos castle = site(world, Biomes.OLD_GROWTH_SPRUCE_TAIGA, 24, 18);
		world.getServer().runOnServer(server -> castle(new Scene(server.overworld(), castle)));
		time(world, 11600);
		shoot(context, world, castle, "cinematic-castle-gate", -6.5, 1.7, 15, 0, 5, 0);
		}
		if (want("cinematic-barn")) {
		BlockPos barn = site(world, Biomes.SUNFLOWER_PLAINS, 24, 18);
		world.getServer().runOnServer(server -> barn(new Scene(server.overworld(), barn)));
		run(world, "execute positioned " + (barn.getX() + 6) + " " + (barn.getY() + 1) + " " + (barn.getZ() + 9) + " run summon minecraft:horse ~ ~ ~ {NoAI:1b,Variant:1,Rotation:[200f,0f]}");
		run(world, "execute positioned " + (barn.getX() - 7) + " " + (barn.getY() + 1) + " " + (barn.getZ() + 10) + " run summon minecraft:cow ~ ~ ~ {NoAI:1b,Rotation:[120f,0f]}");
		time(world, 1500);
		shoot(context, world, barn, "cinematic-barn", 12.5, 4, 19, 0.5, 3, 0);
		}
		if (want("cinematic-street")) {
		BlockPos street = site(world, Biomes.MEADOW, 30, 26);
		world.getServer().runOnServer(server -> street(new Scene(server.overworld(), street)));
		time(world, 10000);
		shoot(context, world, street, "cinematic-street", -6.5, 2.0, 13, -0.5, 3.6, 0);
		}
		if (want("cinematic-park-gate")) {
		BlockPos park = site(world, Biomes.DARK_FOREST, 20, 16);
		world.getServer().runOnServer(server -> park(new Scene(server.overworld(), park)));
		time(world, 12300);
		shoot(context, world, park, "cinematic-park-gate", 4.5, 2.0, 8, 0.5, 2.8, 0);
		}
	}

	/** A half-timbered cottage, gable to the street, with a glazed arched door and a garden gate. */
	private static void cottage(Scene s) {
		s.clear(-14, 14, -14, 16);
		BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
		BlockState beam = log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
		BlockState fill = block("minecraft", "white_terracotta").defaultBlockState();
		s.fill(-6, 0, -9, 6, 0, 1, Blocks.COBBLESTONE.defaultBlockState());
		s.fill(-5, 1, -8, 5, 5, 0, fill);
		s.fill(-4, 1, -7, 4, 5, -1, Blocks.AIR.defaultBlockState());
		for (int x : new int[] {-5, -2, 2, 5}) {
			s.fill(x, 1, 0, x, 5, 0, log);
			s.fill(x, 1, -8, x, 5, -8, log);
		}
		s.fill(-5, 5, 0, 5, 5, 0, beam);
		s.fill(-5, 1, -8, -5, 5, 0, log);
		s.fill(5, 1, -8, 5, 5, 0, log);
		for (int k = 0; k <= 6; k++) {
			int y = 6 + k;
			BlockState east = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
			BlockState west = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
			if (k < 6) {
				s.fill(-6 + k, y - 1, -9, -6 + k, y - 1, 1, east);
				s.fill(6 - k, y - 1, -9, 6 - k, y - 1, 1, west);
				if (k < 5) {
					s.fill(-5 + k, y - 1, 0, 5 - k, y - 1, 0, fill);
					s.fill(-5 + k, y - 1, -8, 5 - k, y - 1, -8, fill);
				}
			} else {
				s.fill(0, y - 1, -9, 0, y - 1, 1, Blocks.DARK_OAK_SLAB.defaultBlockState());
			}
		}
		s.fill(0, 6, 0, 0, 10, 0, log);
		s.set(-3, 7, 0, Blocks.GLASS_PANE.defaultBlockState());
		s.set(3, 7, 0, Blocks.GLASS_PANE.defaultBlockState());
		s.fill(-4, 2, 0, -3, 3, 0, Blocks.GLASS_PANE.defaultBlockState());
		s.fill(3, 2, 0, 4, 3, 0, Blocks.GLASS_PANE.defaultBlockState());
		for (int x : new int[] {-4, -3, 3, 4}) {
			s.set(x, 1, 1, Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
			s.set(x, 2, 1, Blocks.AIR.defaultBlockState());
		}
		s.fill(-1, 1, 0, 1, 4, 0, Blocks.AIR.defaultBlockState());
		s.door("oak_arched_split_glazed_door_3x4", -1, 1, 0, DoorHingeSide.LEFT, false);
		s.set(-2, 4, 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		s.set(-2, 5, 1, Blocks.DARK_OAK_FENCE.defaultBlockState());
		s.set(2, 4, 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		s.set(2, 5, 1, Blocks.DARK_OAK_FENCE.defaultBlockState());
		s.fill(-1, 0, 1, 1, 0, 12, Blocks.DIRT_PATH.defaultBlockState());
		for (int x = -9; x <= 10; x++) {
			if (x < -1 || x > 1) {
				s.set(x, 1, 6, Blocks.OAK_FENCE.defaultBlockState());
			}
		}
		s.door("oak_split_fence_gate_3x1", -1, 1, 6, DoorHingeSide.LEFT, true);
		Block[] flowers = {Blocks.POPPY, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.PINK_TULIP};
		for (int x = -8; x <= 9; x++) {
			for (int z = 2; z <= 5; z++) {
				if ((x < -1 || x > 1) && Math.floorMod(x * 7 + z * 3, 4) == 0) {
					s.set(x, 1, z, flowers[Math.floorMod(x + z, flowers.length)].defaultBlockState());
				}
			}
		}
		s.finish();
	}

	/** A stone curtain wall between two towers, its gate a stone door four by four, open. */
	private static void castle(Scene s) {
		s.clear(-20, 20, -14, 18);
		BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
		s.fill(-14, -3, -3, 14, 0, 0, stone);
		s.fill(-10, 1, -2, 10, 9, 0, stone);
		s.fill(-15, 1, -5, -10, 13, 1, stone);
		s.fill(10, 1, -5, 15, 13, 1, stone);
		for (int x = -10; x <= 10; x += 2) {
			s.set(x, 10, 0, stone);
		}
		for (int x = -15; x <= 15; x++) {
			if (Math.abs(x) >= 10 && Math.floorMod(x, 2) == 1) {
				s.set(x, 14, 1, stone);
			}
		}
		s.fill(-10, 1, -2, 10, 3, -2, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
		s.fill(-2, 1, -2, 1, 4, 0, Blocks.AIR.defaultBlockState());
		s.door("stone_brick_split_door_4x4", -2, 1, 0, DoorHingeSide.LEFT, true);
		s.fill(-14, 3, 2, -11, 3, 2, Blocks.AIR.defaultBlockState());
		for (int x : new int[] {-4, 3}) {
			s.set(x, 4, 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
			s.set(x, 5, 1, Blocks.IRON_BARS.defaultBlockState());
		}
		s.fill(-2, 0, 1, 1, 0, 16, Blocks.COBBLESTONE.defaultBlockState());
		s.fill(-2, 0, -6, 1, 0, -3, Blocks.COBBLESTONE.defaultBlockState());
		for (int z = 3; z <= 16; z += 3) {
			s.set(-3, 0, z, Blocks.GRAVEL.defaultBlockState());
			s.set(2, 0, z + 1, Blocks.COARSE_DIRT.defaultBlockState());
		}
		s.finish();
	}

	/** A spruce barn with a wide door four by four, open, and a paddock with a wide fence gate. */
	private static void barn(Scene s) {
		s.clear(-20, 20, -16, 20);
		BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState log = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
		s.fill(-7, 0, -12, 7, 0, 0, Blocks.COBBLESTONE.defaultBlockState());
		s.fill(-6, 1, -11, 6, 6, 0, planks);
		s.fill(-5, 1, -10, 5, 6, -1, Blocks.AIR.defaultBlockState());
		for (int x : new int[] {-6, -3, 2, 6}) {
			s.fill(x, 1, 0, x, 6, 0, log);
		}
		for (int k = 0; k <= 7; k++) {
			int y = 7 + k;
			if (k < 7) {
				s.fill(-7 + k, y - 1, -12, -7 + k, y - 1, 1, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
				s.fill(7 - k, y - 1, -12, 7 - k, y - 1, 1, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
				if (k < 6) {
					s.fill(-6 + k, y - 1, 0, 6 - k, y - 1, 0, planks);
				}
			} else {
				s.fill(0, y - 1, -12, 0, y - 1, 1, Blocks.DARK_OAK_SLAB.defaultBlockState());
			}
		}
		s.set(0, 9, 0, Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
		s.fill(-2, 1, 0, 1, 4, 0, Blocks.AIR.defaultBlockState());
		s.door("spruce_split_door_4x4", -2, 1, 0, DoorHingeSide.LEFT, true);
		s.fill(-2, 5, 0, 1, 5, 0, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		s.set(-5, 1, 1, Blocks.HAY_BLOCK.defaultBlockState());
		s.set(-5, 2, 1, Blocks.HAY_BLOCK.defaultBlockState());
		s.set(-4, 1, 1, Blocks.HAY_BLOCK.defaultBlockState());
		s.set(4, 1, 1, Blocks.BARREL.defaultBlockState());
		s.fill(-2, 0, 1, 1, 0, 18, Blocks.COARSE_DIRT.defaultBlockState());
		for (int z = 5; z <= 14; z++) {
			s.set(-12, 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
			s.set(12, 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
		}
		for (int x = -12; x <= 12; x++) {
			if (x < -1 || x > 1) {
				s.set(x, 1, 14, Blocks.SPRUCE_FENCE.defaultBlockState());
			}
		}
		s.door("spruce_split_fence_gate_3x1", -1, 1, 14, DoorHingeSide.LEFT, true);
		s.finish();
	}

	/** Three narrow town houses in a row, each with a house door: boarded, windowed, lattice. */
	private static void street(Scene s) {
		s.clear(-30, 30, -16, 30);
		house(s, -11, 7, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_STAIRS, "spruce_boarded_door_1x2", 3, 2);
		house(s, -4, 6, Blocks.BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(), Blocks.DEEPSLATE_TILE_STAIRS, "dark_oak_split_windowed_door_2x2", 3, 3);
		house(s, 2, 7, block("minecraft", "white_terracotta").defaultBlockState(), Blocks.DARK_OAK_LOG.defaultBlockState(), Blocks.DARK_OAK_STAIRS, "oak_lattice_door_1x2", 3, 2);
		s.fill(-14, 0, 1, 13, 0, 6, Blocks.COBBLESTONE.defaultBlockState());
		s.fill(-14, 0, 1, 13, 0, 1, Blocks.STONE_BRICKS.defaultBlockState());
		for (int x = -12; x <= 12; x += 3) {
			s.set(x, 0, 3 + Math.floorMod(x, 2), Blocks.ANDESITE.defaultBlockState());
		}
		for (int x : new int[] {-4, 4}) {
			s.set(x, 3, 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
			s.set(x, 4, 1, Blocks.IRON_BARS.defaultBlockState());
		}
		s.set(-9, 1, 1, Blocks.BARREL.defaultBlockState());
		s.set(10, 1, 1, Blocks.POTTED_RED_TULIP.defaultBlockState());
		s.finish();
	}

	/** A narrow house, gable to the street, its left wall at x0, width w, with a door centred. */
	private static void house(Scene s, int x0, int w, BlockState wall, BlockState post, Block roof, String door, int doorX, int floors) {
		int h = floors * 4;
		int x1 = x0 + w - 1;
		s.fill(x0, 0, -9, x1, 0, 0, Blocks.COBBLESTONE.defaultBlockState());
		s.fill(x0, 1, -9, x1, h, 0, wall);
		s.fill(x0 + 1, 1, -8, x1 - 1, h, -1, Blocks.AIR.defaultBlockState());
		s.fill(x0, 1, 0, x0, h, 0, post);
		s.fill(x1, 1, 0, x1, h, 0, post);
		for (int f = 1; f < floors; f++) {
			s.fill(x0, f * 4, 0, x1, f * 4, 0, post);
			for (int x = x0 + 2; x <= x1 - 2; x += 2) {
				s.fill(x, f * 4 + 1, 0, x, f * 4 + 2, 0, Blocks.GLASS_PANE.defaultBlockState());
			}
		}
		int half = w / 2;
		for (int k = 0; k <= half; k++) {
			int y = h + 1 + k;
			if (k < half) {
				s.fill(x0 - 1 + k, y, -10, x0 - 1 + k, y, 1, roof.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
				s.fill(x1 + 1 - k, y, -10, x1 + 1 - k, y, 1, roof.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
				s.fill(x0 + k, y, 0, x1 - k, y, 0, wall);
			} else {
				s.fill(x0 + half, y, -10, x0 + half, y, 1, roof.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
			}
		}
		GrandDoorBlock d = door(door);
		int dx = x0 + doorX - (d.width() == 2 ? 1 : 0);
		s.fill(dx, 1, 0, dx + d.width() - 1, d.height(), 0, Blocks.AIR.defaultBlockState());
		s.door(door, dx, 1, 0, DoorHingeSide.LEFT, false);
		s.fill(dx - 1, d.height() + 1, 1, dx + d.width(), d.height() + 1, 1, Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
				.setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP));
	}

	/** A walled park in a dark forest at dusk, its gate of iron bars, arched, shut. */
	private static void park(Scene s) {
		s.clear(-14, 14, -12, 14);
		BlockState wall = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
		s.fill(-12, 1, 0, 12, 3, 0, wall);
		s.fill(-12, 4, 0, 12, 4, 0, Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState());
		for (int x : new int[] {-3, 3}) {
			s.fill(x, 1, 0, x, 6, 0, Blocks.STONE_BRICKS.defaultBlockState());
			s.set(x, 7, 0, Blocks.LANTERN.defaultBlockState());
		}
		s.fill(-1, 1, 0, 1, 5, 0, Blocks.AIR.defaultBlockState());
		s.fill(-2, 1, 0, 2, 5, 0, Blocks.AIR.defaultBlockState());
		s.fill(-2, 5, 0, 2, 5, 0, Blocks.STONE_BRICKS.defaultBlockState());
		s.set(-2, 1, 0, Blocks.STONE_BRICKS.defaultBlockState());
		s.set(-2, 2, 0, Blocks.STONE_BRICKS.defaultBlockState());
		s.set(-2, 3, 0, Blocks.STONE_BRICKS.defaultBlockState());
		s.set(-2, 4, 0, Blocks.STONE_BRICKS.defaultBlockState());
		s.set(2, 1, 0, Blocks.STONE_BRICKS.defaultBlockState());
		s.set(2, 2, 0, Blocks.STONE_BRICKS.defaultBlockState());
		s.set(2, 3, 0, Blocks.STONE_BRICKS.defaultBlockState());
		s.set(2, 4, 0, Blocks.STONE_BRICKS.defaultBlockState());
		s.door("iron_arched_split_gate_3x4", -1, 1, 0, DoorHingeSide.LEFT, false);
		s.fill(-1, 0, -10, 1, 0, 12, Blocks.GRAVEL.defaultBlockState());
		s.set(-1, 1, -6, Blocks.LANTERN.defaultBlockState());
		s.set(2, 1, -8, Blocks.LANTERN.defaultBlockState());
		s.finish();
	}

	// ---- helpers -----------------------------------------------------------------------------

	/** A fairly flat spot of the biome: of several found, the one whose ground varies least. */
	private static BlockPos site(TestSingleplayerContext world, ResourceKey<Biome> biome, int half, int depth) {
		BlockPos pos = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			var generator = level.getChunkSource().getGenerator();
			var random = level.getChunkSource().randomState();
			java.util.function.IntBinaryOperator height = (x, z) ->
					generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
			java.util.function.IntBinaryOperator floor = (x, z) ->
					generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
			Predicate<Holder<Biome>> wanted = holder -> holder.is(biome);
			BlockPos best = null;
			double bestScore = Double.MAX_VALUE;
			String why = "";
			for (int i = 0; i < 16; i++) {
				BlockPos from = new BlockPos((i % 4 - 2) * 2500, 80, (i / 4 - 2) * 2500);
				Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(wanted, from, 3000, 32, 64);
				if (found == null) {
					continue;
				}
				for (int ox = -48; ox <= 48; ox += 24) {
					for (int oz = -48; oz <= 48; oz += 24) {
						int px = found.getFirst().getX() + ox;
						int pz = found.getFirst().getZ() + oz;
						// The site itself: flat and dry.
						double sum = 0;
						double sq = 0;
						int n = 0;
						int wet = 0;
						for (int dx = -half; dx <= half; dx += 4) {
							for (int dz = -depth; dz <= depth; dz += 4) {
								int y = height.applyAsInt(px + dx, pz + dz);
								if (floor.applyAsInt(px + dx, pz + dz) < y) {
									wet++;
								}
								sum += y;
								sq += (double) y * y;
								n++;
							}
						}
						double mean = sum / n;
						double spread = sq / n - mean * mean;
						// The view: the camera looks north; hills or mountains behind make the picture.
						int top = Integer.MIN_VALUE;
						for (int dz = -70; dz >= -260; dz -= 16) {
							for (int dx = -90; dx <= 90; dx += 18) {
								top = Math.max(top, height.applyAsInt(px + dx, pz + dz));
							}
						}
						double rise = Math.min(top - mean, 80);
						// Nothing high in front of the camera.
						int front = Integer.MIN_VALUE;
						for (int dz = depth; dz <= depth + 30; dz += 6) {
							for (int dx = -half; dx <= half; dx += 8) {
								front = Math.max(front, height.applyAsInt(px + dx, pz + dz));
							}
						}
						double score = spread + wet * 50 + (mean < 64 ? 1000 : 0)
								+ (rise < 20 ? 60 : -rise * 0.5) + Math.max(0, front - mean - 3) * 10;
						if (score < bestScore) {
							bestScore = score;
							best = new BlockPos(px, (int) Math.round(mean), pz);
							why = "spread " + Math.round(spread) + ", backdrop +" + Math.round(rise) + ", wet " + wet;
						}
					}
				}
			}
			System.out.println("[gallery] " + biome.identifier() + " at " + best + ": " + why);
			return best;
		});
		run(world, "forceload add " + (pos.getX() - 64) + " " + (pos.getZ() - 64) + " " + (pos.getX() + 64) + " " + (pos.getZ() + 64));
		return pos;
	}

	private static void shoot(ClientGameTestContext context, TestSingleplayerContext world, BlockPos site, String name,
			double cx, double cy, double cz, double tx, double ty, double tz) {
		double dx = tx - cx;
		double dy = ty - cy;
		double dz = tz - cz;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		run(world, String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f",
				site.getX() + cx, site.getY() + cy, site.getZ() + cz, yaw, pitch));
		// Light is worked out after the blocks are set: wait for it, or shut doors in their
		// niches come out black.
		context.waitTicks(200);
		// A far teleport with a render distance of 16 can take longer than the default wait.
		for (int i = 0; i < 2; i++) {
			try {
				world.getConnection().waitForChunksRender();
				break;
			} catch (AssertionError e) {
				System.out.println("[gallery] chunks still rendering for " + name);
			}
		}
		context.waitTicks(40);
		context.takeScreenshot(TestScreenshotOptions.of(name).withSize(W, H).disableCounterPrefix());
	}

	private static void time(TestSingleplayerContext world, int time) {
		run(world, "time set " + time);
	}

	private static void fov(ClientGameTestContext context, int fov) {
		context.runOnClient(client -> client.options.fov().set(fov));
	}

	private static void run(TestSingleplayerContext world, String command) {
		world.getServer().runCommand(command);
	}

	static GrandDoorBlock door(String name) {
		return (GrandDoorBlock) block("granddoors", name);
	}

	static Block block(String namespace, String name) {
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(namespace, name));
		if (block == Blocks.AIR) {
			throw new IllegalArgumentException("no block " + namespace + ":" + name);
		}
		return block;
	}

	/**
	 * With GD_SHADER naming a shader pack and Iris installed, switches it on. Iris is reached by
	 * reflection: no compile dependency.
	 */
	private static void enableShader(ClientGameTestContext context) {
		String shader = System.getenv("GD_SHADER");
		if (shader == null || !FabricLoader.getInstance().isModLoaded("iris")) {
			return;
		}
		context.runOnClient(client -> {
			try {
				Path pack = Path.of(shader);
				Path dir = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
				Files.createDirectories(dir);
				Files.copy(pack, dir.resolve(pack.getFileName()), StandardCopyOption.REPLACE_EXISTING);
				Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
				Object config = iris.getMethod("getIrisConfig").invoke(null);
				config.getClass().getMethod("setShaderPackName", String.class).invoke(config, pack.getFileName().toString());
				config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, true);
				config.getClass().getMethod("save").invoke(config);
				iris.getMethod("reload").invoke(null);
			} catch (ReflectiveOperationException | IOException e) {
				throw new RuntimeException(e);
			}
		});
		context.waitTicks(60);
	}

	/**
	 * Builds in coordinates relative to a site: x to the right as the camera looks north, y up
	 * from the ground, z towards the camera (south). Doors face south, towards the camera.
	 */
	static final class Scene {
		private final ServerLevel level;
		private final BlockPos origin;
		private final List<BlockPos> placed = new ArrayList<>();
		/** Door cells get their light checked but are never reshaped (that would take a door apart). */
		private final List<BlockPos> doorCells = new ArrayList<>();

		Scene(ServerLevel level, BlockPos origin) {
			this.level = level;
			this.origin = origin;
		}

		BlockPos at(int x, int y, int z) {
			return this.origin.offset(x, y, z);
		}

		void set(int x, int y, int z, BlockState state) {
			BlockPos pos = this.at(x, y, z);
			this.level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			this.placed.add(pos);
		}

		void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
					for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
						this.set(x, y, z, state);
					}
				}
			}
		}

		/** Levels the ground: air above, dirt under grass below, over the box. */
		void clear(int x0, int x1, int z0, int z1) {
			BlockState air = Blocks.AIR.defaultBlockState();
			java.util.ArrayDeque<BlockPos> trees = new java.util.ArrayDeque<>();
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					for (int y = 1; y <= 40; y++) {
						BlockPos pos = this.at(x, y, z);
						if (tree(this.level.getBlockState(pos))) {
							trees.add(pos);
						}
						this.level.setBlock(pos, air, Block.UPDATE_CLIENTS);
					}
					this.level.setBlock(this.at(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
					for (int y = -1; y >= -8; y--) {
						BlockPos pos = this.at(x, y, z);
						if (!this.level.getBlockState(pos).isSolid()) {
							this.level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					}
				}
			}
			// Trees just outside are taken away whole (no half trees hanging over the edge), and
			// ground higher than the site is cut back in a slope instead of a cliff.
			int ring = 12;
			for (int x = x0 - ring; x <= x1 + ring; x++) {
				for (int z = z0 - ring; z <= z1 + ring; z++) {
					if (x >= x0 && x <= x1 && z >= z0 && z <= z1) {
						continue;
					}
					int d = Math.max(Math.max(x0 - x, x - x1), Math.max(z0 - z, z - z1));
					boolean topped = false;
					for (int y = 40; y >= 1; y--) {
						BlockPos pos = this.at(x, y, z);
						BlockState state = this.level.getBlockState(pos);
						if (state.isAir()) {
							continue;
						}
						if (tree(state)) {
							this.level.setBlock(pos, air, Block.UPDATE_CLIENTS);
							trees.add(pos);
						} else if (y > d) {
							this.level.setBlock(pos, air, Block.UPDATE_CLIENTS);
							topped = true;
						} else {
							if (topped && state.isSolid()) {
								this.level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
							}
							break;
						}
					}
				}
			}
			// Every tree that was cut goes whole: logs and leaves joined to what was taken away.
			java.util.Set<BlockPos> seen = new java.util.HashSet<>(trees);
			int removed = 0;
			while (!trees.isEmpty() && removed < 60000) {
				BlockPos pos = trees.poll();
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						for (int dz = -1; dz <= 1; dz++) {
							BlockPos next = pos.offset(dx, dy, dz);
							int ax = next.getX() - this.origin.getX();
							int az = next.getZ() - this.origin.getZ();
							boolean near = ax >= x0 - 24 && ax <= x1 + 24 && az >= z0 - 24 && az <= z1 + 24;
							if (near && next.getY() > this.origin.getY() - 4 && seen.add(next) && tree(this.level.getBlockState(next))) {
								this.level.setBlock(next, air, Block.UPDATE_CLIENTS);
								trees.add(next);
								removed++;
							}
						}
					}
				}
			}
			this.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
					new net.minecraft.world.phys.AABB(net.minecraft.world.phys.Vec3.atCenterOf(this.at(x0, -2, z0)), net.minecraft.world.phys.Vec3.atCenterOf(this.at(x1, 40, z1))),
					e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(e -> e.discard());
		}

		private static boolean tree(BlockState state) {
			return state.is(net.minecraft.tags.BlockTags.LOGS) || state.is(net.minecraft.tags.BlockTags.LEAVES)
					|| state.is(Blocks.VINE) || state.is(Blocks.BEE_NEST) || state.is(Blocks.MANGROVE_ROOTS)
					|| state.is(Blocks.PALE_HANGING_MOSS) || state.is(Blocks.MUSHROOM_STEM)
					|| state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.BROWN_MUSHROOM_BLOCK);
		}

		/** A door whose left cell, as the camera sees it, is at (x, y, z); facing the camera. */
		void door(String name, int x, int y, int z, DoorHingeSide hinge, boolean open) {
			GrandDoorBlock door = GalleryShots.door(name);
			Direction facing = Direction.SOUTH;
			BlockState first = null;
			BlockPos firstPos = null;
			for (int part = 0; part < door.width(); part++) {
				for (int row = 0; row < door.height(); row++) {
					BlockState state = door.defaultBlockState().setValue(GrandDoorBlock.FACING, facing);
					if (!door.fence()) {
						state = state.setValue(GrandDoorBlock.FRONT, false);
					}
					if (door.rowProperty() != null) {
						state = state.setValue(door.rowProperty(), row);
					}
					if (door.partProperty() != null) {
						state = state.setValue(door.partProperty(), part);
					}
					if (!door.split()) {
						state = state.setValue(GrandDoorBlock.HINGE, hinge);
					}
					// part 0 is the east end: the right as the camera sees it.
					BlockPos pos = this.at(x + door.width() - 1 - part, y + row, z);
					this.level.setBlock(pos, state, Block.UPDATE_CLIENTS);
					this.doorCells.add(pos);
					if (first == null) {
						first = state;
						firstPos = pos;
					}
				}
			}
			if (open) {
				door.setOpen(this.level, first, firstPos, true);
			}
		}

		/** Gives fences, walls, panes, bars and stairs their shapes, and shows it all. */
		void finish() {
			// Light is checked again around everything set: the doors in their niches otherwise
			// keep the darkness of the ground that stood there.
			var light = this.level.getChunkSource().getLightEngine();
			List<BlockPos> all = new ArrayList<>(this.placed);
			all.addAll(this.doorCells);
			for (BlockPos pos : all) {
				light.checkBlock(pos);
				for (Direction side : Direction.values()) {
					light.checkBlock(pos.relative(side));
				}
			}
			for (BlockPos pos : this.placed) {
				BlockState state = this.level.getBlockState(pos);
				BlockState shaped = Block.updateFromNeighbourShapes(state, this.level, pos);
				if (shaped != state) {
					this.level.setBlock(pos, shaped, Block.UPDATE_CLIENTS);
				}
			}
		}
	}
}
