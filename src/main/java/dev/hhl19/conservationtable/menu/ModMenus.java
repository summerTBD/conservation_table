package dev.hhl19.conservationtable.menu;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
	public static final MenuType<StoreMenu> STORE = net.minecraft.core.Registry.register(
			net.minecraft.core.registries.BuiltInRegistries.MENU,
			dev.hhl19.conservationtable.Conservation_table.id("store"),
			new MenuType<>(StoreMenu::new, net.minecraft.world.flag.FeatureFlags.VANILLA_SET));

	private ModMenus() {
	}

	public static void init() {
	}
}
