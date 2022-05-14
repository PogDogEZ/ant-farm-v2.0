package ez.pogdog.yescom.core.query;

import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.query.invalidmove.InvalidMoveQuery;

/**
 * Something that queries if a chunk is loaded.
 */
public abstract class IsLoadedQuery<T extends IQueryHandle<? extends IsLoadedQuery>> implements IQuery<T> {

	/**
	 * Provides the default implementation of the query for a given server.
	 * @param server The server to query.
	 * @param position The position of the chunk.
	 * @param dimension The dimension of the chunk.
	 * @param expected The expected state of the chunk.
	 * @param priority The priority of the query.
	 * @return The query handle.
	 */
	public static IsLoadedQuery<?> forServer(
			Server server,
			ChunkPosition position,
			Dimension dimension,
			State expected,
			Priority priority
	) {
		// TODO: Could also check the throttling limits of both, and decide which would be better
		if (server.INVALID_MOVE_ENABLED.value) {
			return new InvalidMoveQuery(position, dimension, expected, priority);
		} else if (server.DIGGING_ENABLED.value) {
			// TODO: Digging query
		}

		throw new IllegalStateException(String.format("No query available for server %s:%d.", server.hostname, server.port));
	}

	public final ChunkPosition position;
	public final Dimension dimension;

	protected final State expected; // The expected state of this query
	protected final Priority priority; // The priority of this query

	public IsLoadedQuery(ChunkPosition position, Dimension dimension, State expected, Priority priority) {
		this.position = position;
		this.dimension = dimension;
		this.expected = expected;
		this.priority = priority;
	}

	/**
	 * @return The state of the chunk.
	 */
	public abstract State getState();

	/* ------------------------------ Classes ------------------------------ */

	/**
	 * The state of the chunk.
	 */
	public enum State {
		WAITING, LOADED, UNLOADED;
	}

	/**
	 * Query priority.
	 */
	public enum Priority {
		LOW, MEDIUM, HIGH;
	}
}
