package ez.pogdog.yescom.core.query.invalidmove;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.core.query.IsLoadedQuery;

public class InvalidMoveQuery extends IsLoadedQuery<InvalidMoveHandle> {

    // TODO: Priority, expected loaded/unloaded, etc...

    private final YesCom yesCom = YesCom.getInstance();

    private final ChunkPosition chunkPosition;
    private final Dimension dimension;

    private State state; // State callback

    public InvalidMoveQuery(ChunkPosition chunkPosition, Dimension dimension, State expected, Priority priority) {
        super(expected, priority);

        this.chunkPosition = chunkPosition;
        this.dimension = dimension;

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
    public ChunkPosition getPosition() {
        return chunkPosition;
    }

    @Override
    public Dimension getDimension() {
        return dimension;
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
