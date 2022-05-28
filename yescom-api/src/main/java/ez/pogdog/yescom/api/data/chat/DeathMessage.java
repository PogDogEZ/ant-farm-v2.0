package ez.pogdog.yescom.api.data.chat;

import ez.pogdog.yescom.api.Globals;
import ez.pogdog.yescom.api.data.player.PlayerInfo;
import ez.pogdog.yescom.api.data.player.death.Death;

import java.util.Date;
import java.util.UUID;

/**
 * Occurs when a player dies. May also contain the player that killed that player.
 */
public class DeathMessage extends ChatMessage {

    public final UUID player;
    public final Death death;

    public DeathMessage(long timestamp, UUID receiver, String message, UUID player, Death death) {
        super(timestamp, receiver, message);

        this.player = player;
        this.death = death;
    }

    @Override
    public String toString() {
        return String.format("DeathMessage(timestamp=%s, player=%s, death=%s)",
                Globals.DATE_FORMAT.format(new Date(timestamp)), player, death);
    }

    @Override
    public Type getType() {
        return Type.DEATH;
    }
}
