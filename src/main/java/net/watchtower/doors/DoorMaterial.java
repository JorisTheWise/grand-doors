package net.watchtower.doors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** What a door is made of. The ids match the "material" field of the door list. */
public enum DoorMaterial {
	OAK("oak", Blocks.OAK_PLANKS, BlockSetType.OAK, SoundType.WOOD, 3.0F, false, true),
	SPRUCE("spruce", Blocks.SPRUCE_PLANKS, BlockSetType.SPRUCE, SoundType.WOOD, 3.0F, false, true),
	BIRCH("birch", Blocks.BIRCH_PLANKS, BlockSetType.BIRCH, SoundType.WOOD, 3.0F, false, true),
	JUNGLE("jungle", Blocks.JUNGLE_PLANKS, BlockSetType.JUNGLE, SoundType.WOOD, 3.0F, false, true),
	ACACIA("acacia", Blocks.ACACIA_PLANKS, BlockSetType.ACACIA, SoundType.WOOD, 3.0F, false, true),
	DARK_OAK("dark_oak", Blocks.DARK_OAK_PLANKS, BlockSetType.DARK_OAK, SoundType.WOOD, 3.0F, false, true),
	MANGROVE("mangrove", Blocks.MANGROVE_PLANKS, BlockSetType.MANGROVE, SoundType.WOOD, 3.0F, false, true),
	CHERRY("cherry", Blocks.CHERRY_PLANKS, BlockSetType.CHERRY, SoundType.CHERRY_WOOD, 3.0F, false, true),
	PALE_OAK("pale_oak", Blocks.PALE_OAK_PLANKS, BlockSetType.PALE_OAK, SoundType.WOOD, 3.0F, false, true),
	BAMBOO("bamboo", Blocks.BAMBOO_PLANKS, BlockSetType.BAMBOO, SoundType.BAMBOO_WOOD, 3.0F, false, true),
	CRIMSON("crimson", Blocks.CRIMSON_PLANKS, BlockSetType.CRIMSON, SoundType.NETHER_WOOD, 3.0F, false, false),
	WARPED("warped", Blocks.WARPED_PLANKS, BlockSetType.WARPED, SoundType.NETHER_WOOD, 3.0F, false, false),
	COBBLESTONE("cobblestone", Blocks.COBBLESTONE, BlockSetType.STONE, SoundType.STONE, 3.5F, true, false),
	STONE_BRICK("stone_brick", Blocks.STONE_BRICKS, BlockSetType.STONE, SoundType.STONE, 3.5F, true, false),
	COBBLED_DEEPSLATE("cobbled_deepslate", Blocks.COBBLED_DEEPSLATE, BlockSetType.STONE, SoundType.DEEPSLATE, 3.5F, true, false),
	DEEPSLATE_BRICK("deepslate_brick", Blocks.DEEPSLATE_BRICKS, BlockSetType.STONE, SoundType.DEEPSLATE_BRICKS, 3.5F, true, false),
	IRON("iron", Blocks.IRON_BLOCK, BlockSetType.IRON, SoundType.METAL, 5.0F, true, false),
	COPPER("copper", vanilla("copper_block"), BlockSetType.COPPER, SoundType.COPPER, 3.0F, true, false),
	EXPOSED_COPPER("exposed_copper", vanilla("exposed_copper"), BlockSetType.COPPER, SoundType.COPPER, 3.0F, true, false),
	WEATHERED_COPPER("weathered_copper", vanilla("weathered_copper"), BlockSetType.COPPER, SoundType.COPPER, 3.0F, true, false),
	OXIDIZED_COPPER("oxidized_copper", vanilla("oxidized_copper"), BlockSetType.COPPER, SoundType.COPPER, 3.0F, true, false);

	public final String id;
	public final Block base;
	public final BlockSetType type;
	public final SoundType sound;
	public final float strength;
	public final boolean needsPickaxe;
	public final boolean flammable;

	DoorMaterial(String id, Block base, BlockSetType type, SoundType sound, float strength, boolean needsPickaxe, boolean flammable) {
		this.id = id;
		this.base = base;
		this.type = type;
		this.sound = sound;
		this.strength = strength;
		this.needsPickaxe = needsPickaxe;
		this.flammable = flammable;
	}

	/** A vanilla block by its id, for the ones that are no longer a plain field of Blocks. */
	private static Block vanilla(String id) {
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id));
		if (block == Blocks.AIR) {
			throw new IllegalStateException("no vanilla block " + id);
		}
		return block;
	}

	/** Villagers open wooden doors, as they do vanilla's. */
	public boolean opensForMobs() {
		return !this.needsPickaxe;
	}

	public static DoorMaterial byId(String id) {
		for (DoorMaterial material : values()) {
			if (material.id.equals(id)) {
				return material;
			}
		}
		throw new IllegalArgumentException("no door material " + id);
	}
}
