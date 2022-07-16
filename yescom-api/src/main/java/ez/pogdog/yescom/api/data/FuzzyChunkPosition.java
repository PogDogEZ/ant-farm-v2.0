package ez.pogdog.yescom.api.data;

/**
 * Represents the position of a chunk, except with a given X and Z error.
 */
public class FuzzyChunkPosition extends ChunkPosition {

    private final float errorX;
    private final float errorZ;

    public FuzzyChunkPosition(int x, int z, float errorX, float errorZ) {
        super(x, z);

        this.errorX = errorX;
        this.errorZ = errorZ;
    }

    public FuzzyChunkPosition(ChunkPosition position, float errorX, float errorZ) {
        this(position.getX(), position.getZ(), errorX, errorZ);
    }

    public float getErrorX() {
        return errorX;
    }

    public float getErrorZ() {
        return errorZ;
    }
}
