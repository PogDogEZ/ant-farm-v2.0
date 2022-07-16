package ez.pogdog.yescom.core.scanning.scanners;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.api.data.tracking.Highway;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.config.Option;
import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.query.loaded.IsLoadedQuery;
import ez.pogdog.yescom.core.scanning.IScanner;
import ez.pogdog.yescom.core.scanning.StandardTask;
import ez.pogdog.yescom.core.scanning.tasks.BasicHighwayScanTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class HighwayScanner implements IScanner {

    private final Logger logger = Logging.getLogger("yescom.core.scanning.scanners");
    private final YesCom yesCom = YesCom.getInstance();

    /* ------------------------------ Options ------------------------------ */

    public final Option<Integer> MIN_OVERWORLD_DISTANCE = new Option<>(
            "Min overworld distance",
            "The minimum overworld distance to start scanning from, in blocks.",
            1000
    );
    public final Option<Integer> MAX_OVERWORLD_DISTANCE = new Option<>(
            "Max overworld distance",
            "The maximum overworld distance to scan up until, in blocks.",
            500000
    );

    public final Option<Integer> MIN_NETHER_DISTANCE = new Option<>(
            "Min nether distance",
            "The minimum nether distance to start scanning from, in blocks.",
            300
    );
    public final Option<Integer> MAX_NETHER_DISTANCE = new Option<>(
            "Max nether distance",
            "The maximum nether distance to scan up until, in blocks.",
            200000 // 3750000
    );

    public final Option<Integer> MIN_END_DISTANCE = new Option<>(
            "Min end distance",
            "The minimum end distance to start scanning from, in blocks.",
            200
    );
    public final Option<Integer> MAX_END_DISTANCE = new Option<>(
            "Max end distance",
            "The maximum end distance to scan up until, in blocks.",
            10000
    );

    /* ------------------------------ Other fields ------------------------------ */

    private /* final */ Server server;

    private StandardTask overworldScan;
    private StandardTask netherScan;
    private StandardTask endScan;

    private boolean paused;

    @Override
    public String getName() {
        return "Highway scanner";
    }

    @Override
    public String getDescription() {
        return "Scans the highways repeatedly.";
    }

    @Override
    public Dimension[] getApplicableDimensions() {
        return new Dimension[] { Dimension.OVERWORLD, Dimension.NETHER, Dimension.END };
    }

    @Override
    public boolean apply(Server server) {
        if (!server.DIGGING_ENABLED.value && !server.INVALID_MOVE_ENABLED.value) return false;
        this.server = server;
        yesCom.configHandler.addConfiguration(this); // The server is the parent, so we can only add this config here
        return true;
    }

    @Override
    public void tick() {
        // Don't queue up queries if they won't get processed
        if (server.isConnected() && server.getRenderDistance() > 0 && server.getConnectionTime() * 1000 > server.HIGH_TSLP.value * 2) {
            if (overworldScan != null) overworldScan.tick();
            if (netherScan != null) netherScan.tick();
            if (endScan != null) endScan.tick();

            Set<Highway> highways = new HashSet<>(); // So we don't have to keep requesting highways if we start multiple
            Map<String, Object> parameters = new HashMap<>(); // Default parameters that are the same across all dimensions
            parameters.put("Chunk skip", server.getRenderDistance() - 1);
            parameters.put("Priority", IsLoadedQuery.Priority.LOWEST);
            //parameters.put("Max query throughput", 0.25); // This is intentionally the highest value of all the scanners

            if (overworldScan == null || overworldScan.isFinished()) {
                highways.addAll(server.behaviour.getHighways());

                if (highways.isEmpty()) {
                    logger.finer(String.format(
                            "No highway data, defaulting to basic highway scan task for server %s:%d, dimension overworld.",
                            server.hostname, server.port
                    ));

                    Map<String, Object> overworldParameters = new HashMap<>(parameters);
                    overworldParameters.put("Min distance", MIN_OVERWORLD_DISTANCE.value);
                    overworldParameters.put("Max distance", MAX_OVERWORLD_DISTANCE.value);

                    overworldScan = new BasicHighwayScanTask();
                    if (!overworldScan.apply(server, Dimension.OVERWORLD, overworldParameters)) { // Hmm
                        overworldScan = null;
                    } else {
                        if (paused) overworldScan.pause();
                    }

                } else {

                }
            }

            if (netherScan == null || netherScan.isFinished()) {
                if (highways.isEmpty()) highways.addAll(server.behaviour.getHighways());

                if (highways.isEmpty()) {
                    logger.finer(String.format(
                            "No highway data, defaulting to basic highway scan task for server %s:%d, dimension nether.",
                            server.hostname, server.port
                    ));

                    Map<String, Object> netherParameters = new HashMap<>(parameters);
                    netherParameters.put("Min distance", MIN_NETHER_DISTANCE.value);
                    netherParameters.put("Max distance", MAX_NETHER_DISTANCE.value);

                    netherScan = new BasicHighwayScanTask();
                    if (!netherScan.apply(server, Dimension.NETHER, netherParameters)) {
                        netherScan = null;
                    } else {
                        if (paused) netherScan.pause();
                    }

                } else {

                }
            }

            if (endScan == null || endScan.isFinished()) {
                if (highways.isEmpty()) highways.addAll(server.behaviour.getHighways());

                if (highways.isEmpty()) {
                    logger.finer(String.format(
                            "No highway data, defaulting to basic highway scan task for server %s:%d, dimension end.",
                            server.hostname, server.port
                    ));

                    Map<String, Object> endParameters = new HashMap<>(parameters);
                    endParameters.put("Min distance", MIN_END_DISTANCE.value);
                    endParameters.put("Max distance", MAX_END_DISTANCE.value);

                    endScan = new BasicHighwayScanTask();
                    if (!endScan.apply(server, Dimension.END, endParameters)) {
                        endScan = null;
                    } else {
                        if (paused) endScan.pause();
                    }

                } else {

                }
            }
        }
    }

    @Override
    public void restart() {
        logger.fine("Restarted highway scan.");

        if (overworldScan != null) overworldScan.restart();
        if (netherScan != null) netherScan.restart();
        if (endScan != null) endScan.restart();
    }

    @Override
    public void pause() {
        if (overworldScan != null) overworldScan.pause();
        if (netherScan != null) netherScan.pause();
        if (endScan != null) endScan.pause();

        paused = true;
    }

    @Override
    public void unpause() {
        if (overworldScan != null) overworldScan.unpause();
        if (netherScan != null) netherScan.unpause();
        if (endScan != null) endScan.unpause();

        paused = false;
    }

    @Override
    public String getIdentifier() {
        return "highway-scanner";
    }

    @Override
    public IConfig getParent() {
        return server;
    }

    @Override
    public Server getServer() {
        return server;
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public ChunkPosition getCurrentPosition(Dimension dimension) {
        switch (dimension) {
            default:
            case OVERWORLD: {
                return overworldScan.getCurrentPosition();
            }
            case NETHER: {
                return netherScan.getCurrentPosition();
            }
            case END: {
                return endScan.getCurrentPosition();
            }
        }
    }
}
