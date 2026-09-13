package net.mcreator.mcanomalyarchives.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import net.mcreator.mcanomalyarchives.config.ComfortConfig;

/**
 * 客户端专属：把舒适度配置挂到 NeoForge 自动生成的设置界面上
 * （游戏内：模组列表 → MC诡异见闻录 → Config）。
 *
 * 单独一个类是为了把 {@link ConfigurationScreen}（客户端专属类）挡在 dist 判断之后，
 * 避免在专用服务器上加载到它。
 */
public final class ComfortConfigScreen {

    private ComfortConfigScreen() {
    }

    public static void register(ModContainer container) {
        // 注意：ConfigurationScreen 只有 (ModContainer, Screen) 这个构造器，
        // 没有收 IConfigSpec 的三参版本（NeoForge 21.1）—— 它会列出该模组的所有配置。
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) -> new ConfigurationScreen(modContainer, parent));
    }
}
