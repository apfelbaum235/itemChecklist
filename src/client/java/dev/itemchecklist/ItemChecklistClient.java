package dev.itemchecklist;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ItemChecklistClient implements ClientModInitializer {
	public static final String MOD_ID = "itemchecklist";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final int SCAN_INTERVAL_TICKS = 10;

	private static volatile Map<Item, Integer> counts = Map.of();
	private static int tickCounter = 0;

	private static KeyMapping openKey;
	private static KeyMapping toggleKey;
	private static KeyMapping prevListKey;
	private static KeyMapping nextListKey;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static Map<Item, Integer> counts() {
		return counts;
	}

	public static void rescan() {
		var player = Minecraft.getInstance().player;
		update(player == null ? Map.of() : InventoryScanner.scan(player));
	}

	private static void update(Map<Item, Integer> fresh) {
		if (!fresh.equals(counts)) {
			counts = fresh;
		}
	}

	private static void switchList(int delta) {
		ChecklistConfig config = ChecklistConfig.get();
		if (!config.hudVisible || config.lists.size() < 2) {
			return;
		}
		config.cycleActive(delta);
		ChecklistConfig.save();
	}

	@Override
	public void onInitializeClient() {
		ChecklistConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(id("main"));
		openKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.itemchecklist.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, category));
		toggleKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.itemchecklist.toggle", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, category));
		prevListKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.itemchecklist.prev_list", InputConstants.Type.KEYBOARD, InputConstants.KEY_LEFT, category));
		nextListKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.itemchecklist.next_list", InputConstants.Type.KEYBOARD, InputConstants.KEY_RIGHT, category));

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("checklist"), ChecklistHud::extract);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				client.gui.setScreen(new ChecklistScreen());
			}
			while (toggleKey.consumeClick()) {
				ChecklistConfig config = ChecklistConfig.get();
				config.hudVisible = !config.hudVisible;
				ChecklistConfig.save();
			}
			while (prevListKey.consumeClick()) {
				switchList(-1);
			}
			while (nextListKey.consumeClick()) {
				switchList(1);
			}

			if (client.player == null) {
				counts = Map.of();
				tickCounter = 0;
				return;
			}
			if (tickCounter++ % SCAN_INTERVAL_TICKS == 0) {
				update(InventoryScanner.scan(client.player));
			}
		});

		LOGGER.info("Item Checklist loaded");
	}
}
