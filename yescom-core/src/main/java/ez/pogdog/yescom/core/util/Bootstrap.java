package ez.pogdog.yescom.core.util;

import ez.pogdog.yescom.YesCom;

import java.io.File;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class Bootstrap {
    /**
     * Finds the location of the current jar file.
     * @return The location of the jar file.
     * @throws Exception If the location cannot be found.
     */
    public static String findJar() throws Exception {
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
}
