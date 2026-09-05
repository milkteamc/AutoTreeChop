package example;

import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;
import org.milkteamc.autotreechop.AutoTreeChopAPI;

/** Minimal integration; pair with docs/examples/plugin.yml in a separate plugin project. */
public final class AtcIntegrationExample extends JavaPlugin {
    @Override
    public void onEnable() {
        if (getServer().getServicesManager().load(AutoTreeChopAPI.class) == null) {
            getLogger().severe("AutoTreeChop API is unavailable; disabling this integration.");
            getServer().getPluginManager().disablePlugin(this);
        }
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
}
