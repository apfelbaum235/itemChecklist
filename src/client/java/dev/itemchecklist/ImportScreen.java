package dev.itemchecklist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImportScreen extends Screen {
	private static final int ROW_HEIGHT = 22;
	private static final int ROWS_TOP = 62;
	private static final int BOTTOM_RESERVED = 34;
	private static final int CHOICE_WIDTH = 150;
	private static final int MAX_OPTIONS = 12;

	private static final Pattern COUNT_FIRST = Pattern.compile(
			"^[\\s\\-*\\u2022]*(\\d{1,5})\\s*(?:[x\\u00d7](?=\\s))?\\s*[\\-\\u2013\\u2014:.)]*\\s*(\\S.*)$");
	private static final Pattern NAME_FIRST = Pattern.compile(
			"^(\\S.*?)(?:\\s+[x\\u00d7]\\s*|\\s*[:\\-\\u2013\\u2014]\\s*|\\s+)(\\d{1,5})\\s*$");

	private static final class ParsedLine {
		final int count;
		final String original;
		final List<ItemStack> options;
		int choice;

		ParsedLine(int count, String original, List<ItemStack> options, boolean confident) {
			this.count = count;
			this.original = original;
			this.options = options;
			this.choice = confident ? 0 : options.size();
		}

		ItemStack chosen() {
			return choice < options.size() ? options.get(choice) : null;
		}

		void cycle() {
			choice = (choice + 1) % (options.size() + 1);
		}
	}

	private final Screen parent;
	private List<ParsedLine> lines = new ArrayList<>();
	private boolean loaded = false;
	private boolean replace = false;
	private int page = 0;

	private Button replaceButton;
	private Button prevButton;
	private Button nextButton;
	private Button applyButton;
	private Component summary = Component.empty();
	private final List<AbstractWidget> dynamicWidgets = new ArrayList<>();

	private int left;
	private int panelWidth;
	private int rowsPerPage;

	public ImportScreen(Screen parent) {
		super(Component.translatable("itemchecklist.import.title"));
		this.parent = parent;
	}

	private void readClipboard() {
		String text = Minecraft.getInstance().keyboardHandler.getClipboard();
		lines = parse(text == null ? "" : text);
		loaded = true;
	}

	private static List<ParsedLine> parse(String text) {
		List<ParsedLine> result = new ArrayList<>();
		for (String rawLine : text.split("\\R")) {
			String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			int count;
			String name;
			Matcher first = COUNT_FIRST.matcher(line);
			if (first.matches()) {
				count = parseCount(first.group(1));
				name = first.group(2).trim();
			} else {
				Matcher second = NAME_FIRST.matcher(line);
				if (!second.matches()) {
					continue;
				}
				name = second.group(1).trim();
				count = parseCount(second.group(2));
			}

			ItemMatcher.Result match = ItemMatcher.find(name, MAX_OPTIONS);
			List<ItemStack> stacks = new ArrayList<>(match.items().size());
			for (Item item : match.items()) {
				stacks.add(item.getDefaultInstance());
			}
			result.add(new ParsedLine(count, name, stacks, match.confident()));
		}
		return result;
	}

	private static int parseCount(String digits) {
		try {
			return ChecklistConfig.clamp(Integer.parseInt(digits));
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	@Override
	protected void init() {
		dynamicWidgets.clear();
		if (!loaded) {
			readClipboard();
		}

		panelWidth = Math.min(this.width - 20, 360);
		left = (this.width - panelWidth) / 2;
		rowsPerPage = Math.max(1, (this.height - BOTTOM_RESERVED - ROWS_TOP) / ROW_HEIGHT);

		int half = (panelWidth - 4) / 2;
		addRenderableWidget(Button.builder(Component.translatable("itemchecklist.import.reread"), b -> {
			readClipboard();
			page = 0;
			refresh();
		}).bounds(left, 32, half, 20).build());
		replaceButton = Button.builder(replaceLabel(), b -> {
			replace = !replace;
			b.setMessage(replaceLabel());
		}).bounds(left + half + 4, 32, half, 20).build();
		addRenderableWidget(replaceButton);

		int bottomY = this.height - 28;
		prevButton = Button.builder(Component.literal("<"), b -> {
			page--;
			refresh();
		}).bounds(left, bottomY, 24, 20).build();
		nextButton = Button.builder(Component.literal(">"), b -> {
			page++;
			refresh();
		}).bounds(left + panelWidth - 24, bottomY, 24, 20).build();
		applyButton = Button.builder(Component.empty(), b -> apply())
				.bounds(this.width / 2 - 134, bottomY, 130, 20).build();
		Button cancelButton = Button.builder(Component.translatable("gui.cancel"), b -> this.onClose())
				.bounds(this.width / 2 + 4, bottomY, 130, 20).build();
		addRenderableWidget(prevButton);
		addRenderableWidget(nextButton);
		addRenderableWidget(applyButton);
		addRenderableWidget(cancelButton);

		refresh();
	}

	private Component replaceLabel() {
		return Component.translatable("itemchecklist.import.replace",
				Component.translatable(replace ? "options.on" : "options.off"));
	}

	private int pageCount() {
		return Math.max(1, (lines.size() + rowsPerPage - 1) / rowsPerPage);
	}

	private int matchedCount() {
		int matched = 0;
		for (ParsedLine line : lines) {
			if (line.chosen() != null) {
				matched++;
			}
		}
		return matched;
	}

	private void refresh() {
		for (AbstractWidget widget : dynamicWidgets) {
			removeWidget(widget);
		}
		dynamicWidgets.clear();

		page = Math.max(0, Math.min(page, pageCount() - 1));
		prevButton.active = page > 0;
		nextButton.active = page < pageCount() - 1;

		int matched = matchedCount();
		applyButton.setMessage(Component.translatable("itemchecklist.import.apply", matched));
		applyButton.active = matched > 0;
		summary = Component.translatable("itemchecklist.import.summary", lines.size(), matched);

		int start = page * rowsPerPage;
		int end = Math.min(lines.size(), start + rowsPerPage);
		for (int index = start; index < end; index++) {
			ParsedLine line = lines.get(index);
			int y = ROWS_TOP + (index - start) * ROW_HEIGHT;
			Button choice = Button.builder(choiceLabel(line), b -> {
				line.cycle();
				b.setMessage(choiceLabel(line));
				refreshSummaryOnly();
			}).bounds(left + panelWidth - CHOICE_WIDTH, y, CHOICE_WIDTH, ROW_HEIGHT - 2).build();
			choice.active = !line.options.isEmpty();
			dynamicWidgets.add(choice);
			addRenderableWidget(choice);
		}
	}

	private void refreshSummaryOnly() {
		int matched = matchedCount();
		applyButton.setMessage(Component.translatable("itemchecklist.import.apply", matched));
		applyButton.active = matched > 0;
		summary = Component.translatable("itemchecklist.import.summary", lines.size(), matched);
	}

	private Component choiceLabel(ParsedLine line) {
		ItemStack chosen = line.chosen();
		if (chosen == null) {
			return Component.translatable("itemchecklist.import.skip");
		}
		return Component.literal(this.font.plainSubstrByWidth(chosen.getHoverName().getString(), CHOICE_WIDTH - 10));
	}

	private void apply() {
		ChecklistConfig.Checklist list = ChecklistConfig.get().active();
		if (replace) {
			list.entries.clear();
		}
		for (ParsedLine line : lines) {
			ItemStack chosen = line.chosen();
			if (chosen != null) {
				list.add(chosen.getItem(), line.count);
			}
		}
		ChecklistConfig.save();
		ItemChecklistClient.rescan();
		this.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		graphics.text(this.font, this.title, left, 8, 0xFFFFFFFF, true);
		graphics.text(this.font, Component.translatable("itemchecklist.import.hint"), left, 22, 0xFFAAAAAA, true);
		graphics.text(this.font, summary, left, 54 - 1, 0xFFFFFFFF, true);

		if (lines.isEmpty()) {
			graphics.text(this.font, Component.translatable("itemchecklist.import.empty"), left, ROWS_TOP + 4, 0xFFFF8888, true);
		}

		Component pageText = Component.translatable("itemchecklist.screen.page", page + 1, pageCount());
		graphics.text(this.font, pageText, this.width / 2 - this.font.width(pageText) / 2, this.height - 40, 0xFFAAAAAA, true);

		int start = page * rowsPerPage;
		int end = Math.min(lines.size(), start + rowsPerPage);
		int originalWidth = Math.max(10, panelWidth - 62 - CHOICE_WIDTH - 4);
		for (int index = start; index < end; index++) {
			ParsedLine line = lines.get(index);
			int y = ROWS_TOP + (index - start) * ROW_HEIGHT;
			ItemStack chosen = line.chosen();
			boolean ok = chosen != null;
			if (ok) {
				graphics.item(chosen, left + 2, y + 1);
			}
			graphics.text(this.font, line.count + "x", left + 22, y + 6, ok ? 0xFF55FF55 : 0xFFFF8888, true);
			graphics.text(this.font, this.font.plainSubstrByWidth(line.original, originalWidth), left + 62, y + 6,
					ok ? 0xFFFFFFFF : 0xFFAAAAAA, true);
		}
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(parent);
	}

	@Override
	public void removed() {
		ItemMatcher.clear();
		super.removed();
	}
}
