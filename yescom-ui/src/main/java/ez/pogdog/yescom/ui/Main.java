package ez.pogdog.yescom.ui;

import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.core.util.Bootstrap;
import jep.Interpreter;
import jep.MainInterpreter;
import jep.PyConfig;
import jep.SharedInterpreter;

import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Main UI class.
 */
public class Main {

    public static void main(String[] args) {
        Logger logger = Logging.getLogger("yescom.ui");

        logger.fine("Bootstrapping jep...");
        PyConfig config = new PyConfig();
        MainInterpreter.setInitParams(config);

        // Python should be bootstrapped at this point, and YesCom should be running
        logger.info("Starting YesCom UI...");

        Interpreter interpreter = new SharedInterpreter();
        interpreter.exec("from sys import path");
        String jarPath = "";
        try {
            jarPath = Bootstrap.findJar();
        } catch (Exception error) {
            logger.severe("Couldn't start YesCom: " + error.getMessage());
            logger.throwing(Main.class.getSimpleName(), "main", error);
            System.exit(1);
        }
        interpreter.invoke("path.append", jarPath);

        // YesCom.getInstance().start();

        interpreter.exec("from yescom.ui import main");
        interpreter.invoke("main", Arrays.asList(args), jarPath);
    }
}