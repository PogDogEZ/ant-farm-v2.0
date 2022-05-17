package ez.pogdog.yescom.core.util;

import com.github.steveice10.mc.protocol.data.message.Message;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import ez.pogdog.yescom.api.Logging;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class Chat {

    private static final Logger logger = Logging.getLogger("yescom.core.util");
    private static final Map<String, String> COLOURS = new HashMap<>();

    static {
        COLOURS.put("black", "\u001b[7m"); // Inverted
        COLOURS.put("dark_blue", "\u001b[34m"); // Blue
        COLOURS.put("dark_green", "\u001b[32m"); // Green
        COLOURS.put("dark_aqua", "\u001b[36m"); // Cyan
        COLOURS.put("dark_red", "\u001b[31m"); // Red
        COLOURS.put("dark_purple", "\u001b[35m"); // Magenta
        COLOURS.put("gold", "\u001b[33m"); // Yellow
        COLOURS.put("gray", "\u001b[37;1m"); // Bright white
        COLOURS.put("dark_gray", "\u001b[30;1m"); // Bright black
        COLOURS.put("blue", "\u001b[34;1m"); // Bright blue
        COLOURS.put("green", "\u001b[32;1m"); // Bright green
        COLOURS.put("aqua", "\u001b[36;1m"); // Bright cyan
        COLOURS.put("red", "\u001b[31;1m"); // Bright red
        COLOURS.put("light_purple", "\u001b[35;1m"); // Bright magenta
        COLOURS.put("yellow", "\u001b[33;1m"); // Bright yellow
        COLOURS.put("white", "\u001b[0m"); // Reset
    }

    /**
     * Unwraps a chat message.
     * @param message The message to unwrap.
     * @param colour Should ANSI colour be added?
     * @return The unwrapped message.
     */
    public static String unwrap(Message message, boolean colour) {
        StringBuilder builder = new StringBuilder();
        try {
            JsonObject json = message.toJson().getAsJsonObject();
            if (colour) {
                if (json.has("color"))
                    builder.append(COLOURS.getOrDefault(json.get("color").getAsString(), COLOURS.get("white")));
                if (json.has("bold"))
                    builder.append("\u001b[1m"); // Bold
                if (json.has("italic"))
                    builder.append("\u001b[3m"); // Italic
                if (json.has("underlined"))
                    builder.append("\u001b[4m"); // Underlined
                if (json.has("strikethrough"))
                    builder.append("\u001b[9m"); // Strikethrough
            }
            if (json.has("text")) builder.append(json.get("text").getAsString());
            if (json.has("extra")) {
                for (JsonElement element : json.get("extra").getAsJsonArray())
                    builder.append(unwrap(Message.fromJson(element), colour));
            }

            if (colour) builder.append("\u001b[0m"); // Reset

        } catch (IllegalStateException error) {
            logger.warning("Failed to properly unwrap message: " + message);
            return message.getFullText(); // At least try something
        }

        return builder.toString();
    }
}
