# AutoTreeChop API (1.7.5)

AutoTreeChop exposes loaded players' enabled preference and daily usage. Integrations can
read an immutable snapshot and change the preference with an explicit result. The API does
not chop trees, load offline records, edit quotas, or report whether a save reached SQL.

## Add the compile dependency

Use Java 17 or newer. Copy the AutoTreeChop 1.7.5 plugin JAR into your integration's `libs/`
folder and add this to its Gradle build (alongside your existing Bukkit/Paper API dependency):

```groovy
dependencies {
    compileOnly files('libs/AutoTreeChop-1.7.5.jar')
}
```

Before the release, use the actual filename of your locally built 1.7.5 alpha JAR instead.
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

`PlayerState` is immutable and detached from later player changes. Daily counters reset
according to the server's local date when read. `isPlayerDataReady(UUID)` is a convenience
check, not a reservation: the player can leave before your next operation. Always handle
the result of that operation.

## Set the preference

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

## Threading and invalid arguments

The UUID-based methods operate on synchronized in-memory data and can be called from any
thread. They perform no SQL or world/entity operations. Calling plugins must still use the
correct Paper/Folia scheduler for any player, inventory, message, or world work of their own.
Use UUID methods in asynchronous code; call the legacy `Player` overloads from that player's
own execution context. Null plugin, UUID, or Player arguments throw `NullPointerException`.

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
