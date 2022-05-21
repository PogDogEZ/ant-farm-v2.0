package ez.pogdog.yescom.core.query.invalidmove;

import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.core.query.IQuery;
import ez.pogdog.yescom.core.query.IQueryHandle;
import ez.pogdog.yescom.core.query.IsLoadedQuery;

public class InvalidMoveQuery extends IsLoadedQuery<InvalidMoveHandle> {

    private State state; // State callback

    /**
     * @param chunkPosition Position to query.
     * @param dimension Dimension to query in.
     * @param expected Expected result.
     * @param priority Priority of query.
     * @param expiry Time until expiry, in milliseconds.
     */
    public InvalidMoveQuery(ChunkPosition chunkPosition, Dimension dimension, State expected, Priority priority, long expiry) {
        super(chunkPosition, dimension, expected, priority, expiry);

        state = State.WAITING;
    }

    @Override
    public void dispatch(InvalidMoveHandle handle) {
        state = State.WAITING;
    }

    @Override
    public void tick(InvalidMoveHandle handle) {
        // state = State.WAITING;
    }

    @Override
    public Dimension getDimension(IQueryHandle<? extends IQuery> handle) {
        return handle.getDimension();
    }

    @Override
    public State getState() {
        return state;
    }

    /**
     * Sets the state of this query. ONLY FOR USE WITH {@link InvalidMoveHandle}.
     */
    public void setState(State state) { // TODO: Package private
        this.state = state;
    }
}
