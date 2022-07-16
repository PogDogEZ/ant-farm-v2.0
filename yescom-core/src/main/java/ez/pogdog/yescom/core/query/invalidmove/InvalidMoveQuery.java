package ez.pogdog.yescom.core.query.invalidmove;

import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.ChunkState;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.core.query.IQuery;
import ez.pogdog.yescom.core.query.IQueryHandle;
import ez.pogdog.yescom.core.query.loaded.IsLoadedQuery;

public class InvalidMoveQuery extends IsLoadedQuery<InvalidMoveHandle> {

    private ChunkState.State state; // State callback

    /**
     * @param chunkPosition Position to query.
     * @param dimension Dimension to query in.
     * @param expected Expected result.
     * @param priority Priority of query.
     * @param expiry Time until expiry, in milliseconds.
     */
    public InvalidMoveQuery(ChunkPosition chunkPosition, Dimension dimension, ChunkState.State expected, Priority priority, long expiry) {
        super(chunkPosition, dimension, expected, priority, expiry);

        // state = State.WAITING;
    }

    @Override
    public void dispatch(InvalidMoveHandle handle) {
        // state = State.WAITING;
    }

    @Override
    public void tick(InvalidMoveHandle handle) {
        // state = State.WAITING;
    }

    @Override
    public ChunkState getState() {
        return new ChunkState(position, dimension, state);
    }

    /**
     * Sets the state of this query. ONLY FOR USE WITH {@link InvalidMoveHandle}.
     */
    public void setState(ChunkState.State state) { // TODO: Package private
        this.state = state;
    }
}
