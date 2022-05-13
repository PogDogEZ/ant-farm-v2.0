package ez.pogdog.yescom.core.query;

import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.Dimension;

/**
 * Something that queries if a chunk is loaded.
 */
public abstract class IsLoadedQuery<T extends IQueryHandle<? extends IsLoadedQuery>> implements IQuery<T> {

	protected final State expected; // The expected state of this query
	protected final Priority priority; // The priority of this query

	public IsLoadedQuery(State expected, Priority priority) {
		this.expected = expected;
		this.priority = priority;
	}

	/**
	 * @return The chunk position that this query has been requested for.
	 */
	public abstract ChunkPosition getPosition();
	public abstract Dimension getDimension();

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
