package com.darkignite.proximityantixray.antixray;

import io.papermc.paper.antixray.ChunkPacketInfo;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ChunkPacketInfoAntiXray extends ChunkPacketInfo<BlockState> implements Runnable {
    private final ChunkPacketBlockControllerAntiXray chunkPacketBlockControllerAntiXray;
    private LevelChunk[] nearbyChunks;
    private java.util.List<net.minecraft.world.level.levelgen.structure.StructureStart> structureStarts;

    public ChunkPacketInfoAntiXray(ClientboundLevelChunkWithLightPacket chunkPacket, LevelChunk chunk, ChunkPacketBlockControllerAntiXray chunkPacketBlockControllerAntiXray) {
        super(chunkPacket, chunk);
        this.chunkPacketBlockControllerAntiXray = chunkPacketBlockControllerAntiXray;
    }

    public LevelChunk[] getNearbyChunks() {
        return nearbyChunks;
    }

    public void setNearbyChunks(LevelChunk... nearbyChunks) {
        this.nearbyChunks = nearbyChunks;
    }

    public java.util.List<net.minecraft.world.level.levelgen.structure.StructureStart> getStructureStarts() {
        return structureStarts;
    }

    public void setStructureStarts(java.util.List<net.minecraft.world.level.levelgen.structure.StructureStart> structureStarts) {
        this.structureStarts = structureStarts;
    }

    @Override
    public void run() {
        chunkPacketBlockControllerAntiXray.obfuscate(this);
    }
}
