package ez.pogdog.yescom.core.data;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.player.PlayerInfo;
import ez.pogdog.yescom.api.data.player.Session;
import ez.pogdog.yescom.api.data.player.death.Death;
import ez.pogdog.yescom.api.data.player.death.Kill;
import ez.pogdog.yescom.core.ITickable;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.data.serialisers.PlayerSerialiser;
import ez.pogdog.yescom.core.data.serialisers.ServerSerialiser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handles data management.
 */
public class DataHandler implements IConfig, ITickable {

    private final Logger logger = Logging.getLogger("yescom.core.data");
    private final YesCom yesCom = YesCom.getInstance();

    public final List<ISerialiser> serialisers = new ArrayList<>();

    // Standard serialisers
    public final PlayerSerialiser players;
    public final ServerSerialiser servers;

    private final String dataDirectory;

    private long lastAutoSaveTime;

    public DataHandler(String dataDirectory) {
        this.dataDirectory = dataDirectory;

        players = new PlayerSerialiser();
        servers = new ServerSerialiser();
        serialisers.add(players);
        serialisers.add(servers);

        yesCom.slowAsyncUpdater.tickables.add(this);

        lastAutoSaveTime = System.currentTimeMillis() - 90000;
    }

    @Override
    public String getIdentifier() {
        return "data";
    }

    @Override
    public IConfig getParent() {
        return yesCom;
    }

    @Override
    public void tick() {
        if (System.currentTimeMillis() - lastAutoSaveTime > 120000) {
            try {
                saveDatabase(false);
            } catch (IOException error) {
                logger.warning(String.format("Couldn't save local database: %s.", error.getMessage()));
                logger.throwing(getClass().getSimpleName(), "run", error);
                return;
            }
            lastAutoSaveTime = System.currentTimeMillis();
        }
    }

    /* ------------------------------ Public API ------------------------------ */

    /**
     * Loads the local database.
     * @throws IOException Thrown if an error occurs, should not happen unless something properly went wrong.
     */
    public void loadDatabase() throws IOException {
        logger.fine("Reading local database...");
        File dataDirectory = new File(this.dataDirectory);
        if (!dataDirectory.exists() && !dataDirectory.mkdirs()) throw new IOException("Could not create data directory.");

        for (ISerialiser serialiser : serialisers) {
            try {
                serialiser.load(dataDirectory);
            } catch (IOException error) {
                logger.warning(String.format("Error while loading data from serialiser %s: %s", serialiser, error.getMessage()));
                logger.throwing(getClass().getSimpleName(), "loadDatabase", error);
            }
        }
    }

    /**
     * Saves the local database.
     * @throws IOException Thrown if an error occurs, should not happen unless something properly went wrong.
     */
    public void saveDatabase(boolean force) throws IOException {
        logger.fine("Saving local database...");
        File dataDirectory = new File(this.dataDirectory);
        if (!dataDirectory.exists() && !dataDirectory.mkdirs()) throw new IOException("Could not create data directory.");

        for (ISerialiser serialiser : serialisers) {
            try {
                serialiser.save(dataDirectory, force);
            } catch (IOException error) {
                logger.warning(String.format("Error while saving data from serialiser %s: %s", serialiser, error.getMessage()));
                logger.throwing(getClass().getSimpleName(), "saveDatabase", error);
            }
        }
    }

    /**
     * Gets the recorded sessions for a given player.
     * @param info The player info.
     * @return The recorded sessions.
     */
    public Set<Session> getSessions(PlayerInfo info) {
        return players.getSessions(info);
    }

    /**
     * Gets the recorded sessions for a given player.
     * @param uuid The UUID of the player.
     * @return The recorded sessions.
     */
    public Set<Session> getSessions(UUID uuid) {
        return players.getSessions(yesCom.playersHandler.getInfo(uuid));
    }

    /**
     * Gets the recorded deaths for a given player.
     * @param info The player info.
     * @return The recorded deaths.
     */
    public Set<Death> getDeaths(PlayerInfo info) {
        return players.getDeaths(info);
    }

    /**
     * Gets the recorded deaths for a given player.
     * @param uuid The UUID of the player.
     * @return The recorded deaths.
     */
    public Set<Death> getDeaths(UUID uuid) {
        return players.getDeaths(yesCom.playersHandler.getInfo(uuid));
    }

    /**
     * Gets the recorded kills for a given player.
     * @param info The player info.
     * @return The recorded kills.
     */
    public Set<Kill> getKills(PlayerInfo info) {
        return players.getKills(info);
    }

    /**
     * Gets the recorded kills for a given player.
     * @param uuid The UUID of the player.
     * @return The recorded kills.
     */
    public Set<Kill> getKills(UUID uuid) {
        return players.getKills(yesCom.playersHandler.getInfo(uuid));
    }
}
