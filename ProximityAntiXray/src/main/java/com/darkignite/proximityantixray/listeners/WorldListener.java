package com.darkignite.proximityantixray.listeners;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;

import com.darkignite.proximityantixray.ProximityAntiXray;
import com.darkignite.proximityantixray.antixray.ChunkPacketBlockControllerAntiXray;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class WorldListener implements Listener {
    private final ProximityAntiXray plugin;

    public WorldListener(ProximityAntiXray plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        World world = event.getWorld();

        if (plugin.isEnabled(world)) {
            FileConfiguration config = plugin.getConfig();
            String worldName = world.getName();
            double revealDistance = Math.max(config.getDouble("world-settings." + worldName + ".reveal-distance", config.getDouble("world-settings.default.reveal-distance", 6.0)), 0.);
            boolean rehideBlocks = config.getBoolean("world-settings." + worldName + ".rehide-blocks", config.getBoolean("world-settings.default.rehide-blocks", true));
            double rehideDistance = Math.max(config.getDouble("world-settings." + worldName + ".rehide-distance", config.getDouble("world-settings.default.rehide-distance", 8.0)), 0.);
            int maxBlocksPerChunk = Math.max(config.getInt("world-settings." + worldName + ".max-blocks-per-chunk", config.getInt("world-settings.default.max-blocks-per-chunk", 60)), 0);
            List<String> proximityBlocks = config.getList("world-settings." + worldName + ".proximity-blocks", config.getList("world-settings.default.proximity-blocks")).stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.toList());
            ServerLevel serverLevel = ((CraftWorld) world).getHandle();
            ChunkPacketBlockControllerAntiXray controller = new ChunkPacketBlockControllerAntiXray(plugin, false, revealDistance, rehideBlocks, rehideDistance, maxBlocksPerChunk, proximityBlocks.isEmpty() ? null : proximityBlocks, serverLevel, MinecraftServer.getServer().executor);

            try {
                Field field = Level.class.getDeclaredField("chunkPacketBlockController");
                field.setAccessible(true);
                field.set(serverLevel, controller);
                plugin.getLogger().info("Injected ProximityAntiXray controller into world: " + worldName);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
