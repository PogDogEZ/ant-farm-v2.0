package ez.pogdog.yescom.api.data.chat;

import java.util.UUID;

/**
 * A server status message, i.e. rebooting.
 */
public class StatusMessage extends ChatMessage {

    public StatusMessage(long timestamp, UUID receiver, String message) {
        super(timestamp, receiver, message);
    }

    @Override
    public Type getType() {
        return Type.STATUS;
    }
}
