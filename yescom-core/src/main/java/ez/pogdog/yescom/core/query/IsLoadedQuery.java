package ez.pogdog.yescom.core.query;

import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.ChunkState;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.query.invalidmove.InvalidMoveQuery;

import java.util.Objects;

/**
 * Something that queries if a chunk is loaded.
 */
@SuppressWarnings("rawtypes")
public abstract class IsLoadedQuery<T extends IQueryHandle<? extends IsLoadedQuery>> implements Comparable<IsLoadedQuery<?>>, IQuery<T> {

	// TODO: More specific query handle

	public final ChunkPosition position;
	public final Dimension dimension;

	public final ChunkState.State expected; // The expected state of this query
	public final Priority priority; // The priority of this query

	private final long expiry;

	public IsLoadedQuery(ChunkPosition position, Dimension dimension, ChunkState.State expected, Priority priority, long expiry) {
		this.position = position;
		this.dimension = dimension;
		this.expected = expected;
		this.priority = priority;
		this.expiry = System.currentTimeMillis() + expiry;
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

	@Override
	public boolean isExpired() {
		return System.currentTimeMillis() <= expiry;
	}

	/**
	 * @return The state of the chunk.
	 */
	public abstract ChunkState getState();

	/* ------------------------------ Classes ------------------------------ */

	/**
	 * Query priority.
	 */
	public enum Priority {
		LOW, MEDIUM, HIGH;
	}
}
