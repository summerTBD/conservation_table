package dev.hhl19.conservationtable.store;

import dev.hhl19.conservationtable.Conservation_table;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.player.Player;

public final class ModAttachments {
	/** 挂在玩家身上的仓库。copyOnDeath 让仓库在玩家死亡后仍然保留。 */
	public static final AttachmentType<ConservationStore> STORE =
			AttachmentRegistry.<ConservationStore>create(Conservation_table.id("store"), builder -> builder
					.initializer(ConservationStore::new)
					.persistent(ConservationStore.CODEC)
					.copyOnDeath());

	private ModAttachments() {
	}

	/** 需要在世界加载前调用一次，否则存档里的仓库数据不会被反序列化。 */
	public static void init() {
	}

	public static ConservationStore storeOf(Player player) {
		ConservationStore store = player.getAttached(STORE);
		if (store == null) {
			store = new ConservationStore();
			player.setAttached(STORE, store);
		}
		return store;
	}
}
