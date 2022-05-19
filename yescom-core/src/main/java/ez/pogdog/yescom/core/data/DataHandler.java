package ez.pogdog.yescom.core.data;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.core.config.IConfig;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Handles data management.
 */
public class DataHandler implements IConfig {

    private final Logger logger = Logging.getLogger("yescom.core.data");
    private final YesCom yesCom = YesCom.getInstance();

    private final String dataDirectory;

    private long lastAutoSaveTime;

    public DataHandler(String dataDirectory) {
        this.dataDirectory = dataDirectory;

        try {
            readDatabase();
        } catch (IOException error) {
            logger.warning("Couldn't read local database.");
            logger.throwing(getClass().getSimpleName(), "<init>", error);
        }

        lastAutoSaveTime = System.currentTimeMillis() - 60000;
    }

    @Override
    public String getIdentifier() {
        return "data";
    }

    @Override
    public IConfig getParent() {
        return yesCom;
    }

    private void readDatabase() throws IOException {
        logger.fine("Reading local database...");
        File dataDirectory = new File(this.dataDirectory);
        if (!dataDirectory.exists() && !dataDirectory.mkdirs()) throw new IOException("Could not create data directory.");
    }

    private void saveDatabase() throws IOException {
        logger.fine("Saving local database...");
        File dataDirectory = new File(this.dataDirectory);
        if (!dataDirectory.exists() && !dataDirectory.mkdirs()) throw new IOException("Could not create data directory.");
    }

    public void tick() {
        if (System.currentTimeMillis() - lastAutoSaveTime > 120000) {
            new Thread(() -> {
                try {
                    saveDatabase();
                } catch (IOException error) {
                    logger.warning(String.format("Couldn't save database: %s.", error.getMessage()));
                    logger.throwing(getClass().getSimpleName(), "tick", error);
                }
            }).start();
            lastAutoSaveTime = System.currentTimeMillis();
        }
    }

    /* ------------------------------ Public API ------------------------------ */

    /**
     * @return The file in which the UUID -> name cache is stored.
     */
    public File getUUIDFile() {
        return new File(dataDirectory, "uuids.bin");
    }
}
