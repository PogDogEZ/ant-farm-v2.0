package ez.pogdog.yescom.ui;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import jep.Interpreter;
import jep.SharedInterpreter;

import java.util.logging.Logger;

public class Main {
    public static void main(String[] args) {
        Logger logger = Logging.getLogger("yescom.ui");

        logger.info("Starting YesCom core...");
        YesCom.main(args, false);

        // Python should be bootstrapped at this point, and YesCom should be running
        logger.info("Starting YesCom UI...");

        Interpreter interpreter = new SharedInterpreter();
        interpreter.exec("from yescom.ui import main");
        interpreter.invoke("main");
    }
}