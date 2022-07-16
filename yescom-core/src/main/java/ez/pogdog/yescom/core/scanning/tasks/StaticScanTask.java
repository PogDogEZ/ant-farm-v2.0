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

public class StaticScanTask extends StandardTask {

    /* ------------------------------ Parameters ------------------------------ */

    private final Parameter<BlockChunkPosition[]> POSITIONS = new Parameter<>(
            "Positions", "The positions to scan at.", BlockChunkPosition[].class, server -> null
    );

    /* ------------------------------ Other fields ------------------------------ */

    private final List<ChunkPosition> positions = new ArrayList<>();

    @Override
    public String getName() {
        return "Static scan";
    }

    @Override
    public String getDescription() {
        return "Scan static locations.";
    }

    @Override
    public Parameter<?>[] getParameters() {
        return new Parameter<?>[] {
                POSITIONS,
                StandardParameters.PRIORITY,
                StandardParameters.MAX_QUERY_THROUGHPUT,
                StandardParameters.STOP_ON_LOADED
        };
    }

    @Override
    public boolean apply(Server server, Dimension dimension, Map<String, Object> parameters) {
        if (!super.apply(server, dimension, parameters)) return false;

        BlockChunkPosition[] positions = POSITIONS.from(server, parameters);
        if (positions == null) return false;

        for (BlockChunkPosition position : positions) {
            if (!this.positions.contains(position)) this.positions.add(position);
        }
        maxIndex = this.positions.size();

        parameterValues = new ParameterValue<?>[] {
                new ParameterValue<>(POSITIONS, positions),
                new ParameterValue<>(StandardParameters.PRIORITY, priority),
                new ParameterValue<>(StandardParameters.MAX_QUERY_THROUGHPUT, maxQueries),
                new ParameterValue<>(StandardParameters.STOP_ON_LOADED, stopOnLoaded)
        };
        return true;
    }

    @Override
    public ChunkPosition getCurrentPosition() {
        return positions.get(currentIndex);
    }
}
