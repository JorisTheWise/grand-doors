package net.watchtower.doors;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** The item of a door: it says how big the door is and how to place it the other way round. */
public class GrandDoorItem extends BlockItem {
	private final GrandDoorBlock door;

	public GrandDoorItem(GrandDoorBlock door, Item.Properties properties) {
		super(door, properties);
		this.door = door;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, lines, flag);
		String leaves = this.door.split() ? "tooltip.granddoors.two_leaves" : "tooltip.granddoors.one_leaf";
		lines.accept(Component.translatable("tooltip.granddoors.size", this.door.width(), this.door.height(), Component.translatable(leaves))
			.withStyle(ChatFormatting.GRAY));
		lines.accept(Component.translatable("tooltip.granddoors.sneak").withStyle(ChatFormatting.DARK_GRAY));
	}
}
