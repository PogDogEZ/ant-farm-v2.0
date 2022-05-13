package ez.pogdog.yescom.core.query.invalidmove;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.core.query.IQueryHandle;
import ez.pogdog.yescom.core.query.IsLoadedQuery;

public class InvalidMoveQuery extends IsLoadedQuery {

	private final YesCom yesCom = YesCom.getInstance();

	private final ChunkPosition chunkPosition;

	private State state; // State callback

	public InvalidMoveQuery(ChunkPosition chunkPosition) {
		this.chunkPosition = chunkPosition;
	}

	@Override
	public IQueryHandle<InvalidMoveQuery> getHandle() {
		return yesCom.invalidMoveHandle;
	}

	@Override
	public ChunkPosition getPosition() {
		return chunkPosition;
	}

	@Override
	public State getState() {
		return state;
	}
}
