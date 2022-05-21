package ez.pogdog.yescom.api.data;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Stores information about a player.
 */
public class PlayerInfo implements Cloneable {

    public final List<Server> servers = new ArrayList<>();
    public final Set<Session> sessions = new HashSet<>();

    public final UUID uuid;
    public final long firstSeen;

    public String username = "";
    public String skinURL = "";
    public int ping = 0;
    public GameMode gameMode = GameMode.SURVIVAL;

    public PlayerInfo(UUID uuid, long firstSeen, String username) {
        this.uuid = uuid;
        this.firstSeen = firstSeen;
        this.username = username;
    }

    public PlayerInfo(UUID uuid, long firstSeen) {
        this.uuid = uuid;
        this.firstSeen = firstSeen;
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
    public String toString() {
        return String.format("Player(uuid=%s, firstSeen=%s, username=%s)", uuid,
                new SimpleDateFormat("dd/MM/yyyy hh:mm:ss").format(new Date(firstSeen)), username);
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

    public static class Server {

        public final String hostname;
        public final int port;

        public Server(String hostname, int port) {
            this.hostname = hostname;
            this.port = port;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            Server server = (Server)other;
            return port == server.port && hostname.equals(server.hostname);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hostname, port);
        }

        @Override
        public String toString() {
            return String.format("Server(hostname=%s, port=%d)", hostname, port);
        }
    }

    /**
     * A session represents a time in which a player was online at.
     */
    public static class Session {

        public final Server server;
        public final long start;
        public final long end;

        public Session(Server server, long start, long end) {
            this.server = server;
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            Session session = (Session)other;
            return start == session.start && end == session.end && server.equals(session.server);
        }

        @Override
        public int hashCode() {
            return Objects.hash(server, start, end);
        }

        @Override
        public String toString() {
            DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss");
            return String.format("Session(server=%s, start=%s, end=%s)", server, dateFormat.format(new Date(start)),
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
