package dev.minebleach.reiatsutest.core.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The phrase table ({@code voice/phrases.json}, generated from design/VOICE_PHRASES.md by tools/gen_voice_phrases.py).
 * Each command lists the states and the item it is valid for and every spoken variant; keys are computed here with
 * {@link Normalizer}, so the file only holds readable text.
 */
public final class PhraseBook {
	public static final String RESOURCE = "/assets/reiatsu_test/voice/phrases.json";

	/** One spoken form of a command. {@code weak}: only accepted as the whole utterance. */
	public record Variant(String text, String origin, boolean weak, boolean requiresFullReiatsu, String key,
			List<String> tokens, String compact) {
	}

	public record Command(String id, Set<ZanpakutoState> states, CharacterId item, boolean interimSafe, boolean optional,
			String say, List<Variant> variants) {
		/** True when the command is valid for the context (always true for an ungated context). */
		public boolean validIn(GateContext ctx) {
			if (!ctx.gated()) {
				return true;
			}
			return states.contains(ctx.state()) && (item == CharacterId.NONE || item == ctx.held());
		}
	}

	private final List<Command> commands;
	private final Set<String> fillerKeys;
	private final Map<String, Command> byId = new LinkedHashMap<>();

	private PhraseBook(List<Command> commands, Set<String> fillerKeys) {
		this.commands = Collections.unmodifiableList(commands);
		this.fillerKeys = Collections.unmodifiableSet(fillerKeys);
		for (Command c : commands) {
			byId.put(c.id(), c);
		}
	}

	public List<Command> commands() {
		return commands;
	}

	public Command command(String id) {
		return byId.get(id);
	}

	/** Normalised filler words ("please", "пожалуйста", ...) that are ignored for the whole-utterance rule. */
	public Set<String> fillerKeys() {
		return fillerKeys;
	}

	/** Loads the bundled table from the classpath. */
	public static PhraseBook loadBundled() {
		try (InputStream in = PhraseBook.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("missing resource " + RESOURCE);
			}
			return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new IllegalStateException("cannot read " + RESOURCE, e);
		}
	}

	public static PhraseBook parse(String json) {
		return parse(new java.io.StringReader(json));
	}

	/** Parses and validates the schema; throws {@link IllegalArgumentException} naming the offending path. */
	public static PhraseBook parse(Reader reader) {
		JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
		int version = root.has("version") ? root.get("version").getAsInt() : 0;
		if (version != 1) {
			throw new IllegalArgumentException("version must be 1, got " + version);
		}
		Set<String> fillers = new LinkedHashSet<>();
		for (JsonElement e : array(root, "fillers", "$")) {
			String key = Normalizer.normalize(e.getAsString());
			if (!key.isEmpty()) {
				fillers.add(key);
			}
		}
		List<Command> out = new ArrayList<>();
		Set<String> ids = new LinkedHashSet<>();
		for (JsonElement ce : array(root, "commands", "$")) {
			JsonObject c = ce.getAsJsonObject();
			String id = string(c, "id", "command");
			if (!ids.add(id)) {
				throw new IllegalArgumentException("duplicate command id " + id);
			}
			EnumSet<ZanpakutoState> states = EnumSet.noneOf(ZanpakutoState.class);
			for (JsonElement s : array(c, "states", id)) {
				try {
					states.add(ZanpakutoState.valueOf(s.getAsString()));
				} catch (IllegalArgumentException ex) {
					throw new IllegalArgumentException(id + ": unknown state " + s.getAsString());
				}
			}
			if (states.isEmpty()) {
				throw new IllegalArgumentException(id + ": states is empty");
			}
			CharacterId item = switch (string(c, "item", id)) {
				case "rukia" -> CharacterId.RUKIA;
				case "byakuya" -> CharacterId.BYAKUYA;
				case "any" -> CharacterId.NONE;
				default -> throw new IllegalArgumentException(id + ": item must be rukia, byakuya or any");
			};
			List<Variant> variants = new ArrayList<>();
			for (JsonElement ve : array(c, "variants", id)) {
				JsonObject v = ve.getAsJsonObject();
				String text = string(v, "text", id);
				String origin = string(v, "origin", id);
				boolean weak = v.has("weak") && v.get("weak").getAsBoolean();
				boolean full = false;
				if (v.has("requires")) {
					for (JsonElement r : v.getAsJsonArray("requires")) {
						if (r.getAsString().equals("full_reiatsu")) {
							full = true;
						} else {
							throw new IllegalArgumentException(id + ": unknown requirement " + r.getAsString());
						}
					}
				}
				String key = Normalizer.normalize(text);
				if (key.isEmpty()) {
					throw new IllegalArgumentException(id + ": variant '" + text + "' normalises to nothing");
				}
				List<String> tokens = Normalizer.tokens(key);
				variants.add(new Variant(text, origin, weak, full, key, List.copyOf(tokens), Normalizer.compact(tokens)));
			}
			if (variants.isEmpty()) {
				throw new IllegalArgumentException(id + ": no variants");
			}
			out.add(new Command(id, Collections.unmodifiableSet(states), item,
					c.has("interimSafe") && c.get("interimSafe").getAsBoolean(),
					c.has("optional") && c.get("optional").getAsBoolean(), string(c, "say", id), List.copyOf(variants)));
		}
		return new PhraseBook(out, fillers);
	}

	private static JsonArray array(JsonObject o, String name, String where) {
		if (!o.has(name) || !o.get(name).isJsonArray()) {
			throw new IllegalArgumentException(where + ": '" + name + "' must be an array");
		}
		return o.getAsJsonArray(name);
	}

	private static String string(JsonObject o, String name, String where) {
		if (!o.has(name) || !o.get(name).isJsonPrimitive()) {
			throw new IllegalArgumentException(where + ": '" + name + "' must be a string");
		}
		return o.get(name).getAsString();
	}
}
