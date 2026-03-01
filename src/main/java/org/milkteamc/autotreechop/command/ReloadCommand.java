package org.milkteamc.autotreechop.command;

import org.milkteamc.autotreechop.AutoTreeChop;
import org.milkteamc.autotreechop.Config;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;

@Command({"atc", "autotreechop"})
public class ReloadCommand {

    private final AutoTreeChop plugin;
    private final Config config;

    public ReloadCommand(AutoTreeChop plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Subcommand("reload")
    @CommandPermission("autotreechop.reload")
    public void reload(BukkitCommandActor actor) {

        config.load();

        plugin.getTranslationManager()
                .reload(
                        config.getLocale() == null ? java.util.Locale.getDefault() : config.getLocale(),
                        config.isUseClientLocale());

        actor.sender().sendMessage("Config reloaded successfully.");
        actor.sender().sendMessage("Some features might need a fully restart to change properly!");
    }
}
