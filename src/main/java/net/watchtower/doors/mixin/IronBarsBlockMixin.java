package net.watchtower.doors.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.watchtower.doors.GrandDoorBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bars join up with a Grand Doors fence gate beside them. Vanilla asks a neighbour whether
 * bars attach to it without saying on which side, so this adds the arm afterwards.
 */
@Mixin(IronBarsBlock.class)
abstract class IronBarsBlockMixin {
	@Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
	private void granddoors$joinGate(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbour, RandomSource random, CallbackInfoReturnable<BlockState> result) {
		BlockState bars = result.getReturnValue();
		if (bars != null && granddoors$joins(neighbour, direction) && bars.hasProperty(granddoors$arm(direction))) {
			result.setReturnValue(bars.setValue(granddoors$arm(direction), true));
		}
	}

	@Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
	private void granddoors$joinGateWhenPlaced(BlockPlaceContext context, CallbackInfoReturnable<BlockState> result) {
		BlockState bars = result.getReturnValue();
		if (bars == null) {
			return;
		}
		BlockState joined = bars;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockState neighbour = context.getLevel().getBlockState(context.getClickedPos().relative(direction));
			if (granddoors$joins(neighbour, direction) && joined.hasProperty(granddoors$arm(direction))) {
				joined = joined.setValue(granddoors$arm(direction), true);
			}
		}
		if (joined != bars) {
			result.setReturnValue(joined);
		}
	}

	@Unique
	private static boolean granddoors$joins(BlockState neighbour, Direction direction) {
		return direction.getAxis().isHorizontal() && neighbour.getBlock() instanceof GrandDoorBlock door && door.joinsFence(neighbour, direction);
	}

	@Unique
	private static BooleanProperty granddoors$arm(Direction direction) {
		return switch (direction) {
			case NORTH -> BlockStateProperties.NORTH;
			case EAST -> BlockStateProperties.EAST;
			case SOUTH -> BlockStateProperties.SOUTH;
			default -> BlockStateProperties.WEST;
		};
	}
}
