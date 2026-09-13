package com.darkignite.proximityantixray.listeners;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.ChunkCoordIntPair;
import com.darkignite.proximityantixray.ProximityAntiXray;
import com.darkignite.proximityantixray.data.ChunkBlocks;
import com.darkignite.proximityantixray.data.LongWrapper;
import com.darkignite.proximityantixray.data.PlayerData;
import com.darkignite.proximityantixray.data.VectorialLocation;
import com.darkignite.proximityantixray.tasks.ProximityCallable;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

public final class PacketListener extends PacketAdapter {
    private final ProximityAntiXray plugin;

    public PacketListener(ProximityAntiXray plugin) {
        super(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.MAP_CHUNK, PacketType.Play.Server.UNLOAD_CHUNK, PacketType.Play.Server.RESPAWN);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        PacketType packetType = event.getPacketType();

        if (packetType == PacketType.Play.Server.MAP_CHUNK) {
            ChunkBlocks chunkBlocks = plugin.getPacketChunkBlocksCache().get(event.getPacket().getHandle());

            if (chunkBlocks == null) {
                Player player = event.getPlayer();
                Location location = player.getEyeLocation();
                ConcurrentMap<UUID, PlayerData> playerDataMap = plugin.getPlayerData();
                UUID uniqueId = player.getUniqueId();
                PlayerData playerData = playerDataMap.get(uniqueId);

                if (!plugin.validatePlayerData(player, playerData, "onPacketSending")) {
                    return;
                }

                if (!location.getWorld().equals(playerData.getLocations()[0].getWorld())) {
                    playerData = new PlayerData(new VectorialLocation[] { new VectorialLocation(location) });
                    playerData.setCallable(new ProximityCallable(plugin, playerData));
                    playerDataMap.put(uniqueId, playerData);
                }

                return;
            }

            LevelChunk chunk = chunkBlocks.getChunk();

            if (chunk == null) {
                return;
            }

            CraftWorld world = chunk.getLevel().getWorld();
            ConcurrentMap<UUID, PlayerData> playerDataMap = plugin.getPlayerData();
            Player player = event.getPlayer();
            UUID uniqueId = player.getUniqueId();
            PlayerData playerData = playerDataMap.get(uniqueId);

            if (!plugin.validatePlayerData(player, playerData, "onPacketSending")) {
                return;
            }

            if (!world.equals(playerData.getLocations()[0].getWorld())) {
                Location location = player.getEyeLocation();

                if (!world.equals(location.getWorld())) {
                    return;
                }

                playerData = new PlayerData(new VectorialLocation[] { new VectorialLocation(location) });
                playerData.setCallable(new ProximityCallable(plugin, playerData));
                playerDataMap.put(uniqueId, playerData);
            }

            chunkBlocks = new ChunkBlocks(chunk, new HashMap<>(chunkBlocks.getBlocks()));
            playerData.getChunks().put(chunkBlocks.getKey(), chunkBlocks);

            ChunkBlocks cb = chunkBlocks;
            Runnable concealTask = () -> plugin.sendInitialDungeonConceal(player, cb);
            if (plugin.isFolia()) {
                player.getScheduler().runDelayed(plugin, (t) -> concealTask.run(), null, 1L);
            } else {
                plugin.getServer().getScheduler().runTaskLater(plugin, concealTask, 1L);
            }
        } else if (packetType == PacketType.Play.Server.UNLOAD_CHUNK) {
            Player player = event.getPlayer();
            PlayerData playerData = plugin.getPlayerData().get(player.getUniqueId());

            if (!plugin.validatePlayerData(player, playerData, "onPacketSending")) {
                return;
            }

            ChunkCoordIntPair chunkCoordIntPair = event.getPacket().getChunkCoordIntPairs().read(0);
            playerData.getChunks().remove(new LongWrapper(ChunkPos.asLong(chunkCoordIntPair.getChunkX(), chunkCoordIntPair.getChunkZ())));
        } else if (packetType == PacketType.Play.Server.RESPAWN) {
            Player player = event.getPlayer();
            PlayerData playerData = plugin.getPlayerData().get(player.getUniqueId());

            if (!plugin.validatePlayerData(player, playerData, "onPacketSending")) {
                return;
            }

            playerData.getChunks().clear();
        }
    }
}
