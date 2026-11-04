package dev.itemchecklist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ChecklistConfig {
	public static final int MAX_COUNT = 9999;
	public static final int MAX_NAME_LENGTH = 24;

	public static final class Entry {
		public String item;
		public int count;

		private transient Item resolved;
		private transient ItemStack stack;
		private transient String name;

		public Entry() {
		}

		public Entry(String item, int count) {
			this.item = item;
			this.count = count;
		}

		public ItemStack stack() {
			Item found = item();
			if (found == null) {
				return ItemStack.EMPTY;
			}
			if (stack == null) {
				stack = found.getDefaultInstance();
			}
			return stack;
		}

		public String name() {
			if (name == null) {
				ItemStack s = stack();
				name = s.isEmpty() ? String.valueOf(item) : s.getHoverName().getString();
			}
			return name;
		}

		public Item item() {
			if (resolved == null && item != null) {
				Identifier id = Identifier.tryParse(item);
				if (id != null) {
					Item found = BuiltInRegistries.ITEM.getValue(id);
					if (found != null && found != Items.AIR) {
						resolved = found;
					}
				}
			}
			return resolved;
		}
	}

	public static final class Checklist {
		public String name;
		public List<Entry> entries = new ArrayList<>();

		public Checklist() {
		}

		public Checklist(String name) {
			this.name = name;
		}

		public void add(Item item, int amount) {
			String id = BuiltInRegistries.ITEM.getKey(item).toString();
			for (Entry entry : entries) {
				if (id.equals(entry.item)) {
					entry.count = clamp(entry.count + amount);
					return;
				}
			}
			entries.add(new Entry(id, clamp(amount)));
		}

		public int targetOf(Item item) {
			String id = BuiltInRegistries.ITEM.getKey(item).toString();
			for (Entry entry : entries) {
				if (id.equals(entry.item)) {
					return entry.count;
				}
			}
			return 0;
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("itemchecklist.json");
	private static ChecklistConfig instance = createDefault();

	public boolean hudVisible = true;
	public int hudX = 4;
	public int hudY = 4;
	public float hudScale = 1.0f;
	public int activeList = 0;
	public List<Checklist> lists = new ArrayList<>();
	public List<Entry> entries;

	public static ChecklistConfig get() {
		return instance;
	}

	private static ChecklistConfig createDefault() {
		ChecklistConfig config = new ChecklistConfig();
		config.normalize();
		return config;
	}

	public static String defaultName(int index) {
		return "List " + (index + 1);
	}

	public static void load() {
		if (!Files.exists(FILE)) {
			return;
		}
		try {
			String json = Files.readString(FILE, StandardCharsets.UTF_8);
			ChecklistConfig loaded = GSON.fromJson(json, ChecklistConfig.class);
			if (loaded != null) {
				loaded.normalize();
				instance = loaded;
			}
		} catch (IOException | JsonParseException e) {
			ItemChecklistClient.LOGGER.warn("Could not read {}, starting with an empty list", FILE, e);
		}
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(instance), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ItemChecklistClient.LOGGER.warn("Could not save {}", FILE, e);
		}
	}

	private void normalize() {
		if (lists == null) {
			lists = new ArrayList<>();
		}
		lists.removeIf(list -> list == null);
		if (lists.isEmpty()) {
			Checklist first = new Checklist(defaultName(0));
			if (entries != null) {
				first.entries.addAll(entries);
			}
			lists.add(first);
		}
		entries = null;

		for (Checklist list : lists) {
			if (list.entries == null) {
				list.entries = new ArrayList<>();
			}
			list.entries.removeIf(entry -> entry == null || entry.item == null);
		}
		activeList = activeIndex();
	}

	public int activeIndex() {
		return Math.max(0, Math.min(activeList, lists.size() - 1));
	}

	public Checklist active() {
		return lists.get(activeIndex());
	}

	public void setActive(int index) {
		activeList = Math.max(0, Math.min(index, lists.size() - 1));
	}

	public void cycleActive(int delta) {
		activeList = Math.floorMod(activeIndex() + delta, lists.size());
	}

	public String nameOf(int index) {
		String name = lists.get(index).name;
		return name == null || name.isBlank() ? defaultName(index) : name.strip();
	}

	public void addList() {
		lists.add(new Checklist(defaultName(lists.size())));
		activeList = lists.size() - 1;
	}

	public void removeList(int index) {
		if (lists.size() <= 1 || index < 0 || index >= lists.size()) {
			return;
		}
		int active = activeIndex();
		lists.remove(index);
		if (active > index) {
			active--;
		}
		activeList = Math.max(0, Math.min(active, lists.size() - 1));
	}

	public static int clampScalePercent(int percent) {
		return Math.max(30, Math.min(400, percent));
	}

	public static int clamp(int value) {
		return Math.max(1, Math.min(MAX_COUNT, value));
	}
}
