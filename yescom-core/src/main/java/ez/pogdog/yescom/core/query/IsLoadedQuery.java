package ez.pogdog.yescom.core.query;

import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.query.invalidmove.InvalidMoveQuery;

import java.util.Objects;

/**
 * Something that queries if a chunk is loaded.
 */
@SuppressWarnings("rawtypes")
public abstract class IsLoadedQuery<T extends IQueryHandle<? extends IsLoadedQuery>> implements Comparable<IsLoadedQuery<?>>, IQuery<T> {

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

	public final State expected; // The expected state of this query
	public final Priority priority; // The priority of this query

	public IsLoadedQuery(ChunkPosition position, Dimension dimension, State expected, Priority priority) {
		this.position = position;
		this.dimension = dimension;
		this.expected = expected;
		this.priority = priority;
	}

	@Override
	public boolean equals(Object other) {
		return this == other;
		/*
		if (this == other) return true;
		if (other == null || getClass() != other.getClass()) return false;
		IsLoadedQuery<?> that = (IsLoadedQuery<?>)other;
		return position.equals(that.position) && dimension.equals(that.dimension);
		 */
	}

	@Override
	public int hashCode() {
		return Objects.hash(position, dimension);
	}

	@Override
	public String toString() {
		return String.format("%s(position=%s, dimension=%s, expected=%s, priority=%s)", getClass().getSimpleName(),
				position, dimension, expected, priority);
	}

	@Override
	public int compareTo(IsLoadedQuery<?> other) {
		return priority.compareTo(other.priority);
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
