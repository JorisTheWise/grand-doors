package net.watchtower.doors.mixin;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.watchtower.doors.GrandDoorBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A wall only joins up with vanilla's own fence gate block. This lets it join a Grand Doors fence gate. */
@Mixin(WallBlock.class)
abstract class WallBlockMixin {
	@Inject(method = "connectsTo", at = @At("HEAD"), cancellable = true)
	private void granddoors$joinGate(BlockState state, boolean sturdy, Direction direction, CallbackInfoReturnable<Boolean> result) {
		if (state.getBlock() instanceof GrandDoorBlock door && door.joinsFence(state, direction)) {
			result.setReturnValue(true);
		}
	}
}
