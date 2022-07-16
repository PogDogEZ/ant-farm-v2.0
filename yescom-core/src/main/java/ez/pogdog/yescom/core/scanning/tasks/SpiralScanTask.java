package ez.pogdog.yescom.core.scanning.tasks;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.BlockChunkPosition;
import ez.pogdog.yescom.api.data.BlockPosition;
import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.ChunkState;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.api.data.player.PlayerInfo;
import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.query.loaded.IsLoadedQuery;
import ez.pogdog.yescom.core.scanning.ITask;
import ez.pogdog.yescom.core.scanning.StandardTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class SpiralScanTask extends StandardTask {

    /* ------------------------------ Parameters ------------------------------ */

    private final Parameter<BlockChunkPosition> CENTER_POSITION = new Parameter<>(
            "Center", "The center starting point (as a block position) for the scan.", BlockChunkPosition.class,
            server -> null
    );
    private final Parameter<Integer> MIN_DISTANCE = new Parameter<>(
            "Min distance", "The minimum starting distance from the center point to scan at, in blocks.",
            Integer.class, server -> 0
    );
    private final Parameter<Integer> MAX_DISTANCE = new Parameter<>(
            "Max distance", "The maximum distance to scan from the center up until, in blocks.", 
            Integer.class, server -> 100000
    );

    /* ------------------------------ Other fields ------------------------------ */

    private /* final */ ChunkPosition center;
    private /* final */ int minDistance;
    private /* final */ int maxDistance;

    private ChunkPosition currentPosition;
    private int x;
    private int z;
    private int dx = 0;
    private int dz = -1; // IMPORTANT

    @Override
    public String getName() {
        return "Sprial scan";
    }

    @Override
    public String getDescription() {
        return "Scans outwards in a spiral from a given starting point.";
    }

    @Override
    public Parameter<?>[] getParameters() {
        return new Parameter<?>[] {
                CENTER_POSITION,
                MIN_DISTANCE,
                MAX_DISTANCE,
                StandardParameters.CHUNK_SKIP,
                StandardParameters.PRIORITY,
                StandardParameters.MAX_QUERY_THROUGHPUT,
                StandardParameters.STOP_ON_LOADED
        };
    }

    @Override
    public boolean apply(Server server, Dimension dimension, Map<String, Object> parameters) {
        if (!super.apply(server, dimension, parameters)) return false;
        
        center = CENTER_POSITION.from(server, parameters);
        if (center == null) return false;

        minDistance = MIN_DISTANCE.from(server, parameters) / 16;
        maxDistance = MAX_DISTANCE.from(server, parameters) / 16;

        x = center.getX() + minDistance;
        z = center.getZ() + minDistance;

        maxIndex = Integer.MAX_VALUE; // Make sure we never reach this

        parameterValues = new ParameterValue<?>[] {
                new ParameterValue<>(CENTER_POSITION, (BlockChunkPosition)center),
                new ParameterValue<>(MIN_DISTANCE, minDistance * 16),
                new ParameterValue<>(MAX_DISTANCE, maxDistance * 16),
                new ParameterValue<>(StandardParameters.CHUNK_SKIP, chunkSkip),
                new ParameterValue<>(StandardParameters.PRIORITY, priority),
                new ParameterValue<>(StandardParameters.MAX_QUERY_THROUGHPUT, maxQueries),
                new ParameterValue<>(StandardParameters.STOP_ON_LOADED, stopOnLoaded)
        };
        return true;
    }

    @Override
    public void tick() { // FIXME: Fix spiral scanning task
        if (Math.abs(x) >= maxDistance || Math.abs(z) >= maxDistance) return;

        currentPosition = new ChunkPosition(x * chunkSkip, z * chunkSkip);
        if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
            int oldDx = dx;
            dx = -dz;
            dz = oldDx;
        }

        x += dx;
        z += dz;
    }

    @Override
    public float getProgress() {
        return 0.0f; // TODO: Spiral task progress?
    }

    @Override
    public boolean isFinished() {
        return Math.abs(x) >= maxDistance || Math.abs(z) >= maxDistance || super.isFinished();
    }

    @Override
    public ChunkPosition getCurrentPosition() {
        return currentPosition;
    }
}
