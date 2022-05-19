package ez.pogdog.yescom.core.data;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.config.Option;
import ez.pogdog.yescom.core.util.PositionInputStream;
import ez.pogdog.yescom.core.util.Serial;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Caches a mapping of UUID -> player names.
 */
public class UUIDCache extends Thread implements Map<UUID, String>, IConfig {

    private final Logger logger = Logging.getLogger("yescom.core.data");
    private final YesCom yesCom = YesCom.getInstance();

    /* ------------------------------ Options ------------------------------ */

    public final Option<Integer> MAX_CACHE = new Option<>(
            "Max cache",
            "The maximum number of UUID mappings to cache in memory.",
            512
    );

    /* ------------------------------ Other fields ------------------------------ */

    private final Map<UUID, String> cached = new LinkedHashMap<>();
    private final Map<UUID, String> dirty = new HashMap<>();

    private final File uuidFile;
    private boolean cacheEnabled;

    private /* final */ InputStream inputStream;
    private /* final */ OutputStream outputStream;

    private int size = 0;

    public UUIDCache() {
        uuidFile = yesCom.dataHandler.getUUIDFile();

        try {
            if ((!uuidFile.exists() || uuidFile.isDirectory()) && !uuidFile.createNewFile())
                throw new IOException("Could not create UUID file.");
        } catch (IOException error) {
            logger.warning("Couldn't setup UUID cache: " + error.getMessage());
            logger.throwing(getClass().getSimpleName(), "<init>", error);
        }

        cacheEnabled = uuidFile.exists() && !uuidFile.isDirectory();
        if (cacheEnabled) {
            try {
                inputStream = new PositionInputStream(new FileInputStream(uuidFile));
                outputStream = new FileOutputStream(uuidFile, true);

                inputStream.mark(0); // Position 0
                readInitial();

            } catch (IOException error) {
                logger.warning("Couldn't read initial UUID cache.");
                logger.throwing(getClass().getSimpleName(), "<init>", error);

                cacheEnabled = false;
            }

            if (cacheEnabled) start();
        }
    }

    @Override
    public void run() {
        while (yesCom.isRunning()) {
            try {
                Thread.sleep(2500);
            } catch (InterruptedException ignored) {
            }

            checkSize();
            if (!dirty.isEmpty()) {
                Map<UUID, String> dirty;
                synchronized (this.dirty) {
                    synchronized (cached) {
                        cached.putAll(this.dirty);
                    }
                    dirty = new HashMap<>(this.dirty);
                    this.dirty.clear();
                    synchronized (uuidFile) {
                        try {
                            for (Map.Entry<UUID, String> entry : dirty.entrySet()) {
                                Serial.Write.writeUUID(entry.getKey(), outputStream); // FIXME: Doesn't account for duplicates
                                Serial.Write.writeString(entry.getValue(), outputStream);
                            }
                            outputStream.flush();

                        } catch (IOException error) {
                            logger.warning("Couldn't write to UUID cache.");
                            logger.throwing(getClass().getSimpleName(), "run", error);
                        }
                    }
                }
            }
        }

        try {
            inputStream.close();
            outputStream.close();
        } catch (IOException ignored) { // Oh well
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean containsKey(Object key) {
        if (!(key instanceof UUID)) return false;
        if (cached.containsKey(key) || dirty.containsKey(key)) return true;

        if (cacheEnabled) {
            synchronized (uuidFile) {
                try {
                    inputStream.reset();
                } catch (IOException error) {
                    logger.warning("Couldn't reset UUID cache input stream: " + error.getMessage());
                    logger.throwing(getClass().getSimpleName(), "containsKey", error);
                    return false;
                }

                while (true) {
                    try {
                        if (key.equals(Serial.Read.readUUID(inputStream))) return true;
                        inputStream.skip(Serial.Read.readInteger(inputStream)); // No need to decode the name
                    } catch (IOException error) {
                        break;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        if (!(value instanceof String)) return false;
        if (cached.containsValue(value) || dirty.containsValue(value)) return true;

        if (cacheEnabled) {
            synchronized (uuidFile) {
                try {
                    inputStream.reset();
                } catch (IOException error) {
                    logger.warning("Couldn't reset UUID cache input stream: " + error.getMessage());
                    logger.throwing(getClass().getSimpleName(), "containsValue", error);
                    return false;
                }

                while (true) {
                    try {
                        inputStream.skip(16); // No need to decode the UUID
                        if (value.equals(Serial.Read.readString(inputStream))) return true;
                    } catch (IOException error) {
                        break;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public String getOrDefault(Object key, String defaultValue) {
        if (!(key instanceof UUID)) return null;
        if (cached.containsKey(key)) {
            synchronized (cached) {
                return cached.get(key);
            }
        }
        if (dirty.containsKey(key)) {
            synchronized (dirty) {
                return dirty.get(key);
            }
        }

        if (cacheEnabled) {
            synchronized (uuidFile) {
                try {
                    inputStream.reset();
                } catch (IOException error) {
                    logger.warning("Couldn't reset UUID cache input stream: " + error.getMessage());
                    logger.throwing(getClass().getSimpleName(), "getOrDefault", error);
                    return null;
                }

                while (true) {
                    try {
                        if (key.equals(Serial.Read.readUUID(inputStream))) {
                            String value = Serial.Read.readString(inputStream);
                            synchronized (cached) {
                                cached.put((UUID)key, value);
                            }
                            return value;
                        }
                        inputStream.skip(Serial.Read.readInteger(inputStream)); // No need to decode the name
                    } catch (IOException error) {
                        break;
                    }
                }
            }
        }

        return defaultValue;
    }

    @Override
    public String get(Object key) {
        return getOrDefault(key, null);
    }

    @Override
    public String put(UUID uuid, String name) {
        synchronized (dirty) {
            if (getOrDefault(uuid, null) == null) {
                ++size;
                return dirty.put(uuid, name);
            }
        }
        return name;
    }

    @Override
    public String remove(Object o) {
        throw new IllegalStateException("Cannot remove UUIDs from cache.");
    }

    @Override
    public void putAll(Map<? extends UUID, ? extends String> map) {
        synchronized (dirty) {
            dirty.putAll(map);
        }
    }

    @Override
    public void clear() {
        throw new IllegalStateException("Cannot clear UUID cache.");
    }

    @Override
    public Set<UUID> keySet() {
        return null;
    }

    @Override
    public Collection<String> values() {
        return null;
    }

    @Override
    public Set<Entry<UUID, String>> entrySet() {
        return null;
    }

    @Override
    public String getIdentifier() {
        return "uuid-cache";
    }

    @Override
    public IConfig getParent() {
        return yesCom.dataHandler;
    }

    /**
     * Reads the initial UUID cache from disk.
     */
    private void readInitial() throws IOException {
        logger.fine("Reading initial UUID cache...");

        while (true) {
            try {
                UUID uuid = Serial.Read.readUUID(inputStream);
                String name = Serial.Read.readString(inputStream);

                ++size;
                // No need to do anything here synchronized, it'll only be called in the constructor
                cached.put(uuid, name);

            } catch (IOException error) {
                break;
            }

            checkSize();
        }

        logger.fine(String.format("Read %d initial UUID mapping(s).", size));
    }

    private void checkSize() {
        Iterator<UUID> iterator = cached.keySet().iterator();
        synchronized (cached) {
            while (cached.size() > MAX_CACHE.value) iterator.remove();
        }
    }
}
