package ez.pogdog.yescom.core.scanning.tasks;

import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.scanning.StandardTask;

import java.util.Map;

public class BasicHighwayScanTask extends StandardTask {

    private final int[] HIGHWAY_X_ORDINATES = new int[] { 1, 1, 1, 0, 0, -1, -1, -1 };
    private final int[] HIGHWAY_Z_ORDINATES = new int[] { -1, 0, 1, -1, 1, -1, 0, 1 };

    /* ------------------------------ Parameters ------------------------------ */

    // TODO: Base highways off the current server, min/max scan distances for axis highways, diagonals, etc...
    // TODO: ^^^^ proper highway scan task (not basic)

    private final Parameter<Integer> MIN_DISTANCE = new Parameter<>(
            "Min distance", "The minimum distance to start scanning at, in blocks.", Integer.class, server -> 0
    );
    private final Parameter<Integer> MAX_DISTANCE = new Parameter<>(
            "Max distance", "The maximum distance to scan until, in blocks.", Integer.class, server -> 500000
    );

    /* ------------------------------ Other fields ------------------------------ */

    private /* final */ int minDistance;

    @Override
    public String getName() {
        return "Basic highway scan";
    }

    @Override
    public String getDescription() {
        return "Scans only the diagonal and axis highways up to a fixed distance.";
    }

    @Override
    public Parameter<?>[] getParameters() {
        return new Parameter<?>[] {
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

        minDistance = MIN_DISTANCE.from(server, parameters) / 16;
        int maxDistance = MAX_DISTANCE.from(server, parameters) / 16;
        maxIndex = (maxDistance - minDistance) / chunkSkip * 8;

        parameterValues = new ParameterValue<?>[] {
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
    public ChunkPosition getCurrentPosition() {
        int ordinateX = HIGHWAY_X_ORDINATES[currentIndex % 8];
        int ordinateZ = HIGHWAY_Z_ORDINATES[currentIndex % 8];
        return new ChunkPosition(
                (ordinateX * (currentIndex / 8) * chunkSkip) + (ordinateX * minDistance),
                (ordinateZ * (currentIndex / 8) * chunkSkip) + (ordinateZ * minDistance)
        );
    }
}
