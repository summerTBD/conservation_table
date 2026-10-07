package dev.hhl19.conservationtable.client;

import dev.hhl19.conservationtable.net.StoreSyncPayload;

import java.util.List;

/** 服务端发来的仓库快照。界面只读这里，绝不自己改数据。 */
public final class ClientStoreCache {
	private static List<StoreSyncPayload.Entry> entries = List.of();

	private ClientStoreCache() {
	}

	public static List<StoreSyncPayload.Entry> get() {
		return entries;
	}

	public static void set(List<StoreSyncPayload.Entry> newEntries) {
		entries = newEntries;
	}
}
