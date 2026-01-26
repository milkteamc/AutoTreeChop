package org.milkteamc.autotreechop;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.google.gson.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Stream;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModrinthUpdateChecker implements Listener {

    private static final String API_URL = "https://api.modrinth.com/v2/project/{id}/version";

    private final JavaPlugin plugin;
    private final String projectId;
    private final String currentVersion;
    private final String loader;

    @Nullable
    private String minecraftVersion;

    @Nullable
    private String latestVersion;

    @Nullable
    private String downloadLink;

    @Nullable
    private String changelogLink;

    @Nullable
    private String donationLink;

    @Nullable
    private String supportLink;

    private boolean notifyOps = false;
    private String notifyPermission = null;
    private int checkIntervalHours = 24;
    private long lastCheckTime = 0;
    private UpdateCheckResult lastResult = UpdateCheckResult.UNKNOWN;
    private boolean suppressUpToDateMessage = false;
    private boolean coloredConsole = true;

    public enum UpdateCheckResult {
        RUNNING_LATEST_VERSION,
        NEW_VERSION_AVAILABLE,
        UNKNOWN
    }

    /**
     * @param plugin    the plugin instance
     * @param projectId the Modrinth project ID (slug or ID)
     * @param loader    the mod loader (e.g., "bukkit", "spigot", "paper")
     */
    public ModrinthUpdateChecker(@NotNull JavaPlugin plugin, @NotNull String projectId, @NotNull String loader) {
        this.plugin = plugin;
        this.projectId = projectId;
        this.currentVersion = plugin.getDescription().getVersion();
        this.loader = loader;
        this.minecraftVersion = plugin.getServer().getBukkitVersion().split("-")[0];
    }

    /**
     * Set the Minecraft version filter (null for any version)
     */
    public ModrinthUpdateChecker setMinecraftVersion(@Nullable String version) {
        this.minecraftVersion = version;
        return this;
    }

    /**
     * Set the download link
     */
    public ModrinthUpdateChecker setDownloadLink(@NotNull String link) {
        this.downloadLink = link;
        return this;
    }

    /**
     * Set the changelog link
     */
    public ModrinthUpdateChecker setChangelogLink(@NotNull String link) {
        this.changelogLink = link;
        return this;
    }

    /**
     * Set the donation link
     */
    public ModrinthUpdateChecker setDonationLink(@NotNull String link) {
        this.donationLink = link;
        return this;
    }

    /**
     * Set the support link
     */
    public ModrinthUpdateChecker setSupportLink(@NotNull String link) {
        this.supportLink = link;
        return this;
    }

    /**
     * Notify ops when they join
     */
    public ModrinthUpdateChecker setNotifyOpsOnJoin(boolean notify) {
        this.notifyOps = notify;
        return this;
    }

    /**
     * Notify players with permission when they join
     */
    public ModrinthUpdateChecker setNotifyByPermissionOnJoin(@NotNull String permission) {
        this.notifyPermission = permission;
        return this;
    }

    /**
     * Set the check interval in hours
     */
    public ModrinthUpdateChecker checkEveryXHours(int hours) {
        this.checkIntervalHours = hours;
        return this;
    }

    /**
     * Suppress the "up to date" message in console
     */
    public ModrinthUpdateChecker setSuppressUpToDateMessage(boolean suppress) {
        this.suppressUpToDateMessage = suppress;
        return this;
    }

    /**
     * Enable/disable colored console output
     */
    public ModrinthUpdateChecker setColoredConsoleOutput(boolean colored) {
        this.coloredConsole = colored;
        return this;
    }

    /**
     * Check for updates now
     */
    public ModrinthUpdateChecker checkNow() {
        if (notifyOps || notifyPermission != null) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        }

        performCheck();
        return this;
    }

    /**
     * Start periodic checks
     */
    public ModrinthUpdateChecker startPeriodicCheck() {
        checkNow();

        long intervalTicks = checkIntervalHours * 60 * 60 * 20L; // hours to ticks
        plugin.getServer()
                .getScheduler()
                .runTaskTimerAsynchronously(plugin, this::performCheck, intervalTicks, intervalTicks);

        return this;
    }

    private void performCheck() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL.replace("{id}", projectId)))
                    .header("User-Agent", "minecraft-plugin/" + plugin.getName() + "/" + currentVersion)
                    .GET()
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAcceptAsync(response -> {
                        if (response.statusCode() != 200) {
                            lastResult = UpdateCheckResult.UNKNOWN;
                            plugin.getLogger()
                                    .warning("Failed to check for updates (HTTP " + response.statusCode() + ")");
                            return;
                        }

                        try {
                            JsonArray versionsArray =
                                    JsonParser.parseString(response.body()).getAsJsonArray();
                            String latest = getLatestVersion(versionsArray);

                            if (latest == null) {
                                lastResult = UpdateCheckResult.UNKNOWN;
                                return;
                            }

                            latestVersion = latest;
                            lastCheckTime = System.currentTimeMillis();

                            String currentRaw = getRawVersion(currentVersion);
                            String latestRaw = getRawVersion(latest);

                            if (compareVersions(latestRaw, currentRaw) > 0) {
                                lastResult = UpdateCheckResult.NEW_VERSION_AVAILABLE;
                            } else {
                                lastResult = UpdateCheckResult.RUNNING_LATEST_VERSION;
                            }

                            UniversalScheduler.getScheduler(plugin).runTask(this::printCheckResultToConsole);

                        } catch (Exception e) {
                            lastResult = UpdateCheckResult.UNKNOWN;
                            plugin.getLogger().log(Level.WARNING, "Error parsing update check response", e);
                        }
                    })
                    .exceptionally(throwable -> {
                        lastResult = UpdateCheckResult.UNKNOWN;
                        return null;
                    });
        } catch (Exception e) {
            lastResult = UpdateCheckResult.UNKNOWN;
        }
    }

    @Nullable
    private String getLatestVersion(JsonArray versions) {
        return versions.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(version ->
                        "release".equalsIgnoreCase(version.get("version_type").getAsString())) // ✅ 只取正式版
                .filter(this::isVersionCompatible)
                .map(version -> version.get("version_number").getAsString())
                .map(ModrinthUpdateChecker::getRawVersion)
                .max(ModrinthUpdateChecker::compareVersions)
                .orElse(null);
    }

    private boolean isVersionCompatible(JsonObject version) {
        JsonArray versions = version.get("game_versions").getAsJsonArray();
        JsonArray loaders = version.get("loaders").getAsJsonArray();
        return (minecraftVersion == null || versions.contains(new JsonPrimitive(minecraftVersion)))
                && loaders.contains(new JsonPrimitive(loader));
    }

    private static String getRawVersion(String version) {
        if (version.isEmpty()) return version;
        version = version.replaceAll("^\\D+", "");
        String[] split = version.split("\\+");
        return split[0];
    }

    /**
     * Compare two version strings
     * Returns: positive if v1 > v2, negative if v1 < v2, 0 if equal
     */
    private static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int p2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;

            if (p1 != p2) {
                return Integer.compare(p1, p2);
            }
        }

        if (v1.matches(".*(?i)(snapshot|beta|dev|rc).*") && !v2.matches(".*(?i)(snapshot|beta|dev|rc).*")) {
            return -1;
        }
        if (!v1.matches(".*(?i)(snapshot|beta|dev|rc).*") && v2.matches(".*(?i)(snapshot|beta|dev|rc).*")) {
            return 1;
        }

        return 0;
    }

    private static int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (lastResult != UpdateCheckResult.NEW_VERSION_AVAILABLE) {
            return;
        }

        boolean shouldNotify = false;

        if (notifyOps && player.isOp()) {
            shouldNotify = true;
        }

        if (notifyPermission != null && player.hasPermission(notifyPermission)) {
            shouldNotify = true;
        }

        if (shouldNotify) {
            plugin.getServer()
                    .getScheduler()
                    .runTaskLater(plugin, () -> printCheckResultToPlayer(player, false), 40L); // 2 second delay
        }
    }

    private void printCheckResultToConsole() {
        if (lastResult == UpdateCheckResult.UNKNOWN) {
            plugin.getLogger().warning("Could not check for updates.");
            return;
        }

        if (lastResult == UpdateCheckResult.RUNNING_LATEST_VERSION) {
            if (suppressUpToDateMessage) return;
            plugin.getLogger().info(String.format("You are using the latest version of %s.", plugin.getName()));
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add(String.format("There is a new version of %s available!", plugin.getName()));
        lines.add(" ");
        lines.add(String.format("Your version:   %s%s", coloredConsole ? ChatColor.RED : "", currentVersion));
        lines.add(String.format("Latest version: %s%s", coloredConsole ? ChatColor.GREEN : "", latestVersion));

        if (downloadLink != null) {
            lines.add(" ");
            lines.add("Please update to the newest version.");
            lines.add(" ");
            lines.add("Download:");
            lines.add("  " + downloadLink);
        }

        if (supportLink != null) {
            lines.add(" ");
            lines.add("Support:");
            lines.add("  " + supportLink);
        }

        if (donationLink != null) {
            lines.add(" ");
            lines.add("Donate:");
            lines.add("  " + donationLink);
        }

        printNiceBoxToConsole(lines);
    }

    private void printCheckResultToPlayer(Player player, boolean showMessageWhenLatestVersion) {
        if (lastResult == UpdateCheckResult.NEW_VERSION_AVAILABLE) {
            player.sendMessage(ChatColor.GRAY + "There is a new version of " + ChatColor.GOLD + plugin.getName()
                    + ChatColor.GRAY + " available.");
            sendLinks(player);
            player.sendMessage(ChatColor.DARK_GRAY + "Latest version: " + ChatColor.GREEN + latestVersion
                    + ChatColor.DARK_GRAY + " | Your version: " + ChatColor.RED + currentVersion);
            player.sendMessage("");
        } else if (lastResult == UpdateCheckResult.UNKNOWN) {
            player.sendMessage(ChatColor.GOLD + plugin.getName() + ChatColor.RED + " could not check for updates.");
        } else {
            if (showMessageWhenLatestVersion) {
                player.sendMessage(
                        ChatColor.GREEN + "You are running the latest version of " + ChatColor.GOLD + plugin.getName());
            }
        }
    }

    private void printNiceBoxToConsole(List<String> lines) {
        int longestLine = 0;
        for (String line : lines) {
            longestLine = Math.max(line.length(), longestLine);
        }
        longestLine = Math.min(longestLine + 4, 120);

        StringBuilder dash = new StringBuilder();
        Stream.generate(() -> "*").limit(longestLine).forEach(dash::append);

        plugin.getLogger().log(Level.WARNING, dash.toString());
        for (String line : lines) {
            plugin.getLogger().log(Level.WARNING, "* " + line);
        }
        plugin.getLogger().log(Level.WARNING, dash.toString());
    }

    private void sendLinks(@NotNull Player player) {
        List<TextComponent> links = new ArrayList<>();

        if (downloadLink != null) {
            links.add(createLink("Download", downloadLink));
        }
        if (donationLink != null) {
            links.add(createLink("Donate", donationLink));
        }
        if (changelogLink != null) {
            links.add(createLink("Changelog", changelogLink));
        }
        if (supportLink != null) {
            links.add(createLink("Support", supportLink));
        }

        if (links.isEmpty()) return;

        TextComponent placeholder = new TextComponent(" | ");
        placeholder.setColor(net.md_5.bungee.api.ChatColor.GRAY);

        TextComponent text = new TextComponent("");
        Iterator<TextComponent> iterator = links.iterator();
        while (iterator.hasNext()) {
            text.addExtra(iterator.next());
            if (iterator.hasNext()) {
                text.addExtra(placeholder);
            }
        }

        player.spigot().sendMessage(text);
    }

    @NotNull
    private static TextComponent createLink(@NotNull String text, @NotNull String link) {
        ComponentBuilder lore =
                new ComponentBuilder("Link: ").bold(true).append(link).bold(false);

        TextComponent component = new TextComponent(text);
        component.setBold(true);
        component.setColor(net.md_5.bungee.api.ChatColor.GOLD);
        component.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, link));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, lore.create()));
        return component;
    }

    public UpdateCheckResult getLastResult() {
        return lastResult;
    }

    @Nullable
    public String getLatestVersion() {
        return latestVersion;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }
}
