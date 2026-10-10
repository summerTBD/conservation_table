package dev.hhl19.conservationtable.net;

import dev.hhl19.conservationtable.store.ConservationStore;
import dev.hhl19.conservationtable.store.ModAttachments;
import dev.hhl19.conservationtable.store.StoreOps;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** 网络包注册与发送。数据只在服务端改，客户端发请求、收快照。 */
public final class ModNetworking {
	/** 仓库可能有很多种物品，所以放宽上限。 */
	private static final int SYNC_PAYLOAD_LIMIT = 1024 * 1024;

	private ModNetworking() {
	}

	public static void registerPayloads() {
		PayloadTypeRegistry.clientboundPlay()
				.registerLarge(StoreSyncPayload.TYPE, StoreSyncPayload.CODEC, SYNC_PAYLOAD_LIMIT);
		PayloadTypeRegistry.serverboundPlay().register(StoreActionPayload.TYPE, StoreActionPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SortStorePayload.TYPE, SortStorePayload.CODEC);
	}

	public static void registerServerHandlers() {
		ServerPlayNetworking.registerGlobalReceiver(StoreActionPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			// 服务端权威：数量由服务端钳制，不信任客户端传来的值。
			long taken = StoreOps.withdrawToInventory(player, payload.stack(), payload.amount());
			if (taken > 0) {
				sendSync(player);
			}
		});

		// 排序同样由服务端执行：顺序既是存档顺序，也是下次同步的顺序。
		ServerPlayNetworking.registerGlobalReceiver(SortStorePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			ConservationStore store = ModAttachments.storeOf(player);
			if (!store.isEmpty()) {
				store.sort(ConservationStore.BY_ITEM);
				sendSync(player);
			}
		});
	}

	public static void sendSync(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, StoreSyncPayload.TYPE)) {
			ServerPlayNetworking.send(player, StoreSyncPayload.of(ModAttachments.storeOf(player)));
		}
	}
}
