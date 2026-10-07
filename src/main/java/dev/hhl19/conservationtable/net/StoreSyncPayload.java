package dev.hhl19.conservationtable.net;

import dev.hhl19.conservationtable.Conservation_table;
import dev.hhl19.conservationtable.store.ConservationStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 服务端 → 客户端：把仓库快照发给玩家本人。 */
public record StoreSyncPayload(List<Entry> entries) implements CustomPacketPayload {
	/** stack 的数量恒为 1，实际数量记在 count。 */
	public record Entry(ItemStack stack, long count) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
				ItemStack.STREAM_CODEC, Entry::stack,
				ByteBufCodecs.VAR_LONG, Entry::count,
				Entry::new);
	}

	// 注意：createType(String) 内部用的是 withDefaultNamespace，会把 "命名空间:路径" 整个当成路径。
	public static final CustomPacketPayload.Type<StoreSyncPayload> TYPE =
			new CustomPacketPayload.Type<>(Conservation_table.id("store_sync"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StoreSyncPayload> CODEC = StreamCodec.composite(
			Entry.CODEC.apply(ByteBufCodecs.list()), StoreSyncPayload::entries,
			StoreSyncPayload::new);

	public static StoreSyncPayload of(ConservationStore store) {
		List<Entry> entries = new ArrayList<>(store.view().size());
		for (ConservationStore.Entry entry : store.view()) {
			entries.add(new Entry(entry.template(), entry.count()));
		}
		return new StoreSyncPayload(List.copyOf(entries));
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
