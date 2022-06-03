package ez.pogdog.yescom.core.util;

import com.github.steveice10.mc.protocol.data.message.Message;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import ez.pogdog.yescom.api.Globals;
import ez.pogdog.yescom.api.Logging;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class MinecraftChat {

    private static final Logger logger = Logging.getLogger("yescom.core.util");

    public static final Map<String, String> ANSI_COLOURS = new HashMap<>();
    public static final Map<String, String> MINECRAFT_COLOURS = new HashMap<>();

    static {
        ANSI_COLOURS.put("black", "\u001b[7m"); // Inverted
        ANSI_COLOURS.put("dark_blue", "\u001b[34m"); // Blue
        ANSI_COLOURS.put("dark_green", "\u001b[32m"); // Green
        ANSI_COLOURS.put("dark_aqua", "\u001b[36m"); // Cyan
        ANSI_COLOURS.put("dark_red", "\u001b[31m"); // Red
        ANSI_COLOURS.put("dark_purple", "\u001b[35m"); // Magenta
        ANSI_COLOURS.put("gold", "\u001b[33m"); // Yellow
        ANSI_COLOURS.put("gray", "\u001b[37;1m"); // Bright white
        ANSI_COLOURS.put("dark_gray", "\u001b[30;1m"); // Bright black
        ANSI_COLOURS.put("blue", "\u001b[34;1m"); // Bright blue
        ANSI_COLOURS.put("green", "\u001b[32;1m"); // Bright green
        ANSI_COLOURS.put("aqua", "\u001b[36;1m"); // Bright cyan
        ANSI_COLOURS.put("red", "\u001b[31;1m"); // Bright red
        ANSI_COLOURS.put("light_purple", "\u001b[35;1m"); // Bright magenta
        ANSI_COLOURS.put("yellow", "\u001b[33;1m"); // Bright yellow
        ANSI_COLOURS.put("white", "\u001b[0m"); // Reset
        ANSI_COLOURS.put("bold", "\u001b[1m");
        ANSI_COLOURS.put("italic", "\u001b[3m");
        ANSI_COLOURS.put("underlined", "\u001b[4m");
        ANSI_COLOURS.put("strikethrough", "\u001b[9m");
        ANSI_COLOURS.put("reset", "\u001b[0m");

        MINECRAFT_COLOURS.put("black", "§0");
        MINECRAFT_COLOURS.put("dark_blue", "§1");
        MINECRAFT_COLOURS.put("dark_green", "§2");
        MINECRAFT_COLOURS.put("dark_aqua", "§3");
        MINECRAFT_COLOURS.put("dark_red", "§4");
        MINECRAFT_COLOURS.put("dark_purple", "§5");
        MINECRAFT_COLOURS.put("gold", "§6");
        MINECRAFT_COLOURS.put("gray", "§7");
        MINECRAFT_COLOURS.put("dark_gray", "§8");
        MINECRAFT_COLOURS.put("blue", "§9");
        MINECRAFT_COLOURS.put("green", "§a");
        MINECRAFT_COLOURS.put("aqua", "§b");
        MINECRAFT_COLOURS.put("red", "§c");
        MINECRAFT_COLOURS.put("light_purple", "§d");
        MINECRAFT_COLOURS.put("yellow", "§e");
        MINECRAFT_COLOURS.put("white", "§f");
        MINECRAFT_COLOURS.put("obfuscated", "§k");
        MINECRAFT_COLOURS.put("bold", "§l");
        MINECRAFT_COLOURS.put("strikethrough", "§m");
        MINECRAFT_COLOURS.put("underline", "§n");
        MINECRAFT_COLOURS.put("italic", "§o");
        MINECRAFT_COLOURS.put("reset", "§r");
    }

    /**
     * Unwraps a chat message.
     * @param element The JSON element to unwrap.
     * @param colours The colours to use.
     * @return The unwrapped message.
     */
    public static String unwrap(JsonElement element, Map<String, String> colours) {
        StringBuilder builder = new StringBuilder();
        try {
            JsonObject json = element.getAsJsonObject();
            if (json.has("color"))
                builder.append(colours.getOrDefault(json.get("color").getAsString(), ""));
            if (json.has("obfuscated"))
                builder.append(colours.getOrDefault("obfuscated", ""));
            if (json.has("bold"))
                builder.append(colours.getOrDefault("bold", ""));
            if (json.has("italic"))
                builder.append(colours.getOrDefault("italic", ""));
            if (json.has("underlined"))
                builder.append(colours.getOrDefault("underlined", ""));
            if (json.has("strikethrough"))
                builder.append(colours.getOrDefault("strikethrough", ""));
            if (json.has("reset"))
            builder.append(colours.getOrDefault("reset", ""));
            if (json.has("text")) builder.append(json.get("text").getAsString());
            if (json.has("extra")) {
                for (JsonElement extraElement : json.get("extra").getAsJsonArray())
                    builder.append(unwrap(extraElement, colours));
            }

            builder.append(colours.getOrDefault("reset", "")); // Reset

        } catch (IllegalStateException error) {
            logger.warning("Failed to properly unwrap message: " + element.toString());
            return element.toString(); // At least try something
        }

        return builder.toString();
    }
}
