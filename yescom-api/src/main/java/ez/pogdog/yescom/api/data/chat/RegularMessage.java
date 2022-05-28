package ez.pogdog.yescom.api.data.chat;

import ez.pogdog.yescom.api.Globals;
import ez.pogdog.yescom.api.data.player.PlayerInfo;

import java.util.Date;
import java.util.UUID;

/**
 * A regular message sent by a player.
 */
public class RegularMessage extends ChatMessage {

    public final UUID sender;
    public final String actualMessage;

    public RegularMessage(long timestamp, UUID receiver, String message, UUID sender, String actualMessage) {
        super(timestamp, receiver, message);

        this.sender = sender;
        this.actualMessage = actualMessage;
    }

    @Override
    public String toString() {
        return String.format("RegularMessage(timestamp=%s, sender=%s, message=%s)",
                Globals.DATE_FORMAT.format(new Date(timestamp)), sender, actualMessage);
    }

    @Override
    public Type getType() {
        return Type.REGULAR;
    }
}
