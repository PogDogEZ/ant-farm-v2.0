package ez.pogdog.yescom.core.query;

import java.util.function.Consumer;

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
     * @param callback The callback to call when the query is complete.
     */
    void dispatch(T query, Consumer<T> callback);

    /**
     * Cancels a query.
     * @param query The query to cancel.
     */
    void cancel(T query);

    /**
     * @return The number of queries being processed per second.
     */
    float getQPS();

    /**
     * @return The number of queries waiting to be processed.
     */
    int getWaitingSize();

    /**
     * @return The number of queries currently being processed.
     */
    int getProcessingSize();
}
