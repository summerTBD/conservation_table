package dev.hhl19.conservationtable.client;

import dev.hhl19.conservationtable.client.screen.StoreScreen;
import dev.hhl19.conservationtable.menu.ModMenus;
import dev.hhl19.conservationtable.net.StoreSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

public class Conservation_tableClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.STORE, StoreScreen::new);

		// 快照到达时先切回客户端线程，再刷新缓存。
		ClientPlayNetworking.registerGlobalReceiver(StoreSyncPayload.TYPE,
				(payload, context) -> context.client().execute(() -> ClientStoreCache.set(payload.entries())));
	}
}