package ez.pogdog.yescom.api.data;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Stores information about a player.
 */
public class PlayerInfo implements Cloneable {

    public final Set<Session> sessions = new HashSet<>();

    public final UUID uuid;

    public String username = "";
    public String skinURL = "";
    public int ping = 0;
    public GameMode gameMode = GameMode.SURVIVAL;

    public PlayerInfo(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username;
    }

    public PlayerInfo(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        PlayerInfo that = (PlayerInfo)other;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid); // Everything else is subject to change
    }

    @Override
    public PlayerInfo clone() {
        try {
            PlayerInfo clone = (PlayerInfo)super.clone();
            clone.username = username;
            clone.skinURL = skinURL;
            return clone;
        } catch (CloneNotSupportedException error) {
            throw new IllegalStateException(error);
        }
    }

    /* ------------------------------ Classes ------------------------------ */

    /**
     * Player game mode.
     */
    public enum GameMode {
        SURVIVAL,
        CREATIVE,
        ADVENTURE,
        SPECTATOR;
    }

    /**
     * A session represents a time in which a player was online at.
     */
    public static class Session {

        public final long start;
        public final long end;

        public Session(long start, long end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            Session session = (Session)other;
            return start == session.start && end == session.end;
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end);
        }

        @Override
        public String toString() {
            DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss");
            return String.format("Session(start=%s, end=%s)", dateFormat.format(new Date(start)),
                    dateFormat.format(new Date(end)));
        }

        /**
         * @return The time played in this session, in milliseconds.
         */
        public int getPlayTime() {
            return (int)(end - start);
        }
    }
}
