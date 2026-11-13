package net.watchtower.doors.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.watchtower.doors.GrandDoorBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla only knows its own door block as a door a mob can plan a path through. This tells
 * the path finder that a closed wooden Grand Door can be opened, like a wooden vanilla door,
 * and that the others are shut for good, like an iron one.
 */
@Mixin(WalkNodeEvaluator.class)
abstract class WalkNodeEvaluatorMixin {
	@Inject(method = "getPathTypeFromState", at = @At("HEAD"), cancellable = true)
	private static void granddoors$doorType(BlockGetter level, BlockPos pos, CallbackInfoReturnable<PathType> result) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof GrandDoorBlock door) {
			result.setReturnValue(door.pathType(state));
		}
	}
}
