package com.darkignite.proximityantixray.tasks;

import java.util.Queue;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.darkignite.proximityantixray.ProximityAntiXray;
import com.darkignite.proximityantixray.data.ChunkBlocks;
import com.darkignite.proximityantixray.data.LongWrapper;
import com.darkignite.proximityantixray.data.PlayerData;
import com.darkignite.proximityantixray.data.Result;

import io.netty.channel.Channel;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class UpdateBukkitRunnable extends BukkitRunnable implements Consumer<ScheduledTask> {
    private final ProximityAntiXray plugin;
    private final Player player;

    public UpdateBukkitRunnable(ProximityAntiXray plugin) {
        this(plugin, null);
    }

    public UpdateBukkitRunnable(ProximityAntiXray plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    public void run() {
        if (player == null) {
            plugin.getServer().getOnlinePlayers().forEach(this::update);
        } else {
            update(player);
        }
    }

    @Override
    public void accept(ScheduledTask t) {
        run();
    }

    public void update(Player player) {
        PlayerData playerData = plugin.getPlayerData().get(player.getUniqueId());

        if (!plugin.validatePlayerData(player, playerData, "update")) {
            return;
        }

        World world = playerData.getLocations()[0].getWorld();

        if (!player.getWorld().equals(world)) {
            return;
        }

        ConcurrentMap<LongWrapper, ChunkBlocks> chunks = playerData.getChunks();
        ServerLevel serverLevel = ((CraftWorld) world).getHandle();
        Environment environment = world.getEnvironment();
        Queue<Result> results = playerData.getResults();
        Result result;

        ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
        if (connection == null || connection.processedDisconnect) {
            return;
        }

        Channel channel = connection.connection.channel;
        if (channel == null || !channel.isOpen()) {
            return;
        }

        boolean written = false;

        while ((result = results.poll()) != null) {
            ChunkBlocks chunkBlocks = result.getChunkBlocks();

            if (chunkBlocks.getChunk() == null || chunks.get(chunkBlocks.getKey()) != chunkBlocks) {
                continue;
            }

            BlockPos block = result.getBlock();

            if (!world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                continue;
            }

            BlockState blockState;
            BlockEntity blockEntity = null;

            if (result.isVisible()) {
                blockState = serverLevel.getBlockState(block);

                if (blockState.hasBlockEntity()) {
                    blockEntity = serverLevel.getBlockEntity(block);
                }
            } else {
                Boolean isHidden = chunkBlocks.getBlocks().get(block);
                if (isHidden == null || !isHidden) {
                    continue; // Spawner broken or block un-tracked, do NOT turn into stone!
                }
                if (plugin.isSpawnerDestroyedNear(world, block)) {
                    chunkBlocks.getBlocks().remove(block);
                    continue; // Spawner near this block was destroyed!
                }

                if (environment == Environment.NETHER) {
                    blockState = Blocks.NETHERRACK.defaultBlockState();
                } else if (environment == Environment.THE_END) {
                    blockState = Blocks.END_STONE.defaultBlockState();
                } else if (block.getY() < 0) {
                    blockState = Blocks.DEEPSLATE.defaultBlockState();
                } else {
                    blockState = Blocks.STONE.defaultBlockState();
                }
            }

            channel.write(new ClientboundBlockUpdatePacket(block, blockState));
            written = true;

            if (blockEntity != null) {
                Packet<ClientGamePacketListener> packet = blockEntity.getUpdatePacket();

                if (packet != null) {
                    channel.write(packet);
                    written = true;
                }
            }
        }

        if (written) {
            channel.flush();
        }
    }
}
