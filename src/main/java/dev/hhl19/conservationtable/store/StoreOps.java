package dev.hhl19.conservationtable.store;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;

/** 存取的落地动作：把结果真正放进玩家背包。命令和 GUI 都走这里，保证行为一致。 */
public final class StoreOps {
	private StoreOps() {
	}

	/** 把一种物品取出至多 amount 个放进背包；背包放不下的会掉在地上，不会凭空消失。 */
	public static long withdrawToInventory(ServerPlayer player, ItemStack probe, long amount) {
		long taken = ModAttachments.storeOf(player).withdraw(probe, amount);
		if (taken <= 0) {
			return 0L;
		}
		int maxStack = probe.getMaxStackSize();
		long remaining = taken;
		while (remaining > 0) {
			int batch = (int) Math.min(remaining, maxStack);
			ItemStack stack = probe.copy();
			stack.setCount(batch);
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false, Prediction.SERVER_ONLY);
			}
			remaining -= batch;
		}
		return taken;
	}
}
