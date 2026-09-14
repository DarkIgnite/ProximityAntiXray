package com.darkignite.proximityantixray.data;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ChunkBlocks {
    private final WeakReference<LevelChunk> chunk;
    private final LongWrapper key;
    private final ConcurrentMap<BlockPos, Boolean> blocks;

    public ChunkBlocks(LevelChunk chunk, Map<BlockPos, Boolean> blocks) {
        this.chunk = new WeakReference<>(chunk);
        this.key = new LongWrapper(ChunkPos.asLong(chunk.getPos().x, chunk.getPos().z));
        this.blocks = blocks instanceof ConcurrentMap ? (ConcurrentMap<BlockPos, Boolean>) blocks : new ConcurrentHashMap<>(blocks);
    }

    public LevelChunk getChunk() {
        return chunk.get();
    }

    public LongWrapper getKey() {
        return key;
    }

    public ConcurrentMap<BlockPos, Boolean> getBlocks() {
        return blocks;
    }
}
