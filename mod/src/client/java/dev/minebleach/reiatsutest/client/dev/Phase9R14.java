package dev.minebleach.reiatsutest.client.dev;

import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cmd;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.mc;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.step;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.stepUntil;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.wants;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.FxTune;
import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import dev.minebleach.reiatsutest.client.fx.UryuFx;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import java.util.Locale;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;

/**
 * R14 harness (files {@code run/screenshots/p7_r14_*.png}): {@code r14_cross} (the sealed cross in the hand, first and third person),
 * {@code r14_cast} (Uryu's ability casts for a right hander, third person), {@code r14_focus} (Focus pips under the crosshair, Focus on shift),
 * {@code r14_steam} (the shikai aura of Rukia and Byakuya: idle, old behaviour, after a cast), {@code r14_frost} (Rukia's freeze on armoured zombies).
 * {@code gradlew runPhase7 -Phold=r14_steam}. Numbers are logged with the prefix {@code [r14]}, the pictures are the result.
 */
final class Phase9R14 {
	private static final String ZOMBIE = "summon minecraft:zombie %.1f -60 %.1f {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b,Rotation:[%df,0f],"
			+ "ArmorItems:[{id:\"minecraft:iron_boots\",count:1},{id:\"minecraft:iron_leggings\",count:1},{id:\"minecraft:iron_chestplate\",count:1},{id:\"minecraft:iron_helmet\",count:1}]}";
	/** An unarmoured Steve-like target: a husk shows the skin, a zombie too. */
	private static final String HUSK = "summon minecraft:husk %.1f -60 %.1f {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b,Rotation:[%df,0f]}";

	private Phase9R14() {
	}

	static boolean wantsAny() {
		return wants("r14_dev") || wants("r14_cross") || wants("r14_crossvar") || wants("r14_cast") || wants("r14_castfp") || wants("r14_focus") || wants("r14_steam") || wants("r14_frost");
	}

	static void steps() {
		if (wants("r14_dev")) {
			devCommands();
		}
		if (wants("r14_cross")) {
			for (String view : new String[] {"fp", "back", "front"}) {
				final String n = "cross_" + view;
				scene(n, view, "sealed", "uryu", "uryu_cross");
				step(n + " shot", 4, () -> shot(n));
			}
		}
		if (wants("r14_crossvar")) {
			crossVariants();
		}
		if (wants("r14_cast")) {
			for (String view : new String[] {"back", "front"}) {
				abilitySet(view);
			}
		}
		if (wants("r14_castfp")) {
			abilitySet("fp"); // R15: the same six casts from the first person view
		}
		if (wants("r14_focus")) {
			focus();
		}
		if (wants("r14_steam")) {
			for (String ch : new String[] {"rukia", "byakuya"}) {
				steam(ch);
			}
		}
		if (wants("r14_frost")) {
			frost();
		}
		step("r14: cleanup", 4, () -> {
			mc.options.useKey.setPressed(false);
			mc.options.attackKey.setPressed(false);
			mc.options.sneakKey.setPressed(false);
			mc.options.forwardKey.setPressed(false);
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			mc.options.hudHidden = false;
			FxTune.unset("aura.shikaiOnlyOnCast");
			cmd("kill @e[type=minecraft:zombie]", "kill @e[type=minecraft:husk]", "time set noon", "reiatsu state sealed uryu");
		});
	}

	private static void scene(String name, String view, String state, String character, String item) {
		scene(name, view, state, character, item, true);
	}

	private static void scene(String name, String view, String state, String character, String item, boolean hudHidden) {
		step(name + " (scene)", 40, () -> {
			mc.options.useKey.setPressed(false);
			mc.options.attackKey.setPressed(false);
			FxClock.unfreeze();
			FxTimelines.setFreezeAt(-1);
			FxTimelines.clear();
			ScreenFx.clear();
			mc.options.getFov().setValue(70);
			boolean fp = view.equals("fp");
			mc.options.hudHidden = hudHidden && !fp; // vanilla draws no first person hands with the HUD hidden
			mc.options.setPerspective(fp ? Perspective.FIRST_PERSON : view.equals("back") ? Perspective.THIRD_PERSON_BACK : Perspective.THIRD_PERSON_FRONT);
			cmd("tp @s 0.5 -60 0.5 0 5", "time set noon", "kill @e[type=minecraft:zombie]", "kill @e[type=minecraft:husk]", "difficulty easy", "gamerule doMobSpawning false",
					"item replace entity @s hotbar.0 with reiatsu_test:" + item);
			Phase6Harness.selectSlot(0);
			cmd("reiatsu cooldowns clear", "reiatsu state " + state + " " + character, "reiatsu dev ragefree true", "reiatsu full");
		});
		step(name + " (settle)", 24, () -> { });
	}

	private static void shot(String name) {
		Phase7Uryu.shot7("r14_" + name);
	}

	private static void log(String what) {
		ReiatsuTest.LOGGER.info("[r14] {}", what);
	}

	private static String balanceLine() {
		var c = dev.minebleach.reiatsutest.core.state.BalanceConfig.live();
		var t = c.spec(dev.minebleach.reiatsutest.core.state.AbilityId.TSUKISHIRO);
		return String.format(Locale.ROOT, "live balance: tsukishiro cost %.1f cooldown %.1f s radius %.1f | shikai rate %d/%d | uryu shunpo cooldown %d ticks",
				t.costTenths() / 10.0, t.cooldownTicks() / 20.0, t.num("radius"), c.rate(dev.minebleach.reiatsutest.core.state.ZanpakutoState.SHIKAI).regenPerBatch(),
				c.rate(dev.minebleach.reiatsutest.core.state.ZanpakutoState.SHIKAI).drainPerBatch(),
				c.shunpo().forCharacter(dev.minebleach.reiatsutest.core.state.CharacterId.URYU).cooldownTicks());
	}

	private static String fileSection(String key) {
		try {
			var root = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(mc.runDirectory.toPath().resolve("config").resolve("reiatsu_test.json")))
					.getAsJsonObject();
			return String.valueOf(root.get(key));
		} catch (java.io.IOException | RuntimeException e) {
			return "unreadable: " + e;
		}
	}

	/** /reiatsu dev get | set | unset: every change is read back from the live numbers and from the file. */
	private static void devCommands() {
		step("dev: start", 10, () -> log(balanceLine()));
		step("dev: get sections", 6, () -> cmd("reiatsu dev get"));
		step("dev: get tsukishiro", 6, () -> cmd("reiatsu dev get balance.tsukishiro"));
		step("dev: set cost", 10, () -> cmd("reiatsu dev set balance.tsukishiro.cost 20", "reiatsu dev set balance.tsukishiro.cooldown 8.5", "reiatsu dev set balance.regen.shikai.perBatchTenths 12",
				"reiatsu dev set balance.shunpo.uryuCooldownBonus 2"));
		step("dev: read back", 4, () -> {
			log(balanceLine());
			log("file balance = " + fileSection("balance"));
		});
		step("dev: set others", 10, () -> cmd("reiatsu dev set uryu.bow.damage 5", "reiatsu dev set rage.parryGainTenths 60", "reiatsu dev set uryu.rage.abilityArrowsPayCastReward false",
				"reiatsu dev set balance.tsukishiro.radius 99999", "reiatsu dev set nosuch.key 1", "reiatsu dev set rage.parryGainTenths many"));
		step("dev: read back 2", 4, () -> {
			log("file uryu = " + fileSection("uryu"));
			log("file rage.parryGainTenths = " + (com.google.gson.JsonParser.parseString(fileSection("rage")).getAsJsonObject().get("parryGainTenths")));
			log(balanceLine());
		});
		step("dev: unset and restore", 10, () -> cmd("reiatsu dev unset balance.tsukishiro.cost", "reiatsu dev unset balance.tsukishiro.cooldown", "reiatsu dev unset balance.tsukishiro.radius",
				"reiatsu dev unset balance.regen.shikai.perBatchTenths", "reiatsu dev unset balance.shunpo.uryuCooldownBonus", "reiatsu dev set uryu.bow.damage 3",
				"reiatsu dev set rage.parryGainTenths 50", "reiatsu dev set uryu.rage.abilityArrowsPayCastReward true"));
		step("dev: final", 4, () -> {
			log(balanceLine());
			log("file balance = " + fileSection("balance"));
			log("file uryu = " + fileSection("uryu"));
		});
	}

	/** Dev: variants of the sealed cross hold (FxTune keys uryu.cross.*), first person and third person. */
	private static void crossVariants() {
		String[][] fp = {
				{"a", "uryu.cross.fpX=0.0", "uryu.cross.fpRy=30", "uryu.cross.fpItemPitch=0", "uryu.cross.fpItemScale=0.6", "uryu.cross.fpItemYaw=-40", "uryu.cross.fpItemDx=-0.07", "uryu.cross.fpItemDy=0.08", "uryu.cross.fpItemDz=0.1"},
				{"b", "uryu.cross.fpX=0.0", "uryu.cross.fpRy=30", "uryu.cross.fpItemPitch=0", "uryu.cross.fpItemScale=0.6", "uryu.cross.fpItemYaw=-40", "uryu.cross.fpItemDx=-0.07", "uryu.cross.fpItemDy=0.0", "uryu.cross.fpItemDz=0.1"},
				{"c", "uryu.cross.fpX=0.0", "uryu.cross.fpRy=30", "uryu.cross.fpItemPitch=0", "uryu.cross.fpItemScale=0.6", "uryu.cross.fpItemYaw=-40", "uryu.cross.fpItemDx=-0.07", "uryu.cross.fpItemDy=-0.05", "uryu.cross.fpItemDz=0.1"},
				{"d", "uryu.cross.fpX=0.0", "uryu.cross.fpRy=30", "uryu.cross.fpItemPitch=0", "uryu.cross.fpItemScale=0.6", "uryu.cross.fpItemYaw=-40", "uryu.cross.fpItemDx=-0.07", "uryu.cross.fpItemDy=-0.08", "uryu.cross.fpItemDz=0.1"},
				{"e", "uryu.cross.fpX=0.0", "uryu.cross.fpRy=30", "uryu.cross.fpItemPitch=0", "uryu.cross.fpItemScale=0.6", "uryu.cross.fpItemYaw=-40", "uryu.cross.fpItemDx=-0.1", "uryu.cross.fpItemDy=0.0", "uryu.cross.fpItemDz=0.1"},
				{"f", "uryu.cross.fpX=0.0", "uryu.cross.fpRy=30", "uryu.cross.fpItemPitch=0", "uryu.cross.fpItemScale=0.6", "uryu.cross.fpItemYaw=-40", "uryu.cross.fpItemDx=-0.1", "uryu.cross.fpItemDy=-0.05", "uryu.cross.fpItemDz=0.1"},
				{"g", "uryu.cross.fpX=0.0", "uryu.cross.fpRy=30", "uryu.cross.fpItemPitch=0", "uryu.cross.fpItemScale=0.6", "uryu.cross.fpItemYaw=-40", "uryu.cross.fpItemDx=-0.1", "uryu.cross.fpItemDy=-0.05", "uryu.cross.fpItemDz=0.14"},
				{"h", "uryu.cross.fpX=0.0", "uryu.cross.fpRy=30", "uryu.cross.fpItemPitch=0", "uryu.cross.fpItemScale=0.6", "uryu.cross.fpItemYaw=-40", "uryu.cross.fpItemDx=-0.1", "uryu.cross.fpItemDy=-0.05", "uryu.cross.fpItemDz=0.06"}};
		for (String[] v : fp) {
			final String n = "crossvar_" + v[0];
			scene(n, "fp", "sealed", "uryu", "uryu_cross");
			step(n + " apply", 8, () -> {
				FxTune.override("uryu.cross.fpY", 0.45);
				applyVariant(v);
			});
			step(n + " shot", 2, () -> shot(n));
		}
		step("crossvar reset", 1, () -> {
			for (String key : new String[] {"fpX", "fpY", "fpZ", "fpRx", "fpRy", "fpRz", "armPitch", "armYaw", "armRoll", "itemTurn", "fpItemPitch", "fpItemRoll", "fpItemYaw", "fpItemDx", "fpItemDy", "fpItemDz", "fpItemScale", "tpItemPitch", "tpItemRoll", "tpItemYaw"}) {
				FxTune.unset("uryu.cross." + key);
			}
		});
	}

	private static void applyVariant(String[] v) {
		for (int i = 1; i < v.length; i++) {
			String[] kv = v[i].split("=");
			FxTune.override(kv[0], Double.parseDouble(kv[1]));
		}
	}

	/** Uryu's ability poses seen from the third person camera: shikai Z, H, B, bankai Z, H, B. */
	private static void abilitySet(String view) {
		String[][] names = {{"shikai", "z"}, {"shikai", "h"}, {"shikai", "b"}, {"bankai", "z"}, {"bankai", "h"}, {"bankai", "b"}};
		for (String[] nm : names) {
			final String state = nm[0];
			final int idx = nm[1].equals("z") ? 0 : nm[1].equals("h") ? 1 : 2;
			final KeyBinding key = (state.equals("bankai") ? ReiatsuKeys.BANKAI_SLOTS : ReiatsuKeys.SLOTS)[idx];
			final String n = "cast_" + view + "_" + state + "_" + nm[1];
			scene(n, view, state, "uryu", "uryu_cross");
			step(n + " cast", 3, () -> Phase6Harness.press(key));
			step(n + " +3", 3, () -> shot(n + "_a"));
			step(n + " +9", 6, () -> shot(n + "_b"));
			step(n + " +20", 12, () -> shot(n + "_c"));
		}
	}

	/** Focus pips under the crosshair (HUD shown, first person) after standing still, and the Focus while walking on shift. */
	private static void focus() {
		scene("focus", "fp", "shikai", "uryu", "uryu_cross", false);
		step("focus wait", 80, () -> { });
		step("focus shot", 2, () -> {
			ReiatsuTest.LOGGER.info("[r14] focus standing still: {} stacks", UryuFx.focus);
			shot("focus_hud");
		});
		step("focus sneak", 1, () -> {
			mc.options.sneakKey.setPressed(true);
			mc.options.forwardKey.setPressed(true);
		});
		for (int i = 0; i < 6; i++) {
			final int k = i;
			step("focus sneak walk " + k, 12, () -> ReiatsuTest.LOGGER.info(String.format(Locale.ROOT, "[r14] sneak walk +%d ticks: focus %d, speed %.3f b/t", (k + 1) * 12, UryuFx.focus,
					Math.hypot(mc.player.getX() - mc.player.prevX, mc.player.getZ() - mc.player.prevZ))));
		}
		step("focus sneak shot", 1, () -> shot("focus_sneak"));
		step("focus stop", 1, () -> {
			mc.options.sneakKey.setPressed(false);
			mc.options.forwardKey.setPressed(false);
		});
	}

	/** The shikai aura of Rukia and Byakuya in the third person: nothing when idle, the old continuous aura, and after a cast. */
	private static void steam(String ch) {
		final String item = ch.equals("rukia") ? "sode_no_shirayuki" : "senbonzakura";
		final String n = "steam_" + ch;
		scene(n, "back", "sealed", ch, item);
		step(n + " shikai", 1, () -> {
			FxTune.unset("aura.shikaiOnlyOnCast");
			cmd("reiatsu state shikai " + ch);
		});
		step(n + " release", 12, () -> shot(n + "_release"));
		step(n + " quiet", 20 * 8, () -> { });
		step(n + " idle shot", 2, () -> shot(n + "_idle"));
		step(n + " old", 1, () -> FxTune.override("aura.shikaiOnlyOnCast", 0));
		step(n + " old wait", 40, () -> { });
		step(n + " old shot", 2, () -> shot(n + "_old_continuous"));
		step(n + " new", 1, () -> FxTune.unset("aura.shikaiOnlyOnCast"));
		step(n + " quiet2", 20 * 6, () -> { });
		step(n + " cast", 1, () -> Phase6Harness.press(ReiatsuKeys.SLOTS[2]));
		step(n + " cast wait", 10, () -> { });
		step(n + " cast shot", 2, () -> shot(n + "_cast"));
	}

	/** Rukia shikai, armoured zombies and a husk (bare skin): Tsukishiro and Hakuren, shots at +10, +30 and +60 ticks. */
	private static void frost() {
		for (String ab : new String[] {"tsuki", "hak"}) {
			final String n = "frost_" + ab;
			scene(n, "back", "shikai", "rukia", "sode_no_shirayuki");
			step(n + " mobs", 6, () -> {
				cmd(String.format(Locale.ROOT, ZOMBIE, 0.5, 4.5, 180), String.format(Locale.ROOT, ZOMBIE, 2.5, 5.5, 180),
						String.format(Locale.ROOT, HUSK, -1.5, 5.5, 180), String.format(Locale.ROOT, HUSK, 0.5, 7.0, 180));
			});
			step(n + " settle", 30, () -> { });
			step(n + " before", 1, () -> shot(n + "_0"));
			step(n + " cast", 1, () -> Phase6Harness.press(ReiatsuKeys.SLOTS[ab.equals("tsuki") ? 0 : 1]));
			step(n + " +10", 10, () -> shot(n + "_10"));
			step(n + " +30", 20, () -> shot(n + "_30"));
			step(n + " +60", 30, () -> shot(n + "_60"));
			step(n + " +100", 40, () -> shot(n + "_100"));
		}
	}
}
