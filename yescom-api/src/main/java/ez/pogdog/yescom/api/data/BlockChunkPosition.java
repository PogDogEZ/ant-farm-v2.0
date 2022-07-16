package ez.pogdog.yescom.api.data;

/**
 * A {@link ChunkPosition} that operates based on block coordinates, rather than chunk ones. This make it more intuitive
 * for humans to use.
 */
public class BlockChunkPosition extends ChunkPosition {

    public BlockChunkPosition(int x, int z) {
        super(x / 16, z / 16);
    }

    public ChunkPosition add(int x, int z) {
        return super.add(x / 16, z / 16);
    }

    public ChunkPosition subtract(int x, int z) {
        return super.subtract(x / 16, z / 16);
    }
}
