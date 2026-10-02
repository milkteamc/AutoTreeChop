# AutoTreeChop API

AutoTreeChop exposes loaded players' enabled preference and daily usage, plus online players'
effective group policies and personal settings. Integrations can read immutable snapshots and change preferences
with an explicit result. The API also exposes cancellable pre-chop and result-bearing post-chop events.
It does not start chops, load offline records, edit quotas, or report whether a save reached SQL.

## Add the compile dependency

Use Java 17 or newer. Copy the AutoTreeChop plugin JAR into your integration's `libs/`
folder and add this to its Gradle build (alongside your existing Bukkit/Paper API dependency):

```groovy
dependencies {
    compileOnly files('libs/AutoTreeChop-x.y.z.jar')
}
```

Do not shade or relocate AutoTreeChop into your plugin. Install AutoTreeChop separately on
the server so both plugins use the same API classes. This setup uses a local JAR and does
not require an unpublished Maven artifact or an assumed JitPack release tag.

Declare the runtime dependency in your plugin's `plugin.yml`:

```yaml
depend: [AutoTreeChop]
```

For an optional integration, use `softdepend: [AutoTreeChop]` and only load your ATC-specific
bridge class after verifying that AutoTreeChop is enabled. Keep API type references out of
classes that must load when AutoTreeChop is absent. Folia support must be declared by your
own plugin only after its other code is also compatible.

## Get the API

Look up the service in your plugin's `onEnable()` or later:

```java
AutoTreeChopAPI api = getServer().getServicesManager().load(AutoTreeChopAPI.class);
if (api == null) {
    // AutoTreeChop has not completed enabling, or is disabled/unavailable.
    return;
}
```

The existing `AutoTreeChop.getInstance().getAutoTreeChopAPI()` accessor is retained for
compatibility. Prefer service lookup rather than constructing an API object yourself.
The service is registered at the end of enable and unregistered at the start of disable.
A cached registered API instance becomes unavailable after disable; acquire the new
service after a subsequent enable. Plugin availability does not imply player-data readiness.

## Read state

```java
api.getPlayerState(playerId).ifPresentOrElse(
    state -> {
        boolean enabled = state.enabled();
        int uses = state.dailyUses();
        int blocks = state.dailyBlocksBroken();
        // Update your own UI using these values.
    },
    () -> {
        // Offline, still loading, failed loading, or plugin unavailable.
        // Do not treat this as "disabled with zero usage".
    }
);
```

`PlayerState.enabled()` is the saved preference, not the current activation result.
In `command-and-sneak` mode the player must also hold sneak; in `sneak` mode the current
sneak state controls activation independently of that preference. Sneak-only and combined
modes do not rewrite the preference. `disabled` prevents new chops regardless of the preference.
The legacy `%autotreechop_status%` placeholder also
continues to report the saved preference.

`PlayerState` is immutable and detached from later player changes. Daily counters reset
according to the server's local date when read. `isPlayerDataReady(UUID)` is a convenience
check, not a reservation: the player can leave before your next operation. Always handle
the result of that operation.

## Read the effective group policy

```java
// Paper: main thread. Folia: this player's owning execution context.
api.getPlayerPolicy(player).ifPresent(policy -> {
    String group = policy.group();
    String uses = policy.unlimited() ? "∞" : String.valueOf(policy.maxUsesPerDay());
    String blocks = policy.unlimited() ? "∞" : String.valueOf(policy.maxBlocksPerDay());
    int cooldownSeconds = policy.cooldownSeconds();
    // Display the effective group settings in your UI.
});
```

`PlayerPolicy` is an immutable snapshot using the same group resolution as chopping and
`/atc usage`, including legacy VIP and global unlimited settings. Each call reflects current
permissions and the latest successful configuration reload; earlier snapshots stay unchanged.
When `unlimited()` is true, ignore both quota numbers. Cooldown still applies; its value is
the configured duration, not remaining cooldown time. In Lite mode every policy is unlimited
with no cooldown; the saved group settings take effect again when Lite mode is disabled.

The result is empty when the player is offline or the plugin/config is unavailable. This
query does not require loaded player data; use `getPlayerState(UUID)` separately for usage.
The two queries are separate snapshots. A policy does not guarantee permission to chop or
bypass enabled-state, usage, cooldown, tool, or protection checks.

## Personal settings

```java
PlayerPreferences preferences = new PlayerPreferences(
    PlayerPreferences.Activation.HOTKEY,
    PlayerPreferences.Toggle.OFF,
    PlayerPreferences.Toggle.DEFAULT,
    PlayerPreferences.Toggle.DEFAULT
);
AutoTreeChopAPI.ChangeResult result = api.setPlayerPreferences(playerId, preferences);
api.getPlayerPreferences(playerId).ifPresent(saved -> { /* Saved, immutable preferences. */ });
// Paper: main thread. Folia: this player's owning execution context.
api.getPlayerSettings(player).ifPresent(settings -> { /* Effective values and permissions. */ });
```

`PlayerPreferences` contains activation, sneak messages, leaf removal, and auto replant.
Each field supports `DEFAULT`; use `PlayerPreferences.DEFAULTS` to reset all four.
The `withActivation`, `withSneakMessages`, `withLeafRemoval`, and `withAutoReplant`
methods return modified copies. The setter replaces all four fields, so coordinate writers
when deriving updates from an earlier snapshot. Existing players start with all defaults;
the SQLite/MySQL schema upgrade retains their enabled preference and usage counters.

`getPlayerSettings(Player)` resolves defaults against the current server configuration.
Its activation is never `DEFAULT`. Server mode `disabled` overrides personal modes;
leaf removal and replanting require server enablement and, outside Lite mode, their
feature permissions. Sneak messages use the server value as a default. These settings do not
guarantee that chopping is currently allowed: posture, enabled preference, and tool checks
still apply. Outside Lite mode, use permission, limits, and protection checks also apply.
Both getters return empty when player data is unavailable.

`setPlayerPreferences` is a privileged operation with the same result and persistence
semantics as `setAutoTreeChopEnabled`. Changing activation clears pending confirmations;
it does not reset the enabled preference or counters. Disabling leaves/replant also takes
effect before subsequent leaf batches or delayed replanting.

## Set the enabled preference

```java
switch (api.setAutoTreeChopEnabled(playerId, true)) {
    case UPDATED -> { /* Preference changed; normal saving will persist it. */ }
    case UNCHANGED -> { /* Player already had this preference. */ }
    case UNAVAILABLE -> { /* No change; wait for loading or ask the player to reconnect. */ }
}
```

The setter is a privileged integration operation. Authorize your own users before calling
it. It does not check the caller's command permissions, send chat messages, or bypass the
permissions, protection checks, cooldowns and limits used by actual chopping. Disabling
also clears pending confirmations, including when the preference is already disabled.
It does not cancel an already running chopping job.

A successful result describes the in-memory preference, **not a completed database write**.
Normal periodic/quit/shutdown saving handles persistence. Unavailable mutations are not
queued for a later login and do not create default records. After a load failure, reconnect
once the database is healthy to retry loading; this API does not initiate a reload.

## Chopping events

Listen for `TreeChopPreEvent` and `TreeChopPostEvent` from
`org.milkteamc.autotreechop.api.event`. No service lookup is needed for event registration.

```java
@EventHandler
public void beforeChop(TreeChopPreEvent event) {
    if (event.getPlayer().hasPermission("myplugin.no-auto-chop")) event.setCancelled(true);
}

@EventHandler
public void afterChop(TreeChopPostEvent event) {
    event.getRemovedLogs().forEach((location, originalMaterial) -> {
        // Award progress for this log, rather than for every planned log.
    });
}
```

The pre-event fires after tree discovery, protection/limit checks, and any required
confirmation, but before removal, tool damage, and usage charges. Cancelling it prevents
the batch and no post-event follows. `getPlannedLogs()` lists discovered logs, not a
promise that each will break: later protection checks, block changes, or other plugins may
prevent individual removals. The post-event fires once when the started batch ends, even
if zero logs were removed. `getRemovedLogs()` contains only logs ATC actually removed,
mapped to their material before removal. Leaves and replanted saplings are excluded.

Locations returned by these events are detached copies. On Folia the post-event can run
in a block region different from the player's current region; use the appropriate
scheduler before accessing the player or unrelated world blocks. On Paper, events run
on the server thread. A player can also be offline by the time the post-event fires.

## Threading and invalid arguments

The UUID-based methods operate on synchronized in-memory data and can be called from any
thread. They perform no SQL or world/entity operations. Calling plugins must still use the
correct Paper/Folia scheduler for any player, inventory, message, or world work of their own.
Use UUID methods in asynchronous code; call `getPlayerPolicy(Player)`, `getPlayerSettings(Player)`, and the legacy `Player`
overloads from that player's own execution context (Paper's main thread; the owning player
context on Folia). An available policy/settings query throws `IllegalStateException` when
called from the wrong context, before reading permissions. Null plugin, UUID, Player, or preferences
arguments throw `NullPointerException`.

## Compatibility methods

These existing signatures remain available, with their previous unavailable-data defaults:

| Method | When unavailable |
| --- | --- |
| `isAutoTreeChopEnabled(Player)` | `false` |
| `enableAutoTreeChop(Player)` / `disableAutoTreeChop(Player)` | No-op |
| `getPlayerDailyUses(UUID)` / `getPlayerDailyBlocksBroken(UUID)` | `0` |

Use `getPlayerState(UUID)` and `setAutoTreeChopEnabled(UUID, boolean)` for new integrations
that must distinguish unavailable data from a real disabled/zero state.

## Compiled example and verification

[AtcIntegrationExample.java](examples/AtcIntegrationExample.java) and its
[plugin.yml](examples/plugin.yml) show a minimal external plugin. The example provides
methods for your own command/event handlers; it does not register commands by itself.

`./gradlew build` runs the API tests and compiles this example against the actual shaded
plugin JAR, rather than the project's loose classes. To compile just the example, run
`./gradlew compileApiExample`. This verifies compile-time integration; it is not a live
Paper/Folia server test.
