package ez.pogdog.yescom.api.data.player.death;

import ez.pogdog.yescom.api.Globals;
import ez.pogdog.yescom.api.data.player.PlayerInfo;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * When a {@link PlayerInfo} kills another.
 */
public class Kill {

    public final PlayerInfo.ServerInfo server;
    public final long timestamp;
    public final UUID victim; // Lol can't really think of a better name for this field :p

    public Kill(PlayerInfo.ServerInfo server, long timestamp, UUID victim) {
        this.server = server;
        this.timestamp = timestamp;
        this.victim = victim;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        Kill kill = (Kill)other;
        return timestamp == kill.timestamp && server.equals(kill.server) && victim.equals(kill.victim);
    }

    @Override
    public int hashCode() {
        return Objects.hash(server, timestamp, victim);
    }

    @Override
    public String toString() {
        return String.format("Kill(server=%s, timestamp=%s, victim=%s)", server,
                Globals.DATE_FORMAT.format(new Date(timestamp)), victim);
    }
}
