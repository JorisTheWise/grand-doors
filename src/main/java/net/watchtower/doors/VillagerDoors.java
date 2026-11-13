package net.watchtower.doors;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Lets villagers use the wooden doors. Vanilla's own behaviour for this only handles vanilla's
 * door block, so this does the same job from outside: a villager whose path runs through a
 * closed door opens it, and the door closes again once no villager is near it.
 */
public final class VillagerDoors {
	private static final int EVERY_TICKS = 5;
	/** A villager opens a door from this close, and keeps one open from this far past its edge. */
	private static final double REACH = 2.5;

	/** A door a villager opened: one of its cells while open, and the middle of its doorway. */
	private record Opened(BlockPos cell, Vec3 middle, double radius) {
	}

	private static final Map<ServerLevel, List<Opened>> OPENED = new WeakHashMap<>();

	private VillagerDoors() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % EVERY_TICKS == 0) {
				tick(level);
			}
		});
	}

	private static void tick(ServerLevel level) {
		List<Opened> opened = OPENED.computeIfAbsent(level, key -> new ArrayList<>());
		for (Villager villager : level.getEntities(EntityTypeTest.forClass(Villager.class), Entity::isAlive)) {
			Path path = villager.getNavigation().getPath();
			if (path == null || path.notStarted() || path.isDone()) {
				continue;
			}
			open(level, villager, path.getPreviousNode(), opened);
			open(level, villager, path.getNextNode(), opened);
		}
		for (Iterator<Opened> it = opened.iterator(); it.hasNext();) {
			Opened door = it.next();
			BlockState state = level.getBlockState(door.cell());
			if (!(state.getBlock() instanceof GrandDoorBlock block) || !state.getValue(GrandDoorBlock.OPEN) || state.getValue(GrandDoorBlock.POWERED)) {
				it.remove();
			} else if (level.getEntitiesOfClass(Villager.class, AABB.ofSize(door.middle(), 2 * door.radius(), 4, 2 * door.radius())).isEmpty()) {
				block.setOpen(level, state, door.cell(), false);
				it.remove();
			}
		}
	}

	private static void open(ServerLevel level, Villager villager, Node node, List<Opened> opened) {
		if (node == null) {
			return;
		}
		BlockPos pos = node.asBlockPos();
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof GrandDoorBlock door && door.opensForMobs() && !state.getValue(GrandDoorBlock.OPEN)
				&& pos.closerToCenterThan(villager.position(), REACH)) {
			Vec3 middle = door.middleOf(state, pos);
			BlockPos cell = door.setOpen(level, state, pos, true);
			if (cell != null) {
				opened.add(new Opened(cell, middle, door.width() / 2.0 + REACH));
			}
		}
	}
}
