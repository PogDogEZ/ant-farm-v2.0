package ez.pogdog.yescom.core.query;

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
}
