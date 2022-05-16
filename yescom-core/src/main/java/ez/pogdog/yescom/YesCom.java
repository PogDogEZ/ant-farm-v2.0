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
import ez.pogdog.yescom.core.query.IsLoadedQuery;
import ez.pogdog.yescom.core.query.invalidmove.InvalidMoveHandle;
import ez.pogdog.yescom.core.query.invalidmove.InvalidMoveQuery;
import jep.Interpreter;
import jep.MainInterpreter;
import jep.PyConfig;
import jep.SharedInterpreter;
import org.apache.commons.cli.*;

import java.io.File;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
                    configDirectory == null ? "config" : configDirectory
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
                instance.join();
            } catch (InterruptedException error) {
                logger.severe("Couldn't join YesCom instance: " + error.getMessage());
                logger.throwing(YesCom.class.getSimpleName(), "main", error);
                System.exit(1);
            }
        }
    }

    public static void main(String[] args) {
        main(args, true);
    }

    private final Logger logger = Logging.getLogger("yescom.core");

    public final List<Server> servers = new ArrayList<>();

    public final AccountHandler accountHandler;
    public final ConfigHandler configHandler;
    public final PlayersHandler playersHandler;

    public /* final */ Interpreter python;

    private final String jarPath;

    private boolean running;

    public YesCom(String accountsFile, String configDirectory) throws Exception {
        instance = this;

        setName("yescom-main-thread");

        logger.fine("Locating jar...");
        jarPath = findJar();
        logger.fine(String.format("Found jar at %s.", jarPath));

        // servers.add(new Server("constantiam.net", 25565)); // :p
        accountHandler = new AccountHandler(accountsFile);
        configHandler = new ConfigHandler(configDirectory);
        playersHandler = new PlayersHandler();

        configHandler.addConfiguration(this);
        configHandler.addConfiguration(playersHandler);

        logger.fine("Bootstrapping jep...");
        PyConfig config = new PyConfig();
        MainInterpreter.setInitParams(config);

        start();

        try {
            Thread.sleep(50); // Wait for the interpreter to start
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public void run() {
        python = new SharedInterpreter();
        python.exec("from sys import path"); // Bruh
        python.invoke("path.append", jarPath);

        running = true;
        while (running) {
            long start = System.currentTimeMillis();

            Emitters.ON_PRE_TICK.emit();
            for (Server server : servers) server.tick();
            configHandler.tick();
            Emitters.ON_POST_TICK.emit();

            long elapsed = System.currentTimeMillis() - start;
            if (elapsed < 50) {
                try {
                    Thread.sleep(50 - elapsed);
                } catch (InterruptedException ignored) {
                }
            } else if (elapsed > 1000) {
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
     * Finds the location of the current jar file.
     * @return The location of the jar file.
     * @throws Exception If the location cannot be found.
     */
    private String findJar() throws Exception {
        URL jarLocation = YesCom.class.getProtectionDomain().getCodeSource().getLocation();
        if (jarLocation == null) throw new Exception("Couldn't find jar location.");

        String jarPath = URLDecoder.decode(jarLocation.getPath(), StandardCharsets.UTF_8.name());
        if (jarPath.startsWith("file:")) jarPath = jarPath.substring(5);
        if (!new File(jarPath).exists() && jarPath.contains("!")) {
            StringBuilder pathBuilder = new StringBuilder();
            boolean exists = false;

            for (String element : jarPath.split("!")) { // Can't believe I have to do this smh
                pathBuilder.append(element);
                if (new File(pathBuilder.toString()).exists()) {
                    exists = true;
                    break;
                }
                pathBuilder.append("!");
            }

            if (!exists) throw new Exception("Couldn't find jar location.");
            jarPath = pathBuilder.toString();
        }

        return jarPath;
    }

    /**
     * Shuts down YesCom.
     */
    public void shutdown() {
        logger.info("Shutting down YesCom \ud83d\udc7f...");
        running = false;

        for (Server server : servers) server.disconnectAll("Shutting down");
    }
}
