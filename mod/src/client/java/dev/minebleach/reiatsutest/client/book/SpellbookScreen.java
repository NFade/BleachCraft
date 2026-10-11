package dev.minebleach.reiatsutest.client.book;

import dev.minebleach.reiatsutest.client.ClientState;
import dev.minebleach.reiatsutest.core.book.BookArg;
import dev.minebleach.reiatsutest.core.book.BookFacts;
import dev.minebleach.reiatsutest.core.book.BookPage;
import dev.minebleach.reiatsutest.core.book.BookPager;
import dev.minebleach.reiatsutest.core.book.SpellbookCatalog;
import dev.minebleach.reiatsutest.core.book.SpellbookId;
import dev.minebleach.reiatsutest.core.kido.KidoConfig;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import dev.minebleach.reiatsutest.registry.SpellbookItem;
import dev.minebleach.reiatsutest.registry.data.ZanpakutoData;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/**
 * Read-only spellbook screen (B3). Uses the vanilla book texture (book.png) and plain buttons, no atlas of our own. The
 * text of every page comes from the lang files ({@code book.reiatsu_test.<id>.page.N}); numbers and key names are filled in
 * from {@link BookFacts} and the player's current key bindings. UNVERIFIED in game.
 */
public final class SpellbookScreen extends Screen {
	private static final int BOOK_W = 192;
	private static final int TEXT_X = 36;
	private static final int TEXT_W = 114;
	private static final int TEXT_Y = 30;
	/** Lines that fit between the top of the text and the page buttons at the normal size. */
	private static final int MAX_LINES = 14;
	private static final float SMALL_SCALE = 0.75f;

	private final SpellbookId book;
	private final List<BookPage> pages;
	private int page;
	private ButtonWidget prevButton;
	private ButtonWidget nextButton;

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
		int left = (width - BOOK_W) / 2;
		prevButton = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> turn(-1))
				.dimensions(left + 34, 168, 24, 14).build());
		nextButton = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> turn(1))
				.dimensions(left + 134, 168, 24, 14).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
				.dimensions(width / 2 - 100, 196, 200, 20).build());
		updateButtons();
	}

	private void turn(int delta) {
		page = delta < 0 ? BookPager.prev(page, pages.size()) : BookPager.next(page, pages.size());
		updateButtons();
	}

	private void updateButtons() {
		if (prevButton != null) {
			prevButton.active = BookPager.hasPrev(page, pages.size());
			nextButton.active = BookPager.hasNext(page, pages.size());
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
			turn(1);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
			turn(-1);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		context.drawTexture(BookScreen.BOOK_TEXTURE, (width - BOOK_W) / 2, 2, 0, 0, BOOK_W, BOOK_W);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		int left = (width - BOOK_W) / 2;
		TextRenderer font = textRenderer;
		Text indicator = Text.translatable("book.pageIndicator", page + 1, pages.size());
		context.drawText(font, indicator, left + BOOK_W - 44 - font.getWidth(indicator), 18, 0, false);

		Text title = Text.translatable(SpellbookCatalog.titleKey(book, page)).formatted(Formatting.BOLD);
		Text body = Text.translatable(SpellbookCatalog.pageKey(book, page), argsOf(pages.get(page)));
		List<OrderedText> titleLines = font.wrapLines(title, TEXT_W);
		int y = TEXT_Y;
		for (OrderedText line : titleLines) {
			context.drawText(font, line, left + TEXT_X, y, 0x7A1F1F, false);
			y += font.fontHeight;
		}
		y += 3;
		// Body: normal size when it fits, else 75 percent (re-wrapped to the wider line).
		List<OrderedText> lines = font.wrapLines(body, TEXT_W);
		float scale = 1.0f;
		if (lines.size() + titleLines.size() > MAX_LINES) {
			scale = SMALL_SCALE;
			lines = font.wrapLines(body, Math.round(TEXT_W / scale));
		}
		context.getMatrices().push();
		context.getMatrices().translate(left + TEXT_X, y, 0);
		context.getMatrices().scale(scale, scale, 1.0f);
		int ly = 0;
		for (OrderedText line : lines) {
			context.drawText(font, line, 0, ly, 0, false);
			ly += font.fontHeight;
		}
		context.getMatrices().pop();
	}

	private static Object[] argsOf(BookPage p) {
		BalanceConfig balance = BalanceConfig.defaults();
		KidoConfig kido = KidoConfig.defaults();
		Object[] out = new Object[p.args().size()];
		for (int i = 0; i < out.length; i++) {
			BookArg a = p.args().get(i);
			if (a.kind() == BookArg.Kind.KEY) {
				out[i] = Text.keybind(a.ref()).formatted(Formatting.DARK_BLUE);
			} else {
				String s = BookFacts.resolve(a, balance, kido);
				out[i] = s == null ? "?" : s;
			}
		}
		return out;
	}
}
