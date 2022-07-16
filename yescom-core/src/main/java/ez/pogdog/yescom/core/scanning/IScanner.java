package ez.pogdog.yescom.core.scanning;

import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.core.ITickable;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.connection.Server;

/**
 * A scanner is something that will run in the background, querying chunks.
 */
public interface IScanner extends ITickable, IConfig {

    /* ------------------------------ Scanner info ------------------------------ */

    /**
     * @return A human-readable name for this scanner.
     */
    String getName();

    /**
     * @return A human-readable description of what this scanner does.
     */
    String getDescription();

    /**
     * @return The {@link Dimension}s that this scanner operates in.
     */
    Dimension[] getApplicableDimensions();

    /* ------------------------------ Scanner operation ------------------------------ */

    /**
     * Attempts to apply the scanner to the given {@link Server}.
     * @param server The server to apply the scanner to.
     * @return Was the scanner successfully applied?
     */
    boolean apply(Server server);

    /**
     * Restarts the scan from the beginning.
     */
    void restart();

    /**
     * Pauses this scanner.
     */
    void pause();

    /**
     * Unpauses this scanner.
     */
    void unpause();

    /* ------------------------------ Getters ------------------------------ */

    /**
     * @return The {@link Server} that this scanner is applied to.
     */
    Server getServer();

    /**
     * @return Is this scanner paused?
     */
    boolean isPaused();

    /**
     * @param dimension The dimension to get the current scanning position in.
     * @return The current position of the scanner.
     */
    ChunkPosition getCurrentPosition(Dimension dimension);
}
