package example;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.milkteamc.autotreechop.AutoTreeChopAPI;
import org.milkteamc.autotreechop.PlayerPreferences;
import org.milkteamc.autotreechop.api.event.TreeChopPostEvent;
import org.milkteamc.autotreechop.api.event.TreeChopPreEvent;

/** Minimal integration; pair with docs/examples/plugin.yml in a separate plugin project. */
public final class AtcIntegrationExample extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        if (getServer().getServicesManager().load(AutoTreeChopAPI.class) == null) {
            getLogger().severe("AutoTreeChop API is unavailable; disabling this integration.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onTreeChopPre(TreeChopPreEvent event) {
        if (getConfig().getBoolean("disable-atc-chops", false)) event.setCancelled(true);
    }

    @EventHandler
    public void onTreeChopPost(TreeChopPostEvent event) {
        int choppedLogs = event.getRemovedLogs().size();
        if (choppedLogs > 0) getLogger().info(event.getPlayer().getName() + " chopped " + choppedLogs + " logs");
    }

    /** Call this after authorizing the initiating command or action. */
    public AutoTreeChopAPI.ChangeResult setEnabled(UUID playerId, boolean enabled) {
        AutoTreeChopAPI api = getServer().getServicesManager().load(AutoTreeChopAPI.class);
        if (api == null) return AutoTreeChopAPI.ChangeResult.UNAVAILABLE;
        return api.setAutoTreeChopEnabled(playerId, enabled);
    }

    public String describe(UUID playerId) {
        AutoTreeChopAPI api = getServer().getServicesManager().load(AutoTreeChopAPI.class);
        if (api == null) return "AutoTreeChop is unavailable";
        return api.getPlayerState(playerId)
                .map(state -> "Enabled: " + state.enabled() + ", uses: " + state.dailyUses()
                        + ", blocks: " + state.dailyBlocksBroken())
                .orElse("Player data is not ready; wait or reconnect after a load failure");
    }

    /** Call on Paper's main thread or in this player's owning execution context on Folia. */
    public String describePolicy(Player player) {
        AutoTreeChopAPI api = getServer().getServicesManager().load(AutoTreeChopAPI.class);
        if (api == null) return "AutoTreeChop is unavailable";
        return api.getPlayerPolicy(player)
                .map(policy -> "Group: " + policy.group()
                        + ", uses/day: " + (policy.unlimited() ? "unlimited" : policy.maxUsesPerDay())
                        + ", blocks/day: " + (policy.unlimited() ? "unlimited" : policy.maxBlocksPerDay())
                        + ", cooldown: " + policy.cooldownSeconds() + "s")
                .orElse("Player is offline or AutoTreeChop is unavailable");
    }
    /** Call this after authorizing the initiating command or action. */
    public AutoTreeChopAPI.ChangeResult setPreferences(UUID playerId, PlayerPreferences preferences) {
        AutoTreeChopAPI api = getServer().getServicesManager().load(AutoTreeChopAPI.class);
        if (api == null) return AutoTreeChopAPI.ChangeResult.UNAVAILABLE;
        return api.setPlayerPreferences(playerId, preferences);
    }

    /** Call on Paper's main thread or in this player's owning execution context on Folia. */
    public String describeSettings(Player player) {
        AutoTreeChopAPI api = getServer().getServicesManager().load(AutoTreeChopAPI.class);
        if (api == null) return "AutoTreeChop is unavailable";
        return api.getPlayerSettings(player)
                .map(settings -> "Activation: " + settings.activation().value()
                        + ", sneak messages: " + settings.sneakMessages()
                        + ", leaves: " + settings.leafRemoval()
                        + ", replant: " + settings.autoReplant())
                .orElse("Player data is unavailable");
    }
}
