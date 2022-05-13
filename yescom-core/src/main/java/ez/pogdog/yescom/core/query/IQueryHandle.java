package ez.pogdog.yescom.core.query;

/**
 * Handles {@link IQuery}s. Responsible for throttling, and dispatching.
 */
public interface IQueryHandle<T extends IQuery> {

    /**
     * Ticks this handle.
     */
    void tick();

    /**
     * @return Can this handle use this query?
     */
    boolean handles(IQuery<?> query);

    /**
     * Dispatches a query.
     * @param query The query to dispatch.
     */
    void dispatch(T query);

    /**
     * Cancels a query.
     * @param query The query to cancel.
     */
    void cancel(T query);
}
