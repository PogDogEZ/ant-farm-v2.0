package ez.pogdog.yescom.api;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.*;

/**
 * Utility class for setting up loggers.
 */
public class Logging {

    private static final Map<Level, String> LEVEL_COLOURS = new HashMap<>();
    private static final ConsoleHandler mainHandler = new ConsoleHandler();

    static {
        LEVEL_COLOURS.put(Level.SEVERE, "\u001b[31m"); // FG red
        LEVEL_COLOURS.put(Level.WARNING, "\u001b[33m"); // FG yellow
        LEVEL_COLOURS.put(Level.INFO, "\u001b[0m");
        LEVEL_COLOURS.put(Level.CONFIG, "\u001b[0m");
        LEVEL_COLOURS.put(Level.FINE, "\u001b[34m"); // FG blue
        LEVEL_COLOURS.put(Level.FINER, "\u001b[34m");
        LEVEL_COLOURS.put(Level.FINEST, "\u001b[34m");

        mainHandler.setLevel(Level.ALL);
        mainHandler.setFormatter(new Formatter() {
            @Override
            @SuppressWarnings("StringConcatenationInLoop")
            public String format(LogRecord record) {
                String message = String.format("%s[%tT] [%s] %s %s%n", LEVEL_COLOURS.get(record.getLevel()),
                        record.getMillis(), record.getLevel(), record.getMessage(), "\u001b[0m");

                if (record.getThrown() != null) {
                    message += String.format("%s%s %n", LEVEL_COLOURS.get(Level.SEVERE), record.getThrown());
                    for (StackTraceElement element : record.getThrown().getStackTrace())
                        message += String.format("\tat %s.%s(%s:%d) %n", element.getClassName(), element.getMethodName(),
                                element.getFileName(), element.getLineNumber());
                    message += "\u001b[0m";
                }

                return message;
            }
        });
    }

    /**
     * Gets a correctly formatted logger given the name. Similar to {@link Logger#getLogger(String)}.
     * @param name The name of the logger.
     * @return The logger, duh.
     */
    public static Logger getLogger(String name) {
        Logger logger = Logger.getLogger(name);

        logger.setUseParentHandlers(false);
        for (Handler handler : logger.getHandlers()) { // Don't add the main handler twice
            if (handler.equals(mainHandler)) return logger;
        }
        logger.addHandler(mainHandler);

        return logger;
    }
}
