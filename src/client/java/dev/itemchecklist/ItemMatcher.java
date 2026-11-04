package dev.itemchecklist;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

public final class ItemMatcher {
	public record Result(List<Item> items, boolean confident) {
	}

	private record Entry(Item item, String name, String compactName, String compactId, Set<String> words) {
	}

	private static final Set<String> STOP_WORDS = Set.of("of", "the", "a", "an", "der", "die", "das", "von", "aus");

	private static List<Entry> index;

	private ItemMatcher() {
	}

	public static void clear() {
		index = null;
	}

	private static List<Entry> index() {
		if (index == null) {
			List<Entry> list = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				if (item == Items.AIR) {
					continue;
				}
				String name = normalize(item.getDefaultInstance().getHoverName().getString());
				String idWords = normalize(BuiltInRegistries.ITEM.getKey(item).getPath().replace('_', ' '));
				Set<String> words = new HashSet<>();
				addWords(words, name);
				addWords(words, idWords);
				list.add(new Entry(item, name, name.replace(" ", ""), idWords.replace(" ", ""), words));
			}
			index = list;
		}
		return index;
	}

	private static void addWords(Set<String> words, String text) {
		for (String word : text.split(" ")) {
			if (!word.isEmpty()) {
				words.add(word);
				words.add(singular(word));
			}
		}
	}

	static String normalize(String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
	}

	private static String singular(String word) {
		if (word.length() > 4 && word.endsWith("ies")) {
			return word.substring(0, word.length() - 3) + "y";
		}
		if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")) {
			return word.substring(0, word.length() - 1);
		}
		return word;
	}

	public static Result find(String raw, int max) {
		String q = normalize(raw);
		List<String> tokens = new ArrayList<>();
		for (String token : q.split(" ")) {
			if (!token.isEmpty() && !STOP_WORDS.contains(token)) {
				tokens.add(singular(token));
			}
		}
		if (tokens.isEmpty()) {
			return new Result(List.of(), false);
		}

		String compactRaw = q.replace(" ", "");
		String compactQ = String.join("", tokens);
		List<Entry> all = index();
		LinkedHashSet<Item> out = new LinkedHashSet<>();

		collect(all, out, max, e -> e.compactName().equals(compactRaw) || e.compactId().equals(compactRaw)
				|| e.compactName().equals(compactQ) || e.compactId().equals(compactQ));

		collect(all, out, max, e -> tokens.stream().allMatch(e.words()::contains));

		collect(all, out, max, e -> tokens.stream().allMatch(
				t -> t.length() >= 2 && (e.compactName().contains(t) || e.compactId().contains(t))));

		boolean confident = !out.isEmpty();
		if (!confident) {
			collect(all, out, max, e -> tokens.stream().anyMatch(
					t -> t.length() >= 3 && (e.compactName().contains(t) || e.compactId().contains(t))));
		}
		return new Result(new ArrayList<>(out), confident);
	}

	private static void collect(List<Entry> all, LinkedHashSet<Item> out, int max, Predicate<Entry> filter) {
		if (out.size() >= max) {
			return;
		}
		List<Entry> hits = new ArrayList<>();
		for (Entry entry : all) {
			if (!out.contains(entry.item()) && filter.test(entry)) {
				hits.add(entry);
			}
		}
		hits.sort(Comparator.comparingInt((Entry e) -> e.name().length()).thenComparing(Entry::name));
		for (Entry entry : hits) {
			if (out.size() >= max) {
				break;
			}
			out.add(entry.item());
		}
	}
}
