package ez.pogdog.yescom.core.scanning.tasks;

import ez.pogdog.yescom.api.data.BlockChunkPosition;
import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.scanning.StandardTask;

import java.util.Map;

public class BasicScanTask extends StandardTask {

    /* ------------------------------ Parameters ------------------------------ */

    private final Parameter<BlockChunkPosition> START_POSITION = new Parameter<>(
            "Start", "The starting position to scan from.", BlockChunkPosition.class, server -> null
    );
    private final Parameter<BlockChunkPosition> END_POSITION = new Parameter<>(
            "End", "The end position to scan until.", BlockChunkPosition.class, server -> null
    );

    /* ------------------------------ Other fields ------------------------------ */

    private /* final */ ChunkPosition start;
    private /* final */ ChunkPosition end;

    @Override
    public String getName() {
        return "Basic scan";
    }

    @Override
    public String getDescription() {
        return "Scans from a given starting position to an end position.";
    }

    @Override
    public Parameter<?>[] getParameters() {
        return new Parameter<?>[] {
                START_POSITION,
                END_POSITION,
                StandardParameters.CHUNK_SKIP,
                StandardParameters.PRIORITY,
                StandardParameters.MAX_QUERY_THROUGHPUT,
                StandardParameters.STOP_ON_LOADED
        };
    }

    @Override
    public boolean apply(Server server, Dimension dimension, Map<String, Object> parameters) {
        if (!super.apply(server, dimension, parameters)) return false;

        BlockChunkPosition start = START_POSITION.from(server, parameters);
        BlockChunkPosition end = END_POSITION.from(server, parameters);

        if (start == null || end == null) return false; // Obviously the task can't operate without these

        this.start = new ChunkPosition(
                Math.min(start.getX(), end.getX()) / chunkSkip,
                Math.min(start.getZ(), end.getZ()) / chunkSkip
        );
        this.end = new ChunkPosition(
                Math.max(start.getX(), end.getX()) / chunkSkip,
                Math.max(start.getZ(), end.getZ()) / chunkSkip
        );
        maxIndex = (this.end.getX() - this.start.getX()) * (this.end.getZ() - this.start.getZ());

        parameterValues = new ParameterValue<?>[] {
                new ParameterValue<>(START_POSITION, start),
                new ParameterValue<>(END_POSITION, end),
                new ParameterValue<>(StandardParameters.CHUNK_SKIP, chunkSkip),
                new ParameterValue<>(StandardParameters.PRIORITY, priority),
                new ParameterValue<>(StandardParameters.MAX_QUERY_THROUGHPUT, maxQueries),
                new ParameterValue<>(StandardParameters.STOP_ON_LOADED, stopOnLoaded)
        };
        return true;
    }

    @Override
    public ChunkPosition getCurrentPosition() {
        // FIXME: A better scanning pattern, I guess
        return new ChunkPosition(
                (currentIndex % (end.getX() - start.getX()) + start.getX()) * chunkSkip,
                (Math.floorDiv(currentIndex, end.getX() - start.getX()) + start.getZ()) * chunkSkip
        );
    }
}
