package ez.pogdog.yescom.api;

import com.google.gson.JsonParser;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * Global instances of useful objects.
 */
public class Globals {

    public static final DateFormat DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss");

    /**
     * YAML instance.
     */
    public static final Yaml YAML;

    /**
     * JSON parser instance.
     */
    public static final JsonParser JSON = new JsonParser();

    static {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW); // Looks nicer imo
        dumperOptions.setPrettyFlow(true);

        YAML = new Yaml(dumperOptions);
    }
}
