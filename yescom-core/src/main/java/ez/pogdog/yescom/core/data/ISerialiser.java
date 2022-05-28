package ez.pogdog.yescom.core.data;

import java.io.File;
import java.io.IOException;

/**
 * Serialises data.
 */
public interface ISerialiser {

    /**
     * Loads the data from the data directory.
     * @param dataDirectory The provided data directory.
     */
    void load(File dataDirectory) throws IOException;

    /**
     * Saves the data to the data directory.
     * @param dataDirectory The provided data directory.
     * @param force Force the file to save.
     */
    void save(File dataDirectory, boolean force) throws IOException;
}
