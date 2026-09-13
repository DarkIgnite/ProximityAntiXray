package com.darkignite.proximityantixray.data;

import java.lang.ref.WeakReference;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ChunkBlocks {
    private final WeakReference<LevelChunk> chunk;
    private final LongWrapper key;
    private final Map<BlockPos, Boolean> blocks;

    public ChunkBlocks(LevelChunk chunk, Map<BlockPos, Boolean> blocks) {
        this.chunk = new WeakReference<>(chunk);
        this.key = new LongWrapper(ChunkPos.asLong(chunk.getPos().x, chunk.getPos().z));
        this.blocks = blocks;
    }

    public LevelChunk getChunk() {
        return chunk.get();
    }

    public LongWrapper getKey() {
        return key;
    }

    public Map<BlockPos, Boolean> getBlocks() {
        return blocks;
    }
}
