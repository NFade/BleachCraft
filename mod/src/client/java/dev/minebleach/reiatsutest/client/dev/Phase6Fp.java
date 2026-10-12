package dev.minebleach.reiatsutest.client.dev;

import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.cmd;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.mc;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.selectSlot;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.shot;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.step;
import static dev.minebleach.reiatsutest.client.dev.Phase6Harness.stepUntil;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.fx.FxClock;
import dev.minebleach.reiatsutest.client.fx.FxEvents;
import dev.minebleach.reiatsutest.client.fx.FxTimelines;
import dev.minebleach.reiatsutest.client.fx.ScreenFx;
import dev.minebleach.reiatsutest.client.input.ReiatsuKeys;
import dev.minebleach.reiatsutest.client.model.FpClock;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;

/**
 * Pose harness for the first person animations (fp2). Console commands of {@code -Phold=console} (see {@link Phase6Release}):
 * <pre>
 * fp setup rukia|byakuya sealed|base|shikai|bankai [night]   items, state, first person view, HUD off, look at the horizon
 * fp clock fixed|real        fixed: the FP animations only move when advanced (exact times for contact sheets)
 * fp adv 0.25                advance the fixed FP clock (seconds), waits until done
 * fp shot name               screenshot run/p6_name.png
 * fp series name n step      n screenshots, the clock advanced by step seconds before each (first one at +0)
 * fp use down|up             right mouse button held / released (block)
 * fp attack                  one left click (Better Combat strike)
 * fp key draw|seal|release|bankai|shunpo|slot1..9|kido1..3   a real key press
 * fp cast id                 a server effect event as the server builds it (ability gestures)
 * fp fov 70 | fp gui 3 | fp look yaw pitch | fp hud on|off | fp wait ticks
 * </pre>
 */
final class Phase6Fp {
	private Phase6Fp() {
	}

	static boolean console(String[] w) {
		if (!w[0].equals("fp")) {
			return false;
		}
		switch (w[1]) {
			case "setup" -> setup(w[2].equals("rukia"), w[3], w.length > 4 && w[4].equals("night"));
			case "clock" -> step("fp: clock", 2, () -> FpClock.setFixed(w[2].equals("fixed")));
			case "adv" -> advance(Double.parseDouble(w[2]));
			case "shot" -> step("fp: shot " + w[2], 2, () -> shot(w[2]));
			case "series" -> {
				int n = Integer.parseInt(w[3]);
				double dt = Double.parseDouble(w[4]);
				for (int i = 0; i < n; i++) {
					if (i > 0) {
						advance(dt);
					}
					final String name = w[2] + "_" + String.format("%02d", i);
					step("fp: shot " + name, 2, () -> shot(name));
				}
			}
			case "use" -> step("fp: use " + w[2], 2, () -> mc.options.useKey.setPressed(w[2].equals("down")));
			case "attack" -> step("fp: attack", 2, () -> ReiatsuKeys.press(mc.options.attackKey));
			case "key" -> step("fp: key " + w[2], 2, () -> ReiatsuKeys.press(key(w[2])));
			case "cast" -> step("fp: cast " + w[2], 2, () -> fakeEvent(Integer.parseInt(w[2])));
			case "fov" -> step("fp: fov", 2, () -> mc.options.getFov().setValue(Integer.parseInt(w[2])));
			case "gui" -> step("fp: gui", 6, () -> {
				mc.options.getGuiScale().setValue(Integer.parseInt(w[2]));
				mc.onResolutionChanged();
			});
			case "look" -> step("fp: look", 4, () -> cmd("tp @s 0.5 -60 0.5 " + w[2] + " " + w[3]));
			case "hud" -> step("fp: hud", 2, () -> mc.options.hudHidden = w[2].equals("off"));
			case "wait" -> step("fp: wait", Integer.parseInt(w[2]), () -> { });
			default -> ReiatsuTest.LOGGER.warn("[phase6] console: unknown fp command {}", w[1]);
		}
		return true;
	}

	private static KeyBinding key(String n) {
		return switch (n) {
			case "draw" -> ReiatsuKeys.DRAW;
			case "seal" -> ReiatsuKeys.SEAL;
			case "release" -> ReiatsuKeys.RELEASE;
			case "bankai" -> ReiatsuKeys.BANKAI;
			case "shunpo" -> ReiatsuKeys.SHUNPO;
			case "kido1" -> ReiatsuKeys.KIDO[0];
			case "kido2" -> ReiatsuKeys.KIDO[1];
			case "kido3" -> ReiatsuKeys.KIDO[2];
			default -> {
				if (n.startsWith("bslot")) {
					yield ReiatsuKeys.BANKAI_SLOTS[Integer.parseInt(n.substring(5)) - 1];
				}
				if (n.startsWith("slot")) {
					yield ReiatsuKeys.SLOTS[Integer.parseInt(n.substring(4)) - 1];
				}
				throw new IllegalArgumentException(n);
			}
		};
	}

	private static void advance(double seconds) {
		step("fp: advance " + seconds, 1, () -> FpClock.advance(seconds));
		stepUntil("fp: advancing", 2, 20 * 20, () -> { }, FpClock::idle);
		step("fp: advanced", 2, () -> { });
	}

	private static void setup(boolean rukia, String state, boolean night) {
		String ch = rukia ? "rukia" : "byakuya";
		step("fp: setup " + ch + " " + state, 36, () -> {
			FxClock.unfreeze();
			FxTimelines.clear();
			ScreenFx.clear();
			mc.options.useKey.setPressed(false);
			mc.options.setPerspective(Perspective.FIRST_PERSON);
			mc.options.hudHidden = false;
			cmd("gamemode creative @s", "time set " + (night ? "midnight" : "noon"), "reiatsu cooldowns clear", "reiatsu state sealed", "reiatsu full",
					"item replace entity @s hotbar.0 with " + Phase6Harness.RUKIA, "item replace entity @s hotbar.1 with " + Phase6Harness.BYAKUYA,
					"tp @s 0.5 -60 0.5 0 0");
			selectSlot(rukia ? 0 : 1);
		});
		if (!state.equals("sealed")) {
			step("fp: state " + state, 30, () -> cmd("reiatsu state " + state + " " + ch));
		}
	}

	private static void fakeEvent(int effectId) {
		var p = mc.player;
		var look = p.getRotationVec(1.0f);
		var e = new dev.minebleach.reiatsutest.net.EffectEventS2C(effectId, p.getId(), 4242, p.getX(), p.getY(), p.getZ(), (float) look.x,
				(float) look.y, (float) look.z, -1, 0, new float[0]);
		FxEvents.onEffectEvent(mc, e);
	}
}
