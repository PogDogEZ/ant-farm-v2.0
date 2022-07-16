package ez.pogdog.yescom.core.scanning.scanners;

import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.scanning.IScanner;

public class SpiralScanner implements IScanner {

    @Override
    public String getName() {
        return "Spiral scanner";
    }

    @Override
    public String getDescription() {
        return "Scans outwards in a spiral repeatedly.";
    }

    @Override
    public Dimension[] getApplicableDimensions() {
        return new Dimension[] { Dimension.OVERWORLD, Dimension.NETHER, Dimension.END };
    }

    @Override
    public boolean apply(Server server) {
        return false;
    }

    @Override
    public void tick() {

    }

    @Override
    public void restart() {

    }

    @Override
    public void pause() {

    }

    @Override
    public void unpause() {

    }

    @Override
    public String getIdentifier() {
        return "spiral-scanner";
    }

    @Override
    public IConfig getParent() {
        return null;
    }

    @Override
    public Server getServer() {
        return null;
    }

    @Override
    public boolean isPaused() {
        return false;
    }

    @Override
    public ChunkPosition getCurrentPosition(Dimension dimension) {
        return null;
    }
}
