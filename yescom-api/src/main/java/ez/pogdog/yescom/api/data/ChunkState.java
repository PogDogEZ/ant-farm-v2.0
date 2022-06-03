package ez.pogdog.yescom.api.data;

import java.util.Objects;

/**
 * Represents the state of a chunk.
 */
public class ChunkState {

    private final ChunkPosition position;
    private final Dimension dimension;
    private final State state;

    public ChunkState(ChunkPosition position, Dimension dimension, State state) {
        this.position = position;
        this.dimension = dimension;
        this.state = state;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        ChunkState that = (ChunkState)other;
        return position.equals(that.position) && dimension == that.dimension && state == that.state;
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, dimension, state);
    }

    public ChunkPosition getPosition() {
        return position;
    }

    public State getState() {
        return state;
    }

    public enum State {
        LOADED, UNLOADED;
    }
}
