package dev.hhl19.conservationtable.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.hhl19.conservationtable.menu.StoreMenu;
import dev.hhl19.conservationtable.net.ModNetworking;
import dev.hhl19.conservationtable.store.ConservationStore;
import dev.hhl19.conservationtable.store.ModAttachments;
import dev.hhl19.conservationtable.store.StoreOps;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** GUI 完成前的调试入口，用来验证数据层能存能取。 */
public final class ConservationCommand {
	private static final long DEFAULT_WITHDRAW = 64L;

	private ConservationCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext buildContext,
			Commands.CommandSelection selection) {
		dispatcher.register(Commands.literal("conserve")
				.executes(ConservationCommand::usage)
				.then(Commands.literal("open").executes(ConservationCommand::open))
				.then(Commands.literal("list").executes(ConservationCommand::list))
				.then(Commands.literal("sort").executes(ConservationCommand::sort))
				.then(Commands.literal("deposit").executes(ConservationCommand::deposit))
				.then(Commands.literal("withdraw")
						.then(Commands.argument("item", ItemArgument.item(buildContext))
								.executes(ctx -> withdraw(ctx, DEFAULT_WITHDRAW))
								.then(Commands.argument("count", LongArgumentType.longArg(1L))
										.executes(ctx -> withdraw(ctx, LongArgumentType.getLong(ctx, "count")))))));
	}

	private static int usage(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSuccess(() -> Component.literal(
				"用法: /conserve open | /conserve list | /conserve sort | /conserve deposit | /conserve withdraw <物品> [数量]"), false);
		return 1;
	}

	private static int open(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			return 0;
		}
		player.openMenu(new SimpleMenuProvider(
				(syncId, inventory, p) -> new StoreMenu(syncId, inventory),
				Component.literal("保存台")));
		ModNetworking.sendSync(player);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			return 0;
		}
		ConservationStore store = ModAttachments.storeOf(player);
		if (store.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("仓库是空的"), false);
			return 0;
		}
		List<ConservationStore.Entry> entries = store.view();
		ctx.getSource().sendSuccess(() -> Component.literal("仓库内容（" + entries.size() + " 种）:"), false);
		for (int i = 0; i < entries.size(); i++) {
			ConservationStore.Entry entry = entries.get(i);
			Component line = describe(i + 1, entry);
			ctx.getSource().sendSuccess(() -> line, false);
			String patch = patchArgument(ctx.getSource(), entry.template().getComponentsPatch());
			if (patch != null) {
				Component patchLine = Component.literal("      " + patch).withStyle(ChatFormatting.DARK_GRAY);
				ctx.getSource().sendSuccess(() -> patchLine, false);
			}
		}
		return entries.size();
	}

	/** 给物品排序,达到相同种类的放在一起可以很好提升视觉体验 */
	private static int sort(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			return 0;
		}
		ConservationStore store = ModAttachments.storeOf(player);
		if (store.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("仓库是空的"), false);
			return 0;
		}
		store.sort(ConservationStore.BY_ITEM);
		ctx.getSource().sendSuccess(() -> Component.literal("已按物品种类排序"), true);
		ModNetworking.sendSync(player);
		return store.view().size();
	}

	/** 同类物品可能只靠附魔、命名等组件区分，所以把物品 id 和组件都显示出来。 */
	private static Component describe(int index, ConservationStore.Entry entry) {
		ItemStack template = entry.template();
		return Component.literal("  " + index + ". ")
				.append(Component.literal(shortId(template)).withStyle(ChatFormatting.GRAY))
				.append(" ×" + entry.count())
				.append("  ")
				.append(template.getHoverName());
	}

	/** vanilla 物品省掉 "minecraft:" 前缀，让整行能塞进聊天栏；mod 物品保留完整 id。 */
	private static String shortId(ItemStack template) {
		Identifier id = BuiltInRegistries.ITEM.getKey(template.getItem());
		return id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? id.getPath() : id.toString();
	}

	/** 组件的命令写法，可直接粘到 withdraw 的物品 id 后面；没有组件时返回 null。 */
	private static String patchArgument(CommandSourceStack source, DataComponentPatch patch) {
		if (patch.isEmpty()) {
			return null;
		}
		RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, source.registryAccess());
		if (!(DataComponentPatch.CODEC.encodeStart(ops, patch).result().orElse(null) instanceof CompoundTag compound)) {
			return null;
		}
		StringBuilder text = new StringBuilder("[");
		for (String key : compound.keySet()) {
			if (text.length() > 1) {
				text.append(',');
			}
			text.append(key).append('=').append(compound.get(key));
		}
		return text.append(']').toString();
	}

	private static int deposit(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			return 0;
		}
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("主手没有物品可存入"));
			return 0;
		}
		int amount = held.getCount();
		ModAttachments.storeOf(player).deposit(held, amount);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		ctx.getSource().sendSuccess(() -> Component.literal("已存入 ")
				.append(held.getHoverName())
				.append(Component.literal(" × " + amount)), true);
		return amount;
	}

	private static int withdraw(CommandContext<CommandSourceStack> ctx, long amount) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			return 0;
		}
		ItemStack probe;
		try {
			probe = ItemArgument.getItem(ctx, "item").createItemStack(1);
		} catch (CommandSyntaxException e) {
			ctx.getSource().sendFailure(Component.literal("无法解析该物品: " + e.getMessage()));
			return 0;
		}
		long taken = StoreOps.withdrawToInventory(player, probe, amount);
		if (taken <= 0) {
			ctx.getSource().sendFailure(Component.literal("仓库里没有这个物品"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("已取出 ")
				.append(probe.getHoverName())
				.append(Component.literal(" × " + taken)), true);
		return (int) Math.min(taken, Integer.MAX_VALUE);
	}
}
