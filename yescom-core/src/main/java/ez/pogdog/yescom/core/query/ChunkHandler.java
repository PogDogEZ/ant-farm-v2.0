package ez.pogdog.yescom.core.query;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.ChunkState;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.core.Emitters;
import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.query.invalidmove.InvalidMoveHandle;
import ez.pogdog.yescom.core.query.invalidmove.InvalidMoveQuery;

/**
 * Handles more specific queries about the states of chunks.
 */
public class ChunkHandler {

    private final YesCom yesCom = YesCom.getInstance();

    private void queryCallback(Server server, ChunkState state) {
        Emitters.ON_CHUNK_STATE.emit(new Emitters.ServerChunkState(server, state));
    }

    /**
     * Requests that a {@link ChunkState} be resolved.
     * @param server The server to resolve it on.
     * @param dimension The dimension to resolve it in.
     * @param position The position of the chunk.
     * @param expected The expected state of the chunk.
     * @param priority The priority to request at.
     * @param expiry The time until expiry, in milliseconds.
     * @return The created {@link IsLoadedQuery}.
     */
    public IsLoadedQuery<?> requestState(
            Server server,
            Dimension dimension,
            ChunkPosition position,
            ChunkState.State expected,
            IsLoadedQuery.Priority priority,
            long expiry
    ) {
        if (server.DIGGING_ENABLED.value) { // TODO: Digging queries
        }

        if (server.INVALID_MOVE_ENABLED.value) {
            InvalidMoveHandle handle;
            switch (dimension) {
                default:
                case OVERWORLD: {
                    handle = server.overworldInvalidMoveHandle;
                    break;
                }
                case NETHER: {
                    handle = server.netherInvalidMoveHandle;
                    break;
                }
                case END: {
                    handle = server.endInvalidMoveHandle;
                    break;
                }
            }

            InvalidMoveQuery query = new InvalidMoveQuery(position, dimension, expected, priority, expiry);
            handle.dispatch(query, query1 -> queryCallback(server, query1.getState()));
            return query;
        }

        return null;
    }
}
