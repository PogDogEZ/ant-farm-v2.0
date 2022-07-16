package ez.pogdog.yescom.core.config;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.Globals;
import ez.pogdog.yescom.core.ITickable;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Handles configuration for elements of YesCom.
 */
public class ConfigHandler implements ITickable {

    private final Logger logger = Logging.getLogger("yescom.core.config");
    private final YesCom yesCom = YesCom.getInstance();

    private final List<IConfig> configurations = new ArrayList<>();
    private final Map<String, Map<String, Object>> values = new HashMap<>();

    private final String configDirectory;

    private long lastAutoSaveTime;

    public ConfigHandler(String configDirectory) {
        this.configDirectory = configDirectory;

        yesCom.slowAsyncUpdater.tickables.add(this);

        try {
            readConfiguration();
        } catch (IOException error) {
            logger.warning("Couldn't read configuration.");
            logger.throwing(getClass().getSimpleName(), "<init>", error);
        }

        lastAutoSaveTime = System.currentTimeMillis() - 60000;
    }

    @Override
    public void tick() {
        if (System.currentTimeMillis() - lastAutoSaveTime > 120000) {
            // new Thread(() -> {
            try {
                saveConfiguration(); // Honestly don't think this is slow enough to warrant a separate thread
            } catch (IOException error) {
                logger.warning(String.format("Couldn't save configuration: %s.", error.getMessage()));
                logger.throwing(getClass().getSimpleName(), "run", error);
                return; // Don't spam console
            }
            // }).start();
            lastAutoSaveTime = System.currentTimeMillis();
        }
    }

    /* ------------------------------ Public API ------------------------------ */

    /**
     * Reads the {@link IConfig}s from the disk.
     * @throws IOException If they could not be read for any reason.
     */
    public void readConfiguration() throws IOException {
        logger.fine("Reading configurations...");
        File configDirectory = new File(this.configDirectory);
        if (!configDirectory.exists() && !configDirectory.mkdirs()) throw new IOException("Could not create config directory.");

        File[] files = configDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".yml")) {
                    logger.finest(String.format("Reading configuration %s.", file.getName()));
                    try (InputStream inputStream = new FileInputStream(file)) {
                        Map<String, Object> map = Globals.YAML.load(inputStream);
                        if (map != null /* && !map.containsValue(null) */) {
                            String name = file.getName().substring(0, file.getName().length() - 4);
                            Map<String, Object> options = values.computeIfAbsent(name, name1 -> new HashMap<>());
                            for (Map.Entry<String, Object> entry : map.entrySet()) {
                                if (entry.getKey() != null && entry.getValue() != null) options.put(entry.getKey(), entry.getValue());
                            }
                        } else {
                            logger.warning(String.format("Couldn't read configuration %s.", file.getName()));
                        }
                    } catch (IOException error) {
                        logger.warning(String.format("Couldn't read configuration %s.", file.getName()));
                        logger.throwing(getClass().getSimpleName(), "readConfiguration", error);
                    }
                }
            }
        }
    }

    /**
     * Writes the {@link IConfig}s to the disk.
     * @throws IOException If they could not be written for any reason.
     */
    public void saveConfiguration() throws IOException {
        logger.finer("Saving configurations...");
        File configDirectory = new File(this.configDirectory);
        if (!configDirectory.exists() && !configDirectory.mkdirs()) throw new IOException("Could not create config directory.");

        synchronized (this) {
            values.clear();

            long start = System.currentTimeMillis();
            int options = 0;
            for (IConfig configuration : configurations) {
                Map<String, Object> values = new HashMap<>();
                for (Option<?> option : configuration.getOptions(true)) {
                    ++options;
                    values.put(option.name, option.value);
                }

                logger.finest(String.format("Dumping configuration %s (%s).", configuration.getFullIdentifier(), configuration));
                OutputStream outputStream = new FileOutputStream(new File(configDirectory, configuration.getFullIdentifier() + ".yml"));
                Globals.YAML.dump(values, new OutputStreamWriter(outputStream));
                outputStream.close();

                this.values.put(configuration.getFullIdentifier(), values);
            }

            logger.finer(String.format("Saved %d option(s) from %d configuration(s) in %dms.", options,
                    configurations.size(), System.currentTimeMillis() - start));
        }
    }

    /**
     * Adds a configuration to the known configurations. If the data from the configuration has been loaded from disk,
     * it will populate the option values with it.
     * @param configuration The configuration to add.
     */
    @SuppressWarnings("rawtypes")
    public synchronized void addConfiguration(IConfig configuration) {
        if (!configurations.contains(configuration)) {
            logger.finer(String.format("Adding configuration %s with identifier: %s.", configuration, configuration.getFullIdentifier()));
            configurations.add(configuration);

            Map<String, Object> values = this.values.get(configuration.getFullIdentifier());
            if (values != null) {
                logger.finer(String.format("Populating %d value(s) for configuration %s.", values.size(), configuration.getFullIdentifier()));
                for (Option option : configuration.getOptions(true)) option.value = values.get(option.name);
                values.clear();
            } else {
                values = new HashMap<>();
            }

            for (Option<?> option : configuration.getOptions(true)) values.put(option.name, option.value);
            logger.finer(String.format("%d values found for configuration %s.", values.size(), configuration.getFullIdentifier()));
            this.values.put(configuration.getFullIdentifier(), values);
        }
    }
}
