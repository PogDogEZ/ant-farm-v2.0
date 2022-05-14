package ez.pogdog.yescom.core.query.invalidmove;

import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.core.query.IsLoadedQuery;

public class InvalidMoveQuery extends IsLoadedQuery<InvalidMoveHandle> {

    private State state; // State callback

    public InvalidMoveQuery(ChunkPosition chunkPosition, Dimension dimension, State expected, Priority priority) {
        super(chunkPosition, dimension, expected, priority);

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
