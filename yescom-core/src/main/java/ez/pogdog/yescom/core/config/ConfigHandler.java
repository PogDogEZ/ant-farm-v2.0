package ez.pogdog.yescom.core.config;

import ez.pogdog.yescom.api.Logging;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Handles configuration for elements of YesCom.
 */
public class ConfigHandler {

    private final Logger logger = Logging.getLogger("yescom.config");

    private final List<IConfig> configurations = new ArrayList<>();
    private final Map<String, Map<String, Object>> values = new HashMap<>();

    private final String configDirectory;

    private long lastAutoSaveTime;

    public ConfigHandler(String configDirectory) {
        this.configDirectory = configDirectory;

        try {
            readConfiguration();
        } catch (IOException error) {
            logger.warning("Couldn't read configuration.");
            logger.throwing(getClass().getSimpleName(), "<init>", error);
        }

        lastAutoSaveTime = System.currentTimeMillis() - 60000;
    }

    private void readConfiguration() throws IOException {
        logger.fine("Reading configurations...");
        File configDirectory = new File(this.configDirectory);
        if (!configDirectory.exists() && !configDirectory.mkdirs()) throw new IOException("Could not create config directory.");

        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW); // Looks nicer imo
        dumperOptions.setPrettyFlow(true);

        Yaml yaml = new Yaml(dumperOptions);

        File[] files = configDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".yml")) {
                    logger.finest(String.format("Reading configuration %s.", file.getName()));
                    try (InputStream inputStream = new FileInputStream(file)) {
                        Map<String, Object> map = yaml.load(inputStream);
                        if (map != null /* && !map.containsValue(null) */) {
                            String name = file.getName().substring(0, file.getName().length() - 4);
                            values.put(name, map);
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

    private void saveConfiguration() throws IOException {
        logger.finer("Saving configurations...");
        File configDirectory = new File(this.configDirectory);
        if (!configDirectory.exists() && !configDirectory.mkdirs()) throw new IOException("Could not create config directory.");

        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW); // Looks nicer imo
        dumperOptions.setPrettyFlow(true);

        Yaml yaml = new Yaml(dumperOptions);

        synchronized (this) {
            values.clear();

            int options = 0;
            for (IConfig configuration : configurations) {
                Map<String, Object> values = new HashMap<>();
                for (Option<?> option : configuration.getOptions(true)) {
                    ++options;
                    values.put(option.name, option.value);
                }

                logger.finest(String.format("Dumping configuration %s (%s).", configuration.getFullIdentifier(), configuration));
                OutputStream outputStream = new FileOutputStream(new File(configDirectory, configuration.getFullIdentifier() + ".yml"));
                yaml.dump(values, new OutputStreamWriter(outputStream));
                outputStream.close();

                this.values.put(configuration.getFullIdentifier(), values);
            }

            logger.finer(String.format("Saved %d options from %d configurations.", options, configurations.size()));
        }
    }

    public void tick() {
        if (System.currentTimeMillis() - lastAutoSaveTime > 120000) {
            new Thread(() -> {
                try {
                    saveConfiguration();
                } catch (IOException error) {
                    logger.warning(String.format("Couldn't save configuration: %s.", error.getMessage()));
                    logger.throwing(getClass().getSimpleName(), "tick", error);
                }
            }).start();
            lastAutoSaveTime = System.currentTimeMillis();
        }
    }

    /* ------------------------------ Public API ------------------------------ */

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
                logger.finer(String.format("Populating %d values for configuration %s.", values.size(), configuration.getFullIdentifier()));
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
