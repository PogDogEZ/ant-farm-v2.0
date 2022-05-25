package ez.pogdog.yescom.api.data.chat;

import ez.pogdog.yescom.api.Globals;
import ez.pogdog.yescom.api.data.player.PlayerInfo;

import java.util.Date;
import java.util.UUID;

/**
 * More constantiam-specific, a party chat message.
 */
public class PartyMessage extends ChatMessage {

    public final PlayerInfo sender;
    public final String actualMessage;

    public PartyMessage(long timestamp, UUID receiver, String message, PlayerInfo sender, String actualMessage) {
        super(timestamp, receiver, message);

        this.sender = sender;
        this.actualMessage = actualMessage;
    }

    @Override
    public String toString() {
        return String.format("PartyMessage(timestamp=%s, sender=%s, message=%s)",
                Globals.DATE_FORMAT.format(new Date(timestamp)), sender, actualMessage);
    }

    @Override
    public Type getType() {
        return Type.PARTY;
    }
}
