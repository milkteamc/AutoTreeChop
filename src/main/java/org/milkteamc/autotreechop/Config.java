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
 
package org.milkteamc.autotreechop;

import com.cryptomorin.xseries.XMaterial;
import dev.dejvokep.boostedyaml.YamlDocument;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.milkteamc.autotreechop.configuration.ActivationMode;
import org.milkteamc.autotreechop.configuration.ConfigLoadException;
import org.milkteamc.autotreechop.configuration.ConfigSchema;
import org.milkteamc.autotreechop.configuration.GroupPolicies;

public class Config {
    private final AutoTreeChop plugin;
    private volatile State state;
    private volatile List<String> restartRequired = List.of();

    public Config(AutoTreeChop plugin) {
        this.plugin = plugin;
        load();
    }

    public synchronized void load() {
        List<String> warnings = new ArrayList<>();
        try {
            ConfigSchema.Prepared prepared;
            try (InputStream defaults = plugin.getResource("config.yml")) {
                if (defaults == null) throw new ConfigLoadException("Missing bundled config.yml");
                prepared = ConfigSchema.prepare(
                        plugin.getDataFolder().toPath().resolve("config.yml"), defaults, warnings::add);
            }
            YamlDocument effective = ConfigSchema.parse(prepared.document().dump());
            List<String> pendingRestart = new ArrayList<>();
            if (state != null) {
                retainUntilRestart(effective, "storage.use-mysql", state.useMysql, pendingRestart);
                retainUntilRestart(effective, "storage.mysql.hostname", state.hostname, pendingRestart);
                retainUntilRestart(effective, "storage.mysql.port", state.port, pendingRestart);
                retainUntilRestart(effective, "storage.mysql.database", state.database, pendingRestart);
                retainUntilRestart(effective, "storage.mysql.username", state.username, pendingRestart);
                retainUntilRestart(effective, "storage.mysql.password", state.password, pendingRestart);
                retainUntilRestart(effective, "integrations.residence.flag", state.residenceFlag, pendingRestart);
                retainUntilRestart(
                        effective, "integrations.grief-prevention.flag", state.griefPreventionFlag, pendingRestart);
            }
            State candidate = readValues(effective);
            prepared.commit();
            state = candidate;
            restartRequired = List.copyOf(pendingRestart);
            warnings.forEach(plugin.getLogger()::warning);
            if (!pendingRestart.isEmpty()) {
                plugin.getLogger()
                        .warning("Configuration changes require restart: " + String.join(", ", pendingRestart));
            }
            plugin.getLogger()
                    .info("Loaded " + candidate.logTypes.size() + " log types, "
                            + candidate.leafTypes.size() + " leaf types and "
                            + candidate.logSaplingMapping.size() + " log-sapling mappings");
        } catch (IOException e) {
            throw new ConfigLoadException(
                    "Could not read or save config.yml (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static void retainUntilRestart(YamlDocument config, String path, Object current, List<String> changed) {
        if (!Objects.equals(config.get(path), current)) {
            changed.add(path);
            config.set(path, current);
        }
    }

    public List<String> getRestartRequiredSettings() {
        return restartRequired;
    }

    public GroupPolicies.Policy resolvePolicy(Player player) {
        return state.groupPolicies.resolve(
                permission -> player.isPermissionSet(permission) && player.hasPermission(permission),
                player.hasPermission("autotreechop.vip"));
    }

    private State readValues(YamlDocument config) {
        boolean visualEffect = config.getBoolean("chopping.visual-effect");
        boolean toolDamage = config.getBoolean("chopping.tool-damage.enabled");
        boolean limitUsage = config.getBoolean("chopping.limit-usage");
        int maxUsesPerDay = config.getInt("groups.default.max-uses-per-day");
        int maxBlocksPerDay = config.getInt("groups.default.max-blocks-per-day");
        int cooldownTime = config.getInt("groups.default.cooldown-seconds");
        int vipCooldownTime = config.getInt("groups.vip.cooldown-seconds");
        boolean stopChoppingIfNotConnected = config.getBoolean("chopping.stop-if-not-connected");
        boolean stopChoppingIfDifferentTypes = config.getBoolean("chopping.stop-if-different-types");
        String residenceFlag = config.getString("integrations.residence.flag");
        String griefPreventionFlag = config.getString("integrations.grief-prevention.flag");
        boolean useClientLocale = config.getBoolean("messages.use-player-locale");

        boolean useMysql = config.getBoolean("storage.use-mysql");
        String hostname = config.getString("storage.mysql.hostname");
        int port = config.getInt("storage.mysql.port");
        String database = config.getString("storage.mysql.database");
        String username = config.getString("storage.mysql.username");
        String password = config.getString("storage.mysql.password");

        boolean limitVipUsage = config.getBoolean("groups.vip.limit-usage");
        int vipUsesPerDay = config.getInt("groups.vip.max-uses-per-day");
        int vipBlocksPerDay = config.getInt("groups.vip.max-blocks-per-day");

        int toolDamageDecrease = config.getInt("chopping.tool-damage.amount");
        boolean mustUseTool = config.getBoolean("chopping.require-tool");
        boolean respectUnbreaking = config.getBoolean("chopping.tool-damage.respect-unbreaking");

        boolean defaultTreeChop = config.getBoolean("activation.default-enabled");
        boolean playBreakSound = config.getBoolean("chopping.play-break-sound");
        boolean sneakMessage = config.getBoolean("activation.sneak-message");

        int chopBatchSize = config.getInt("chopping.batch-size");
        int maxTreeSize = config.getInt("chopping.max-tree-size");
        int maxDiscoveryBlocks = config.getInt("chopping.max-discovery-blocks");
        boolean callBlockBreakEvent = config.getBoolean("integrations.call-block-break-event");

        boolean autoReplantEnabled = config.getBoolean("replant.enabled");
        long replantDelayTicks = config.getLong("replant.delay-ticks");
        boolean requireSaplingInInventory = config.getBoolean("replant.require-sapling-in-inventory");
        boolean replantVisualEffect = config.getBoolean("replant.visual-effect");

        boolean leafRemovalEnabled = config.getBoolean("leaves.enabled");
        long leafRemovalDelayTicks = config.getLong("leaves.delay-ticks");
        int leafRemovalRadius = config.getInt("leaves.radius");
        boolean leafRemovalDropItems = config.getBoolean("leaves.drop-items");
        boolean leafRemovalVisualEffects = config.getBoolean("leaves.visual-effects");
        boolean leafRemovalAsync = config.getBoolean("leaves.async");
        int leafRemovalBatchSize = config.getInt("leaves.batch-size");
        boolean leafRemovalCountsTowardsLimit = config.getBoolean("leaves.counts-towards-limit");
        String leafRemovalMode = config.getString("leaves.mode");

        int idleTimeoutSeconds = config.getInt("safety.idle-timeout-seconds");
        int confirmationWindowSeconds = config.getInt("safety.confirmation-window-seconds");
        boolean noLeavesConfirmationEnabled = config.getBoolean("safety.no-leaves-confirmation");
        boolean preventNoLeavesChopping = config.getBoolean("safety.prevent-no-leaves-chopping");
        boolean enableIdleConfirmation = config.getBoolean("safety.idle-confirmation");
        int noLeavesDetectionRadius = config.getInt("safety.no-leaves-detection-radius");

        Locale locale =
                Locale.forLanguageTag(config.getString("messages.locale").replace('_', '-'));

        Set<Material> logTypes = loadMaterialSet(config, "chopping.log-types");
        logTypes.addAll(loadMaterialSet(config, "chopping.root-types"));
        Set<Material> leafTypes = loadMaterialSet(config, "leaves.types");
        for (String name : config.getStringList("leaves.additional-types")) {
            Material material = parseMaterial(name);
            if (material != null) leafTypes.add(material);
        }
        Set<Material> validSoilTypes = loadMaterialSet(config, "replant.valid-soil-types");

        Map<Material, Material> logSaplingMapping = loadLogSaplingMapping(config);

        return new State(
                visualEffect,
                toolDamage,
                maxUsesPerDay,
                maxBlocksPerDay,
                cooldownTime,
                vipCooldownTime,
                stopChoppingIfNotConnected,
                stopChoppingIfDifferentTypes,
                residenceFlag,
                griefPreventionFlag,
                locale,
                useClientLocale,
                useMysql,
                hostname,
                port,
                database,
                username,
                password,
                limitVipUsage,
                vipUsesPerDay,
                vipBlocksPerDay,
                toolDamageDecrease,
                mustUseTool,
                defaultTreeChop,
                respectUnbreaking,
                playBreakSound,
                Set.copyOf(logTypes),
                sneakMessage,
                autoReplantEnabled,
                replantDelayTicks,
                requireSaplingInInventory,
                replantVisualEffect,
                Map.copyOf(logSaplingMapping),
                Set.copyOf(validSoilTypes),
                leafRemovalEnabled,
                leafRemovalDelayTicks,
                leafRemovalRadius,
                leafRemovalDropItems,
                leafRemovalVisualEffects,
                leafRemovalAsync,
                leafRemovalBatchSize,
                leafRemovalCountsTowardsLimit,
                leafRemovalMode,
                Set.copyOf(leafTypes),
                idleTimeoutSeconds,
                confirmationWindowSeconds,
                noLeavesConfirmationEnabled,
                preventNoLeavesChopping,
                enableIdleConfirmation,
                noLeavesDetectionRadius,
                chopBatchSize,
                maxTreeSize,
                maxDiscoveryBlocks,
                callBlockBreakEvent,
                limitUsage,
                GroupPolicies.from(config),
                ActivationMode.parse(config.getString("activation.mode")));
    }

    private Set<Material> loadMaterialSet(YamlDocument config, String path) {
        List<String> materialNames = config.getStringList(path);
        return materialNames.stream()
                .map(this::parseMaterial)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Material parseMaterial(String name) {
        try {
            return XMaterial.matchXMaterial(name).map(XMaterial::get).orElse(null);
        } catch (Exception e) {
            plugin.getLogger().fine("Material not available in this version: " + name);
            return null;
        }
    }

    private Map<Material, Material> loadLogSaplingMapping(YamlDocument config) {
        Map<Material, Material> mapping = new HashMap<>();

        var section = config.getSection("replant.log-sapling-mapping");
        if (section == null) {
            plugin.getLogger().warning("replant.log-sapling-mapping section not found in config");
            return mapping;
        }

        Set<Object> keys = section.getKeys();
        for (Object keyObj : keys) {
            String logTypeStr = keyObj.toString();
            String saplingTypeStr = config.getString("replant.log-sapling-mapping." + logTypeStr);

            if (saplingTypeStr == null) {
                continue;
            }

            Material logType = parseMaterial(logTypeStr);
            Material saplingType = parseMaterial(saplingTypeStr);

            if (logType != null && saplingType != null) {
                mapping.put(logType, saplingType);
            } else {
                plugin.getLogger()
                        .fine("Skipping log-sapling mapping (materials not available): " + logTypeStr + " -> "
                                + saplingTypeStr);
            }
        }

        return mapping;
    }

    public boolean isVisualEffect() {
        return state.visualEffect;
    }

    public boolean isToolDamage() {
        return state.toolDamage;
    }

    public int getMaxUsesPerDay() {
        return state.maxUsesPerDay;
    }

    public int getMaxBlocksPerDay() {
        return state.maxBlocksPerDay;
    }

    public int getCooldownTime() {
        return state.cooldownTime;
    }

    /** @deprecated Legacy VIP settings; use {@link #resolvePolicy(Player)} for player limits and cooldown. */
    @Deprecated(since = "1.8.0", forRemoval = false)
    public int getVipCooldownTime() {
        return state.vipCooldownTime;
    }

    public boolean isStopChoppingIfNotConnected() {
        return state.stopChoppingIfNotConnected;
    }

    public boolean isStopChoppingIfDifferentTypes() {
        return state.stopChoppingIfDifferentTypes;
    }

    public String getResidenceFlag() {
        return state.residenceFlag;
    }

    public String getGriefPreventionFlag() {
        return state.griefPreventionFlag;
    }

    public Locale getLocale() {
        return state.locale;
    }

    public boolean isUseClientLocale() {
        return state.useClientLocale;
    }

    public boolean isUseMysql() {
        return state.useMysql;
    }

    public String getHostname() {
        return state.hostname;
    }

    public int getPort() {
        return state.port;
    }

    public String getDatabase() {
        return state.database;
    }

    public String getUsername() {
        return state.username;
    }

    public String getPassword() {
        return state.password;
    }

    public boolean getLimitUsage() {
        return state.limitUsage;
    }

    /** @deprecated Legacy VIP settings; use {@link #resolvePolicy(Player)} for player limits and cooldown. */
    @Deprecated(since = "1.8.0", forRemoval = false)
    public boolean getLimitVipUsage() {
        return state.limitVipUsage;
    }

    /** @deprecated Legacy VIP settings; use {@link #resolvePolicy(Player)} for player limits and cooldown. */
    @Deprecated(since = "1.8.0", forRemoval = false)
    public int getVipUsesPerDay() {
        return state.vipUsesPerDay;
    }

    /** @deprecated Legacy VIP settings; use {@link #resolvePolicy(Player)} for player limits and cooldown. */
    @Deprecated(since = "1.8.0", forRemoval = false)
    public int getVipBlocksPerDay() {
        return state.vipBlocksPerDay;
    }

    public int getToolDamageDecrease() {
        return state.toolDamageDecrease;
    }

    public boolean getMustUseTool() {
        return state.mustUseTool;
    }

    public boolean getDefaultTreeChop() {
        return state.defaultTreeChop;
    }

    public boolean getRespectUnbreaking() {
        return state.respectUnbreaking;
    }

    public boolean getPlayBreakSound() {
        return state.playBreakSound;
    }

    public boolean isCommandActivationEnabled() {
        ActivationMode mode = state.activationMode;
        return mode == ActivationMode.COMMAND || mode == ActivationMode.COMMAND_AND_SNEAK;
    }

    public ActivationMode getActivationMode() {
        return state.activationMode;
    }

    public boolean getSneakMessage() {
        return state.sneakMessage;
    }

    public Set<Material> getLogTypes() {
        return state.logTypes;
    }

    public boolean isAutoReplantEnabled() {
        return state.autoReplantEnabled;
    }

    public long getReplantDelayTicks() {
        return state.replantDelayTicks;
    }

    public boolean getRequireSaplingInInventory() {
        return state.requireSaplingInInventory;
    }

    public boolean getReplantVisualEffect() {
        return state.replantVisualEffect;
    }

    public Set<Material> getValidSoilTypes() {
        return state.validSoilTypes;
    }

    public Map<Material, Material> getLogSaplingMapping() {
        return state.logSaplingMapping;
    }

    public Material getSaplingForLog(Material logType) {
        return state.logSaplingMapping.get(org.milkteamc.autotreechop.utils.BlockDiscoveryUtils.treeFamily(logType));
    }

    public boolean isLeafRemovalEnabled() {
        return state.leafRemovalEnabled;
    }

    public long getLeafRemovalDelayTicks() {
        return state.leafRemovalDelayTicks;
    }

    public int getLeafRemovalRadius() {
        return state.leafRemovalRadius;
    }

    public boolean getLeafRemovalDropItems() {
        return state.leafRemovalDropItems;
    }

    public boolean getLeafRemovalVisualEffects() {
        return state.leafRemovalVisualEffects;
    }

    public boolean isLeafRemovalAsync() {
        return state.leafRemovalAsync;
    }

    public int getLeafRemovalBatchSize() {
        return state.leafRemovalBatchSize;
    }

    public boolean getLeafRemovalCountsTowardsLimit() {
        return state.leafRemovalCountsTowardsLimit;
    }

    public Set<Material> getLeafTypes() {
        return state.leafTypes;
    }

    public String getLeafRemovalMode() {
        return state.leafRemovalMode;
    }

    public int getChopBatchSize() {
        return state.chopBatchSize;
    }

    public int getMaxTreeSize() {
        return state.maxTreeSize;
    }

    public int getMaxDiscoveryBlocks() {
        return state.maxDiscoveryBlocks;
    }

    public boolean isCallBlockBreakEvent() {
        return state.callBlockBreakEvent;
    }

    public int getIdleTimeoutSeconds() {
        return state.idleTimeoutSeconds;
    }

    public int getConfirmationWindowSeconds() {
        return state.confirmationWindowSeconds;
    }

    public boolean isNoLeavesConfirmationEnabled() {
        return state.noLeavesConfirmationEnabled;
    }

    public boolean isPreventNoLeavesChopping() {
        return state.preventNoLeavesChopping;
    }

    public boolean isIdleConfirmationEnabled() {
        return state.enableIdleConfirmation;
    }

    public int getNoLeavesDetectionRadius() {
        return state.noLeavesDetectionRadius;
    }

    private record State(
            boolean visualEffect,
            boolean toolDamage,
            int maxUsesPerDay,
            int maxBlocksPerDay,
            int cooldownTime,
            int vipCooldownTime,
            boolean stopChoppingIfNotConnected,
            boolean stopChoppingIfDifferentTypes,
            String residenceFlag,
            String griefPreventionFlag,
            Locale locale,
            boolean useClientLocale,
            boolean useMysql,
            String hostname,
            int port,
            String database,
            String username,
            String password,
            boolean limitVipUsage,
            int vipUsesPerDay,
            int vipBlocksPerDay,
            int toolDamageDecrease,
            boolean mustUseTool,
            boolean defaultTreeChop,
            boolean respectUnbreaking,
            boolean playBreakSound,
            Set<Material> logTypes,
            boolean sneakMessage,
            boolean autoReplantEnabled,
            long replantDelayTicks,
            boolean requireSaplingInInventory,
            boolean replantVisualEffect,
            Map<Material, Material> logSaplingMapping,
            Set<Material> validSoilTypes,
            boolean leafRemovalEnabled,
            long leafRemovalDelayTicks,
            int leafRemovalRadius,
            boolean leafRemovalDropItems,
            boolean leafRemovalVisualEffects,
            boolean leafRemovalAsync,
            int leafRemovalBatchSize,
            boolean leafRemovalCountsTowardsLimit,
            String leafRemovalMode,
            Set<Material> leafTypes,
            int idleTimeoutSeconds,
            int confirmationWindowSeconds,
            boolean noLeavesConfirmationEnabled,
            boolean preventNoLeavesChopping,
            boolean enableIdleConfirmation,
            int noLeavesDetectionRadius,
            int chopBatchSize,
            int maxTreeSize,
            int maxDiscoveryBlocks,
            boolean callBlockBreakEvent,
            boolean limitUsage,
            GroupPolicies groupPolicies,
            ActivationMode activationMode) {}
}
