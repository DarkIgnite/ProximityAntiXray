package com.darkignite.proximityantixray.listeners;

import java.util.Iterator;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import com.darkignite.proximityantixray.ProximityAntiXray;
import com.darkignite.proximityantixray.data.ChunkBlocks;
import com.darkignite.proximityantixray.data.PlayerData;

import io.netty.channel.Channel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public final class BlockListener implements Listener {
    private final ProximityAntiXray plugin;

    public BlockListener(ProximityAntiXray plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();
        if (!plugin.isEnabled(world)) {
            return;
        }

        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();
        BlockPos breakPos = new BlockPos(bx, by, bz);

        if (block.getType() == Material.SPAWNER || block.getType() == Material.TRIAL_SPAWNER) {
            // 1. Permanently register spawner as destroyed
            plugin.getDestroyedSpawners().add(breakPos);

            int hr = plugin.getDungeonHorizontalRadius(world) + 4;
            int vu = plugin.getDungeonVerticalRadiusUp(world) + 4;
            int vd = plugin.getDungeonVerticalRadiusDown(world) + 4;

            ServerLevel serverLevel = ((CraftWorld) world).getHandle();

            // 2. Remove all matching blocks from packetChunkBlocksCache
            for (ChunkBlocks cb : plugin.getPacketChunkBlocksCache().values()) {
                cb.getBlocks().keySet().removeIf(pos ->
                    Math.abs(pos.getX() - bx) <= hr && Math.abs(pos.getZ() - bz) <= hr &&
                    pos.getY() >= by - vd && pos.getY() <= by + vu
                );
            }

            // 3. Un-conceal for all online players and purge pending rehide results
            for (Player player : world.getPlayers()) {
                PlayerData data = plugin.getPlayerData().get(player.getUniqueId());
                if (data == null) continue;

                // Remove any pending re-hide results queued for these positions
                data.getResults().removeIf(r -> {
                    BlockPos pos = r.getBlock();
                    return Math.abs(pos.getX() - bx) <= hr && Math.abs(pos.getZ() - bz) <= hr &&
                           pos.getY() >= by - vd && pos.getY() <= by + vu;
                });

                ServerGamePacketListenerImpl conn = ((CraftPlayer) player).getHandle().connection;
                Channel channel = (conn != null && !conn.processedDisconnect) ? conn.connection.channel : null;
                boolean written = false;

                for (ChunkBlocks cb : data.getChunks().values()) {
                    Iterator<Map.Entry<BlockPos, Boolean>> it = cb.getBlocks().entrySet().iterator();
                    while (it.hasNext()) {
                        BlockPos pos = it.next().getKey();
                        if (Math.abs(pos.getX() - bx) <= hr && Math.abs(pos.getZ() - bz) <= hr &&
                            pos.getY() >= by - vd && pos.getY() <= by + vu) {
                            it.remove();
                            if (channel != null && channel.isOpen()) {
                                net.minecraft.world.level.block.state.BlockState realState = pos.equals(breakPos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : serverLevel.getBlockState(pos);
                                channel.write(new ClientboundBlockUpdatePacket(pos, realState));
                                written = true;
                            }
                        }
                    }
                }
                if (written && channel != null) {
                    channel.flush();
                }
            }

            // 4. Schedule 1-tick delay packet update to guarantee client shows AIR at spawner position
            Runnable delayedUpdate = () -> {
                for (Player player : world.getPlayers()) {
                    ServerGamePacketListenerImpl conn = ((CraftPlayer) player).getHandle().connection;
                    Channel channel = (conn != null && !conn.processedDisconnect) ? conn.connection.channel : null;
                    if (channel != null && channel.isOpen()) {
                        channel.write(new ClientboundBlockUpdatePacket(breakPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
                        channel.flush();
                    }
                }
            };
            if (plugin.isFolia()) {
                plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, (t) -> delayedUpdate.run(), 1L);
            } else {
                plugin.getServer().getScheduler().runTaskLater(plugin, delayedUpdate, 1L);
            }
        } else {
            removeTrackedBlock(world, breakPos);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();
        if (!plugin.isEnabled(world)) {
            return;
        }

        BlockPos placePos = new BlockPos(block.getX(), block.getY(), block.getZ());
        removeTrackedBlock(world, placePos);

        if (block.getType() == Material.SPAWNER) {
            try {
                if (block.getState(false) instanceof org.bukkit.block.CreatureSpawner spawner) {
                    spawner.getPersistentDataContainer().set(plugin.getPlayerPlacedKey(), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                    spawner.update();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void removeTrackedBlock(World world, BlockPos pos) {
        for (ChunkBlocks cb : plugin.getPacketChunkBlocksCache().values()) {
            cb.getBlocks().remove(pos);
        }

        for (Player player : world.getPlayers()) {
            PlayerData data = plugin.getPlayerData().get(player.getUniqueId());
            if (data == null) continue;

            data.getResults().removeIf(r -> r.getBlock().equals(pos));

            for (ChunkBlocks cb : data.getChunks().values()) {
                cb.getBlocks().remove(pos);
            }
        }
    }
}
