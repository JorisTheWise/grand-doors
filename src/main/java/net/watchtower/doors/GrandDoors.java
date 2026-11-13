package net.watchtower.doors;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.registry.OxidizableBlocksRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.PushReaction;

/**
 * Doors one to four blocks wide and two to four tall. Every door is one block per cell; see
 * {@link GrandDoorBlock}.
 *
 * <p>Which doors exist is decided in one place, gen_assets.py, which writes the list this
 * class registers ({@code granddoors_doors.json}) next to the models it draws for them.
 */
public final class GrandDoors implements ModInitializer {
	public static final String MOD_ID = "granddoors";
	private static final String DOOR_LIST = "/granddoors_doors.json";
	public static final List<Block> DOORS = new ArrayList<>();
	/** The doors of each creative tab, in the order of the door list. */
	private static final Map<String, List<Block>> TABS = new LinkedHashMap<>();

	@Override
	public void onInitialize() {
		List<JsonObject> list = new ArrayList<>();
		JsonParser.parseReader(doorList()).getAsJsonArray().forEach(entry -> list.add(entry.getAsJsonObject()));
		Map<String, GrandDoorBlock> byName = new LinkedHashMap<>();
		for (JsonObject door : list) {
			String name = door.get("name").getAsString();
			byName.put(name, register(
				door.get("tab").getAsString(),
				name,
				DoorMaterial.byId(door.get("material").getAsString()),
				door.get("width").getAsInt(),
				door.get("height").getAsInt(),
				door.get("split").getAsBoolean(),
				door.has("weathers_to")));
		}
		// Copper: what each door weathers to, and its waxed twin. Registering the pairs is what
		// makes an axe scrape a door and honeycomb wax it, as with vanilla copper.
		for (JsonObject door : list) {
			GrandDoorBlock block = byName.get(door.get("name").getAsString());
			if (door.has("weathers_to")) {
				GrandDoorBlock next = byName.get(door.get("weathers_to").getAsString());
				block.weathersTo(next, door.get("fresh").getAsBoolean());
				OxidizableBlocksRegistry.registerNextStage(block, next);
			}
			if (door.has("waxed")) {
				OxidizableBlocksRegistry.registerWaxable(block, byName.get(door.get("waxed").getAsString()));
			}
		}
		VillagerDoors.register();
		// One tab per kind of door; a tab's icon is its first three-wide split door.
		TABS.forEach((tab, doors) -> Registry.register(
			BuiltInRegistries.CREATIVE_MODE_TAB,
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(MOD_ID, tab)),
			FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.granddoors." + tab))
				.icon(() -> new ItemStack(doors.stream()
					.filter(door -> door instanceof GrandDoorBlock grand && grand.split() && grand.width() == 3)
					.findFirst().orElse(doors.get(0))))
				.displayItems((parameters, output) -> doors.forEach(output::accept))
				.build()));
	}

	private static Reader doorList() {
		InputStream in = GrandDoors.class.getResourceAsStream(DOOR_LIST);
		if (in == null) {
			throw new UncheckedIOException(new IOException(DOOR_LIST + " is missing from the jar; run gen_assets.py before building"));
		}
		return new InputStreamReader(in, StandardCharsets.UTF_8);
	}

	private static GrandDoorBlock register(String tab, String name, DoorMaterial material, int width, int height, boolean split, boolean weathers) {
		Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, name);
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);

		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
			.mapColor(material.base.defaultMapColor())
			.instrument(material.needsPickaxe ? NoteBlockInstrument.BASEDRUM : NoteBlockInstrument.BASS)
			.strength(material.strength)
			.noOcclusion()
			.pushReaction(PushReaction.IMMOVEABLE)
			.sound(material.sound)
			.setId(blockKey);
		if (material.flammable) {
			properties = properties.ignitedByLava();
		}
		if (material.needsPickaxe) {
			properties = properties.requiresCorrectToolForDrops();
		}
		if (weathers) {
			properties = properties.randomTicks();
		}
		BlockBehaviour.Properties finished = properties;
		GrandDoorBlock block = Registry.register(BuiltInRegistries.BLOCK, blockKey,
			GrandDoorBlock.sized(width, height, split, () -> new GrandDoorBlock(width, height, split, material.type, material.opensForMobs(), finished)));

		GrandDoorItem item = new GrandDoorItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey));
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		item.registerBlocks(Item.BY_BLOCK, item);
		DOORS.add(block);
		TABS.computeIfAbsent(tab, key -> new ArrayList<>()).add(block);
		return block;
	}
}
