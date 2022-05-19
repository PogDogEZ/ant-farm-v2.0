package ez.pogdog.yescom;

import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.core.Emitters;
import ez.pogdog.yescom.core.account.AccountHandler;
import ez.pogdog.yescom.core.config.ConfigHandler;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.connection.PlayersHandler;
import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.data.DataHandler;
import ez.pogdog.yescom.core.query.IsLoadedQuery;
import ez.pogdog.yescom.core.query.invalidmove.InvalidMoveQuery;
import ez.pogdog.yescom.core.util.Bootstrap;
import jep.Interpreter;
import jep.MainInterpreter;
import jep.PyConfig;
import jep.SharedInterpreter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * YesCom(2) - an anarchy coordinate exploit.
 */
public class YesCom extends Thread implements IConfig {

    private static YesCom instance;

    public static YesCom getInstance() {
        return instance;
    }

    public static void main(String[] args, boolean standalone) {
        Logger logger = Logging.getLogger("yescom.core");
        logger.info("Welcome to YesCom \ud83d\ude08!!!!");

        Options options = new Options();

        Option logLevelOpt = new Option("l", "log-level", true, "The log level.");
        logLevelOpt.setType(Level.class);
        options.addOption(logLevelOpt);

        Option accountsFileOpt = new Option("af", "accounts-file", true, "The path to the accounts file.");
        accountsFileOpt.setArgName("path");
        accountsFileOpt.setType(String.class);
        options.addOption(accountsFileOpt);

        Option configDirOpt = new Option("cd", "config-directory", true, "The path to the config directory.");
        configDirOpt.setArgName("path");
        configDirOpt.setType(String.class);
        options.addOption(configDirOpt);

        Option dataDirOpt = new Option("dd", "data-directory", true, "The path to the data directory.");
        dataDirOpt.setArgName("path");
        configDirOpt.setType(String.class);
        options.addOption(dataDirOpt);

        Option hostOpt = new Option("h", "host", true, "The host IP to connect to.");
        // hostOpt.setRequired(true);
        hostOpt.setType(String.class);
        options.addOption(hostOpt);

        Option portOpt = new Option("p", "port", true, "The host port to connect to.");
        portOpt.setType(Integer.class);
        options.addOption(portOpt);

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException error) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("yescom", options);
            System.exit(0);
        }

        String logLevel = cmd.getOptionValue("log-level");
        String accountsFile = cmd.getOptionValue("accounts-file");
        String configDirectory = cmd.getOptionValue("config-directory");
        String dataDirectory = cmd.getOptionValue("data-directory");
        String host = cmd.getOptionValue("host");
        String port = cmd.getOptionValue("port");

        try {
            Level level = Level.parse(logLevel.toUpperCase(Locale.ROOT));

            Arrays.stream(logger.getHandlers()).forEach(handler -> handler.setLevel(level));
            logger.setLevel(level);
            Logger.getLogger("").setLevel(level);

        } catch (IllegalArgumentException | NullPointerException ignored) {
        }

        try {
            new YesCom(
                    accountsFile == null ? "accounts.txt" : accountsFile,
                    configDirectory == null ? "config" : configDirectory,
                    dataDirectory == null ? "data" : dataDirectory
            );
            Runtime.getRuntime().addShutdownHook(new Thread(instance::shutdown));
            if (host != null) {
                int port0 = port != null ? Integer.parseInt(port) : 25565;
                logger.fine(String.format("Found server %s:%d.", host, port0));
                instance.servers.add(new Server(host, port0));
            }

        } catch (Exception error) {
            logger.severe("Couldn't start YesCom: " + error.getMessage());
            logger.throwing(YesCom.class.getSimpleName(), "main", error);
            System.exit(1);
        }

        if (standalone) { // TODO: Proper CLI?
            try {
                logger.fine("Bootstrapping jep...");
                PyConfig config = new PyConfig();
                MainInterpreter.setInitParams(config);

                instance.start();
                instance.join();
            } catch (InterruptedException error) {
                logger.severe("Couldn't join YesCom instance: " + error.getMessage());
                logger.throwing(YesCom.class.getSimpleName(), "main", error);
                System.exit(1);
            }
        }
    }

    public static void main(List<String> args) { // For Python, cos bruh
        main(args.toArray(String[]::new), false);
    }

    public static void main(String[] args) {
        main(args, true);
    }

    private final Logger logger = Logging.getLogger("yescom.core");

    public final List<Server> servers = new ArrayList<>();

    public final AccountHandler accountHandler;
    public final ConfigHandler configHandler;
    public final DataHandler dataHandler;
    public final PlayersHandler playersHandler;

    public /* final */ Interpreter python;

    public final String jarPath;

    private boolean running;
    private boolean initialised;

    public YesCom(String accountsFile, String configDirectory, String dataDirectory) throws Exception {
        instance = this; // Need this to be true for the UI, otherwise threads will exit before initialised
        running = true;
        initialised = false;

        setName("yescom-main-thread");

        logger.fine("Locating jar...");
        jarPath = Bootstrap.findJar();
        logger.fine(String.format("Found jar at %s.", jarPath));

        // servers.add(new Server("constantiam.net", 25565)); // :p
        configHandler = new ConfigHandler(configDirectory);

        accountHandler = new AccountHandler(accountsFile);
        playersHandler = new PlayersHandler();

        dataHandler = new DataHandler(dataDirectory);

        configHandler.addConfiguration(this);
        configHandler.addConfiguration(playersHandler);
    }

    @Override
    public void run() {
        running = true;

        python = new SharedInterpreter();
        python.exec("from sys import path"); // Bruh
        python.invoke("path.append", jarPath);

        for (Server server : servers) server.tick(); // Tick initial servers, auth accounts

        initialised = true;
        logger.fine("YesCom initialised, let the chaos begin.");

        while (running) {
            long start = System.currentTimeMillis();

            Emitters.ON_PRE_TICK.emit();
            for (Server server : servers) server.tick();
            Emitters.ON_POST_TICK.emit();

            long elapsed = System.currentTimeMillis() - start;
            if (elapsed < 50) {
                try {
                    Thread.sleep(50 - elapsed);
                } catch (InterruptedException ignored) {
                }
            } else if (elapsed > 50) {
                logger.warning(String.format("Tick took %dms!", elapsed));
            }
        }
    }

    @Override
    public String getIdentifier() {
        return "root";
    }

    @Override
    public IConfig getParent() {
        return null; // No parent for the root config
    }

    /**
     * Shuts down YesCom.
     */
    public void shutdown() {
        logger.info("Shutting down YesCom \ud83d\udc7f...");
        running = false;

        for (Server server : servers) server.disconnectAll("Shutting down");
    }

    /* ------------------------------ Setters and getters ------------------------------ */

    /**
     * @return Is YesCom running?
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * @return Is YesCom initialised fully?
     */
    public boolean isInitialised() {
        return initialised;
    }
}
