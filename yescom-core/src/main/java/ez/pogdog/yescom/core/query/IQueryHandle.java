package ez.pogdog.yescom.core.query;

import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.core.ITickable;

import java.util.function.Consumer;

/**
 * Handles {@link IQuery}s. Responsible for throttling, and dispatching.
 */
@SuppressWarnings("rawtypes")
public interface IQueryHandle<T extends IQuery> extends ITickable {

    /**
     * Ticks this handle.
     */
    @Override
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
     * Due to {@link IQuery}s basically only being loaded chunk checks, I think having a specific {@link Dimension}
     * associated with each handle makes sense. It also makes code a lot easier to manage :p.
     * At this point, most of this loaded chunk stuff is hardcoded in to classes like
     * {@link ez.pogdog.yescom.core.connection.Server} so yeah, whatever.
     *
     * @return The dimension that this query handle acts on.
     */
    Dimension getDimension();

    /**
     * @param ahead The time look ahead for, in ticks.
     * @return The maximum number of queries that could be processed in the given time.
     */
    float getThroughputFor(int ahead);

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
