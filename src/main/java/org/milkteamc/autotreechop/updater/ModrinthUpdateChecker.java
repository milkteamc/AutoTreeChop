/*
 * Copyright (C) 2026 MilkTeaMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.milkteamc.autotreechop.updater;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.milkteamc.autotreechop.AutoTreeChop;

public class ModrinthUpdateChecker {

    private static final String API_URL = "https://api.modrinth.com/v2/project/{id}/version";

    private final AutoTreeChop plugin;
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

    @Nullable
    private String notifyPermission = null;

    private int checkIntervalHours = 6;
    private boolean suppressUpToDateMessage = false;
    private volatile UpdateCheckResult lastResult = UpdateCheckResult.UNKNOWN;

    public enum UpdateCheckResult {
        RUNNING_LATEST_VERSION,
        NEW_VERSION_AVAILABLE,
        UNKNOWN
    }

    /**
     * @param plugin    the plugin instance
     * @param projectId the Modrinth project ID (slug or ID)
     * @param loader    the mod loader (e.g. "paper", "spigot")
     */
    public ModrinthUpdateChecker(@NotNull AutoTreeChop plugin, @NotNull String projectId, @NotNull String loader) {
        this.plugin = plugin;
        this.projectId = projectId;
        this.currentVersion = plugin.getPluginDescription().getVersion();
        this.loader = loader;
        this.minecraftVersion = plugin.getServer().getBukkitVersion().split("-")[0];
    }

    public ModrinthUpdateChecker setMinecraftVersion(@Nullable String version) {
        this.minecraftVersion = version;
        return this;
    }

    public ModrinthUpdateChecker setDownloadLink(@NotNull String link) {
        this.downloadLink = link;
        return this;
    }

    public ModrinthUpdateChecker setChangelogLink(@NotNull String link) {
        this.changelogLink = link;
        return this;
    }

    public ModrinthUpdateChecker setDonationLink(@NotNull String link) {
        this.donationLink = link;
        return this;
    }

    public ModrinthUpdateChecker setSupportLink(@NotNull String link) {
        this.supportLink = link;
        return this;
    }

    public ModrinthUpdateChecker setNotifyOpsOnJoin(boolean notify) {
        this.notifyOps = notify;
        return this;
    }

    public ModrinthUpdateChecker setNotifyByPermissionOnJoin(@NotNull String permission) {
        this.notifyPermission = permission;
        return this;
    }

    public ModrinthUpdateChecker checkEveryXHours(int hours) {
        this.checkIntervalHours = hours;
        return this;
    }

    public ModrinthUpdateChecker setSuppressUpToDateMessage(boolean suppress) {
        this.suppressUpToDateMessage = suppress;
        return this;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Run a single immediate check. */
    public ModrinthUpdateChecker checkNow() {
        performCheck();
        return this;
    }

    /** Run an immediate check and schedule recurring checks at the configured interval. */
    public ModrinthUpdateChecker startPeriodicCheck() {
        checkNow();
        long intervalTicks = checkIntervalHours * 60 * 60 * 20L;
        UniversalScheduler.getScheduler(plugin)
                .runTaskTimerAsynchronously(this::performCheck, intervalTicks, intervalTicks);
        return this;
    }

    // -------------------------------------------------------------------------
    // Player notification API — consumed by PlayerJoinListener
    // -------------------------------------------------------------------------

    /**
     * Returns whether this player should receive an update notification.
     * Called by {@link org.milkteamc.autotreechop.events.PlayerJoinListener}.
     */
    public boolean shouldNotifyPlayer(@NotNull Player player) {
        if (lastResult != UpdateCheckResult.NEW_VERSION_AVAILABLE) return false;
        return (notifyOps && player.isOp()) || (notifyPermission != null && player.hasPermission(notifyPermission));
    }

    /**
     * Send the update notification message to a player.
     * Routes through {@link org.milkteamc.autotreechop.translation.TranslationManager}'s
     * {@link net.kyori.adventure.platform.bukkit.BukkitAudiences} to avoid class loader conflicts
     * with the shaded Adventure library.
     * Called by {@link org.milkteamc.autotreechop.events.PlayerJoinListener}.
     */
    public void notifyPlayer(@NotNull Player player) {
        if (lastResult != UpdateCheckResult.NEW_VERSION_AVAILABLE) return;

        Audience audience = plugin.getTranslationManager().getAdventure().player(player);

        audience.sendMessage(Component.text("There is a new version of ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(plugin.getName()).color(NamedTextColor.GOLD))
                .append(Component.text(" available.").color(NamedTextColor.GRAY)));

        buildLinkBar().ifPresent(audience::sendMessage);

        audience.sendMessage(Component.text("Latest: ")
                .color(NamedTextColor.DARK_GRAY)
                .append(Component.text(latestVersion).color(NamedTextColor.GREEN))
                .append(Component.text(" | Your version: ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(currentVersion).color(NamedTextColor.RED)));
    }

    // -------------------------------------------------------------------------
    // Internal — HTTP check
    // -------------------------------------------------------------------------

    private void performCheck() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL.replace("{id}", projectId)))
                    .header("User-Agent", "Java-HttpClient " + plugin.getName() + "/" + currentVersion)
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
                            JsonArray versions =
                                    JsonParser.parseString(response.body()).getAsJsonArray();
                            String latest = getLatestVersion(versions);

                            if (latest == null) {
                                lastResult = UpdateCheckResult.UNKNOWN;
                                return;
                            }

                            latestVersion = latest;
                            lastResult = compareVersions(getRawVersion(latest), getRawVersion(currentVersion)) > 0
                                    ? UpdateCheckResult.NEW_VERSION_AVAILABLE
                                    : UpdateCheckResult.RUNNING_LATEST_VERSION;

                            UniversalScheduler.getScheduler(plugin).runTask(this::printResultToConsole);

                        } catch (Exception e) {
                            lastResult = UpdateCheckResult.UNKNOWN;
                            plugin.getLogger().log(Level.WARNING, "Error parsing update check response", e);
                        }
                    })
                    .exceptionally(t -> {
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
                .filter(v -> "release".equalsIgnoreCase(v.get("version_type").getAsString()))
                .filter(this::isVersionCompatible)
                .map(v -> v.get("version_number").getAsString())
                .map(ModrinthUpdateChecker::getRawVersion)
                .max(ModrinthUpdateChecker::compareVersions)
                .orElse(null);
    }

    private boolean isVersionCompatible(JsonObject version) {
        JsonArray gameVersions = version.get("game_versions").getAsJsonArray();
        JsonArray loaders = version.get("loaders").getAsJsonArray();
        return (minecraftVersion == null || gameVersions.contains(new JsonPrimitive(minecraftVersion)))
                && loaders.contains(new JsonPrimitive(loader));
    }

    // -------------------------------------------------------------------------
    // Internal — Console output (plain JUL, avoids shaded Adventure conflict)
    // -------------------------------------------------------------------------

    private void printResultToConsole() {
        switch (lastResult) {
            case UNKNOWN -> plugin.getLogger().warning("Could not check for updates.");
            case RUNNING_LATEST_VERSION -> {
                if (suppressUpToDateMessage) return;
                plugin.getLogger().info("You are running the latest version of " + plugin.getName() + ".");
            }
            case NEW_VERSION_AVAILABLE -> printUpdateBoxToConsole();
        }
    }

    private void printUpdateBoxToConsole() {
        List<String> lines = new ArrayList<>();

        lines.add("A new version of " + plugin.getName() + " is available!");
        lines.add("");
        lines.add("Your version:   " + currentVersion);
        lines.add("Latest version: " + latestVersion);

        if (downloadLink != null) {
            lines.add("");
            lines.add("Download:  " + downloadLink);
        }
        if (supportLink != null) {
            lines.add("Support:   " + supportLink);
        }
        if (donationLink != null) {
            lines.add("Donate:    " + donationLink);
        }

        String border = "*".repeat(60);
        plugin.getLogger().warning(border);
        for (String line : lines) {
            plugin.getLogger().warning("* " + line);
        }
        plugin.getLogger().warning(border);
    }

    // -------------------------------------------------------------------------
    // Internal — clickable link bar for player messages
    // -------------------------------------------------------------------------

    private Optional<Component> buildLinkBar() {
        record Link(String label, String url) {}

        List<Link> links = new ArrayList<>();
        if (downloadLink != null) links.add(new Link("Download", downloadLink));
        if (donationLink != null) links.add(new Link("Donate", donationLink));
        if (changelogLink != null) links.add(new Link("Changelog", changelogLink));
        if (supportLink != null) links.add(new Link("Support", supportLink));

        if (links.isEmpty()) return Optional.empty();

        Component separator = Component.text(" | ").color(NamedTextColor.GRAY);
        Component bar = Component.empty();
        Iterator<Link> it = links.iterator();
        while (it.hasNext()) {
            Link link = it.next();
            Component btn = Component.text(link.label())
                    .color(NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD)
                    .clickEvent(ClickEvent.openUrl(link.url()))
                    .hoverEvent(HoverEvent.showText(Component.text("Link: ")
                            .color(NamedTextColor.GRAY)
                            .append(Component.text(link.url()).color(NamedTextColor.AQUA))));
            bar = bar.append(btn);
            if (it.hasNext()) bar = bar.append(separator);
        }

        return Optional.of(bar);
    }

    // -------------------------------------------------------------------------
    // Internal — version parsing
    // -------------------------------------------------------------------------

    private static String getRawVersion(String version) {
        if (version.isEmpty()) return version;
        version = version.replaceAll("^\\D+", "");
        return version.split("\\+")[0];
    }

    /** Returns positive if v1 > v2, negative if v1 < v2, 0 if equal. */
    private static int compareVersions(String v1, String v2) {
        String[] p1 = v1.split("\\.");
        String[] p2 = v2.split("\\.");
        int len = Math.max(p1.length, p2.length);
        for (int i = 0; i < len; i++) {
            int a = i < p1.length ? parseVersionPart(p1[i]) : 0;
            int b = i < p2.length ? parseVersionPart(p2[i]) : 0;
            if (a != b) return Integer.compare(a, b);
        }
        boolean v1Pre = v1.matches(".*(?i)(alpha|snapshot|beta|dev|rc).*");
        boolean v2Pre = v2.matches(".*(?i)(alpha|snapshot|beta|dev|rc).*");
        if (v1Pre && !v2Pre) return -1;
        if (!v1Pre && v2Pre) return 1;
        return 0;
    }

    private static int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

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
