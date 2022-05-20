package ez.pogdog.yescom.core.data;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.PlayerInfo;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.util.Serial;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;

/**
 * Handles data management.
 */
public class DataHandler extends Thread implements IConfig {

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
        start();
    }

    @Override
    public void run() {
        while (yesCom.isRunning()) {
            try {
                Thread.sleep(2500);
            } catch (InterruptedException ignored) {
            }

            if (System.currentTimeMillis() - lastAutoSaveTime > 120000) {
                try {
                    saveDatabase();
                } catch (IOException error) {
                    logger.warning(String.format("Couldn't save local database: %s.", error.getMessage()));
                    logger.throwing(getClass().getSimpleName(), "run", error);
                    return;
                }
                lastAutoSaveTime = System.currentTimeMillis();
            }
        }
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

        File playersFile = new File(dataDirectory, "players.bin");
        if (!playersFile.exists() && !playersFile.createNewFile()) throw new IOException("Could not create players file.");

        logger.finer("Reading player cache...");
        InputStream inputStream = new FileInputStream(playersFile);
        int count = 0;
        try {
            count = Serial.Read.readInteger(inputStream);
        } catch (EOFException ignored) { // First read will be empty if the file didn't exist
        }
        for (int index = 0; index < count; ++index) {
            PlayerInfo info = Serial.Read.readPlayerInfo(inputStream);
            yesCom.playersHandler.playerCache.put(info.uuid, info);
        }
        inputStream.close();
        logger.finer(String.format("Read %d player cache entries.", yesCom.playersHandler.playerCache.size()));
    }

    private void saveDatabase() throws IOException {
        logger.fine("Saving local database...");
        File dataDirectory = new File(this.dataDirectory);
        if (!dataDirectory.exists() && !dataDirectory.mkdirs()) throw new IOException("Could not create data directory.");

        File playersFile = new File(dataDirectory, "players.bin");
        if (!playersFile.exists() && !playersFile.createNewFile()) throw new IOException("Could not create players file.");

        logger.finer("Writing player cache...");
        long start = System.currentTimeMillis();
        OutputStream outputStream = new FileOutputStream(playersFile);
        Serial.Write.writeInteger(yesCom.playersHandler.playerCache.size(), outputStream);
        for (PlayerInfo info : yesCom.playersHandler.playerCache.values()) Serial.Write.writePlayerInfo(info, outputStream);
        outputStream.close();
        logger.finer(String.format("Wrote %d player cache entries in %dms.", yesCom.playersHandler.playerCache.size(),
                System.currentTimeMillis() - start));
    }
}
