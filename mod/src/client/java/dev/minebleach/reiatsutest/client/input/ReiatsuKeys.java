package dev.minebleach.reiatsutest.client.input;

import dev.minebleach.reiatsutest.client.ClientState;
import dev.minebleach.reiatsutest.client.net.ClientNet;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.RequestSource;
import dev.minebleach.reiatsutest.core.state.ResultCode;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Key bindings (STATE_MACHINE section 4): J draw / sheathe, R release (BASE to SHIKAI), G bankai, V seal, Z / H / B ability slots 1 to 3. None of them is a
 * vanilla 1.21.1 default (checked against GameOptions: W A S D, Space, E, F, Q, T, Tab, /, P, L, 1-9, C, X, F2, F5, F11,
 * Ctrl, Shift and the mouse buttons). All are rebindable in Options > Controls.
 */
public final class ReiatsuKeys {
	public static final String CATEGORY = "key.categories.reiatsu_test";

	/** Draw / sheathe toggle (B4 step 3). J is free in vanilla 1.21.1 (X and C are the creative hotbar keys). */
	public static final KeyBinding DRAW = register("key.reiatsu_test.draw", GLFW.GLFW_KEY_J);
	public static final KeyBinding RELEASE = register("key.reiatsu_test.release", GLFW.GLFW_KEY_R);
	public static final KeyBinding BANKAI = register("key.reiatsu_test.bankai", GLFW.GLFW_KEY_G);
	public static final KeyBinding SEAL = register("key.reiatsu_test.seal", GLFW.GLFW_KEY_V);
	public static final KeyBinding[] SLOTS = {
			register("key.reiatsu_test.ability_1", GLFW.GLFW_KEY_Z),
			register("key.reiatsu_test.ability_2", GLFW.GLFW_KEY_H),
			register("key.reiatsu_test.ability_3", GLFW.GLFW_KEY_B)};

	private ReiatsuKeys() {
	}

	private static KeyBinding register(String translationKey, int glfwKey) {
		return KeyBindingHelper.registerKeyBinding(new KeyBinding(translationKey, InputUtil.Type.KEYSYM, glfwKey, CATEGORY));
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ReiatsuKeys::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client.player == null) {
			drain();
			return;
		}
		if (client.currentScreen != null) {
			drain(); // keys typed into a screen never trigger abilities
			return;
		}
		while (DRAW.wasPressed()) {
			// toggle: SEALED draws the sword, any drawn state sheathes it (V seals as well)
			ClientNet.requestTransition(ClientState.zanpakuto().zanpakutoState() == ZanpakutoState.SEALED
					? ZanpakutoState.BASE : ZanpakutoState.SEALED, RequestSource.KEY);
		}
		while (RELEASE.wasPressed()) {
			ClientNet.requestTransition(ZanpakutoState.SHIKAI, RequestSource.KEY);
		}
		while (BANKAI.wasPressed()) {
			ClientNet.requestTransition(ZanpakutoState.BANKAI, RequestSource.KEY);
		}
		while (SEAL.wasPressed()) {
			ClientNet.requestTransition(ZanpakutoState.SEALED, RequestSource.KEY);
		}
		for (int slot = 0; slot < SLOTS.length; slot++) {
			while (SLOTS[slot].wasPressed()) {
				castSlot(client, slot);
			}
		}
	}

	/** Resolves slot + synced state + held item to an ability and sends it; the server re-validates everything. */
	public static void castSlot(MinecraftClient client, int slot) {
		var z = ClientState.zanpakuto();
		CharacterId character = z.zanpakutoState() == ZanpakutoState.SEALED ? ClientState.heldCharacter() : z.characterId();
		AbilityId ability = AbilityId.forSlot(character, z.zanpakutoState(), slot);
		if (ability == null || z.zanpakutoState() == ZanpakutoState.SEALED || z.zanpakutoState() == ZanpakutoState.BASE) {
			ClientNet.feedback(client, ResultCode.DENIED_STATE);
			return;
		}
		ClientNet.castAbility(ability, RequestSource.KEY);
	}

	private static void drain() {
		while (DRAW.wasPressed()) { }
		while (RELEASE.wasPressed()) { }
		while (BANKAI.wasPressed()) { }
		while (SEAL.wasPressed()) { }
		for (KeyBinding k : SLOTS) {
			while (k.wasPressed()) { }
		}
	}

	/** Dev/harness: simulate a physical key press of one binding (goes through the same wasPressed() path). */
	public static void press(KeyBinding binding) {
		KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey()));
	}
}
