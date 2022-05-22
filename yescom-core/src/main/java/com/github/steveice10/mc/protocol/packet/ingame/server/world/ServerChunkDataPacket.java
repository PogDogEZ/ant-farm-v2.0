package com.github.steveice10.mc.protocol.packet.ingame.server.world;

import com.github.steveice10.mc.protocol.data.game.chunk.Column;
import com.github.steveice10.mc.protocol.packet.MinecraftPacket;
import com.github.steveice10.mc.protocol.util.NetUtil;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.packetlib.io.NetInput;
import com.github.steveice10.packetlib.io.NetOutput;
import com.github.steveice10.packetlib.io.stream.StreamNetOutput;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Dang x2.
 */
public class ServerChunkDataPacket extends MinecraftPacket {

    private int x;
    private int z;
    private boolean fullChunk;
    private int chunkMask;
    private CompoundTag[] tileEntities;
    private byte[] data;

    private Column column;

    @SuppressWarnings("unused")
    private ServerChunkDataPacket() {
    }

    public ServerChunkDataPacket(Column column) {
        this.column = column;
    }

    @Override
    public void read(NetInput in) throws IOException {
        x = in.readInt();
        z = in.readInt();
        fullChunk = in.readBoolean();
        chunkMask = in.readVarInt();
        data = in.readBytes(in.readVarInt());
        tileEntities = new CompoundTag[in.readVarInt()];
        for(int i = 0; i < tileEntities.length; i++) {
            tileEntities[i] = NetUtil.readNBT(in);
        }

        // this.column = NetUtil.readColumn(data, x, z, fullChunk, false, chunkMask, tileEntities);
    }

    @Override
    public void write(NetOutput out) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        NetOutput netOut = new StreamNetOutput(byteOut);
        int mask = NetUtil.writeColumn(netOut, this.column, this.column.hasBiomeData(), this.column.hasSkylight());

        out.writeInt(this.column.getX());
        out.writeInt(this.column.getZ());
        out.writeBoolean(this.column.hasBiomeData());
        out.writeVarInt(mask);
        out.writeVarInt(byteOut.size());
        out.writeBytes(byteOut.toByteArray(), byteOut.size());
        out.writeVarInt(this.column.getTileEntities().length);
        for(CompoundTag tag : this.column.getTileEntities()) {
            NetUtil.writeNBT(out, tag);
        }
    }

    /* ------------------------------ Getters ------------------------------ */

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public boolean isFullChunk() {
        return fullChunk;
    }

    public int getChunkMask() {
        return chunkMask;
    }

    public CompoundTag[] getTileEntities() {
        return tileEntities;
    }

    public byte[] getData() {
        return data;
    }

    public Column getColumn() {
        if (column == null) {
            try {
                column = NetUtil.readColumn(data, x, z, fullChunk, false, chunkMask, tileEntities);
            } catch (IOException ignored) {
            }
        }
        return this.column;
    }
}
