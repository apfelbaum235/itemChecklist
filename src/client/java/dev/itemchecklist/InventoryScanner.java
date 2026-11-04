package dev.itemchecklist;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.HashMap;
import java.util.Map;

public final class InventoryScanner {
	private InventoryScanner() {
	}

	public static Map<Item, Integer> scan(Player player) {
		Map<Item, Integer> counts = new HashMap<>();

		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			addStack(counts, inventory.getItem(slot));
		}

		addStack(counts, player.containerMenu.getCarried());

		return counts;
	}

	private static void addStack(Map<Item, Integer> counts, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		counts.merge(stack.getItem(), stack.getCount(), Integer::sum);

		if (isShulkerBox(stack)) {
			ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
			if (contents != null) {
				contents.nonEmptyItemCopyStream()
						.forEach(inner -> counts.merge(inner.getItem(), inner.getCount(), Integer::sum));
			}
		}
	}

	private static boolean isShulkerBox(ItemStack stack) {
		return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
	}
}
