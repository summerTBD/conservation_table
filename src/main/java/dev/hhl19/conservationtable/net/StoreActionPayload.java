package dev.hhl19.conservationtable.net;

import dev.hhl19.conservationtable.Conservation_table;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * 客户端 → 服务端：取出某种物品。
 * 传的是完整的物品（含组件）而不是条目下标——下标会在两次同步之间失效，
 * 而物品+组件是稳定的身份。
 */
public record StoreActionPayload(ItemStack stack, long amount) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<StoreActionPayload> TYPE =
			new CustomPacketPayload.Type<>(Conservation_table.id("store_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StoreActionPayload> CODEC = StreamCodec.composite(
			ItemStack.STREAM_CODEC, StoreActionPayload::stack,
			ByteBufCodecs.VAR_LONG, StoreActionPayload::amount,
			StoreActionPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
