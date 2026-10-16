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
import org.bukkit.craftbukkit.entity.CraftPlayer;
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
import com.darkignite.proximityantixray.listeners.BlockListener;
import com.darkignite.proximityantixray.tasks.ProximityTimerTask;
import com.darkignite.proximityantixray.tasks.UpdateBukkitRunnable;

import io.netty.channel.Channel;
import io.papermc.paper.configuration.WorldConfiguration.Anticheat.AntiXray;
import io.papermc.paper.configuration.type.EngineMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class ProximityAntiXray extends JavaPlugin {
    private boolean folia = false;
    private volatile boolean running = false;
    private volatile boolean timingsEnabled = false;
    private final ConcurrentMap<ClientboundLevelChunkWithLightPacket, ChunkBlocks> packetChunkBlocksCache = new MapMaker().weakKeys().makeMap();
    private final ConcurrentMap<UUID, PlayerData> playerData = new ConcurrentHashMap<>();
    private final java.util.Set<BlockPos> destroyedSpawners = ConcurrentHashMap.newKeySet();
    private final java.util.Set<BlockPos> playerPlacedSpawners = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, ConcurrentHashMap<Long, BoundingBox>> discoveredBoxesByWorld = new ConcurrentHashMap<>();
    private org.bukkit.NamespacedKey playerPlacedKey;
    private ExecutorService executorService;
    private Timer timer;
    private long updateTicks = 1L;

    @Override
    public void onEnable() {
        playerPlacedKey = new org.bukkit.NamespacedKey(this, "player_placed");
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        config.options().copyDefaults(true);
        loadDiscoveredData();

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

        WorldListener worldListener = new WorldListener(this);
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(worldListener, this);
        pluginManager.registerEvents(new PlayerListener(this), this);
        pluginManager.registerEvents(new BlockListener(this), this);
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketListener(this));

        for (World world : getServer().getWorlds()) {
            worldListener.injectWorld(world);
        }

        ProximityAntiXrayCommand cmd = new ProximityAntiXrayCommand(this);
        getCommand("proximityantixray").setExecutor(cmd);
        getCommand("proximityantixray").setTabCompleter(cmd);

        getLogger().info("=========================================");
        getLogger().info(" ProximityAntiXray v" + getPluginMeta().getVersion() + " by " + String.join(", ", getPluginMeta().getAuthors()));
        getLogger().info(" Ultra-lightweight proximity hider enabled!");
        getLogger().info(" Dungeon stone concealer: " + (config.getBoolean("world-settings.default.fill-dungeon-with-stone", true) ? "ACTIVE" : "DISABLED"));
        getLogger().info("=========================================");
    }

    @Override
    public void onDisable() {
        saveDiscoveredData();
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
                destroyedSpawners.clear();
                playerPlacedSpawners.clear();
                discoveredBoxesByWorld.clear();
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

    public boolean isConcealDungeons(World world) {
        FileConfiguration config = getConfig();
        String prefix = "world-settings." + world.getName() + ".";
        String def = "world-settings.default.";
        return config.getBoolean(prefix + "structures.dungeons",
               config.getBoolean(def + "structures.dungeons",
               config.getBoolean(prefix + "fill-dungeon-with-stone",
               config.getBoolean(def + "fill-dungeon-with-stone", true))));
    }

    public boolean isFillDungeonWithStone(World world) {
        return isConcealDungeons(world);
    }

    public boolean isConcealMineshafts(World world) {
        FileConfiguration config = getConfig();
        String prefix = "world-settings." + world.getName() + ".";
        String def = "world-settings.default.";
        return config.getBoolean(prefix + "structures.mineshafts",
               config.getBoolean(def + "structures.mineshafts", true));
    }

    public boolean isConcealDesertPyramids(World world) {
        FileConfiguration config = getConfig();
        String prefix = "world-settings." + world.getName() + ".";
        String def = "world-settings.default.";
        return config.getBoolean(prefix + "structures.desert-pyramids",
               config.getBoolean(def + "structures.desert-pyramids", true));
    }

    public boolean isConcealJungleTemples(World world) {
        FileConfiguration config = getConfig();
        String prefix = "world-settings." + world.getName() + ".";
        String def = "world-settings.default.";
        return config.getBoolean(prefix + "structures.jungle-temples",
               config.getBoolean(def + "structures.jungle-temples", true));
    }

    public boolean isConcealStrongholdPortal(World world) {
        FileConfiguration config = getConfig();
        String prefix = "world-settings." + world.getName() + ".";
        String def = "world-settings.default.";
        return config.getBoolean(prefix + "structures.stronghold-portal-room",
               config.getBoolean(def + "structures.stronghold-portal-room", true));
    }

    public boolean isConcealBuriedTreasure(World world) {
        FileConfiguration config = getConfig();
        String prefix = "world-settings." + world.getName() + ".";
        String def = "world-settings.default.";
        return config.getBoolean(prefix + "structures.buried-treasure",
               config.getBoolean(def + "structures.buried-treasure", true));
    }

    public int getDungeonHorizontalRadius(World world) {
        FileConfiguration config = getConfig();
        return config.getInt("world-settings." + world.getName() + ".dungeon-horizontal-radius", config.getInt("world-settings.default.dungeon-horizontal-radius", 4));
    }

    public int getDungeonVerticalRadiusUp(World world) {
        FileConfiguration config = getConfig();
        return config.getInt("world-settings." + world.getName() + ".dungeon-vertical-radius-up", config.getInt("world-settings.default.dungeon-vertical-radius-up", 4));
    }

    public int getDungeonVerticalRadiusDown(World world) {
        FileConfiguration config = getConfig();
        return config.getInt("world-settings." + world.getName() + ".dungeon-vertical-radius-down", config.getInt("world-settings.default.dungeon-vertical-radius-down", 2));
    }

    public BlockState getDisguiseBlockState(ServerLevel serverLevel, BlockPos pos) {
        if (serverLevel.dimension() == Level.NETHER) {
            return Blocks.NETHERRACK.defaultBlockState();
        }
        if (serverLevel.dimension() == Level.END) {
            return Blocks.END_STONE.defaultBlockState();
        }

        BlockState below = serverLevel.getBlockState(pos.below());
        if (below.is(Blocks.SANDSTONE) || below.is(Blocks.SAND) || below.is(Blocks.RED_SANDSTONE)) {
            return below.is(Blocks.RED_SANDSTONE) ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
        }
        if (below.is(Blocks.TERRACOTTA) || below.getBlock().getDescriptionId().contains("terracotta") || below.is(Blocks.RED_SAND)) {
            return below.is(Blocks.RED_SAND) ? Blocks.RED_SANDSTONE.defaultBlockState() : below;
        }
        if (below.is(Blocks.STONE_BRICKS) || below.is(Blocks.CRACKED_STONE_BRICKS) || below.is(Blocks.MOSSY_STONE_BRICKS)) {
            return pos.getY() < 0 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
        }
        if (below.is(Blocks.COBBLESTONE) || below.is(Blocks.MOSSY_COBBLESTONE)) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }

        return pos.getY() < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    public void sendInitialDungeonConceal(Player player, ChunkBlocks chunkBlocks) {
        World world = player.getWorld();
        if (!isEnabled(world)) {
            return;
        }

        ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
        if (connection == null || connection.processedDisconnect) {
            return;
        }

        Channel channel = connection.connection.channel;
        if (channel == null || !channel.isOpen()) {
            return;
        }

        double px = player.getLocation().getX();
        double py = player.getLocation().getY();
        double pz = player.getLocation().getZ();

        double revealDist = getConfig().getDouble("world-settings." + world.getName() + ".reveal-distance", getConfig().getDouble("world-settings.default.reveal-distance", 8.0));
        double revealDistSq = revealDist * revealDist;
        ServerLevel serverLevel = ((CraftWorld) world).getHandle();

        int count = 0;
        for (java.util.Map.Entry<BlockPos, Boolean> entry : chunkBlocks.getBlocks().entrySet()) {
            if (entry.getValue()) {
                BlockPos pos = entry.getKey();
                double dx = px - (pos.getX() + 0.5);
                double dy = py - (pos.getY() + 0.5);
                double dz = pz - (pos.getZ() + 0.5);
                if (dx * dx + dy * dy + dz * dz > revealDistSq) {
                    BlockState fakeState = getDisguiseBlockState(serverLevel, pos);
                    channel.write(new ClientboundBlockUpdatePacket(pos, fakeState));
                    count++;
                }
            }
        }

        if (count > 0) {
            channel.flush();
        }
    }

    public static long getPieceKey(BoundingBox bb) {
        return BlockPos.asLong(bb.minX(), bb.minY(), bb.minZ());
    }

    public boolean isPieceDiscovered(World world, BoundingBox bb) {
        ConcurrentHashMap<Long, BoundingBox> map = discoveredBoxesByWorld.get(world.getUID());
        if (map == null) return false;
        return map.containsKey(getPieceKey(bb));
    }

    public void markPieceDiscovered(World world, BoundingBox bb) {
        ConcurrentHashMap<Long, BoundingBox> map = discoveredBoxesByWorld.computeIfAbsent(world.getUID(), k -> new ConcurrentHashMap<>());
        map.put(getPieceKey(bb), bb);
    }

    public boolean isStructureDiscoveredAt(World world, BlockPos pos) {
        ConcurrentHashMap<Long, BoundingBox> map = discoveredBoxesByWorld.get(world.getUID());
        if (map == null || map.isEmpty()) return false;
        for (BoundingBox bb : map.values()) {
            if (bb.isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    public void markStructureDiscoveredAt(World world, BlockPos pos) {
        if (!isEnabled(world)) return;
        try {
            ServerLevel serverLevel = ((CraftWorld) world).getHandle();
            ChunkPos chunkPos = new ChunkPos(pos);
            java.util.List<StructureStart> starts = serverLevel.structureManager().startsForStructure(chunkPos, s -> true);
            if (starts.isEmpty()) return;

            for (StructureStart start : starts) {
                if (!start.isValid()) continue;
                for (StructurePiece piece : start.getPieces()) {
                    BoundingBox bb = piece.getBoundingBox();
                    if (bb.isInside(pos)) {
                        long key = getPieceKey(bb);
                        ConcurrentHashMap<Long, BoundingBox> map = discoveredBoxesByWorld.computeIfAbsent(world.getUID(), k -> new ConcurrentHashMap<>());
                        if (map.putIfAbsent(key, bb) == null) {
                            unconcealBoundingBox(world, bb);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public void unconcealBoundingBox(World world, BoundingBox bb) {
        ServerLevel serverLevel = ((CraftWorld) world).getHandle();
        int minX = bb.minX(), maxX = bb.maxX();
        int minY = bb.minY(), maxY = bb.maxY();
        int minZ = bb.minZ(), maxZ = bb.maxZ();

        for (ChunkBlocks cb : packetChunkBlocksCache.values()) {
            if (cb.getChunk() != null && cb.getChunk().getLevel().getWorld().equals(world)) {
                cb.getBlocks().keySet().removeIf(p ->
                    p.getX() >= minX && p.getX() <= maxX &&
                    p.getY() >= minY && p.getY() <= maxY &&
                    p.getZ() >= minZ && p.getZ() <= maxZ
                );
            }
        }

        for (Player player : world.getPlayers()) {
            PlayerData data = playerData.get(player.getUniqueId());
            if (data == null) continue;

            data.getResults().removeIf(r -> {
                BlockPos p = r.getBlock();
                return p.getX() >= minX && p.getX() <= maxX &&
                       p.getY() >= minY && p.getY() <= maxY &&
                       p.getZ() >= minZ && p.getZ() <= maxZ;
            });

            ServerGamePacketListenerImpl conn = ((CraftPlayer) player).getHandle().connection;
            Channel channel = (conn != null && !conn.processedDisconnect) ? conn.connection.channel : null;
            boolean written = false;

            for (ChunkBlocks cb : data.getChunks().values()) {
                java.util.Iterator<java.util.Map.Entry<BlockPos, Boolean>> it = cb.getBlocks().entrySet().iterator();
                while (it.hasNext()) {
                    BlockPos p = it.next().getKey();
                    if (p.getX() >= minX && p.getX() <= maxX &&
                        p.getY() >= minY && p.getY() <= maxY &&
                        p.getZ() >= minZ && p.getZ() <= maxZ) {
                        it.remove();
                        if (channel != null && channel.isOpen()) {
                            channel.write(new ClientboundBlockUpdatePacket(p, serverLevel.getBlockState(p)));
                            written = true;
                        }
                    }
                }
            }
            if (written && channel != null) {
                channel.flush();
            }
        }
    }

    private void loadDiscoveredData() {
        try {
            java.io.File file = new java.io.File(getDataFolder(), "discovered_data.yml");
            if (!file.exists()) return;
            org.bukkit.configuration.file.YamlConfiguration data = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

            java.util.List<String> spawners = data.getStringList("destroyed-spawners");
            for (String s : spawners) {
                String[] parts = s.split(",");
                if (parts.length == 3) {
                    try {
                        destroyedSpawners.add(new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
                    } catch (NumberFormatException ignored) {}
                }
            }

            org.bukkit.configuration.ConfigurationSection sec = data.getConfigurationSection("discovered-pieces");
            if (sec != null) {
                for (String worldUidStr : sec.getKeys(false)) {
                    try {
                        UUID worldUid = UUID.fromString(worldUidStr);
                        ConcurrentHashMap<Long, BoundingBox> map = discoveredBoxesByWorld.computeIfAbsent(worldUid, k -> new ConcurrentHashMap<>());
                        java.util.List<String> boxList = sec.getStringList(worldUidStr);
                        for (String bStr : boxList) {
                            String[] p = bStr.split(",");
                            if (p.length == 6) {
                                BoundingBox bb = new BoundingBox(
                                    Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                                    Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5])
                                );
                                map.put(getPieceKey(bb), bb);
                            }
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void saveDiscoveredData() {
        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }
            java.io.File file = new java.io.File(getDataFolder(), "discovered_data.yml");
            org.bukkit.configuration.file.YamlConfiguration data = new org.bukkit.configuration.file.YamlConfiguration();

            java.util.List<String> spawners = new java.util.ArrayList<>();
            for (BlockPos p : destroyedSpawners) {
                spawners.add(p.getX() + "," + p.getY() + "," + p.getZ());
            }
            data.set("destroyed-spawners", spawners);

            for (java.util.Map.Entry<UUID, ConcurrentHashMap<Long, BoundingBox>> entry : discoveredBoxesByWorld.entrySet()) {
                java.util.List<String> boxes = new java.util.ArrayList<>();
                for (BoundingBox bb : entry.getValue().values()) {
                    boxes.add(bb.minX() + "," + bb.minY() + "," + bb.minZ() + "," + bb.maxX() + "," + bb.maxY() + "," + bb.maxZ());
                }
                data.set("discovered-pieces." + entry.getKey().toString(), boxes);
            }

            data.save(file);
        } catch (Throwable ignored) {
        }
    }

    public java.util.Set<BlockPos> getDestroyedSpawners() {
        return destroyedSpawners;
    }

    public java.util.Set<BlockPos> getPlayerPlacedSpawners() {
        return playerPlacedSpawners;
    }

    public org.bukkit.NamespacedKey getPlayerPlacedKey() {
        return playerPlacedKey;
    }

    public boolean isSpawnerDestroyedNear(World world, BlockPos pos) {
        if (destroyedSpawners.isEmpty()) {
            return false;
        }
        int hr = getDungeonHorizontalRadius(world) + 4;
        int vu = getDungeonVerticalRadiusUp(world) + 4;
        int vd = getDungeonVerticalRadiusDown(world) + 4;
        int px = pos.getX();
        int py = pos.getY();
        int pz = pos.getZ();

        for (BlockPos spawnerPos : destroyedSpawners) {
            if (Math.abs(px - spawnerPos.getX()) <= hr && Math.abs(pz - spawnerPos.getZ()) <= hr &&
                py >= spawnerPos.getY() - vd && py <= spawnerPos.getY() + vu) {
                return true;
            }
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
