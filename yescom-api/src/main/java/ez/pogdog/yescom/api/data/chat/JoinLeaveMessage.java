package ez.pogdog.yescom.api.data.chat;

import ez.pogdog.yescom.api.Globals;
import ez.pogdog.yescom.api.data.player.PlayerInfo;

import java.util.Date;
import java.util.UUID;

/**
 * Occurs when a player joins or leaves the game.
 */
public class JoinLeaveMessage extends ChatMessage {

    public final UUID player;
    public final boolean joining;

    public JoinLeaveMessage(long timestamp, UUID receiver, String message, UUID player, boolean joining) {
        super(timestamp, receiver, message);

        this.player = player;
        this.joining = joining;
    }

    @Override
    public String toString() {
        return String.format("JoinLeaveMessage(timestamp=%s, player=%s, joined=%s)",
                Globals.DATE_FORMAT.format(new Date(timestamp)), player, joining);
    }

    @Override
    public Type getType() {
        return Type.JOIN_LEAVE;
    }
}
