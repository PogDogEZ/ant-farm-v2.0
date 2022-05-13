package ez.pogdog.yescom.core.query;

/**
 * Provides some sort of information.
 */
public interface IQuery {
	/**
	 * @return The handle responsible for this type of query.
	 */
	IQueryHandle<? extends IQuery> getHandle();
}
