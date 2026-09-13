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

        if (block.getType() == Material.SPAWNER) {
            // Spawner was destroyed! Un-conceal the entire dungeon room permanently.
            int hr = plugin.getDungeonHorizontalRadius(world) + 1;
            int vu = plugin.getDungeonVerticalRadiusUp(world) + 1;
            int vd = plugin.getDungeonVerticalRadiusDown(world) + 1;

            ServerLevel serverLevel = ((CraftWorld) world).getHandle();

            for (Player player : world.getPlayers()) {
                PlayerData data = plugin.getPlayerData().get(player.getUniqueId());
                if (data == null) continue;

                ServerGamePacketListenerImpl conn = ((CraftPlayer) player).getHandle().connection;
                Channel channel = (conn != null && !conn.processedDisconnect) ? conn.connection.channel : null;
                boolean written = false;

                for (ChunkBlocks cb : data.getChunks().values()) {
                    Map<BlockPos, Boolean> map = cb.getBlocks();
                    Iterator<Map.Entry<BlockPos, Boolean>> it = map.entrySet().iterator();
                    while (it.hasNext()) {
                        BlockPos pos = it.next().getKey();
                        if (Math.abs(pos.getX() - bx) <= hr && Math.abs(pos.getZ() - bz) <= hr &&
                            pos.getY() >= by - vd && pos.getY() <= by + vu) {
                            it.remove();
                            if (channel != null && channel.isOpen()) {
                                channel.write(new ClientboundBlockUpdatePacket(pos, serverLevel.getBlockState(pos)));
                                written = true;
                            }
                        }
                    }
                }
                if (written && channel != null) {
                    channel.flush();
                }
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
    }

    private void removeTrackedBlock(World world, BlockPos pos) {
        for (Player player : world.getPlayers()) {
            PlayerData data = plugin.getPlayerData().get(player.getUniqueId());
            if (data == null) continue;

            for (ChunkBlocks cb : data.getChunks().values()) {
                cb.getBlocks().remove(pos);
            }
        }
    }
}
