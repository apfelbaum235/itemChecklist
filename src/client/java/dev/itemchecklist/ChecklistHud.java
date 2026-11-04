package dev.itemchecklist;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ChecklistHud {
	private static final int PADDING = 3;
	private static final int ROW_HEIGHT = 18;
	private static final int BOX_SIZE = 9;

	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_DONE = 0xFF55FF55;
	private static final int COLOR_BACKGROUND = 0x90000000;

	private static final int[][] CHECK_PIXELS = {{2, 4}, {3, 5}, {4, 4}, {5, 3}, {6, 2}};

	public static boolean editorOpen = false;

	public static int lastX, lastY, lastW, lastH;

	private record Row(ItemStack stack, String text, boolean done) {
	}

	private static boolean cacheValid = false;
	private static boolean cachedVisible = false;
	private static Map<Item, Integer> cachedCounts;
	private static int cachedSignature;
	private static List<Row> cachedRows = List.of();
	private static Component cachedTitle = Component.empty();
	private static Component cachedMore = null;
	private static boolean cachedAllDone;
	private static int cachedWidth;
	private static int cachedHeight;

	private ChecklistHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!editorOpen) {
			draw(graphics);
		}
	}

	public static void invalidate() {
		cacheValid = false;
	}

	public static void draw(GuiGraphicsExtractor graphics) {
		Minecraft mc = Minecraft.getInstance();
		ChecklistConfig config = ChecklistConfig.get();

		lastX = config.hudX;
		lastY = config.hudY;
		lastW = 0;
		lastH = 0;
		if (mc.player == null || (!editorOpen && !config.hudVisible)) {
			return;
		}

		float scale = ChecklistConfig.clampScalePercent(Math.round(config.hudScale * 100)) / 100f;
		int guiW = mc.getWindow().getGuiScaledWidth();
		int guiH = mc.getWindow().getGuiScaledHeight();

		Map<Item, Integer> counts = ItemChecklistClient.counts();
		int signature = signature(config, scale, guiH);
		if (!cacheValid || counts != cachedCounts || signature != cachedSignature) {
			rebuild(mc, config, counts, scale, guiH);
			cachedCounts = counts;
			cachedSignature = signature;
			cacheValid = true;
		}
		if (!cachedVisible) {
			return;
		}

		int width = cachedWidth;
		int height = cachedHeight;
		int scaledW = Math.round(width * scale);
		int scaledH = Math.round(height * scale);
		int x = Math.max(0, Math.min(config.hudX, guiW - scaledW));
		int y = Math.max(0, Math.min(config.hudY, guiH - scaledH));
		lastX = x;
		lastY = y;
		lastW = scaledW;
		lastH = scaledH;

		int lineHeight = mc.font.lineHeight;

		graphics.pose().pushMatrix();
		graphics.pose().translate((float) x, (float) y);
		graphics.pose().scale(scale, scale);

		graphics.fill(0, 0, width, height, COLOR_BACKGROUND);

		int cx = PADDING;
		int cy = PADDING;
		graphics.text(mc.font, cachedTitle, cx, cy, cachedAllDone ? COLOR_DONE : COLOR_TEXT, true);
		cy += lineHeight + 3;

		for (Row row : cachedRows) {
			drawCheckbox(graphics, cx, cy + (ROW_HEIGHT - BOX_SIZE) / 2, row.done());
			graphics.item(row.stack(), cx + BOX_SIZE + 3, cy + 1);
			graphics.text(mc.font, row.text(), cx + BOX_SIZE + 3 + 16 + 3, cy + (ROW_HEIGHT - lineHeight) / 2 + 1,
					row.done() ? COLOR_DONE : COLOR_TEXT, true);
			cy += ROW_HEIGHT;
		}

		if (cachedMore != null) {
			graphics.text(mc.font, cachedMore, cx, cy, 0xFFAAAAAA, true);
		}

		graphics.pose().popMatrix();
	}

	private static int signature(ChecklistConfig config, float scale, int guiH) {
		ChecklistConfig.Checklist list = config.active();
		int h = config.lists.size();
		h = 31 * h + config.activeIndex();
		h = 31 * h + (list.name == null ? 0 : list.name.hashCode());
		h = 31 * h + list.entries.size();
		for (ChecklistConfig.Entry entry : list.entries) {
			h = 31 * h + entry.count;
			h = 31 * h + (entry.item == null ? 0 : entry.item.hashCode());
		}
		h = 31 * h + Float.floatToIntBits(scale);
		h = 31 * h + guiH;
		return h;
	}

	private static void rebuild(Minecraft mc, ChecklistConfig config, Map<Item, Integer> counts, float scale, int guiH) {
		List<Row> rows = new ArrayList<>();
		int doneCount = 0;
		for (ChecklistConfig.Entry entry : config.active().entries) {
			Item item = entry.item();
			if (item == null) {
				continue;
			}
			int have = counts.getOrDefault(item, 0);
			boolean done = have >= entry.count;
			if (done) {
				doneCount++;
			}
			String text = have + "/" + entry.count + " " + entry.name();
			rows.add(new Row(entry.stack(), text, done));
		}

		boolean multiple = config.lists.size() > 1;
		if (rows.isEmpty() && !multiple) {
			cachedVisible = false;
			cachedRows = List.of();
			return;
		}

		int total = rows.size();
		int lineHeight = mc.font.lineHeight;
		int maxRows = Math.max(1, (int) (guiH / scale - lineHeight - 4 * PADDING - 8) / ROW_HEIGHT - 1);
		boolean truncated = total > maxRows;
		List<Row> visible = truncated ? new ArrayList<>(rows.subList(0, maxRows)) : rows;

		String name = config.nameOf(config.activeIndex());
		cachedTitle = Component.literal(multiple ? "< " + name + " >" : name);
		cachedMore = truncated ? Component.translatable("itemchecklist.hud.more", total - maxRows) : null;
		cachedAllDone = total > 0 && doneCount == total;

		int contentWidth = mc.font.width(cachedTitle);
		for (Row row : visible) {
			contentWidth = Math.max(contentWidth, BOX_SIZE + 3 + 16 + 3 + mc.font.width(row.text()));
		}
		if (cachedMore != null) {
			contentWidth = Math.max(contentWidth, mc.font.width(cachedMore));
		}

		cachedWidth = contentWidth + 2 * PADDING;
		cachedHeight = PADDING + lineHeight
				+ (visible.isEmpty() ? 0 : 3 + visible.size() * ROW_HEIGHT)
				+ (cachedMore != null ? lineHeight + 2 : 0) + PADDING;
		cachedRows = visible;
		cachedVisible = true;
	}

	private static void drawCheckbox(GuiGraphicsExtractor graphics, int x, int y, boolean checked) {
		graphics.fill(x, y, x + BOX_SIZE, y + BOX_SIZE, checked ? COLOR_DONE : 0xFFFFFFFF);
		graphics.fill(x + 1, y + 1, x + BOX_SIZE - 1, y + BOX_SIZE - 1, 0xFF000000);
		if (checked) {
			for (int[] pixel : CHECK_PIXELS) {
				graphics.fill(x + pixel[0], y + pixel[1], x + pixel[0] + 1, y + pixel[1] + 1, COLOR_DONE);
			}
		}
	}
}
