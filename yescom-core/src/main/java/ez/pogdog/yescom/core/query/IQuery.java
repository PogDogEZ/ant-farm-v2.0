package ez.pogdog.yescom.core.query;

import ez.pogdog.yescom.api.data.Dimension;

/**
 * Provides some sort of information.
 */
@SuppressWarnings("rawtypes") // Bruh
public interface IQuery<T extends IQueryHandle> {

    /**
     * Called when this query is dispatched.
     * @param handle The handle the query was dispatched by.
     */
    void dispatch(T handle);

    /**
     * Called when this query is ticked, if it has been dispatched.
     * @param handle The handle the query was dispatched by.
     */
    void tick(T handle);

    /**
     * Called when this query is cancelled.
     * @param handle The handle the query was dispatched by.
     */
    @SuppressWarnings("unchecked")
    default void cancel(T handle) {
        handle.cancel(this);
    }

    /**
     * @return Has the query expired?
     */
    boolean isExpired();

    default Dimension getDimension(IQueryHandle<? extends IQuery> handle) {
        return handle.getDimension();
    }
}
