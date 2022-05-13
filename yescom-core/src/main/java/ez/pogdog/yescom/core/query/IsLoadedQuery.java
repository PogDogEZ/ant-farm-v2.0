package ez.pogdog.yescom.core.query;

import ez.pogdog.yescom.api.data.ChunkPosition;

/**
 * Something that queries if a chunk is loaded.
 */
public abstract class IsLoadedQuery implements IQuery {

	/**
	 * @return The chunk position that this query has been requested for.
	 */
	public abstract ChunkPosition getPosition();

	/**
	 * @return The state of the chunk.
	 */
	public abstract State getState();

	/**
	 * The state of the chunk.
	 */
	public enum State {
		LOADED, UNLOADED;
	}
}
