package dev.itemchecklist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChecklistScreen extends Screen {
	private static final int ROW_HEIGHT = 20;
	private static final int ROWS_TOP = 68;
	private static final int BOTTOM_RESERVED = 34;
	private static final int AMOUNT_BOX_WIDTH = 40;
	private static final int SELECT_WIDTH = 56;
	private static final int DELETE_WIDTH = 44;

	private enum Mode {ADD, LIST, LISTS, DISPLAY}

	private record Candidate(Item item, ItemStack stack, String name, String nameLower, String idLower) {
	}

	private static List<Candidate> allCandidates;

	private Mode mode = Mode.ADD;
	private String query = "";
	private String amountText = "1";
	private int page = 0;
	private int confirmDelete = -1;

	private List<Candidate> filtered = new ArrayList<>();

	private EditBox searchBox;
	private EditBox amountBox;
	private Button addTab;
	private Button listTab;
	private Button listsTab;
	private Button displayTab;
	private Button newListButton;
	private Button prevButton;
	private Button nextButton;
	private DragHandle dragHandle;
	private final List<AbstractWidget> dynamicWidgets = new ArrayList<>();

	private int left;
	private int panelWidth;
	private int rowsPerPage;

	public ChecklistScreen() {
		super(Component.translatable("itemchecklist.screen.title"));
	}

	private static List<Candidate> candidates() {
		if (allCandidates == null) {
			List<Candidate> list = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				if (item == Items.AIR) {
					continue;
				}
				ItemStack stack = item.getDefaultInstance();
				String name = stack.getHoverName().getString();
				String id = BuiltInRegistries.ITEM.getKey(item).toString();
				list.add(new Candidate(item, stack, name, name.toLowerCase(Locale.ROOT), id.toLowerCase(Locale.ROOT)));
			}
			list.sort(Comparator.comparing(Candidate::nameLower));
			allCandidates = list;
		}
		return allCandidates;
	}

	private static boolean sanitize(EditBox box, String text) {
		String clean = text.replaceAll("\\D", "");
		if (clean.equals(text)) {
			return false;
		}
		box.setValue(clean);
		return true;
	}

	private static int parseOr(String text, int fallback) {
		try {
			int value = Integer.parseInt(text.trim());
			return value >= 1 ? value : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	@Override
	protected void init() {
		dynamicWidgets.clear();
		ChecklistHud.editorOpen = true;

		panelWidth = Math.min(this.width - 20, 380);
		left = (this.width - panelWidth) / 2;
		rowsPerPage = Math.max(1, (this.height - BOTTOM_RESERVED - ROWS_TOP) / ROW_HEIGHT);

		int tabWidth = (panelWidth - 12) / 4;
		addTab = Button.builder(Component.translatable("itemchecklist.screen.tab.add"), b -> switchMode(Mode.ADD))
				.bounds(left, 20, tabWidth, 20).build();
		listTab = Button.builder(Component.empty(), b -> switchMode(Mode.LIST))
				.bounds(left + (tabWidth + 4), 20, tabWidth, 20).build();
		listsTab = Button.builder(Component.translatable("itemchecklist.screen.tab.lists"), b -> switchMode(Mode.LISTS))
				.bounds(left + 2 * (tabWidth + 4), 20, tabWidth, 20).build();
		displayTab = Button.builder(Component.translatable("itemchecklist.screen.tab.display"), b -> switchMode(Mode.DISPLAY))
				.bounds(left + 3 * (tabWidth + 4), 20, tabWidth, 20).build();
		addRenderableWidget(addTab);
		addRenderableWidget(listTab);
		addRenderableWidget(listsTab);
		addRenderableWidget(displayTab);

		searchBox = new EditBox(this.font, left, 44, panelWidth - AMOUNT_BOX_WIDTH - 4, 18,
				Component.translatable("itemchecklist.screen.search"));
		searchBox.setHint(Component.translatable("itemchecklist.screen.search"));
		searchBox.setValue(query);
		searchBox.setResponder(text -> {
			query = text;
			page = 0;
			refresh();
		});
		addRenderableWidget(searchBox);

		amountBox = new EditBox(this.font, left + panelWidth - AMOUNT_BOX_WIDTH, 44, AMOUNT_BOX_WIDTH, 18,
				Component.translatable("itemchecklist.screen.amount"));
		amountBox.setHint(Component.translatable("itemchecklist.screen.amount"));
		amountBox.setMaxLength(4);
		amountBox.setValue(amountText);
		amountBox.setResponder(text -> {
			if (sanitize(amountBox, text)) {
				return;
			}
			amountText = text;
		});
		addRenderableWidget(amountBox);

		newListButton = Button.builder(Component.translatable("itemchecklist.lists.new"), b -> {
			ChecklistConfig.get().addList();
			ChecklistConfig.save();
			confirmDelete = -1;
			page = Integer.MAX_VALUE;
			refresh();
		}).bounds(left, 43, 130, 20).build();
		addRenderableWidget(newListButton);

		int bottomY = this.height - 28;
		prevButton = Button.builder(Component.literal("<"), b -> changePage(-1))
				.bounds(left, bottomY, 24, 20).build();
		nextButton = Button.builder(Component.literal(">"), b -> changePage(1))
				.bounds(left + panelWidth - 24, bottomY, 24, 20).build();
		Button doneButton = Button.builder(Component.translatable("itemchecklist.screen.done"), b -> this.onClose())
				.bounds(this.width / 2 - 40, bottomY, 80, 20).build();
		addRenderableWidget(prevButton);
		addRenderableWidget(nextButton);
		addRenderableWidget(doneButton);
		addRenderableWidget(Button.builder(Component.translatable("itemchecklist.screen.import"),
						b -> Minecraft.getInstance().gui.setScreen(new ImportScreen(this)))
				.bounds(left + 28, bottomY, 80, 20).build());

		dragHandle = new DragHandle();
		addRenderableWidget(dragHandle);

		if (mode == Mode.ADD) {
			setInitialFocus(searchBox);
		}
		refresh();
	}

	private void switchMode(Mode newMode) {
		if (mode != newMode) {
			mode = newMode;
			page = 0;
			confirmDelete = -1;
			refresh();
		}
	}

	private void changePage(int delta) {
		page += delta;
		confirmDelete = -1;
		refresh();
	}

	private int totalRows() {
		return switch (mode) {
			case ADD -> filtered.size();
			case LIST -> ChecklistConfig.get().active().entries.size();
			case LISTS -> ChecklistConfig.get().lists.size();
			case DISPLAY -> 0;
		};
	}

	private int pageCount() {
		return Math.max(1, (totalRows() + rowsPerPage - 1) / rowsPerPage);
	}

	private void refresh() {
		for (AbstractWidget widget : dynamicWidgets) {
			removeWidget(widget);
		}
		dynamicWidgets.clear();

		ChecklistConfig config = ChecklistConfig.get();

		if (mode == Mode.ADD) {
			String q = query.trim().toLowerCase(Locale.ROOT);
			filtered = new ArrayList<>();
			for (Candidate candidate : candidates()) {
				if (q.isEmpty() || candidate.nameLower().contains(q) || candidate.idLower().contains(q)) {
					filtered.add(candidate);
				}
			}
		}

		page = Math.max(0, Math.min(page, pageCount() - 1));

		addTab.active = mode != Mode.ADD;
		listTab.active = mode != Mode.LIST;
		listsTab.active = mode != Mode.LISTS;
		displayTab.active = mode != Mode.DISPLAY;
		updateListTabLabel();
		searchBox.visible = mode == Mode.ADD;
		searchBox.active = mode == Mode.ADD;
		amountBox.visible = mode == Mode.ADD;
		amountBox.active = mode == Mode.ADD;
		newListButton.visible = mode == Mode.LISTS;
		newListButton.active = mode == Mode.LISTS;
		prevButton.active = page > 0;
		nextButton.active = page < pageCount() - 1;

		if (mode == Mode.DISPLAY) {
			buildDisplayWidgets();
			return;
		}

		int start = page * rowsPerPage;
		int end = Math.min(totalRows(), start + rowsPerPage);
		for (int index = start; index < end; index++) {
			int y = ROWS_TOP + (index - start) * ROW_HEIGHT;
			switch (mode) {
				case ADD -> addResultRow(filtered.get(index), y);
				case LIST -> addListRow(config.active().entries.get(index), y);
				case LISTS -> addListsRow(index, y);
				case DISPLAY -> {
				}
			}
		}
	}

	private void updateListTabLabel() {
		listTab.setMessage(Component.translatable("itemchecklist.screen.tab.list",
				ChecklistConfig.get().active().entries.size()));
	}

	private void addResultRow(Candidate candidate, int y) {
		Button button = Button.builder(Component.empty(), b -> {
			ChecklistConfig.get().active().add(candidate.item(), parseOr(amountText, 1));
			ChecklistConfig.save();
			updateListTabLabel();
		}).bounds(left, y, panelWidth, ROW_HEIGHT - 2).build();
		track(button);
	}

	private void addListRow(ChecklistConfig.Entry entry, int y) {
		int right = left + panelWidth;
		int removeX = right - 20;
		int plus64X = removeX - 2 - 28;
		int plus1X = plus64X - 2 - 22;
		int numberX = plus1X - 2 - 32;
		int minus1X = numberX - 2 - 22;
		int minus64X = minus1X - 2 - 28;
		int h = ROW_HEIGHT - 2;

		EditBox numberBox = new EditBox(this.font, numberX, y, 32, h, Component.empty());
		numberBox.setMaxLength(4);
		numberBox.setValue(Integer.toString(entry.count));
		numberBox.setResponder(text -> {
			if (sanitize(numberBox, text)) {
				return;
			}
			int value = parseOr(text, -1);
			if (value >= 1) {
				entry.count = Math.min(value, ChecklistConfig.MAX_COUNT);
			}
		});
		track(numberBox);

		track(Button.builder(Component.literal("-64"), b -> change(entry, numberBox, -64)).bounds(minus64X, y, 28, h).build());
		track(Button.builder(Component.literal("-1"), b -> change(entry, numberBox, -1)).bounds(minus1X, y, 22, h).build());
		track(Button.builder(Component.literal("+1"), b -> change(entry, numberBox, 1)).bounds(plus1X, y, 22, h).build());
		track(Button.builder(Component.literal("+64"), b -> change(entry, numberBox, 64)).bounds(plus64X, y, 28, h).build());
		track(Button.builder(Component.literal("X"), b -> {
			ChecklistConfig.get().active().entries.remove(entry);
			ChecklistConfig.save();
			refresh();
		}).bounds(removeX, y, 20, h).build());
	}

	private void addListsRow(int index, int y) {
		ChecklistConfig config = ChecklistConfig.get();
		ChecklistConfig.Checklist list = config.lists.get(index);
		int right = left + panelWidth;
		int h = ROW_HEIGHT - 2;
		int deleteX = right - DELETE_WIDTH;
		int selectX = deleteX - 2 - SELECT_WIDTH;
		int nameWidth = selectX - 4 - left;

		EditBox nameBox = new EditBox(this.font, left, y, nameWidth, h, Component.translatable("itemchecklist.screen.tab.lists"));
		nameBox.setMaxLength(ChecklistConfig.MAX_NAME_LENGTH);
		nameBox.setHint(Component.literal(ChecklistConfig.defaultName(index)));
		nameBox.setValue(list.name == null ? "" : list.name);
		nameBox.setResponder(text -> list.name = text);
		track(nameBox);

		boolean isActive = index == config.activeIndex();
		Button select = Button.builder(Component.translatable(isActive ? "itemchecklist.lists.active" : "itemchecklist.lists.select"), b -> {
			config.setActive(index);
			ChecklistConfig.save();
			confirmDelete = -1;
			refresh();
		}).bounds(selectX, y, SELECT_WIDTH, h).build();
		select.active = !isActive;
		track(select);

		boolean confirming = confirmDelete == index;
		Button delete = Button.builder(confirming ? Component.translatable("itemchecklist.lists.confirm") : Component.literal("X"), b -> {
			if (confirmDelete == index || list.entries.isEmpty()) {
				confirmDelete = -1;
				config.removeList(index);
				ChecklistConfig.save();
			} else {
				confirmDelete = index;
			}
			refresh();
		}).bounds(deleteX, y, DELETE_WIDTH, h).build();
		delete.active = config.lists.size() > 1;
		track(delete);
	}

	private void change(ChecklistConfig.Entry entry, EditBox numberBox, int delta) {
		entry.count = ChecklistConfig.clamp(entry.count + delta);
		numberBox.setValue(Integer.toString(entry.count));
		ChecklistConfig.save();
	}

	private void buildDisplayWidgets() {
		ChecklistConfig config = ChecklistConfig.get();
		int y = ROWS_TOP + 12;

		EditBox scaleBox = new EditBox(this.font, left + 28, y + 1, 48, 18, Component.translatable("itemchecklist.screen.size"));
		scaleBox.setMaxLength(3);
		scaleBox.setValue(Integer.toString(Math.round(config.hudScale * 100)));
		scaleBox.setResponder(text -> {
			if (sanitize(scaleBox, text)) {
				return;
			}
			int percent = parseOr(text, -1);
			if (percent >= 30 && percent <= 400) {
				config.hudScale = percent / 100f;
			}
		});

		track(Button.builder(Component.literal("-"), b -> changeScale(scaleBox, -10)).bounds(left, y, 24, 20).build());
		track(scaleBox);
		track(Button.builder(Component.literal("+"), b -> changeScale(scaleBox, 10)).bounds(left + 80, y, 24, 20).build());

		track(Button.builder(Component.translatable("itemchecklist.screen.reset_position"), b -> {
			config.hudX = 4;
			config.hudY = 4;
			ChecklistConfig.save();
		}).bounds(left, y + 32, panelWidth, 20).build());

		Button visibleButton = Button.builder(visibleLabel(), b -> {
			config.hudVisible = !config.hudVisible;
			b.setMessage(visibleLabel());
			ChecklistConfig.save();
		}).bounds(left, y + 58, panelWidth, 20).build();
		track(visibleButton);
	}

	private Component visibleLabel() {
		return Component.translatable("itemchecklist.screen.visible",
				Component.translatable(ChecklistConfig.get().hudVisible ? "options.on" : "options.off"));
	}

	private void changeScale(EditBox scaleBox, int deltaPercent) {
		ChecklistConfig config = ChecklistConfig.get();
		int percent = ChecklistConfig.clampScalePercent(Math.round(config.hudScale * 100) + deltaPercent);
		config.hudScale = percent / 100f;
		scaleBox.setValue(Integer.toString(percent));
		ChecklistConfig.save();
	}

	private void track(AbstractWidget widget) {
		dynamicWidgets.add(widget);
		addRenderableWidget(widget);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		ChecklistConfig config = ChecklistConfig.get();

		ChecklistHud.draw(graphics);
		int hx = ChecklistHud.lastX;
		int hy = ChecklistHud.lastY;
		int hw = Math.max(40, ChecklistHud.lastW);
		int hh = Math.max(16, ChecklistHud.lastH);
		dragHandle.setRectangle(hw, hh, hx, hy);
		int outline = 0xFFFFFF55;
		graphics.fill(hx - 1, hy - 1, hx + hw + 1, hy, outline);
		graphics.fill(hx - 1, hy + hh, hx + hw + 1, hy + hh + 1, outline);
		graphics.fill(hx - 1, hy, hx, hy + hh, outline);
		graphics.fill(hx + hw, hy, hx + hw + 1, hy + hh, outline);

		String heading = this.title.getString() + " - " + config.nameOf(config.activeIndex());
		graphics.text(this.font, this.font.plainSubstrByWidth(heading, panelWidth), left, 8, 0xFFFFFFFF, true);

		if (mode != Mode.DISPLAY) {
			Component pageText = Component.translatable("itemchecklist.screen.page", page + 1, pageCount());
			graphics.text(this.font, pageText, this.width / 2 - this.font.width(pageText) / 2, this.height - 40, 0xFFAAAAAA, true);
		}

		int start = page * rowsPerPage;
		int end = Math.min(totalRows(), start + rowsPerPage);

		if (mode == Mode.ADD) {
			if (filtered.isEmpty()) {
				graphics.text(this.font, Component.translatable("itemchecklist.screen.no_results"), left, ROWS_TOP + 4, 0xFFFF8888, true);
			}
			ChecklistConfig.Checklist active = config.active();
			for (int index = start; index < end; index++) {
				Candidate candidate = filtered.get(index);
				int y = ROWS_TOP + (index - start) * ROW_HEIGHT;
				graphics.item(candidate.stack(), left + 3, y + 1);
				int target = active.targetOf(candidate.item());
				String label = target > 0 ? candidate.name() + "  (" + target + ")" : candidate.name();
				graphics.text(this.font, this.font.plainSubstrByWidth(label, panelWidth - 30), left + 24, y + 5,
						target > 0 ? 0xFF55FF55 : 0xFFFFFFFF, true);
			}
		} else if (mode == Mode.LIST) {
			List<ChecklistConfig.Entry> entries = config.active().entries;
			if (entries.isEmpty()) {
				graphics.text(this.font, Component.translatable("itemchecklist.screen.empty"), left, ROWS_TOP + 4, 0xFFFF8888, true);
			}
			graphics.text(this.font, Component.translatable("itemchecklist.screen.hint_list"), left, 49, 0xFFAAAAAA, true);

			Map<Item, Integer> counts = ItemChecklistClient.counts();
			int right = left + panelWidth;
			int numberX = right - 20 - 2 - 28 - 2 - 22 - 2 - 32;
			int minus64X = numberX - 2 - 22 - 2 - 28;

			for (int index = start; index < end; index++) {
				ChecklistConfig.Entry entry = entries.get(index);
				int y = ROWS_TOP + (index - start) * ROW_HEIGHT;
				Item item = entry.item();

				String name = entry.name();
				int have = 0;
				if (item != null) {
					have = counts.getOrDefault(item, 0);
					graphics.item(entry.stack(), left + 3, y + 1);
				}
				boolean done = item != null && have >= entry.count;
				graphics.text(this.font, this.font.plainSubstrByWidth(name, Math.max(10, minus64X - (left + 24) - 4)),
						left + 24, y + 5, done ? 0xFF55FF55 : 0xFFFFFFFF, true);
			}
		} else if (mode == Mode.LISTS) {
			String hint = Component.translatable("itemchecklist.lists.hint").getString();
			graphics.text(this.font, this.font.plainSubstrByWidth(hint, panelWidth - 140), left + 138, 49, 0xFFAAAAAA, true);
		} else {
			graphics.text(this.font, Component.translatable("itemchecklist.screen.size"), left, ROWS_TOP, 0xFFFFFFFF, true);
			graphics.text(this.font, "%", left + 108, ROWS_TOP + 12 + 6, 0xFFFFFFFF, true);
			graphics.text(this.font, Component.translatable("itemchecklist.screen.drag_hint"), left,
					ROWS_TOP + 12 + 90, 0xFFAAAAAA, true);
		}
	}

	@Override
	public void removed() {
		ChecklistHud.editorOpen = false;
		ChecklistConfig.save();
		ItemChecklistClient.rescan();
		super.removed();
	}

	private static final class DragHandle extends AbstractWidget {
		private double posX;
		private double posY;

		DragHandle() {
			super(0, 0, 0, 0, Component.empty());
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			ChecklistConfig config = ChecklistConfig.get();
			posX = config.hudX;
			posY = config.hudY;
		}

		@Override
		protected void onDrag(MouseButtonEvent event, double dx, double dy) {
			Minecraft mc = Minecraft.getInstance();
			ChecklistConfig config = ChecklistConfig.get();
			double maxX = Math.max(0, mc.getWindow().getGuiScaledWidth() - Math.max(40, ChecklistHud.lastW));
			double maxY = Math.max(0, mc.getWindow().getGuiScaledHeight() - Math.max(16, ChecklistHud.lastH));
			posX = Math.max(0, Math.min(maxX, posX + dx));
			posY = Math.max(0, Math.min(maxY, posY + dy));
			config.hudX = (int) Math.round(posX);
			config.hudY = (int) Math.round(posY);
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}
}
