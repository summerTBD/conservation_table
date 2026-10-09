package dev.hhl19.conservationtable.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hhl19.conservationtable.client.ClientStoreCache;
import dev.hhl19.conservationtable.menu.StoreMenu;
import dev.hhl19.conservationtable.net.StoreActionPayload;
import dev.hhl19.conservationtable.net.StoreSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 保存台界面：上方是可翻页的物品网格，下方是玩家背包。
 * 背景直接复用原版箱子贴图，网格本身是纯渲染的，不占原版槽位。
 */
public class StoreScreen extends AbstractContainerScreen<StoreMenu> {
	private static final int COLS = 9;
	private static final int ROWS = 5;
	private static final int CELL = 18;
	private static final int PER_PAGE = COLS * ROWS;
	private static final int GRID_X = 8;
	private static final int GRID_Y = 18;
	private static final int CONTROLS_Y = 108;
	private static final int CONTROLS_H = 18;
	private static final int BTN_W = 20;
	private static final int BTN_H = 14;
	private static final int BTN_MARGIN = 8;

	/** 原版箱子贴图，正好是 176x222，槽位布局与本界面一致。 */
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

	private static final int PANEL = 0xFFC6C6C6;
	private static final int PANEL_DARK = 0xFF555555;
	private static final int TEXT = 0xFF404040;
	private static final int TEXT_DIM = 0xFF707070;
	private static final int BTN_OFF = 0xFF9E9E9E;

	private int page;

	public StoreScreen(StoreMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 222);
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = StoreMenu.PLAYER_INV_X;
		this.inventoryLabelY = 128;
	}

	private List<StoreSyncPayload.Entry> entries() {
		return ClientStoreCache.get();
	}

	private int pageCount() {
		return Math.max(1, (entries().size() + PER_PAGE - 1) / PER_PAGE);
	}

	/** 物品被取空后总页数会变小，当前页需要跟着收回来，否则会停在空白页。 */
	private void clampPage() {
		page = Math.max(0, Math.min(page, pageCount() - 1));
	}

	private int prevButtonX() {
		return leftPos + BTN_MARGIN;
	}

	private int nextButtonX() {
		return leftPos + imageWidth - BTN_MARGIN - BTN_W;
	}

	private int buttonY() {
		return topPos + CONTROLS_Y + (CONTROLS_H - BTN_H) / 2;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
		// 直接贴原版箱子贴图：面板、网格槽位、玩家背包槽位都是现成的，不用自己描。
		extractor.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0f, 0f,
				imageWidth, imageHeight, 256, 256);

		// 贴图第 6 行槽位让给翻页控件，先盖掉。
		extractor.fill(leftPos + 1, topPos + CONTROLS_Y, leftPos + imageWidth - 1,
				topPos + CONTROLS_Y + CONTROLS_H, PANEL);
	}

	@Override
	public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
		super.extractContents(extractor, mouseX, mouseY, partialTick);

		Font font = this.font;
		List<StoreSyncPayload.Entry> list = entries();
		clampPage();

		int start = page * PER_PAGE;
		for (int i = 0; i < PER_PAGE; i++) {
			int index = start + i;
			if (index >= list.size()) {
				break;
			}
			StoreSyncPayload.Entry entry = list.get(index);
			ItemStack stack = entry.stack();
			int cx = leftPos + GRID_X + (i % COLS) * CELL;
			int cy = topPos + GRID_Y + (i / COLS) * CELL;
			extractor.item(stack, cx + 1, cy + 1);
		}

		if (list.isEmpty()) {
			extractor.centeredText(font, "仓库是空的", leftPos + imageWidth / 2, topPos + 56, TEXT_DIM);
		}

		String total = "共 " + list.size() + " 种";
		extractor.text(font, total, leftPos + imageWidth - 8 - font.width(total), topPos + 7, TEXT_DIM);

		drawButton(extractor, font, prevButtonX(), buttonY(), "<<", page > 0);
		drawButton(extractor, font, nextButtonX(), buttonY(), ">>", page < pageCount() - 1);
		extractor.centeredText(font, (page + 1) + " / " + pageCount(),
				leftPos + imageWidth / 2, topPos + CONTROLS_Y + 5, TEXT);
	}

	private void drawButton(GuiGraphicsExtractor extractor, Font font, int x, int y, String label, boolean enabled) {
		extractor.fill(x, y, x + BTN_W, y + BTN_H, PANEL_DARK);
		extractor.fill(x + 1, y + 1, x + BTN_W - 1, y + BTN_H - 1, enabled ? PANEL : BTN_OFF);
		extractor.centeredText(font, label, x + BTN_W / 2, y + 4, enabled ? TEXT : TEXT_DIM);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
		super.extractTooltip(extractor, mouseX, mouseY);
		int index = hitIndex(mouseX, mouseY);
		if (index < 0) {
			return;
		}
		StoreSyncPayload.Entry entry = entries().get(index);
		List<Component> lines = new ArrayList<>(
				Screen.getTooltipFromItem(Minecraft.getInstance(), entry.stack()));
		// 存量是格子里唯一看不到的信息，插在物品名下面并高亮，别让它被附魔列表淹没。
		lines.add(Math.min(1, lines.size()),
				Component.literal("存量: " + entry.count()).withStyle(ChatFormatting.AQUA));
		extractor.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
	}

	private boolean inButton(double mouseX, double mouseY, int buttonX) {
		return mouseX >= buttonX && mouseX < buttonX + BTN_W
				&& mouseY >= buttonY() && mouseY < buttonY() + BTN_H;
	}

	private int hitIndex(double mouseX, double mouseY) {
		int relX = (int) mouseX - leftPos - GRID_X;
		int relY = (int) mouseY - topPos - GRID_Y;
		if (relX < 0 || relY < 0) {
			return -1;
		}
		int col = relX / CELL;
		int row = relY / CELL;
		if (col >= COLS || row >= ROWS) {
			return -1;
		}
		int index = page * PER_PAGE + row * COLS + col;
		return index < entries().size() ? index : -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int button = event.button();
		// 26.3 改用 SDL 输入，按钮编号是 1=左、2=中、3=右（不再是 GLFW 的 0/1/2），
		// 所以必须用 InputConstants 的常量，不能写魔法数字。
		if (button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT) {
			double mouseX = event.x();
			double mouseY = event.y();
			if (inButton(mouseX, mouseY, prevButtonX())) {
				if (page > 0) {
					page--;
				}
				return true;
			}
			if (inButton(mouseX, mouseY, nextButtonX())) {
				if (page < pageCount() - 1) {
					page++;
				}
				return true;
			}
			int index = hitIndex(mouseX, mouseY);
			if (index >= 0) {
				// 左键取一组、按住 shift 取全部；右键取 1 个。数量最终由服务端钳制。
				long amount;
				if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
					amount = 1L;
				} else {
					amount = event.hasShiftDown() ? Long.MAX_VALUE : 64L;
				}
				ClientPlayNetworking.send(new StoreActionPayload(entries().get(index).stack(), amount));
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}
}
