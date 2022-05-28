package ez.pogdog.yescom.api.data.chat;

import ez.pogdog.yescom.api.Globals;
import ez.pogdog.yescom.api.data.player.PlayerInfo;

import java.util.Date;
import java.util.UUID;

/**
 * Occurs when someone whispers to you, or you whisper to them.
 */
public class WhisperMessage extends ChatMessage {

    public final UUID recipient;
    public final boolean sending;
    public final String actualMessage;

    public WhisperMessage(long timestamp, UUID receiver, String message, UUID recipient, boolean sending, String actualMessage) {
        super(timestamp, receiver, message);

        this.recipient = recipient;
        this.sending = sending;
        this.actualMessage = actualMessage;
    }

    @Override
    public String toString() {
        return String.format("WhisperMessage(timestamp=%s, recipient=%s, sending=%s, message=%s)",
                Globals.DATE_FORMAT.format(new Date(timestamp)), recipient, sending, actualMessage);
    }

    @Override
    public Type getType() {
        return Type.WHISPER;
    }
}
