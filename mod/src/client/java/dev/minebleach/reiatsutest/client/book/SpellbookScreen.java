package dev.minebleach.reiatsutest.client.book;

import dev.minebleach.reiatsutest.client.ClientState;
import dev.minebleach.reiatsutest.core.book.BookArg;
import dev.minebleach.reiatsutest.core.book.BookFacts;
import dev.minebleach.reiatsutest.core.book.BookPage;
import dev.minebleach.reiatsutest.core.book.BookPager;
import dev.minebleach.reiatsutest.core.book.SpellbookCatalog;
import dev.minebleach.reiatsutest.core.book.SpellbookCatalog.Availability;
import dev.minebleach.reiatsutest.core.book.SpellbookId;
import dev.minebleach.reiatsutest.core.kido.KidoConfig;
import dev.minebleach.reiatsutest.core.sound.SoundIds;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.registry.SpellbookItem;
import dev.minebleach.reiatsutest.registry.data.ZanpakutoData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PageTurnWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/**
 * Read-only spellbook screen (B3). The vanilla book frame (book.png) with the vanilla page-turn arrows. Everything is laid
 * out in a fixed 192 x 222 "virtual" space and scaled down as a whole when the window is smaller (GUI scale 4 on a low
 * resolution), so it never runs off the screen; mouse coordinates are mapped back. A page whose abilities do not work in the
 * current state of the player (or with the other zanpakuto) is shown muted with a note. Text of every page comes from the
 * lang files ({@code book.reiatsu_test.<id>.page.N}); numbers and key names are filled in from {@link BookFacts} and the
 * current key bindings of the player. UNVERIFIED in game.
 */
public final class SpellbookScreen extends Screen {
	private static final int VW = 192;
	private static final int VH = 222;
	private static final int TEXT_X = 36;
	private static final int TEXT_W = 114;
	private static final int TITLE_Y = 28;
	/** Bottom of the text area (the page-turn arrows start at 159). */
	private static final int TEXT_BOTTOM = 157;
	private static final float[] SCALES = {1.0f, 0.85f, 0.75f, 0.65f};
	private static final float NOTE_SCALE = 0.75f;
	private static final int COLOR_TITLE = 0x7A1F1F;
	private static final int COLOR_TITLE_MUTED = 0x80584F;
	private static final int COLOR_TEXT = 0x000000;
	private static final int COLOR_TEXT_MUTED = 0x6B6B6B;
	private static final int COLOR_NOTE = 0x5A3E1B;
	private static final int COLOR_LOCK = 0x9A1B1B;
	private static final int COLOR_RULE = 0x887A1F1F;

	private final SpellbookId book;
	private final List<BookPage> pages;
	private int page;
	private PageTurnWidget prevButton;
	private PageTurnWidget nextButton;
	private final List<Drawable> widgets = new ArrayList<>();
	private float scale = 1.0f;
	private float originX;
	private float originY;

	/** Registers the opener used by the right click on a spellbook item. */
	public static void register() {
		SpellbookItem.clientOpener = id -> MinecraftClient.getInstance().setScreen(new SpellbookScreen(id));
	}

	public SpellbookScreen(SpellbookId book) {
		super(Text.translatable(SpellbookCatalog.itemKey(book)));
		this.book = book;
		this.pages = SpellbookCatalog.pages(book);
		ZanpakutoData z = ClientState.zanpakuto();
		this.page = BookPager.clamp(SpellbookCatalog.openingPage(book, z.characterId(), z.zanpakutoState()), pages.size());
	}

	@Override
	protected void init() {
		widgets.clear();
		scale = Math.min(1.0f, Math.min(height / (float) VH, width / (float) (VW + 8)));
		originX = (width - VW * scale) / 2.0f;
		originY = Math.max(0.0f, (height - VH * scale) / 2.0f);
		// Same positions as the vanilla book screen.
		prevButton = addDrawableChild(new PageTurnWidget(43, 159, false, b -> turn(-1, false), true));
		nextButton = addDrawableChild(new PageTurnWidget(116, 159, true, b -> turn(1, false), true));
		ButtonWidget done = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
				.dimensions(16, 198, VW - 32, 20).build());
		widgets.add(prevButton);
		widgets.add(nextButton);
		widgets.add(done);
		updateButtons();
	}

	@Override
	public boolean shouldPause() {
		// Keep the game running: the state of the player (and so the dimming of the pages) stays live.
		return false;
	}

	private void turn(int delta, boolean sound) {
		int before = page;
		page = delta < 0 ? BookPager.prev(page, pages.size()) : BookPager.next(page, pages.size());
		if (sound && page != before) {
			playTurnSound();
		}
		updateButtons();
	}

	private void jump(int target) {
		int before = page;
		page = BookPager.clamp(target, pages.size());
		if (page != before) {
			playTurnSound();
		}
		updateButtons();
	}

	private static void playTurnSound() {
		dev.minebleach.reiatsutest.client.fx.FxSound.ui(SoundIds.UI_BOOK_PAGE, "item.book.page_turn", 1.0, 0.25);
	}

	private void updateButtons() {
		if (prevButton != null) {
			prevButton.visible = BookPager.hasPrev(page, pages.size());
			nextButton.visible = BookPager.hasNext(page, pages.size());
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		switch (keyCode) {
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_PAGE_DOWN -> {
				turn(1, true);
				return true;
			}
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_PAGE_UP -> {
				turn(-1, true);
				return true;
			}
			case GLFW.GLFW_KEY_HOME -> {
				jump(0);
				return true;
			}
			case GLFW.GLFW_KEY_END -> {
				jump(pages.size() - 1);
				return true;
			}
			default -> {
			}
		}
		if (client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers); // Esc closes
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		double d = Math.abs(verticalAmount) >= Math.abs(horizontalAmount) ? verticalAmount : horizontalAmount;
		if (d < 0) {
			turn(1, true);
			return true;
		}
		if (d > 0) {
			turn(-1, true);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private double toVirtX(double x) {
		return (x - originX) / scale;
	}

	private double toVirtY(double y) {
		return (y - originY) / scale;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return super.mouseClicked(toVirtX(mouseX), toVirtY(mouseY), button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		return super.mouseReleased(toVirtX(mouseX), toVirtY(mouseY), button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		return super.mouseDragged(toVirtX(mouseX), toVirtY(mouseY), button, deltaX / scale, deltaY / scale);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		context.getMatrices().push();
		context.getMatrices().translate(originX, originY, 0);
		context.getMatrices().scale(scale, scale, 1.0f);
		context.drawTexture(BookScreen.BOOK_TEXTURE, 0, 2, 0, 0, VW, VW);
		drawPage(context);
		int vx = (int) Math.floor(toVirtX(mouseX));
		int vy = (int) Math.floor(toVirtY(mouseY));
		for (Drawable d : widgets) {
			d.render(context, vx, vy, delta);
		}
		context.getMatrices().pop();
	}

	private void drawPage(DrawContext context) {
		TextRenderer font = textRenderer;
		ZanpakutoData z = ClientState.zanpakuto();
		BookPage p = pages.get(page);
		Availability av = SpellbookCatalog.availability(book, page, z.characterId(), z.zanpakutoState());
		boolean muted = av != Availability.AVAILABLE;

		Text indicator = Text.translatable("book.pageIndicator", page + 1, pages.size());
		context.drawText(font, indicator, VW - 44 - font.getWidth(indicator), 18, 0, false);

		Text title = Text.translatable(SpellbookCatalog.titleKey(book, page)).formatted(Formatting.BOLD);
		int y = TITLE_Y;
		for (OrderedText line : font.wrapLines(title, TEXT_W)) {
			context.drawText(font, line, TEXT_X, y, muted ? COLOR_TITLE_MUTED : COLOR_TITLE, false);
			y += font.fontHeight;
		}
		context.fill(TEXT_X, y + 1, TEXT_X + TEXT_W, y + 2, COLOR_RULE);
		y += 4;

		// Notes: where the page works, and why it is muted right now.
		if (p.hasCommands()) {
			y = drawNote(context, worksIn(p), COLOR_NOTE, y);
			if (muted) {
				Text why = av == Availability.WRONG_ZANPAKUTO
						? Text.translatable("book.reiatsu_test.locked.character")
						: Text.translatable("book.reiatsu_test.locked.state",
								Text.translatable(SpellbookCatalog.stateKey(z.zanpakutoState())));
				y = drawNote(context, why, COLOR_LOCK, y);
			}
			y += 2;
		}

		// Body: the largest scale at which it fits above the arrows.
		Text body = Text.translatable(SpellbookCatalog.pageKey(book, page), argsOf(p, muted));
		float s = SCALES[SCALES.length - 1];
		List<OrderedText> lines = null;
		for (float candidate : SCALES) {
			List<OrderedText> l = font.wrapLines(body, Math.round(TEXT_W / candidate));
			s = candidate;
			lines = l;
			if (y + l.size() * font.fontHeight * candidate <= TEXT_BOTTOM) {
				break;
			}
		}
		context.getMatrices().push();
		context.getMatrices().translate(TEXT_X, y, 0);
		context.getMatrices().scale(s, s, 1.0f);
		int ly = 0;
		for (OrderedText line : lines) {
			context.drawText(font, line, 0, ly, muted ? COLOR_TEXT_MUTED : COLOR_TEXT, false);
			ly += font.fontHeight;
		}
		context.getMatrices().pop();
	}

	/** Draws one small wrapped note block and returns the y below it. */
	private int drawNote(DrawContext context, Text note, int color, int y) {
		TextRenderer font = textRenderer;
		List<OrderedText> lines = font.wrapLines(note, Math.round(TEXT_W / NOTE_SCALE));
		context.getMatrices().push();
		context.getMatrices().translate(TEXT_X, y, 0);
		context.getMatrices().scale(NOTE_SCALE, NOTE_SCALE, 1.0f);
		int ly = 0;
		for (OrderedText line : lines) {
			context.drawText(font, line, 0, ly, color, false);
			ly += font.fontHeight;
		}
		context.getMatrices().pop();
		return y + Math.round(ly * NOTE_SCALE);
	}

	private static Text worksIn(BookPage p) {
		if (p.anyState()) {
			return Text.translatable("book.reiatsu_test.works_in", Text.translatable("book.reiatsu_test.any_state"));
		}
		MutableText joined = Text.empty();
		boolean first = true;
		for (ZanpakutoState s : p.orderedStates()) {
			if (!first) {
				joined.append(", ");
			}
			joined.append(Text.translatable(SpellbookCatalog.stateKey(s)));
			first = false;
		}
		return Text.translatable("book.reiatsu_test.works_in", joined);
	}

	private static Object[] argsOf(BookPage p, boolean muted) {
		BalanceConfig balance = BalanceConfig.defaults();
		KidoConfig kido = KidoConfig.defaults();
		Object[] out = new Object[p.args().size()];
		for (int i = 0; i < out.length; i++) {
			BookArg a = p.args().get(i);
			if (a.kind() == BookArg.Kind.KEY) {
				out[i] = Text.keybind(a.ref()).formatted(muted ? Formatting.DARK_GRAY : Formatting.DARK_BLUE);
			} else {
				String s = BookFacts.resolve(a, balance, kido);
				out[i] = s == null ? "?" : s;
			}
		}
		return out;
	}
}
