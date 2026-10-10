package dev.hhl19.conservationtable.net;

import dev.hhl19.conservationtable.Conservation_table;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：请求重排仓库。
 * 不带参数——排序规则是服务端的事，客户端只说"帮我排一下"。
 */
public record SortStorePayload() implements CustomPacketPayload {
  public static final CustomPacketPayload.Type<SortStorePayload> TYPE = new CustomPacketPayload.Type<>(
      Conservation_table.id("sort_store"));

  /** 空包，没有字段可编解码。 */
  public static final StreamCodec<RegistryFriendlyByteBuf, SortStorePayload> CODEC = StreamCodec
      .unit(new SortStorePayload());

  @Override
  public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
