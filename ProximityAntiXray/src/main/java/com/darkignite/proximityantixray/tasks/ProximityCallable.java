package com.darkignite.proximityantixray.tasks;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.util.Vector;

import com.darkignite.proximityantixray.ProximityAntiXray;
import com.darkignite.proximityantixray.antixray.ChunkPacketBlockControllerAntiXray;
import com.darkignite.proximityantixray.data.ChunkBlocks;
import com.darkignite.proximityantixray.data.LongWrapper;
import com.darkignite.proximityantixray.data.PlayerData;
import com.darkignite.proximityantixray.data.Result;
import com.darkignite.proximityantixray.data.VectorialLocation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ProximityCallable implements Callable<Void> {
    private final ProximityAntiXray plugin;
    private final PlayerData playerData;
    private final Collection<ChunkBlocks> chunks;
    private final double revealDistance;
    private final double revealDistanceSquared;
    private final boolean rehideBlocks;
    private final double rehideDistance;
    private final double rehideDistanceSquared;

    public ProximityCallable(ProximityAntiXray plugin, PlayerData playerData) {
        this.plugin = plugin;
        this.playerData = playerData;
        ConcurrentMap<LongWrapper, ChunkBlocks> chunks = playerData.getChunks();
        this.chunks = chunks.values();
        ChunkPacketBlockControllerAntiXray controller = (ChunkPacketBlockControllerAntiXray) ((CraftWorld) playerData.getLocations()[0].getWorld()).getHandle().chunkPacketBlockController;
        this.revealDistance = controller.revealDistance;
        this.revealDistanceSquared = revealDistance * revealDistance;
        this.rehideBlocks = controller.rehideBlocks;
        this.rehideDistance = controller.rehideDistance;
        this.rehideDistanceSquared = rehideDistance * rehideDistance;
    }

    @Override
    public Void call() {
        try {
            checkProximity();
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "An error occurred on the ProximityAntiXray tick thread", t);
            throw t;
        }
        return null;
    }

    private void checkProximity() {
        ConcurrentMap<LongWrapper, ChunkBlocks> chunks = playerData.getChunks();
        VectorialLocation[] locations = playerData.getLocations();
        Vector playerVector = locations[0].getVector();
        double playerX = playerVector.getX();
        double playerY = playerVector.getY();
        double playerZ = playerVector.getZ();

        double maxCheck = Math.max(revealDistance, rehideDistance);
        int chunkXMin = ((int) Math.floor(playerX - maxCheck)) >> 4;
        int chunkZMin = ((int) Math.floor(playerZ - maxCheck)) >> 4;
        int chunkXMax = ((int) Math.floor(playerX + maxCheck)) >> 4;
        int chunkZMax = ((int) Math.floor(playerZ + maxCheck)) >> 4;

        Queue<Result> results = playerData.getResults();

        for (ChunkBlocks chunkBlocks : this.chunks) {
            LevelChunk chunk = chunkBlocks.getChunk();

            if (chunk == null) {
                chunks.remove(chunkBlocks.getKey(), chunkBlocks);
                continue;
            }

            ChunkPos chunkPos = chunk.getPos();
            int chunkX = chunkPos.x;

            if (chunkX < chunkXMin || chunkX > chunkXMax) {
                continue;
            }

            int chunkZ = chunkPos.z;

            if (chunkZ < chunkZMin || chunkZ > chunkZMax) {
                continue;
            }

            Iterator<Entry<BlockPos, Boolean>> iterator = chunkBlocks.getBlocks().entrySet().iterator();

            while (iterator.hasNext()) {
                Entry<BlockPos, Boolean> blockHidden = iterator.next();
                BlockPos block = blockHidden.getKey();
                int x = block.getX();
                int y = block.getY();
                int z = block.getZ();
                double centerX = x + 0.5;
                double centerY = y + 0.5;
                double centerZ = z + 0.5;
                double differenceX = playerX - centerX;
                double differenceY = playerY - centerY;
                double differenceZ = playerZ - centerZ;
                double distanceSquared = differenceX * differenceX + differenceY * differenceY + differenceZ * differenceZ;

                boolean visible = (distanceSquared <= revealDistanceSquared);
                boolean hidden = blockHidden.getValue();

                if (visible) {
                    if (hidden) {
                        results.add(new Result(chunkBlocks, block, true));

                        if (rehideBlocks) {
                            blockHidden.setValue(false);
                        } else {
                            iterator.remove();
                        }
                    }
                } else if (!hidden && rehideBlocks && distanceSquared >= rehideDistanceSquared) {
                    results.add(new Result(chunkBlocks, block, false));
                    blockHidden.setValue(true);
                }
            }
        }
    }
}
