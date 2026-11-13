package net.watchtower.doors.test;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Logs how many block states the mod adds and the heap in use once the server is up. */
public class MemoryProbe implements ModInitializer {
	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			int all = 0;
			int ours = 0;
			for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
				all++;
				if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("granddoors")) {
					ours++;
				}
			}
			for (int i = 0; i < 3; i++) {
				System.gc();
			}
			Runtime runtime = Runtime.getRuntime();
			long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
			System.out.println("[MemoryProbe] block states: " + all + ", granddoors: " + ours + ", heap in use after gc: " + used + " MB");
		});
	}
}
