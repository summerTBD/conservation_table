package dev.hhl19.conservationtable.block;

import dev.hhl19.conservationtable.menu.StoreMenu;
import dev.hhl19.conservationtable.net.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 保存台方块：右键开界面，并在上方飘附魔粒子。
 * 数据挂在玩家身上，所以这里不需要方块实体。
 */
public class ConservationTableBlock extends Block {
	public ConservationTableBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
	                                           Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(new SimpleMenuProvider(
					(syncId, inventory, p) -> new StoreMenu(syncId, inventory),
					Component.translatable("block.conservation_table.conservation_table")));
			ModNetworking.sendSync(serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}

	/** 和附魔台一样走客户端随机 tick，因此不需要方块实体。 */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) != 0) {
			return;
		}
		double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.2;
		double y = pos.getY() + 1.0 + random.nextDouble() * 0.4;
		double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.2;
		level.addParticle(ParticleTypes.ENCHANT, x, y, z, 0.0, -0.12, 0.0);
	}
}
