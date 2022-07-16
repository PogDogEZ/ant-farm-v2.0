package ez.pogdog.yescom.core.scanning;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.BlockChunkPosition;
import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.ChunkState;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.api.data.player.PlayerInfo;
import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.query.loaded.IsLoadedQuery;
import ez.pogdog.yescom.core.scanning.tasks.BasicScanTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Standardised {@link ITask} so I don't have to write the same code over and over again.
 */
public abstract class StandardTask implements ITask {

    private final Logger logger = Logging.getLogger("yescom.core.scanning");
    private final YesCom yesCom = YesCom.getInstance();

    protected final List<IsLoadedQuery<?>> activeQueries = new ArrayList<>();

    protected /* final */ ParameterValue<?>[] parameterValues; // For faster reference later
    private /* final */ long startTime;

    protected /* final */ Server server;
    protected /* final */ Dimension dimension;
    protected /* final */ int chunkSkip;
    protected /* final */ IsLoadedQuery.Priority priority;
    protected /* final */ double maxQueries;
    protected /* final */ boolean stopOnLoaded;

    private PlayerInfo target;
    private boolean wasOnline;

    protected /* final */ int maxIndex;
    protected int currentIndex = 0;

    private int ticksElapsed = 0;
    private boolean paused = false;

    @Override
    public boolean apply(Server server, Dimension dimension, Map<String, Object> parameters) {
        if (!server.DIGGING_ENABLED.value && !server.INVALID_MOVE_ENABLED.value) return false;

        this.server = server;
        this.dimension = dimension;

        chunkSkip = StandardParameters.CHUNK_SKIP.from(server, parameters);
        priority = StandardParameters.PRIORITY.from(server, parameters);
        maxQueries = StandardParameters.MAX_QUERY_THROUGHPUT.from(server, parameters);
        stopOnLoaded = StandardParameters.STOP_ON_LOADED.from(server, parameters);

        // VV moved to the individual subclasses for finer-tuning
        // parameterValues = new ParameterValue<?>[] {
        //         new ParameterValue<>(StandardParameters.CHUNK_SKIP, chunkSkip),
        //         new ParameterValue<>(StandardParameters.PRIORITY, priority),
        //         new ParameterValue<>(StandardParameters.MAX_QUERY_THROUGHPUT, maxQueries),
        //         new ParameterValue<>(StandardParameters.STOP_ON_LOADED, stopOnLoaded)
        // };
        startTime = System.currentTimeMillis();

        return true;
    }

    @Override
    public void tick() {
        boolean targetOffline = target != null && !server.isOnline(target.uuid);
        if (targetOffline && wasOnline) { // Notify if the target just came online / just went offline
            if (target != null && currentIndex < maxIndex) {
                // TODO: Event?
                logger.finer(String.format(
                        "Pausing task %s as the target (%s) is no longer online.", this, target.username
                ));
            }
            wasOnline = false;

        } else if (!targetOffline && !wasOnline) {
            if (target != null && currentIndex < maxIndex)
                logger.finer(String.format("Resuming task %s as the target (%s) is online.", this, target.username));
            wasOnline = true;
        }
        if (++ticksElapsed < 5 || paused || currentIndex >= maxIndex || targetOffline) return;
        ticksElapsed = 0;

        double maxQueries = yesCom.chunkHandler.getMaxThroughputFor(server, dimension, 5) * this.maxQueries;
        while (activeQueries.size() < maxQueries) {
            // Make sure the query doesn't expire, this is because we really don't care about timings when scanning
            activeQueries.add(yesCom.chunkHandler.requestState(
                    server,
                    dimension,
                    getCurrentPosition(),
                    // TODO: VVV Improve based on certain factors (especially if repeating)
                    ChunkState.State.UNLOADED, // Scans should realistically expect most queries to be unloaded
                    priority,
                    -1,
                    query -> {
                        if (query.getState().getState() == ChunkState.State.LOADED) {
                            // TODO: Event?
                            logger.info(String.format(
                                    "Task %s found loaded chunk at %d, %d (dimension %s).", this,
                                    query.position.getX() * 16, query.position.getZ() * 16, query.dimension
                            ));

                            if (stopOnLoaded) {
                                logger.fine(String.format("Stopping task %s because found a loaded chunk.", this));
                                cancel();
                            }
                        }
                        synchronized (this) {
                            activeQueries.remove(query);
                        }
                    }
            ));
            ++currentIndex;
        }
    }

    @Override
    public void pause() {
        paused = true;
    }

    @Override
    public void unpause() {
        paused = false;
    }

    @Override
    public synchronized void restart() {
        activeQueries.forEach(server::cancel);
        activeQueries.clear();
        currentIndex = 0;
    }

    @Override
    public synchronized void cancel() {
        activeQueries.forEach(server::cancel);
        activeQueries.clear();
        currentIndex = maxIndex;
    }

    @Override
    public ParameterValue<?>[] getParameterValues() {
        return parameterValues;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public Server getServer() {
        return server;
    }

    @Override
    public Dimension getDimension() {
        return dimension;
    }

    @Override
    public float getProgress() {
        return currentIndex / (float)maxIndex;
    }

    @Override
    public boolean isFinished() {
        return currentIndex >= maxIndex && activeQueries.isEmpty();
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public PlayerInfo getTarget() {
        return target;
    }

    @Override
    public void setTarget(PlayerInfo target) {
        if (target != this.target) {
            this.target = target;
            wasOnline = target == null || server.isOnline(target.uuid);
        }
    }

    /**
     * Standard {@link Parameter}s.
     */
    protected static final class StandardParameters {

        public static final Parameter<Integer> CHUNK_SKIP = new Parameter<>(
                "Chunk skip", "The number of chunks to skip over when scanning.", Integer.class,
                server -> server.getRenderDistance() - 1
        );

        public static final Parameter<IsLoadedQuery.Priority> PRIORITY = new Parameter<>(
                "Priority", "The priority of the qeuries that the task makes.", IsLoadedQuery.Priority.class,
                server -> IsLoadedQuery.Priority.LOWEST
        );

        public static final Parameter<Double> MAX_QUERY_THROUGHPUT = new Parameter<>(
                "Max query throughput",
                "A percentage representing the maximum number of queries to make given the current maximum throughput.",
                Double.class, server -> 2.5 // Having 2.5x the maximum throughput in-flight is fine, I hope
        );

        public static final Parameter<Boolean> STOP_ON_LOADED = new Parameter<>(
                "Stop on loaded",
                "Stops the task when a loaded chunk is found.",
                Boolean.class, server -> false
        );
    }
}
