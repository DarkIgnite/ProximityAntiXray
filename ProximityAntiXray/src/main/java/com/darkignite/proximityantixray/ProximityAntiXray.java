package com.darkignite.proximityantixray;

import java.util.Timer;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.ProtocolLibrary;
import com.google.common.base.Throwables;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.darkignite.proximityantixray.commands.ProximityAntiXrayCommand;
import com.darkignite.proximityantixray.data.ChunkBlocks;
import com.darkignite.proximityantixray.data.PlayerData;
import com.darkignite.proximityantixray.listeners.PacketListener;
import com.darkignite.proximityantixray.listeners.PlayerListener;
import com.darkignite.proximityantixray.listeners.WorldListener;
import com.darkignite.proximityantixray.tasks.ProximityTimerTask;
import com.darkignite.proximityantixray.tasks.UpdateBukkitRunnable;

import io.papermc.paper.configuration.WorldConfiguration.Anticheat.AntiXray;
import io.papermc.paper.configuration.type.EngineMode;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;

public final class ProximityAntiXray extends JavaPlugin {
    private boolean folia = false;
    private volatile boolean running = false;
    private volatile boolean timingsEnabled = false;
    private final ConcurrentMap<ClientboundLevelChunkWithLightPacket, ChunkBlocks> packetChunkBlocksCache = new MapMaker().weakKeys().makeMap();
    private final ConcurrentMap<UUID, PlayerData> playerData = new ConcurrentHashMap<>();
    private ExecutorService executorService;
    private Timer timer;
    private long updateTicks = 1L;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        config.options().copyDefaults(true);

        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {

        }

        running = true;

        executorService = Executors.newFixedThreadPool(
            2,
            new ThreadFactoryBuilder()
                .setThreadFactory(Executors.defaultThreadFactory())
                .setNameFormat("ProximityAntiXray thread %d")
                .setDaemon(true)
                .build()
        );

        long checkIntervalTicks = Math.max(config.getLong("settings.check-interval-ticks", 2L), 1L);
        long msInterval = checkIntervalTicks * 50L;

        timer = new Timer("ProximityAntiXray tick thread", true);
        timer.schedule(new ProximityTimerTask(this), 0L, msInterval);
        updateTicks = Math.max(config.getLong("settings.update-ticks", 1L), 1L);

        if (!folia) {
            new UpdateBukkitRunnable(this).runTaskTimer(this, 0L, updateTicks);
        }

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new WorldListener(this), this);
        pluginManager.registerEvents(new PlayerListener(this), this);
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketListener(this));

        ProximityAntiXrayCommand cmd = new ProximityAntiXrayCommand(this);
        getCommand("proximityantixray").setExecutor(cmd);
        getCommand("proximityantixray").setTabCompleter(cmd);

        getLogger().info("=========================================");
        getLogger().info(" ProximityAntiXray v" + getPluginMeta().getVersion() + " by " + String.join(", ", getPluginMeta().getAuthors()));
        getLogger().info(" Ultra-lightweight proximity hider enabled!");
        getLogger().info("=========================================");
    }

    @Override
    public void onDisable() {
        Throwable throwable = null;

        try {
            try {
                try {
                    try {
                        ProtocolLibrary.getProtocolManager().removePacketListeners(this);
                    } catch (Throwable t) {
                        throwable = t;
                    } finally {
                        running = false;
                        if (timer != null) {
                            timer.cancel();
                        }
                    }
                } catch (Throwable t) {
                    if (throwable == null) {
                        throwable = t;
                    } else {
                        throwable.addSuppressed(t);
                    }
                } finally {
                    if (executorService != null) {
                        executorService.shutdownNow();
                        try {
                            executorService.awaitTermination(1000L, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    }
                }
            } catch (Throwable t) {
                if (throwable == null) {
                    throwable = t;
                } else {
                    throwable.addSuppressed(t);
                }
            } finally {
                packetChunkBlocksCache.clear();
                playerData.clear();
            }
        } catch (Throwable t) {
            if (throwable == null) {
                throwable = t;
            } else {
                throwable.addSuppressed(t);
            }
        } finally {
            if (throwable != null) {
                Throwables.throwIfUnchecked(throwable);
                throw new RuntimeException(throwable);
            }
        }

        getLogger().info("ProximityAntiXray disabled.");
    }

    public boolean isFolia() {
        return folia;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isTimingsEnabled() {
        return timingsEnabled;
    }

    public void setTimingsEnabled(boolean timingsEnabled) {
        this.timingsEnabled = timingsEnabled;
    }

    public ConcurrentMap<ClientboundLevelChunkWithLightPacket, ChunkBlocks> getPacketChunkBlocksCache() {
        return packetChunkBlocksCache;
    }

    public ConcurrentMap<UUID, PlayerData> getPlayerData() {
        return playerData;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public long getUpdateTicks() {
        return updateTicks;
    }

    public boolean isEnabled(World world) {
        AntiXray antiXray = ((CraftWorld) world).getHandle().paperConfig().anticheat.antiXray;

        if (antiXray.enabled && (antiXray.engineMode == EngineMode.HIDE || antiXray.engineMode == EngineMode.OBFUSCATE)) {
            FileConfiguration config = getConfig();
            return config.getBoolean("world-settings." + world.getName() + ".enabled", config.getBoolean("world-settings.default.enabled", true));
        }

        return false;
    }

    public boolean validatePlayer(Player player) {
        return !player.hasMetadata("NPC");
    }

    public boolean validatePlayerData(Player player, PlayerData playerData, String methodName) {
        if (playerData == null) {
            if (validatePlayer(player)) {
                Logger logger = getLogger();
                logger.warning("Missing player data detected for player " + player.getName() + " in method " + methodName);
                return true;
            }
            return false;
        }
        return true;
    }
}
