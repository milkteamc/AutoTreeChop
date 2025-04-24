// TabCompleter Class
package org.milkteamc.autotreechop.spigot.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TabCompleter implements org.bukkit.command.TabCompleter {

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!cmd.getName().equalsIgnoreCase("autotreechop") && !cmd.getName().equalsIgnoreCase("atc")) {
            return null;
        }
        if (args.length != 1) {
            return null;
        }
        List<String> completions = new ArrayList<>();
        completions.add("usage");

        boolean hasOtherPermission = sender.hasPermission("autotreechop.other") || sender.hasPermission("autotreechop.op");
        boolean hasOpPermission = sender.hasPermission("autotreechop.op");

        if (hasOtherPermission) {
            completions.add("enable-all");
            completions.add("disable-all");
            Bukkit.getOnlinePlayers().stream()
                    .limit(10) // Limit to 10 players
                    .forEach(player -> completions.add(player.getName()));
        }
        if (hasOpPermission) {
            completions.add("reload");
        }
        return completions;
    }
}