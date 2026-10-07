package dev.hhl19.conservationtable;

import dev.hhl19.conservationtable.block.ModBlocks;
import dev.hhl19.conservationtable.command.ConservationCommand;
import dev.hhl19.conservationtable.menu.ModMenus;
import dev.hhl19.conservationtable.net.ModNetworking;
import dev.hhl19.conservationtable.store.ModAttachments;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Conservation_table implements ModInitializer {
	public static final String MOD_ID = "conservation_table";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 附着类型必须在世界/玩家数据加载前注册完毕
		ModAttachments.init();
		ModBlocks.init();
		ModMenus.init();
		ModNetworking.registerPayloads();
		ModNetworking.registerServerHandlers();
		CommandRegistrationCallback.EVENT.register(ConservationCommand::register);

		LOGGER.info("conservation_table initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
