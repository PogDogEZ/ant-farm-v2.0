package ez.pogdog.yescom.api.data.chat;

import java.util.UUID;

/**
 * Occurs for any command-related message, sort of just a fall through for most cases.
 */
public class CommandMessage extends ChatMessage {

    public CommandMessage(long timestamp, UUID receiver, String message) {
        super(timestamp, receiver, message);
    }

    @Override
    public Type getType() {
        return Type.COMMAND;
    }
}
