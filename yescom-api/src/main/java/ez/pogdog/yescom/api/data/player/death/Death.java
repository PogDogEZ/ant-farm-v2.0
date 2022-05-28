package ez.pogdog.yescom.api.data.player.death;

import ez.pogdog.yescom.api.Globals;
import ez.pogdog.yescom.api.data.player.PlayerInfo;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * When a {@link PlayerInfo} dies.
 */
public class Death {

    public final PlayerInfo.ServerInfo server;
    public final long timestamp;
    public final Type type;
    public final UUID killer;

    public Death(PlayerInfo.ServerInfo server, long timestamp, Type type, UUID killer) {
        this.server = server;
        this.timestamp = timestamp;
        this.type = type;
        this.killer = killer;
    }

    public Death(PlayerInfo.ServerInfo server, long timestamp, Type type) {
        this(server, timestamp, type, null);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        Death death = (Death)other;
        return timestamp == death.timestamp && server.equals(death.server) && Objects.equals(killer, death.killer) && type == death.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(server, timestamp, killer, type);
    }

    @Override
    public String toString() {
        return String.format("Death(server=%s, timestamp=%s, type=%s, killer=%s)", server,
                Globals.DATE_FORMAT.format(new Date(timestamp)), type, killer);
    }

    /**
     * The type of death that occurred.
     */
    public enum Type {
        FIRE,
        DROWNING,
        STARVING,
        FALLING,
        SUFFOCATION,
        CRUSHING,
        WITHER,
        SLASH_KILL,
        MAGIC,

        MELEE,
        CRYSTAL,
        ELYTRA,
        OUT_OF_WORLD,

        ENDER_DRAGON,
        GENERIC;
    }
}
