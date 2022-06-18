package ez.pogdog.yescom.ui;

import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.core.util.Bootstrap;
import jep.Interpreter;
import jep.MainInterpreter;
import jep.PyConfig;
import jep.Run;
import jep.SharedInterpreter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Main UI class.
 */
public class Main {

    private static boolean deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) deleteDirectory(file);
        }
        return directory.delete();
    }

    public static void main(String[] args) {
        Logger logger = Logging.getLogger("yescom.ui");
        logger.setLevel(Level.ALL); // FIXME: Remove, just testing

        logger.fine("Bootstrapping jep...");
        PyConfig config = new PyConfig();
        MainInterpreter.setInitParams(config);

        // Python should be bootstrapped at this point, and YesCom should be running
        logger.info("Starting YesCom UI...");

        Interpreter interpreter = new SharedInterpreter();

        String jarPath = "";
        File yesComNatives = null;
        try {
            jarPath = Bootstrap.findJar();
            yesComNatives = new File(System.getProperty("java.io.tmpdir"), "yescom-natives");
            if (!yesComNatives.exists() && !yesComNatives.mkdirs()) throw new IOException("Couldn't create temp dir.");

            ZipFile thisFile = new ZipFile(jarPath);
            Enumeration<? extends ZipEntry> entries = thisFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!entry.isDirectory() && (name.startsWith("yescom/") || name.startsWith("pyqtconsole/"))) {
                    File outputFile = new File(yesComNatives, entry.getName());
                    logger.fine(String.format("Extracting %s -> %s", entry.getName(), outputFile.getName()));

                    if (!outputFile.exists()) {
                        File parent = outputFile.getParentFile();
                        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Couldn't create output directory.");
                        if (!outputFile.createNewFile()) throw new IOException("Couldn't create output file.");
                    }

                    FileOutputStream outputStream = new FileOutputStream(outputFile);
                    thisFile.getInputStream(entry).transferTo(outputStream);
                    outputStream.close();
                }
            }
            thisFile.close();

            File finalYesComNatives = yesComNatives;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Cleaning up...");
                if (finalYesComNatives.exists()) deleteDirectory(finalYesComNatives);
            }));

        } catch (Exception error) {
            logger.severe("Couldn't start YesCom: " + error.getMessage());
            logger.throwing(Main.class.getSimpleName(), "main", error);
            System.exit(1);
        }
        interpreter.exec("from sys import path");
        interpreter.invoke("path.append", yesComNatives.getAbsolutePath());
        // interpreter.invoke("path.append", jarPath);

        // YesCom.getInstance().start();

        interpreter.exec("from yescom.ui import main");
        interpreter.invoke("main", Arrays.asList(args), jarPath);
    }
}