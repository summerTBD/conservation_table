package dev.hhl19.conservationtable.menu;

import dev.hhl19.conservationtable.net.ModNetworking;
import dev.hhl19.conservationtable.store.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 只有玩家背包是真实槽位；那个大列表是纯渲染的，不占槽位
 * （原版槽位是固定位置，装不下几百种物品）。
 * 因此把物品"存进去"靠的是 shift+左键点击背包物品。
 */
public class StoreMenu extends AbstractContainerMenu {
	public static final int PLAYER_INV_X = 8;
	/** 与 generic_54.png 的槽位布局对齐（6 行箱子的玩家背包位置）。 */
	public static final int PLAYER_INV_Y = 139;
	/** addStandardInventorySlots 把快捷栏放在主背包下方 58 像素处。 */
	public static final int HOTBAR_Y = PLAYER_INV_Y + 58;

	public StoreMenu(int syncId, Inventory inventory) {
		super(ModMenus.STORE, syncId);
		addStandardInventorySlots(inventory, PLAYER_INV_X, PLAYER_INV_Y);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return ItemStack.EMPTY;
		}
		Slot slot = slots.get(index);
		ItemStack stack = slot.getItem();
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		int amount = stack.getCount();
		ModAttachments.storeOf(serverPlayer).deposit(stack, amount);
		slot.set(ItemStack.EMPTY);
		slot.setChanged();
		ModNetworking.sendSync(serverPlayer);
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}
}
